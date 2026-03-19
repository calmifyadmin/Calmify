package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.repository.UserPresenceRepository
import com.lifo.util.repository.UserPresenceRepository.PresenceStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock

/**
 * Firestore-based implementation of UserPresenceRepository.
 *
 * Replaces Firebase Realtime Database for presence tracking.
 * Uses Firestore document per user: presence/{userId}
 *
 * Structure:
 *   presence/{userId} {
 *     online: Boolean,
 *     lastSeen: Long (epoch millis UTC)
 *   }
 *
 * Note: Unlike RTDB, Firestore doesn't have onDisconnect().
 * The app must explicitly call setOffline() when backgrounding/closing.
 * A Cloud Function with a scheduled trigger can clean stale presence
 * (e.g., mark users offline if lastSeen > 5 minutes ago).
 */
class FirestorePresenceRepository(
    private val firestore: FirebaseFirestore
) : UserPresenceRepository {

    companion object {
        private const val COLLECTION_PRESENCE = "presence"
    }

    private val presenceCollection by lazy { firestore.collection(COLLECTION_PRESENCE) }

    override suspend fun setOnline(userId: String) {
        presenceCollection.document(userId).set(
            mapOf(
                "online" to true,
                "lastSeen" to Clock.System.now().toEpochMilliseconds()
            )
        ).await()
    }

    override suspend fun setOffline(userId: String) {
        presenceCollection.document(userId).set(
            mapOf(
                "online" to false,
                "lastSeen" to Clock.System.now().toEpochMilliseconds()
            )
        ).await()
    }

    override fun observePresence(userId: String): Flow<PresenceStatus> = callbackFlow {
        val listener = presenceCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(PresenceStatus(userId = userId))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        PresenceStatus(
                            userId = userId,
                            isOnline = snapshot.getBoolean("online") ?: false,
                            lastSeenAt = snapshot.getLong("lastSeen") ?: 0L
                        )
                    )
                } else {
                    trySend(PresenceStatus(userId = userId))
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeMultiplePresence(userIds: List<String>): Flow<Map<String, PresenceStatus>> {
        val flows = userIds.map { userId -> observePresence(userId) }
        return combine(flows) { statuses ->
            statuses.associateBy { it.userId }
        }
    }
}
