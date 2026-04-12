package com.lifo.mongo.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.model.WellbeingSnapshot
import com.lifo.util.repository.WellbeingRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * FirestoreWellbeingRepository Implementation
 *
 * Firestore-backed implementation of WellbeingRepository
 * Collection: wellbeing_snapshots
 */
class FirestoreWellbeingRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : WellbeingRepository {

    private val collection = firestore.collection("wellbeing_snapshots")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override suspend fun insertSnapshot(snapshot: WellbeingSnapshot): RequestState<String> {
        return try {
            // Set ownerId if not already set
            if (snapshot.ownerId.isEmpty()) {
                snapshot.ownerId = currentUserId
            }

            // Add to Firestore
            val docRef = collection.document()
            snapshot.id = docRef.id
            docRef.set(snapshot.toFirestoreMap()).await()

            RequestState.Success(snapshot.id)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getAllSnapshots(): Flow<RequestState<List<WellbeingSnapshot>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = collection
            .whereEqualTo("ownerId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val snapshots = snapshot.documents.mapNotNull { it.toWellbeingSnapshot() }
                    trySend(RequestState.Success(snapshots))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun getLatestSnapshot(): RequestState<WellbeingSnapshot?> {
        return try {
            val snapshot = collection
                .whereEqualTo("ownerId", currentUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val latest = snapshot.documents.firstOrNull()?.toWellbeingSnapshot()
            RequestState.Success(latest)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override fun getSnapshotsInRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<RequestState<List<WellbeingSnapshot>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = collection
            .whereEqualTo("ownerId", currentUserId)
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(startTimestamp)))
            .whereLessThanOrEqualTo("timestamp", Timestamp(Date(endTimestamp)))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val snapshots = snapshot.documents.mapNotNull { it.toWellbeingSnapshot() }
                    trySend(RequestState.Success(snapshots))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun deleteSnapshot(id: String): RequestState<Boolean> {
        return try {
            collection.document(id).delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}
