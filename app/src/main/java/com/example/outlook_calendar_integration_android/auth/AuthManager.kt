package com.example.outlook_calendar_integration_android.auth

import android.app.Activity
import android.content.Context
import androidx.annotation.RawRes
import com.example.outlook_calendar_integration_android.R
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.CurrentAccountCallback
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.SignOutCallback
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of an MSAL sign-in / token-acquisition attempt.
 */
sealed class AuthResult {
    data class Success(val accessToken: String) : AuthResult()
    data class Error(val message: String, val exception: Exception? = null) : AuthResult()
    data object Cancelled : AuthResult()
}

/**
 * The single method [com.example.outlook_calendar_integration_android.data.CalendarRepository]
 * needs from [AuthManager] for its 401 retry path, pulled out as its own interface so repository
 * unit tests can supply a fake instead of a real MSAL-backed [AuthManager] (which needs a live
 * Android runtime to initialize).
 */
fun interface ReauthProvider {
    suspend fun reauthenticate(): AuthResult
}

/**
 * The subset of [AuthManager] the sign-in and calendar ViewModels need, pulled out as its own
 * interface so their unit tests can supply a fake instead of a real MSAL-backed [AuthManager]
 * (which needs a live Android runtime to initialize).
 */
interface AuthProvider {
    suspend fun signIn(): AuthResult
    suspend fun acquireTokenSilent(): AuthResult
    suspend fun signOut(): Boolean
}

/**
 * Thin coroutine wrapper around MSAL's single-account public client application.
 *
 * Scopes are fixed to read-only calendar + basic profile access (`Calendars.Read User.Read`) --
 * this app never requests write scopes. Tokens are never persisted outside MSAL's own encrypted
 * account cache; [currentAccessToken] is an in-memory cache of the most recently acquired token
 * only, used to attach the bearer header to outgoing Graph requests.
 */
class AuthManager(
    private val appContext: Context,
    @RawRes private val configResId: Int = R.raw.msal_config,
) : ReauthProvider, AuthProvider {

    companion object {
        val SCOPES = arrayOf("Calendars.Read", "User.Read")
    }

    private var singleAccountApp: ISingleAccountPublicClientApplication? = null
    private var activityRef: WeakReference<Activity>? = null

    /** Most recently acquired access token, read synchronously by [com.example.outlook_calendar_integration_android.data.GraphAuthInterceptor]. */
    @Volatile
    var currentAccessToken: String? = null
        private set

    /** Must be called from [android.app.Activity.onCreate] before any interactive auth call. */
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    private suspend fun ensureInitialized(): ISingleAccountPublicClientApplication {
        singleAccountApp?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                appContext,
                configResId,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        singleAccountApp = application
                        if (continuation.isActive) continuation.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    /** True if MSAL has a cached account (a prior successful sign-in) without making a network call. */
    suspend fun hasCachedAccount(): Boolean = getCachedAccount() != null

    private suspend fun getCachedAccount(): IAccount? {
        val app = try {
            ensureInitialized()
        } catch (e: MsalException) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            app.getCurrentAccountAsync(object : CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    if (continuation.isActive) continuation.resume(activeAccount)
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    if (continuation.isActive) continuation.resume(currentAccount)
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
    }

    /** Interactive sign-in. Requires [setActivity] to have been called with a live activity. */
    override suspend fun signIn(): AuthResult {
        val app = try {
            ensureInitialized()
        } catch (e: MsalException) {
            return e.toAuthResult()
        }
        val activity = activityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
            ?: return AuthResult.Error("No active screen to sign in from. Please try again.")

        return suspendCancellableCoroutine { continuation ->
            app.signIn(
                activity,
                null,
                SCOPES,
                object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        currentAccessToken = authenticationResult.accessToken
                        if (continuation.isActive) continuation.resume(AuthResult.Success(authenticationResult.accessToken))
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) continuation.resume(exception.toAuthResult())
                    }

                    override fun onCancel() {
                        if (continuation.isActive) continuation.resume(AuthResult.Cancelled)
                    }
                },
            )
        }
    }

    /** Silent token acquisition using the cached account, if any. */
    override suspend fun acquireTokenSilent(): AuthResult {
        val app = try {
            ensureInitialized()
        } catch (e: MsalException) {
            return e.toAuthResult()
        }
        val account = getCachedAccount()
            ?: return AuthResult.Error("No cached account")
        val authority = account.authority

        return suspendCancellableCoroutine { continuation ->
            app.acquireTokenSilentAsync(
                SCOPES,
                authority,
                object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        currentAccessToken = authenticationResult.accessToken
                        if (continuation.isActive) continuation.resume(AuthResult.Success(authenticationResult.accessToken))
                    }

                    override fun onError(exception: MsalException) {
                        if (continuation.isActive) continuation.resume(exception.toAuthResult())
                    }
                },
            )
        }
    }

    /**
     * Silent-then-interactive re-authentication: tries a silent token refresh first, and only
     * falls back to the interactive sign-in flow if that fails. Used both when the app relaunches
     * with a cached account and when a Graph call comes back 401.
     */
    override suspend fun reauthenticate(): AuthResult {
        val silentResult = acquireTokenSilent()
        if (silentResult is AuthResult.Success) return silentResult
        return signIn()
    }

    override suspend fun signOut(): Boolean {
        val app = singleAccountApp ?: return true
        val result = suspendCancellableCoroutine<Boolean> { continuation ->
            app.signOut(object : SignOutCallback {
                override fun onSignOut() {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
        currentAccessToken = null
        return result
    }

    private fun MsalException.toAuthResult(): AuthResult =
        AuthResult.Error(message ?: "Authentication failed", this)
}
