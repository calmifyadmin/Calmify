package com.lifo.auth

import com.lifo.auth.domain.SignInWithGoogleUseCase
import com.lifo.auth.domain.SignOutUseCase
import com.lifo.util.auth.AuthProvider
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AuthenticationContract - MVI contract for the Authentication screen.
 */
object AuthenticationContract {

    sealed interface Intent : MviContract.Intent {
        data class SetLoading(val loading: Boolean) : Intent
        data class SignInWithGoogle(val tokenId: String) : Intent
        data object SignOut : Intent
    }

    data class State(
        val authenticated: Boolean = false,
        val isLoading: Boolean = false
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object NavigateHome : Effect
        data class ShowSuccess(val message: String) : Effect
        data class ShowError(val error: Exception) : Effect
    }
}

/**
 * AuthenticationViewModel - Delegates to UseCases, uses AuthProvider.
 */
internal class AuthenticationViewModel constructor(
    private val authProvider: AuthProvider,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signOutUseCase: SignOutUseCase,
) : MviViewModel<AuthenticationContract.Intent, AuthenticationContract.State, AuthenticationContract.Effect>(
    initialState = AuthenticationContract.State(
        authenticated = false
    )
) {

    init {
        if (authProvider.currentUserId != null) {
            updateState { copy(authenticated = true) }
        }
    }

    override fun handleIntent(intent: AuthenticationContract.Intent) {
        when (intent) {
            is AuthenticationContract.Intent.SetLoading -> {
                updateState { copy(isLoading = intent.loading) }
            }
            is AuthenticationContract.Intent.SignInWithGoogle -> {
                signInWithGoogle(intent.tokenId)
            }
            is AuthenticationContract.Intent.SignOut -> {
                signOut()
            }
        }
    }

    private fun signInWithGoogle(tokenId: String) {
        scope.launch {
            try {
                val result = signInWithGoogleUseCase(tokenId)
                result.fold(
                    onSuccess = {
                        sendEffect(AuthenticationContract.Effect.ShowSuccess("Autenticazione riuscita!"))
                        updateState { copy(isLoading = false) }
                        delay(600)
                        updateState { copy(authenticated = true) }
                        sendEffect(AuthenticationContract.Effect.NavigateHome)
                    },
                    onFailure = { e ->
                        updateState { copy(isLoading = false) }
                        sendEffect(AuthenticationContract.Effect.ShowError(e as Exception))
                    }
                )
            } catch (e: Exception) {
                updateState { copy(isLoading = false) }
                sendEffect(AuthenticationContract.Effect.ShowError(e))
            }
        }
    }

    private fun signOut() {
        scope.launch {
            signOutUseCase()
            updateState { copy(authenticated = false) }
        }
    }

    fun getCurrentUserId(): String? = authProvider.currentUserId
}
