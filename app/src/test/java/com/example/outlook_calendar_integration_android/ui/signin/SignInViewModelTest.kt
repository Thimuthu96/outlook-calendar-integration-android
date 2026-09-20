package com.example.outlook_calendar_integration_android.ui.signin

import com.example.outlook_calendar_integration_android.auth.AuthProvider
import com.example.outlook_calendar_integration_android.auth.AuthResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake [AuthProvider] whose responses are scripted per method. [acquireTokenSilentDeferred], when
 * supplied, lets a test control exactly when [acquireTokenSilent] resolves -- needed to subscribe
 * to [SignInViewModel.navigateToCalendar] (a one-shot `SharedFlow`, not replayed to late
 * subscribers) before the `init`-triggered silent check completes and emits.
 */
private class FakeAuthProvider(
    private val acquireTokenSilentDeferred: CompletableDeferred<AuthResult>? = null,
    private val acquireTokenSilentResult: AuthResult = AuthResult.Error("no cached account"),
    private var signInResult: AuthResult = AuthResult.Error("not scripted"),
) : AuthProvider {

    fun scriptSignIn(result: AuthResult) {
        signInResult = result
    }

    override suspend fun signIn(): AuthResult = signInResult

    override suspend fun acquireTokenSilent(): AuthResult =
        acquireTokenSilentDeferred?.await() ?: acquireTokenSilentResult

    override suspend fun signOut(): Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private lateinit var collectorScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        collectorScope = CoroutineScope(Dispatchers.Main)
    }

    @After
    fun tearDown() {
        collectorScope.cancel()
        Dispatchers.resetMain()
    }

    // -- silent re-auth on launch --------------------------------------------------------------

    @Test
    fun `cached account present skips sign-in screen and navigates to calendar`() = runTest {
        val silentDeferred = CompletableDeferred<AuthResult>()
        val authProvider = FakeAuthProvider(acquireTokenSilentDeferred = silentDeferred)
        val viewModel = SignInViewModel(authProvider)

        var navigated = false
        collectorScope.launch { viewModel.navigateToCalendar.collect { navigated = true } }

        silentDeferred.complete(AuthResult.Success("cached-token"))
        advanceUntilIdle()

        assertTrue(navigated)
    }

    @Test
    fun `no cached account falls back to the interactive sign-in screen`() = runTest {
        val authProvider = FakeAuthProvider(acquireTokenSilentResult = AuthResult.Error("no cached account"))
        val viewModel = SignInViewModel(authProvider)

        assertEquals(SignInUiState.Idle, viewModel.uiState.value)
    }

    // -- interactive sign-in ---------------------------------------------------------------------

    @Test
    fun `successful interactive sign-in navigates to calendar`() = runTest {
        val authProvider = FakeAuthProvider().apply { scriptSignIn(AuthResult.Success("token")) }
        val viewModel = SignInViewModel(authProvider)

        var navigated = false
        collectorScope.launch { viewModel.navigateToCalendar.collect { navigated = true } }

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertTrue(navigated)
    }

    @Test
    fun `cancelling sign-in stays on the sign-in screen with an inline error`() = runTest {
        val authProvider = FakeAuthProvider().apply { scriptSignIn(AuthResult.Cancelled) }
        val viewModel = SignInViewModel(authProvider)

        viewModel.onSignInClicked()

        val state = viewModel.uiState.value
        assertTrue(state is SignInUiState.Error)
        assertEquals("Sign-in was cancelled.", (state as SignInUiState.Error).message)
    }

    @Test
    fun `sign-in error surfaces its message inline`() = runTest {
        val authProvider = FakeAuthProvider().apply { scriptSignIn(AuthResult.Error("network unavailable")) }
        val viewModel = SignInViewModel(authProvider)

        viewModel.onSignInClicked()

        val state = viewModel.uiState.value
        assertTrue(state is SignInUiState.Error)
        assertEquals("network unavailable", (state as SignInUiState.Error).message)
    }
}
