package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.RequestState
import com.lifo.util.repository.WaitlistRepository
import kotlinx.coroutines.tasks.await

/**
 * Firestore implementation of WaitlistRepository.
 * Stores email signups in `waitlist/{email}` documents.
 */
class FirestoreWaitlistRepository(
    private val firestore: FirebaseFirestore,
) : WaitlistRepository {

    override suspend fun saveWaitlistEmail(
        email: String,
        userId: String?,
        source: String,
    ): RequestState<Boolean> {
        return try {
            val data = hashMapOf(
                "email" to email,
                "userId" to (userId ?: "anonymous"),
                "source" to source,
                "timestamp" to System.currentTimeMillis(),
            )

            firestore.collection("waitlist")
                .document(email)
                .set(data)
                .await()

            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}
