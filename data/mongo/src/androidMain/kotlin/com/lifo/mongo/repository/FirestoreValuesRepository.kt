package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.ValuesDiscovery
import com.lifo.util.repository.ValuesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreValuesRepository(
    firestore: FirebaseFirestore,
) : ValuesRepository {

    private val collection = firestore.collection("values_discovery")

    @Suppress("UNCHECKED_CAST")
    override fun getDiscovery(userId: String): Flow<ValuesDiscovery?> = callbackFlow {
        val reg = collection.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            try {
                val discovery = ValuesDiscovery(
                    id = snapshot.id,
                    ownerId = userId,
                    completedSteps = (snapshot.getLong("completedSteps") ?: 0L).toInt(),
                    aliveMoments = snapshot.get("aliveMoments") as? List<String> ?: emptyList(),
                    indignationTopics = snapshot.get("indignationTopics") as? List<String> ?: emptyList(),
                    finalReflection = snapshot.getString("finalReflection") ?: "",
                    discoveredValues = snapshot.get("discoveredValues") as? List<String> ?: emptyList(),
                    confirmedValues = snapshot.get("confirmedValues") as? List<String> ?: emptyList(),
                    createdAtMillis = snapshot.getLong("createdAtMillis") ?: 0L,
                    lastReviewMillis = snapshot.getLong("lastReviewMillis"),
                    nextReviewMillis = snapshot.getLong("nextReviewMillis"),
                )
                trySend(discovery)
            } catch (_: Exception) {
                trySend(null)
            }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun saveDiscovery(discovery: ValuesDiscovery): Result<Unit> = runCatching {
        val data = hashMapOf(
            "completedSteps" to discovery.completedSteps,
            "aliveMoments" to discovery.aliveMoments,
            "indignationTopics" to discovery.indignationTopics,
            "finalReflection" to discovery.finalReflection,
            "discoveredValues" to discovery.discoveredValues,
            "confirmedValues" to discovery.confirmedValues,
            "createdAtMillis" to discovery.createdAtMillis,
            "lastReviewMillis" to discovery.lastReviewMillis,
            "nextReviewMillis" to discovery.nextReviewMillis,
        )
        collection.document(discovery.ownerId).set(data).await()
    }

    override suspend fun deleteDiscovery(discoveryId: String): Result<Unit> = runCatching {
        collection.document(discoveryId).delete().await()
    }
}
