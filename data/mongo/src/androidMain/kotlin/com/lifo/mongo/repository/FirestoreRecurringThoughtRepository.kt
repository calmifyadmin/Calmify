package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.RecurringThought
import com.lifo.util.model.ThoughtType
import com.lifo.util.repository.RecurringThoughtRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock

class FirestoreRecurringThoughtRepository(
    private val firestore: FirebaseFirestore,
) : RecurringThoughtRepository {

    private val collection = firestore.collection("recurring_thoughts")

    override fun getThoughts(userId: String): Flow<List<RecurringThought>> = callbackFlow {
        val reg = collection.whereEqualTo("ownerId", userId)
            .orderBy("lastSeenMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val thoughts = snapshot.documents.mapNotNull { doc ->
                    try {
                        RecurringThought(
                            id = doc.id,
                            ownerId = doc.getString("ownerId") ?: "",
                            theme = doc.getString("theme") ?: "",
                            type = try { ThoughtType.valueOf(doc.getString("type") ?: "NEUTRAL") } catch (_: Exception) { ThoughtType.NEUTRAL },
                            occurrences = (doc.getLong("occurrences") ?: 1L).toInt(),
                            firstSeenMillis = doc.getLong("firstSeenMillis") ?: 0L,
                            lastSeenMillis = doc.getLong("lastSeenMillis") ?: 0L,
                            reframedAtMillis = doc.getLong("reframedAtMillis"),
                            reframeId = doc.getString("reframeId"),
                            occurrencesPostReframe = (doc.getLong("occurrencesPostReframe") ?: 0L).toInt(),
                            isResolved = doc.getBoolean("isResolved") ?: false,
                        )
                    } catch (_: Exception) { null }
                }
                trySend(thoughts)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun saveThought(thought: RecurringThought): Result<Unit> = runCatching {
        val data = hashMapOf(
            "ownerId" to thought.ownerId,
            "theme" to thought.theme,
            "type" to thought.type.name,
            "occurrences" to thought.occurrences,
            "firstSeenMillis" to thought.firstSeenMillis,
            "lastSeenMillis" to thought.lastSeenMillis,
            "reframedAtMillis" to thought.reframedAtMillis,
            "reframeId" to thought.reframeId,
            "occurrencesPostReframe" to thought.occurrencesPostReframe,
            "isResolved" to thought.isResolved,
        )
        if (thought.id.isNotBlank()) {
            collection.document(thought.id).set(data).await()
        } else {
            collection.add(data).await()
        }
    }

    override suspend fun incrementOccurrence(thoughtId: String): Result<Unit> = runCatching {
        val doc = collection.document(thoughtId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(doc)
            val current = (snapshot.getLong("occurrences") ?: 1L) + 1
            transaction.update(doc, mapOf(
                "occurrences" to current,
                "lastSeenMillis" to Clock.System.now().toEpochMilliseconds(),
            ))
        }.await()
    }

    override suspend fun markReframed(thoughtId: String, reframeId: String): Result<Unit> = runCatching {
        collection.document(thoughtId).update(mapOf(
            "reframedAtMillis" to Clock.System.now().toEpochMilliseconds(),
            "reframeId" to reframeId,
        )).await()
    }

    override suspend fun incrementPostReframe(thoughtId: String): Result<Unit> = runCatching {
        val doc = collection.document(thoughtId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(doc)
            val current = (snapshot.getLong("occurrencesPostReframe") ?: 0L) + 1
            transaction.update(doc, "occurrencesPostReframe", current)
        }.await()
    }

    override suspend fun resolveThought(thoughtId: String): Result<Unit> = runCatching {
        collection.document(thoughtId).update("isResolved", true).await()
    }

    override suspend fun deleteThought(thoughtId: String): Result<Unit> = runCatching {
        collection.document(thoughtId).delete().await()
    }
}
