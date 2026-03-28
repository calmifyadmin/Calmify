package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.MovementLog
import com.lifo.util.model.MovementType
import com.lifo.util.model.PostMovementFeeling
import com.lifo.util.repository.MovementRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreMovementRepository(
    firestore: FirebaseFirestore,
) : MovementRepository {

    private val collection = firestore.collection("movement_logs")

    override suspend fun saveLog(log: MovementLog): Result<Unit> = runCatching {
        val docId = "${log.ownerId}_${log.dayKey}"
        val data = hashMapOf(
            "ownerId" to log.ownerId,
            "timestampMillis" to log.timestampMillis,
            "dayKey" to log.dayKey,
            "movementType" to log.movementType.name,
            "durationMinutes" to log.durationMinutes,
            "feelingAfter" to log.feelingAfter.name,
            "note" to log.note,
        )
        collection.document(docId).set(data).await()
    }

    override fun getTodayLog(userId: String, dayKey: String): Flow<MovementLog?> = callbackFlow {
        val docId = "${userId}_${dayKey}"
        val reg = collection.document(docId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snapshot.toMovementLog())
        }
        awaitClose { reg.remove() }
    }

    override fun getRecentLogs(userId: String, limit: Int): Flow<List<MovementLog>> = callbackFlow {
        val reg = collection
            .whereEqualTo("ownerId", userId)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toMovementLog() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override fun getLogsInRange(userId: String, startDayKey: String, endDayKey: String): Flow<List<MovementLog>> = callbackFlow {
        val reg = collection
            .whereEqualTo("ownerId", userId)
            .whereGreaterThanOrEqualTo("dayKey", startDayKey)
            .whereLessThanOrEqualTo("dayKey", endDayKey)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toMovementLog() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun deleteLog(logId: String): Result<Unit> = runCatching {
        collection.document(logId).delete().await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMovementLog(): MovementLog? = try {
        MovementLog(
            id = id,
            ownerId = getString("ownerId") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            dayKey = getString("dayKey") ?: "",
            movementType = try { MovementType.valueOf(getString("movementType") ?: "CAMMINATA") } catch (_: Exception) { MovementType.CAMMINATA },
            durationMinutes = (getLong("durationMinutes") ?: 20).toInt(),
            feelingAfter = try { PostMovementFeeling.valueOf(getString("feelingAfter") ?: "MEGLIO") } catch (_: Exception) { PostMovementFeeling.MEGLIO },
            note = getString("note") ?: "",
        )
    } catch (_: Exception) { null }
}
