package com.example.outlook_calendar_integration_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.outlook_calendar_integration_android.ui.nav.AppNav
import com.example.outlook_calendar_integration_android.ui.theme.OutlookCalendarTheme

/**
 * Single-Activity Compose shell. Interactive MSAL calls need a live [android.app.Activity], so
 * [com.example.outlook_calendar_integration_android.auth.AuthManager] is handed a reference to
 * this activity once here rather than threading an Activity through every ViewModel.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OutlookCalendarApp
        app.authManager.setActivity(this)

        setContent {
            OutlookCalendarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(
                        authManager = app.authManager,
                        calendarRepository = app.calendarRepository,
                    )
                }
            }
        }
    }
}
