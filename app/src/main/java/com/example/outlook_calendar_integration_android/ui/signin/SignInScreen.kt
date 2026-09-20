package com.example.outlook_calendar_integration_android.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SignInScreen(
    uiState: SignInUiState,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Outlook Calendar",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.padding(top = 16.dp))

            when (uiState) {
                is SignInUiState.CheckingCachedAccount -> {
                    CircularProgressIndicator()
                }

                is SignInUiState.SigningIn -> {
                    CircularProgressIndicator()
                }

                is SignInUiState.Idle -> {
                    Button(onClick = onSignInClick) {
                        Text("Sign in with Microsoft")
                    }
                }

                is SignInUiState.Error -> {
                    Text(
                        text = uiState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = onSignInClick) {
                        Text("Sign in with Microsoft")
                    }
                }
            }
        }
    }
}
