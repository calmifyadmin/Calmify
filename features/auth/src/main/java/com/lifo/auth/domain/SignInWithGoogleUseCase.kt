package com.lifo.auth.domain

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lifo.util.usecase.UseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Authenticates user with Google credential via Firebase Auth.
 * Returns the user's UID on success.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val auth: FirebaseAuth
) : UseCase<String, Result<String>> {

    /**
     * @param params Google ID token from One Tap Sign-In
     * @return Result with user UID on success, or exception on failure
     */
    override suspend fun invoke(params: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAuthProvider.getCredential(params, null)
                val result = auth.signInWithCredential(credential).await()
                val uid = result.user?.uid
                if (uid != null) {
                    Result.success(uid)
                } else {
                    Result.failure(Exception("User is not logged in."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
