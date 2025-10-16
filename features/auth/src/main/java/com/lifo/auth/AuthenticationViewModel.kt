package com.lifo.auth

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * AuthenticationViewModel - Migrated to Firebase Auth 2025
 *
 * Previously used MongoDB Atlas Device Sync (EOL)
 * Now uses Firebase Authentication with Google Sign-In
 */
internal class AuthenticationViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var authenticated = mutableStateOf(false)
        private set
    var loadingState = mutableStateOf(false)
        private set

    init {
        // Check if user is already signed in
        authenticated.value = auth.currentUser != null
    }

    fun setLoading(loading: Boolean) {
        loadingState.value = loading
    }

    /**
     * Sign in with Google using Firebase Auth
     *
     * @param tokenId Google ID token from Google One Tap
     * @param onSuccess Callback on successful authentication
     * @param onError Callback on error
     */
    fun signInWithGoogle(
        tokenId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(tokenId, null)
                val result = withContext(Dispatchers.IO) {
                    auth.signInWithCredential(credential).await()
                }
                withContext(Dispatchers.Main) {
                    if (result.user != null) {
                        onSuccess()
                        delay(600)
                        authenticated.value = true
                    } else {
                        onError(Exception("User is not logged in."))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
        authenticated.value = false
    }

    /**
     * Get current user ID (for Firestore queries)
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid
}