package com.example.outlook_calendar_integration_android

import android.app.Application
import com.example.outlook_calendar_integration_android.auth.AuthManager
import com.example.outlook_calendar_integration_android.data.CalendarRepository
import com.example.outlook_calendar_integration_android.data.GraphApiService
import com.example.outlook_calendar_integration_android.data.GraphAuthInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit

/**
 * Manual DI container -- no Hilt/Koin in this pass. Builds and owns the singletons the app
 * needs: [AuthManager] (MSAL wrapper) and [CalendarRepository] (Graph client + mapping), wired
 * together with a Retrofit/OkHttp client that attaches the current MSAL access token to every
 * outgoing Graph request via [GraphAuthInterceptor].
 */
class OutlookCalendarApp : Application() {

    lateinit var authManager: AuthManager
        private set

    lateinit var calendarRepository: CalendarRepository
        private set

    override fun onCreate() {
        super.onCreate()

        authManager = AuthManager(applicationContext)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Never log request/response bodies -- they can carry calendar content and tokens
            // must never be logged. Headers are also omitted by BASIC level.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(GraphAuthInterceptor { authManager.currentAccessToken })
            .addInterceptor(loggingInterceptor)
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(GraphApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val graphApiService = retrofit.create(GraphApiService::class.java)
        calendarRepository = CalendarRepository(graphApiService, authManager)
    }
}
