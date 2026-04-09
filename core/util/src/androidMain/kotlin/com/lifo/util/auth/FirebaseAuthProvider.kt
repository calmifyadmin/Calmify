package com.lifo.util.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of [AuthProvider] backed by Firebase Auth.
 *
 * Registers an [FirebaseAuth.AuthStateListener] to emit auth changes in real-time.
 * Registered in Koin as `single<AuthProvider> { FirebaseAuthProvider(get()) }`.
 */
class FirebaseAuthProvider(private val auth: FirebaseAuth) : AuthProvider {

    private val _authStateFlow = MutableStateFlow<String?>(auth.currentUser?.uid)

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _authStateFlow.value = firebaseAuth.currentUser?.uid
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val isAuthenticated: Boolean
        get() = auth.currentUser != null

    override val currentUserDisplayName: String?
        get() = auth.currentUser?.displayName

    override val currentUserEmail: String?
        get() = auth.currentUser?.email

    override val currentUserPhotoUrl: String?
        get() = auth.currentUser?.photoUrl?.toString()

    override val authStateFlow: StateFlow<String?> = _authStateFlow.asStateFlow()

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        return try {
            auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}
