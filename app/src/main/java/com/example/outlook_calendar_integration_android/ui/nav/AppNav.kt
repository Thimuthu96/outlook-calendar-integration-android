package com.example.outlook_calendar_integration_android.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.outlook_calendar_integration_android.auth.AuthManager
import com.example.outlook_calendar_integration_android.data.CalendarRepository
import com.example.outlook_calendar_integration_android.ui.calendar.CalendarScreen
import com.example.outlook_calendar_integration_android.ui.calendar.CalendarViewModel
import com.example.outlook_calendar_integration_android.ui.signin.SignInScreen
import com.example.outlook_calendar_integration_android.ui.signin.SignInViewModel

object Routes {
    const val SIGN_IN = "sign_in"
    const val CALENDAR = "calendar"
}

@Composable
fun AppNav(
    authManager: AuthManager,
    calendarRepository: CalendarRepository,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.SIGN_IN) {
        composable(Routes.SIGN_IN) {
            val viewModel: SignInViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SignInViewModel(authManager) }
                },
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.navigateToCalendar.collect {
                    navController.navigate(Routes.CALENDAR) {
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                }
            }

            SignInScreen(
                uiState = uiState,
                onSignInClick = viewModel::onSignInClicked,
            )
        }

        composable(Routes.CALENDAR) {
            val viewModel: CalendarViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { CalendarViewModel(calendarRepository, authManager) }
                },
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.signedOut.collect {
                    navController.navigate(Routes.SIGN_IN) {
                        popUpTo(Routes.CALENDAR) { inclusive = true }
                    }
                }
            }

            CalendarScreen(
                uiState = uiState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::onRefresh,
                onRetry = viewModel::loadEvents,
                onSignOutClick = viewModel::onSignOutClicked,
            )
        }
    }
}
