package com.lifo.auth.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.auth.AuthenticationContract
import com.lifo.auth.AuthenticationScreen
import com.lifo.auth.AuthenticationViewModel
import com.stevdzasan.messagebar.rememberMessageBarState
import com.stevdzasan.onetap.rememberOneTapSignInState

/**
 * Public entry point composable for the Authentication feature.
 * Wraps the internal AuthenticationScreen with ViewModel setup.
 * Used by DecomposeApp to render the auth destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationRouteContent(
    navigateToHome: () -> Unit,
    onDataLoaded: () -> Unit
) {
    val viewModel: AuthenticationViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val oneTapState = rememberOneTapSignInState()
    val messageBarState = rememberMessageBarState()

    LaunchedEffect(key1 = kotlin.Unit) {
        onDataLoaded()
    }

    LaunchedEffect(key1 = kotlin.Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AuthenticationContract.Effect.NavigateHome -> navigateToHome()
                is AuthenticationContract.Effect.ShowSuccess -> messageBarState.addSuccess(effect.message)
                is AuthenticationContract.Effect.ShowError -> messageBarState.addError(effect.error)
            }
        }
    }

    AuthenticationScreen(
        authenticated = state.authenticated,
        loadingState = state.isLoading,
        oneTapState = oneTapState,
        messageBarState = messageBarState,
        onButtonClicked = {
            oneTapState.open()
            viewModel.onIntent(AuthenticationContract.Intent.SetLoading(true))
        },
        onSuccessfulFirebaseSignIn = { tokenId ->
            viewModel.onIntent(AuthenticationContract.Intent.SignInWithGoogle(tokenId))
        },
        onFailedFirebaseSignIn = {
            messageBarState.addError(it)
            viewModel.onIntent(AuthenticationContract.Intent.SetLoading(false))
        },
        onDialogDismissed = { message ->
            messageBarState.addError(Exception(message))
            viewModel.onIntent(AuthenticationContract.Intent.SetLoading(false))
        },
        navigateToHome = navigateToHome
    )
}
