package com.lifo.util.auth

/**
 * Platform-agnostic authentication provider.
 *
 * Decouples all ViewModels from `com.google.firebase.auth.FirebaseAuth`.
 * Android implementation: [FirebaseAuthProvider] in androidMain.
 */
interface AuthProvider {
    /** UID of the currently signed-in user, or null if not authenticated. */
    val currentUserId: String?

    /** Whether a user is currently signed in. */
    val isAuthenticated: Boolean

    /** Display name of the currently signed-in user, or null. */
    val currentUserDisplayName: String?

    /** Email of the currently signed-in user, or null. */
    val currentUserEmail: String?

    /** Photo URL of the currently signed-in user, or null. */
    val currentUserPhotoUrl: String?

    /** Sign out the current user. */
    suspend fun signOut()
}
