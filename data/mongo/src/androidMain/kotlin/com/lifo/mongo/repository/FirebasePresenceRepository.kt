package com.lifo.mongo.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.lifo.util.repository.UserPresenceRepository
import com.lifo.util.repository.UserPresenceRepository.PresenceStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Realtime Database implementation of UserPresenceRepository.
 *
 * Structure:
 *   presence/{userId}/online    — boolean
 *   presence/{userId}/lastSeen  — server timestamp
 *
 * Uses onDisconnect() to auto-set offline when connection drops.
 */
@Singleton
class FirebasePresenceRepository @Inject constructor(
    private val database: FirebaseDatabase
) : UserPresenceRepository {

    companion object {
        private const val PRESENCE_PATH = "presence"
    }

    private val presenceRef by lazy { database.getReference(PRESENCE_PATH) }

    override suspend fun setOnline(userId: String) {
        val userRef = presenceRef.child(userId)
        userRef.child("online").setValue(true).await()
        userRef.child("lastSeen").setValue(ServerValue.TIMESTAMP).await()
        // Auto-set offline on disconnect
        userRef.child("online").onDisconnect().setValue(false)
        userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    override suspend fun setOffline(userId: String) {
        val userRef = presenceRef.child(userId)
        userRef.child("online").setValue(false).await()
        userRef.child("lastSeen").setValue(ServerValue.TIMESTAMP).await()
    }

    override fun observePresence(userId: String): Flow<PresenceStatus> = callbackFlow {
        val userRef = presenceRef.child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                trySend(PresenceStatus(userId = userId, isOnline = isOnline, lastSeenAt = lastSeen))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(PresenceStatus(userId = userId))
            }
        }
        userRef.addValueEventListener(listener)
        awaitClose { userRef.removeEventListener(listener) }
    }

    override fun observeMultiplePresence(userIds: List<String>): Flow<Map<String, PresenceStatus>> {
        val flows = userIds.map { userId -> observePresence(userId) }
        return combine(flows) { statuses ->
            statuses.associateBy { it.userId }
        }
    }
}
