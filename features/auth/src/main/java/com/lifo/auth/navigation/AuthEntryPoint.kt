package com.lifo.auth.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lifo.auth.AuthenticationContract
import com.lifo.auth.AuthenticationScreen
import com.lifo.auth.AuthenticationViewModel
import com.lifo.util.Constants.CLIENT_ID
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Public entry point composable for the Authentication feature.
 * Uses Credential Manager for Google Sign-In (shows account picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationRouteContent(
    navigateToHome: () -> Unit,
    onDataLoaded: () -> Unit
) {
    val viewModel: AuthenticationViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(key1 = Unit) {
        onDataLoaded()
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AuthenticationContract.Effect.NavigateHome -> navigateToHome()
                is AuthenticationContract.Effect.ShowSuccess -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is AuthenticationContract.Effect.ShowError -> {
                    snackbarHostState.showSnackbar(
                        effect.error.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    AuthenticationScreen(
        authenticated = state.authenticated,
        loadingState = state.isLoading,
        snackbarHostState = snackbarHostState,
        onButtonClicked = {
            viewModel.onIntent(AuthenticationContract.Intent.SetLoading(true))
            coroutineScope.launch {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(CLIENT_ID)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(
                        request = request,
                        context = context,
                    )

                    val googleIdTokenCredential = GoogleIdTokenCredential
                        .createFrom(result.credential.data)

                    viewModel.onIntent(
                        AuthenticationContract.Intent.SignInWithGoogle(
                            googleIdTokenCredential.idToken
                        )
                    )
                } catch (e: GetCredentialCancellationException) {
                    // User cancelled the account picker — just reset loading
                    viewModel.onIntent(AuthenticationContract.Intent.SetLoading(false))
                } catch (e: Exception) {
                    viewModel.onIntent(AuthenticationContract.Intent.SetLoading(false))
                    snackbarHostState.showSnackbar(
                        e.message ?: "Sign-in failed"
                    )
                }
            }
        },
        navigateToHome = navigateToHome
    )
}
