package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.DiaryDeltaResponse
import com.lifo.shared.api.GenericDeltaResponse
import com.lifo.shared.model.DiaryProto
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Server-side delta sync service.
 *
 * Returns changes since a given timestamp for a user's entities.
 * Supports diary + all 13 wellness types + chat sessions/messages.
 */
class SyncService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(SyncService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Maps SyncEntityType names to Firestore collection paths
    private val collectionMap = mapOf(
        "DIARY" to "diaries",
        "CHAT_SESSION" to "chat_sessions",
        "CHAT_MESSAGE" to "chat_messages",
        "GRATITUDE" to "wellness_gratitude",
        "HABIT" to "wellness_habits",
        "ENERGY" to "wellness_energy",
        "SLEEP" to "wellness_sleep",
        "MEDITATION" to "wellness_meditation",
        "MOVEMENT" to "wellness_movement",
        "REFRAME" to "wellness_reframe",
        "WELLBEING" to "wellness_wellbeing",
        "AWE" to "wellness_awe",
        "CONNECTION" to "wellness_connection",
        "RECURRING_THOUGHT" to "wellness_recurring_thought",
        "BLOCK" to "wellness_block",
        "VALUES" to "wellness_values",
    )

    /**
     * Get diary changes since [sinceMillis] (typed, backwards-compatible).
     */
    suspend fun getDiaryChangesSince(userId: String, sinceMillis: Long): DiaryDeltaResponse {
        val docs = db.collection("diaries")
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

            if (createdAt > sinceMillis) {
                created.add(proto)
            } else {
                updated.add(proto)
            }
        }

        val deletedIds = getDeletedIds(db, userId, "diary", sinceMillis)

        logger.info("Delta sync DIARY for user $userId: ${created.size} created, ${updated.size} updated, ${deletedIds.size} deleted")

        return DiaryDeltaResponse(
            created = created,
            updated = updated,
            deletedIds = deletedIds,
            serverTime = System.currentTimeMillis(),
        )
    }

    /**
     * Generic delta sync for any entity type.
     * Returns documents as raw JsonElements — client deserializes based on type.
     */
    suspend fun getChangesSince(userId: String, entityType: String, sinceMillis: Long): GenericDeltaResponse {
        val collectionName = collectionMap[entityType]
            ?: return GenericDeltaResponse(entityType = entityType, serverTime = System.currentTimeMillis())

        val docs = db.collection(collectionName)
            .whereEqualTo("ownerId", userId)
            .whereGreaterThan("updatedAt", sinceMillis)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .get().get().documents

        val created = mutableListOf<JsonElement>()
        val updated = mutableListOf<JsonElement>()

        for (doc in docs) {
            val createdAt = doc.getLong("createdAt") ?: 0L
            val data = doc.data ?: continue
            val jsonObj = buildJsonObject {
                put("id", doc.id)
                for ((key, value) in data) {
                    when (value) {
                        is String -> put(key, value)
                        is Long -> put(key, value)
                        is Double -> put(key, value)
                        is Boolean -> put(key, value)
                        is Number -> put(key, JsonPrimitive(value.toLong()))
                        is List<*> -> put(key, buildJsonArray {
                            for (item in value) {
                                when (item) {
                                    is String -> add(item)
                                    is Number -> add(JsonPrimitive(item.toLong()))
                                    else -> add(item.toString())
                                }
                            }
                        })
                        null -> put(key, JsonNull)
                        else -> put(key, value.toString())
                    }
                }
            }

            if (createdAt > sinceMillis) {
                created.add(jsonObj)
            } else {
                updated.add(jsonObj)
            }
        }

        val entityTypeForDeletion = entityType.lowercase().replace("_", "")
        val deletedIds = getDeletedIds(db, userId, entityTypeForDeletion, sinceMillis)

        logger.info("Delta sync $entityType for user $userId: ${created.size} created, ${updated.size} updated, ${deletedIds.size} deleted")

        return GenericDeltaResponse(
            entityType = entityType,
            created = created,
            updated = updated,
            deletedIds = deletedIds,
            serverTime = System.currentTimeMillis(),
        )
    }

    /**
     * Apply a batch of sync operations from the client.
     * Returns success/failure per operation with server timestamps.
     */
    suspend fun applyBatch(
        userId: String,
        operations: List<Triple<String, String, String>>, // (entityType, entityId, operation+payload)
    ): List<Pair<String, Boolean>> {
        val results = mutableListOf<Pair<String, Boolean>>()

        for ((entityType, entityId, opPayload) in operations) {
            val collection = collectionMap[entityType]
            if (collection == null) {
                results.add(entityId to false)
                continue
            }

            try {
                val parts = opPayload.split("|", limit = 2)
                val operation = parts[0]
                val payload = parts.getOrElse(1) { "{}" }

                when (operation) {
                    "CREATE", "UPDATE" -> {
                        val data = json.parseToJsonElement(payload).jsonObject.toFirestoreMap()
                        data["updatedAt"] = System.currentTimeMillis()
                        if (operation == "CREATE") {
                            data["createdAt"] = System.currentTimeMillis()
                        }
                        data["ownerId"] = userId
                        db.collection(collection).document(entityId).set(data).get()
                        results.add(entityId to true)
                    }
                    "DELETE" -> {
                        db.collection(collection).document(entityId).delete().get()
                        // Log deletion for other devices' delta sync
                        db.collection("deletion_log").add(
                            hashMapOf<String, Any>(
                                "userId" to userId,
                                "entityType" to entityType.lowercase(),
                                "entityId" to entityId,
                                "deletedAt" to System.currentTimeMillis(),
                            )
                        ).get()
                        results.add(entityId to true)
                    }
                    else -> results.add(entityId to false)
                }
            } catch (e: Exception) {
                logger.warn("Batch sync failed for $entityType/$entityId: ${e.message}")
                results.add(entityId to false)
            }
        }

        return results
    }

    // ─── Private helpers ──────────────────────────────────────────────

    private fun getDeletedIds(
        firestore: Firestore,
        userId: String,
        entityType: String,
        sinceMillis: Long,
    ): List<String> {
        return try {
            firestore.collection("deletion_log")
                .whereEqualTo("userId", userId)
                .whereEqualTo("entityType", entityType)
                .whereGreaterThan("deletedAt", sinceMillis)
                .get().get().documents
                .map { it.getString("entityId") ?: "" }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.debug("No deletion log for $entityType: ${e.message}")
            emptyList()
        }
    }

    private fun JsonObject.toFirestoreMap(): HashMap<String, Any> {
        val map = hashMapOf<String, Any>()
        for ((key, value) in this) {
            when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> map[key] = value.content
                        value.booleanOrNull != null -> map[key] = value.boolean
                        value.longOrNull != null -> map[key] = value.long
                        value.doubleOrNull != null -> map[key] = value.double
                    }
                }
                is JsonArray -> map[key] = value.map { el ->
                    when (el) {
                        is JsonPrimitive -> when {
                            el.isString -> el.content
                            el.longOrNull != null -> el.long
                            else -> el.content
                        }
                        else -> el.toString()
                    }
                }
                is JsonObject -> map[key] = value.toFirestoreMap()
                else -> map[key] = value.toString()
            }
        }
        return map
    }
}
