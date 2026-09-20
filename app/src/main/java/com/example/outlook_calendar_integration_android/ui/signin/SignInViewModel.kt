package com.example.outlook_calendar_integration_android.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outlook_calendar_integration_android.auth.AuthProvider
import com.example.outlook_calendar_integration_android.auth.AuthResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SignInUiState {
    /** Checking for a cached MSAL account before deciding whether to show the sign-in button. */
    data object CheckingCachedAccount : SignInUiState
    data object Idle : SignInUiState
    data object SigningIn : SignInUiState
    data class Error(val message: String) : SignInUiState
}

class SignInViewModel(private val authManager: AuthProvider) : ViewModel() {

    private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.CheckingCachedAccount)
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private val _navigateToCalendar = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToCalendar: SharedFlow<Unit> = _navigateToCalendar.asSharedFlow()

    init {
        checkCachedAccount()
    }

    /** Silent re-auth on launch: skips the sign-in screen if a cached account is still valid. */
    fun checkCachedAccount() {
        viewModelScope.launch {
            _uiState.value = SignInUiState.CheckingCachedAccount
            when (authManager.acquireTokenSilent()) {
                is AuthResult.Success -> _navigateToCalendar.emit(Unit)
                else -> _uiState.value = SignInUiState.Idle
            }
        }
    }

    fun onSignInClicked() {
        viewModelScope.launch {
            _uiState.value = SignInUiState.SigningIn
            when (val result = authManager.signIn()) {
                is AuthResult.Success -> _navigateToCalendar.emit(Unit)
                is AuthResult.Cancelled -> _uiState.value = SignInUiState.Error("Sign-in was cancelled.")
                is AuthResult.Error -> _uiState.value = SignInUiState.Error(result.message)
            }
        }
    }
}
