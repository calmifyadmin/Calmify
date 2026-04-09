package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.DiaryDeltaResponse
import com.lifo.shared.model.DiaryProto
import org.slf4j.LoggerFactory

/**
 * Server-side delta sync service.
 *
 * Returns changes since a given timestamp for a user's entities.
 * Supports: diaries (more entity types to follow).
 */
class SyncService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(SyncService::class.java)

    /**
     * Get diary changes since [sinceMillis].
     * Returns created, updated, and deleted entries.
     */
    suspend fun getDiaryChangesSince(userId: String, sinceMillis: Long): DiaryDeltaResponse {
        val firestore = db ?: return DiaryDeltaResponse(serverTime = System.currentTimeMillis())

        // Get all diaries modified after sinceMillis
        val docs = firestore.collection("diaries")
            .whereEqualTo("ownerId", userId)
            .whereGreaterThan("updatedAt", sinceMillis)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .get().get().documents

        val created = mutableListOf<DiaryProto>()
        val updated = mutableListOf<DiaryProto>()

        for (doc in docs) {
            val createdAt = doc.getLong("createdAt") ?: 0L
            val updatedAt = doc.getLong("updatedAt") ?: 0L

            val proto = DiaryProto(
                id = doc.id,
                ownerId = doc.getString("ownerId") ?: "",
                mood = doc.getString("mood") ?: "Neutral",
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                dateMillis = doc.getLong("dateMillis") ?: 0L,
                dayKey = doc.getString("dayKey") ?: "",
                timezone = doc.getString("timezone") ?: "",
                emotionIntensity = doc.getLong("emotionIntensity")?.toInt() ?: 5,
                stressLevel = doc.getLong("stressLevel")?.toInt() ?: 5,
                energyLevel = doc.getLong("energyLevel")?.toInt() ?: 5,
                calmAnxietyLevel = doc.getLong("calmAnxietyLevel")?.toInt() ?: 5,
                primaryTrigger = doc.getString("primaryTrigger") ?: "NONE",
                dominantBodySensation = doc.getString("dominantBodySensation") ?: "NONE",
            )

            // If createdAt > sinceMillis, it's a new entry; otherwise it's an update
            if (createdAt > sinceMillis) {
                created.add(proto)
            } else {
                updated.add(proto)
            }
        }

        // Deleted entries — check deletion log
        val deletedIds = try {
            firestore.collection("deletion_log")
                .whereEqualTo("userId", userId)
                .whereEqualTo("entityType", "diary")
                .whereGreaterThan("deletedAt", sinceMillis)
                .get().get().documents
                .map { it.getString("entityId") ?: "" }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.debug("No deletion log collection yet: ${e.message}")
            emptyList()
        }

        logger.info("Delta sync for user $userId: ${created.size} created, ${updated.size} updated, ${deletedIds.size} deleted (since $sinceMillis)")

        return DiaryDeltaResponse(
            created = created,
            updated = updated,
            deletedIds = deletedIds,
            serverTime = System.currentTimeMillis(),
        )
    }
}
