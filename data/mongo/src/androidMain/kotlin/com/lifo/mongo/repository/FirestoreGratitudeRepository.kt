package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.lifo.util.model.GratitudeCategory
import com.lifo.util.model.GratitudeEntry
import com.lifo.util.model.RequestState
import com.lifo.util.repository.GratitudeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FirestoreGratitudeRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : GratitudeRepository {

    private val collection = firestore.collection("gratitude_entries")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override suspend fun upsertEntry(entry: GratitudeEntry): RequestState<String> {
        return try {
            val entryWithOwner = if (entry.ownerId.isEmpty()) entry.copy(ownerId = currentUserId) else entry

            // Use dayKey as document ID to enforce one entry per day
            val docId = "${currentUserId}_${entryWithOwner.dayKey}"
            val docRef = collection.document(docId)
            docRef.set(entryWithOwner.toFirestoreMap()).await()

            RequestState.Success(docId)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getTodayEntry(): Flow<RequestState<GratitudeEntry?>> = callbackFlow {
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
                val entry = snapshot?.toGratitudeEntry()
                trySend(RequestState.Success(entry))
            }

        awaitClose { registration.remove() }
    }

    override fun getRecentEntries(limit: Int): Flow<RequestState<List<GratitudeEntry>>> = callbackFlow {
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
                val entries = snapshot?.documents?.mapNotNull { it.toGratitudeEntry() } ?: emptyList()
                trySend(RequestState.Success(entries))
            }

        awaitClose { registration.remove() }
    }

    override fun getEntriesInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<GratitudeEntry>>> = callbackFlow {
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
                val entries = snapshot?.documents?.mapNotNull { it.toGratitudeEntry() } ?: emptyList()
                trySend(RequestState.Success(entries))
            }

        awaitClose { registration.remove() }
    }

    override suspend fun deleteEntry(id: String): RequestState<Boolean> {
        return try {
            collection.document(id).delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}

// ── Firestore Mapping ──

private fun GratitudeEntry.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "dayKey" to dayKey,
    "timestampMillis" to timestampMillis,
    "timezone" to timezone,
    "item1" to item1,
    "item2" to item2,
    "item3" to item3,
    "category1" to category1.name,
    "category2" to category2.name,
    "category3" to category3.name,
)

private fun DocumentSnapshot.toGratitudeEntry(): GratitudeEntry? {
    if (!exists()) return null
    return try {
        GratitudeEntry(
            id = id,
            ownerId = getString("ownerId") ?: "",
            dayKey = getString("dayKey") ?: "",
            timestampMillis = getLong("timestampMillis") ?: 0L,
            timezone = getString("timezone") ?: "",
            item1 = getString("item1") ?: "",
            item2 = getString("item2") ?: "",
            item3 = getString("item3") ?: "",
            category1 = getString("category1")?.let { safeCategory(it) } ?: GratitudeCategory.ALTRO,
            category2 = getString("category2")?.let { safeCategory(it) } ?: GratitudeCategory.ALTRO,
            category3 = getString("category3")?.let { safeCategory(it) } ?: GratitudeCategory.ALTRO,
        )
    } catch (e: Exception) {
        null
    }
}

private fun safeCategory(name: String): GratitudeCategory =
    try { GratitudeCategory.valueOf(name) } catch (_: Exception) { GratitudeCategory.ALTRO }
