package com.example.outlook_calendar_integration_android.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the current MSAL access token (if any) as a bearer header. Reads a synchronous token
 * supplier rather than calling into MSAL itself -- [com.example.outlook_calendar_integration_android.auth.AuthManager]
 * keeps [com.example.outlook_calendar_integration_android.auth.AuthManager.currentAccessToken]
 * up to date after every successful sign-in / silent acquisition.
 *
 * If no token is available (e.g. silent acquisition failed and the caller hasn't re-authenticated
 * yet), the request goes out without an Authorization header and naturally comes back 401, which
 * [CalendarRepository] treats identically to an expired token -- both funnel through the same
 * silent-then-interactive retry path.
 */
class GraphAuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = chain.request().let { original ->
            if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }
        }
        return chain.proceed(request)
    }
}
