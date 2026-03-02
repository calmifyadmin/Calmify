package com.lifo.util.auth

import com.google.firebase.auth.FirebaseAuth

/**
 * Android implementation of [AuthProvider] backed by Firebase Auth.
 *
 * Registered in Koin as `single<AuthProvider> { FirebaseAuthProvider(get()) }`.
 */
class FirebaseAuthProvider(private val auth: FirebaseAuth) : AuthProvider {

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

    override suspend fun signOut() {
        auth.signOut()
    }
}
