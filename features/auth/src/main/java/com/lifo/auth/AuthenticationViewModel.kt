package com.lifo.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * AuthenticationContract - MVI contract for the Authentication screen.
 *
 * Defines all possible user intents, the single immutable UI state,
 * and one-shot side effects (navigation, error display).
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
 * AuthenticationViewModel - Migrated to MVI + Firebase Auth 2025
 *
 * Previously used MongoDB Atlas Device Sync (EOL) + MVVM.
 * Now uses Firebase Authentication with Google Sign-In + MVI pattern.
 */
internal class AuthenticationViewModel constructor(
    private val auth: FirebaseAuth
) : MviViewModel<AuthenticationContract.Intent, AuthenticationContract.State, AuthenticationContract.Effect>(
    initialState = AuthenticationContract.State(
        authenticated = FirebaseAuth.getInstance().currentUser != null
    )
) {

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

    /**
     * Sign in with Google using Firebase Auth
     *
     * @param tokenId Google ID token from Google One Tap
     */
    private fun signInWithGoogle(tokenId: String) {
        scope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(tokenId, null)
                val result = withContext(Dispatchers.IO) {
                    auth.signInWithCredential(credential).await()
                }
                withContext(Dispatchers.Main) {
                    if (result.user != null) {
                        sendEffect(AuthenticationContract.Effect.ShowSuccess("Successfully Authenticated!"))
                        updateState { copy(isLoading = false) }
                        delay(600)
                        updateState { copy(authenticated = true) }
                        sendEffect(AuthenticationContract.Effect.NavigateHome)
                    } else {
                        val error = Exception("User is not logged in.")
                        updateState { copy(isLoading = false) }
                        sendEffect(AuthenticationContract.Effect.ShowError(error))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateState { copy(isLoading = false) }
                    sendEffect(AuthenticationContract.Effect.ShowError(e))
                }
            }
        }
    }

    /**
     * Sign out current user
     */
    private fun signOut() {
        auth.signOut()
        updateState { copy(authenticated = false) }
    }

    /**
     * Get current user ID (for Firestore queries).
     * This is a synchronous query — not an intent but a direct accessor.
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid
}
