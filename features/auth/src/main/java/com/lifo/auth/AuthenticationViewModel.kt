package com.lifo.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * AuthenticationViewModel - Migrated to Firebase Auth 2025
 *
 * Previously used MongoDB Atlas Device Sync (EOL)
 * Now uses Firebase Authentication with Google Sign-In
 */
@HiltViewModel
internal class AuthenticationViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _authenticated = MutableStateFlow(auth.currentUser != null)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    fun setLoading(loading: Boolean) {
        _loadingState.value = loading
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
                        _authenticated.value = true
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
        _authenticated.value = false
    }

    /**
     * Get current user ID (for Firestore queries)
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid
}