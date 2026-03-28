package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.lifo.util.model.RequestState
import com.lifo.util.model.ThoughtCategory
import com.lifo.util.model.ThoughtReframe
import com.lifo.util.repository.ReframeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreReframeRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ReframeRepository {

    private val collection = firestore.collection("thought_reframes")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override suspend fun saveReframe(reframe: ThoughtReframe): RequestState<String> {
        return try {
            val withOwner = if (reframe.ownerId.isEmpty()) reframe.copy(ownerId = currentUserId) else reframe
            collection.document(withOwner.id).set(withOwner.toFirestoreMap()).await()
            RequestState.Success(withOwner.id)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getRecentReframes(limit: Int): Flow<RequestState<List<ThoughtReframe>>> = callbackFlow {
        trySend(RequestState.Loading)
        val registration = collection
            .whereEqualTo("ownerId", currentUserId)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                val reframes = snapshot?.documents?.mapNotNull { it.toThoughtReframe() } ?: emptyList()
                trySend(RequestState.Success(reframes))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteReframe(id: String): RequestState<Boolean> {
        return try {
            collection.document(id).delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}

private fun ThoughtReframe.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "timestampMillis" to timestampMillis,
    "originalThought" to originalThought,
    "evidenceFor" to evidenceFor,
    "evidenceAgainst" to evidenceAgainst,
    "friendPerspective" to friendPerspective,
    "reframedThought" to reframedThought,
    "category" to category.name,
)

private fun DocumentSnapshot.toThoughtReframe(): ThoughtReframe? {
    if (!exists()) return null
    return try {
        ThoughtReframe(
            id = id,
            ownerId = getString("ownerId") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            originalThought = getString("originalThought") ?: "",
            evidenceFor = getString("evidenceFor") ?: "",
            evidenceAgainst = getString("evidenceAgainst") ?: "",
            friendPerspective = getString("friendPerspective") ?: "",
            reframedThought = getString("reframedThought") ?: "",
            category = getString("category")?.let {
                try { ThoughtCategory.valueOf(it) } catch (_: Exception) { null }
            } ?: ThoughtCategory.ALTRO,
        )
    } catch (e: Exception) {
        null
    }
}
