package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationSession
import com.lifo.util.model.MeditationType
import com.lifo.util.repository.MeditationRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreMeditationRepository(
    firestore: FirebaseFirestore,
) : MeditationRepository {

    private val collection = firestore.collection("meditation_sessions")

    override suspend fun saveSession(session: MeditationSession): Result<Unit> = runCatching {
        val data = hashMapOf(
            "ownerId" to session.ownerId,
            "timestampMillis" to session.timestampMillis,
            "type" to session.type.name,
            "breathingPattern" to session.breathingPattern?.name,
            "durationSeconds" to session.durationSeconds,
            "completedSeconds" to session.completedSeconds,
            "postNote" to session.postNote,
            "stopped" to session.stopped,
            "cyclesCompleted" to session.cyclesCompleted,
        )
        collection.document(session.id).set(data).await()
    }

    override fun getRecentSessions(userId: String, limit: Int): Flow<List<MeditationSession>> =
        callbackFlow {
            val registration = collection
                .whereEqualTo("ownerId", userId)
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val sessions = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            MeditationSession(
                                id = doc.id,
                                ownerId = doc.getString("ownerId") ?: "",
                                timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                                type = try { MeditationType.valueOf(doc.getString("type") ?: "TIMER") } catch (_: Exception) { MeditationType.TIMER },
                                breathingPattern = doc.getString("breathingPattern")?.let { BreathingPattern.fromCanonicalName(it) },
                                durationSeconds = (doc.getLong("durationSeconds") ?: 300).toInt(),
                                completedSeconds = (doc.getLong("completedSeconds") ?: 0).toInt(),
                                postNote = doc.getString("postNote") ?: "",
                                stopped = doc.getBoolean("stopped") ?: false,
                                cyclesCompleted = doc.getLong("cyclesCompleted")?.toInt(),
                            )
                        } catch (_: Exception) { null }
                    } ?: emptyList()
                    trySend(sessions)
                }
            awaitClose { registration.remove() }
        }

    override suspend fun getTotalMinutes(userId: String): Int {
        val snapshot = collection
            .whereEqualTo("ownerId", userId)
            .get()
            .await()
        return snapshot.documents.sumOf { doc ->
            (doc.getLong("completedSeconds") ?: 0L).toInt()
        } / 60
    }

    override suspend fun getSessionCount(userId: String): Int {
        val snapshot = collection
            .whereEqualTo("ownerId", userId)
            .get()
            .await()
        return snapshot.size()
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        collection.document(sessionId).delete().await()
    }
}
