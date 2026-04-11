package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class PresenceService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(PresenceService::class.java)

    companion object {
        private const val PRESENCE_COLLECTION = "presence"
    }

    @Serializable
    data class PresenceData(
        val userId: String = "",
        val isOnline: Boolean = false,
        val lastSeenAt: Long = 0,
    )

    suspend fun setOnline(userId: String) = withContext(Dispatchers.IO) {
        val data = hashMapOf<String, Any>(
            "isOnline" to true,
            "lastSeenAt" to System.currentTimeMillis(),
        )
        db.collection(PRESENCE_COLLECTION).document(userId).set(data).get()
        logger.info("User $userId set online")
    }

    suspend fun setOffline(userId: String) = withContext(Dispatchers.IO) {
        val data = hashMapOf<String, Any>(
            "isOnline" to false,
            "lastSeenAt" to System.currentTimeMillis(),
        )
        db.collection(PRESENCE_COLLECTION).document(userId).set(data).get()
        logger.info("User $userId set offline")
    }

    suspend fun getPresence(userId: String): PresenceData = withContext(Dispatchers.IO) {
        val doc = db.collection(PRESENCE_COLLECTION).document(userId).get().get()
        if (!doc.exists()) return@withContext PresenceData(userId = userId)
        PresenceData(
            userId = userId,
            isOnline = doc.getBoolean("isOnline") ?: false,
            lastSeenAt = doc.getLong("lastSeenAt") ?: 0,
        )
    }

    suspend fun getMultiplePresence(userIds: List<String>): List<PresenceData> = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext emptyList()

        // Firestore getAll for batch reads
        val refs = userIds.map { db.collection(PRESENCE_COLLECTION).document(it) }
        val docs = db.getAll(*refs.toTypedArray()).get()

        docs.map { doc ->
            if (doc.exists()) {
                PresenceData(
                    userId = doc.id,
                    isOnline = doc.getBoolean("isOnline") ?: false,
                    lastSeenAt = doc.getLong("lastSeenAt") ?: 0,
                )
            } else {
                PresenceData(userId = doc.id)
            }
        }
    }
}
