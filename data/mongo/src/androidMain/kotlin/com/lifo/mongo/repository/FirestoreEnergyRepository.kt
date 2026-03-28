package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.lifo.util.model.EnergyCheckIn
import com.lifo.util.model.MovementType
import com.lifo.util.model.RequestState
import com.lifo.util.repository.EnergyRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FirestoreEnergyRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : EnergyRepository {

    private val collection = firestore.collection("energy_checkins")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override suspend fun upsertCheckIn(checkIn: EnergyCheckIn): RequestState<String> {
        return try {
            val withOwner = if (checkIn.ownerId.isEmpty()) checkIn.copy(ownerId = currentUserId) else checkIn
            val docId = "${currentUserId}_${withOwner.dayKey}"
            collection.document(docId).set(withOwner.toFirestoreMap()).await()
            RequestState.Success(docId)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getTodayCheckIn(): Flow<RequestState<EnergyCheckIn?>> = callbackFlow {
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
                trySend(RequestState.Success(snapshot?.toEnergyCheckIn()))
            }

        awaitClose { registration.remove() }
    }

    override fun getRecentCheckIns(limit: Int): Flow<RequestState<List<EnergyCheckIn>>> = callbackFlow {
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
                val entries = snapshot?.documents?.mapNotNull { it.toEnergyCheckIn() } ?: emptyList()
                trySend(RequestState.Success(entries))
            }

        awaitClose { registration.remove() }
    }

    override fun getCheckInsInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<EnergyCheckIn>>> = callbackFlow {
        trySend(RequestState.Loading)

        val registration = collection
            .whereEqualTo("ownerId", currentUserId)
            .whereGreaterThanOrEqualTo("timestampMillis", startMillis)
            .whereLessThanOrEqualTo("timestampMillis", endMillis)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { it.toEnergyCheckIn() } ?: emptyList()
                trySend(RequestState.Success(entries))
            }

        awaitClose { registration.remove() }
    }

    override suspend fun deleteCheckIn(id: String): RequestState<Boolean> {
        return try {
            collection.document(id).delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}

// ── Firestore Mapping ──

private fun EnergyCheckIn.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "dayKey" to dayKey,
    "timestampMillis" to timestampMillis,
    "timezone" to timezone,
    "energyLevel" to energyLevel,
    "sleepHours" to sleepHours.toDouble(),
    "waterGlasses" to waterGlasses,
    "didMovement" to didMovement,
    "movementType" to movementType.name,
    "regularMeals" to regularMeals,
)

private fun DocumentSnapshot.toEnergyCheckIn(): EnergyCheckIn? {
    if (!exists()) return null
    return try {
        EnergyCheckIn(
            id = id,
            ownerId = getString("ownerId") ?: "",
            dayKey = getString("dayKey") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            timezone = getString("timezone") ?: "",
            energyLevel = getLong("energyLevel")?.toInt() ?: 5,
            sleepHours = getDouble("sleepHours")?.toFloat() ?: 7f,
            waterGlasses = getLong("waterGlasses")?.toInt() ?: 0,
            didMovement = getBoolean("didMovement") ?: false,
            movementType = getString("movementType")?.let { safeMovementType(it) } ?: MovementType.NESSUNO,
            regularMeals = getBoolean("regularMeals") ?: true,
        )
    } catch (e: Exception) {
        null
    }
}

private fun safeMovementType(name: String): MovementType =
    try { MovementType.valueOf(name) } catch (_: Exception) { MovementType.NESSUNO }
