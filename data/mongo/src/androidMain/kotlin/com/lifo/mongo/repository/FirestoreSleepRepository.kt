package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.lifo.util.model.RequestState
import com.lifo.util.model.SleepDisturbance
import com.lifo.util.model.SleepLog
import com.lifo.util.repository.SleepRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FirestoreSleepRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SleepRepository {

    private val collection = firestore.collection("sleep_logs")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override suspend fun upsertSleepLog(log: SleepLog): RequestState<String> {
        return try {
            val withOwner = if (log.ownerId.isEmpty()) log.copy(ownerId = currentUserId) else log
            val docId = "${currentUserId}_${withOwner.dayKey}"
            collection.document(docId).set(withOwner.toFirestoreMap()).await()
            RequestState.Success(docId)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getTodayLog(): Flow<RequestState<SleepLog?>> = callbackFlow {
        trySend(RequestState.Loading)
        val todayKey = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toString()
        val docId = "${currentUserId}_$todayKey"

        val registration = collection.document(docId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                trySend(RequestState.Success(snapshot?.toSleepLog()))
            }
        awaitClose { registration.remove() }
    }

    override fun getRecentLogs(limit: Int): Flow<RequestState<List<SleepLog>>> = callbackFlow {
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
                val logs = snapshot?.documents?.mapNotNull { it.toSleepLog() } ?: emptyList()
                trySend(RequestState.Success(logs))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun deleteLog(id: String): RequestState<Boolean> {
        return try {
            collection.document(id).delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}

private fun SleepLog.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "dayKey" to dayKey,
    "timestampMillis" to timestampMillis,
    "timezone" to timezone,
    "bedtimeHour" to bedtimeHour,
    "bedtimeMinute" to bedtimeMinute,
    "waketimeHour" to waketimeHour,
    "waketimeMinute" to waketimeMinute,
    "quality" to quality,
    "disturbances" to disturbances.map { it.name },
    "screenFreeLastHour" to screenFreeLastHour,
    "notes" to notes,
)

private fun DocumentSnapshot.toSleepLog(): SleepLog? {
    if (!exists()) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        val disturbanceNames = get("disturbances") as? List<String> ?: emptyList()
        SleepLog(
            id = id,
            ownerId = getString("ownerId") ?: "",
            dayKey = getString("dayKey") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            timezone = getString("timezone") ?: "",
            bedtimeHour = getLong("bedtimeHour")?.toInt() ?: 23,
            bedtimeMinute = getLong("bedtimeMinute")?.toInt() ?: 0,
            waketimeHour = getLong("waketimeHour")?.toInt() ?: 7,
            waketimeMinute = getLong("waketimeMinute")?.toInt() ?: 0,
            quality = getLong("quality")?.toInt() ?: 3,
            disturbances = disturbanceNames.mapNotNull { name ->
                try { SleepDisturbance.valueOf(name) } catch (_: Exception) { null }
            },
            screenFreeLastHour = getBoolean("screenFreeLastHour") ?: false,
            notes = getString("notes") ?: "",
        )
    } catch (e: Exception) {
        null
    }
}
