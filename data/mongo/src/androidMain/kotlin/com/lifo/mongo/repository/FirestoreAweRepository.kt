package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.AweEntry
import com.lifo.util.repository.AweRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAweRepository(
    firestore: FirebaseFirestore,
) : AweRepository {

    private val collection = firestore.collection("awe_entries")

    override fun getEntries(userId: String): Flow<List<AweEntry>> = callbackFlow {
        val reg = collection.whereEqualTo("ownerId", userId)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val entries = snapshot.documents.mapNotNull { doc ->
                    try {
                        AweEntry(
                            id = doc.id,
                            ownerId = doc.getString("ownerId") ?: "",
                            description = doc.getString("description") ?: "",
                            context = doc.getString("context") ?: "",
                            photoUrl = doc.getString("photoUrl"),
                            timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                            dayKey = doc.getString("dayKey") ?: "",
                        )
                    } catch (_: Exception) { null }
                }
                trySend(entries)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun saveEntry(entry: AweEntry): Result<Unit> = runCatching {
        val data = hashMapOf(
            "ownerId" to entry.ownerId,
            "description" to entry.description,
            "context" to entry.context,
            "photoUrl" to entry.photoUrl,
            "timestampMillis" to entry.timestampMillis,
            "dayKey" to entry.dayKey,
        )
        if (entry.id.isNotBlank()) {
            collection.document(entry.id).set(data).await()
        } else {
            collection.add(data).await()
        }
    }

    override suspend fun deleteEntry(entryId: String): Result<Unit> = runCatching {
        collection.document(entryId).delete().await()
    }
}
