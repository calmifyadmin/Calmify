package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.DiaryDeltaResponse
import com.lifo.shared.api.GenericDeltaResponse
import com.lifo.shared.model.DiaryProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Server-side delta sync service.
 *
 * Returns changes since a given timestamp for a user's entities.
 * Supports diary + all 13 wellness types + chat sessions/messages.
 *
 * IMPORTANT: All collection names MUST match the Android client exactly (snake_case).
 */
class SyncService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(SyncService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Maps SyncEntityType names to Firestore collection paths -- matches client exactly
    private val collectionMap = mapOf(
        "DIARY" to "diaries",
        "DIARY_INSIGHT" to "diary_insights",
        "CHAT_SESSION" to "chat_sessions",
        "CHAT_MESSAGE" to "chat_messages",
        "PROFILE_SETTINGS" to "profile_settings",
        "PSYCHOLOGICAL_PROFILE" to "psychological_profiles",
        "HABIT" to "habits",
        "HABIT_COMPLETION" to "habit_completions",
        "GRATITUDE" to "gratitude_entries",
        "ENERGY" to "energy_checkins",
        "SLEEP" to "sleep_logs",
        "MEDITATION" to "meditation_sessions",
        "MOVEMENT" to "movement_logs",
        "REFRAME" to "thought_reframes",
        "WELLBEING" to "wellbeing_snapshots",
        "AWE" to "awe_entries",
        "CONNECTION" to "connection_entries",
        "RECURRING_THOUGHT" to "recurring_thoughts",
        "BLOCK" to "blocks",
        "VALUES" to "values_discovery",
        "THREAD" to "threads",
        "NOTIFICATION" to "notifications",
    )

    // Owner field varies by collection
    private val ownerFieldMap = mapOf(
        "threads" to "authorId",
        "notifications" to "userId",
    )

    private fun ownerFieldFor(collection: String): String = ownerFieldMap[collection] ?: "ownerId"

    /**
     * Get diary changes since [sinceMillis] (typed, backwards-compatible).
     */
    suspend fun getDiaryChangesSince(userId: String, sinceMillis: Long): DiaryDeltaResponse = withContext(Dispatchers.IO) {
        val docs = db.collection("diaries")
            .whereEqualTo("ownerId", userId)
            .whereGreaterThan("updatedAt", sinceMillis)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .get().get().documents

        val created = mutableListOf<DiaryProto>()
        val updated = mutableListOf<DiaryProto>()

        for (doc in docs) {
            val createdAt = doc.getLong("createdAt") ?: 0L

            // Handle date field: could be Firestore Timestamp or Long millis
            val dateMillis = when (val dateVal = doc.get("date")) {
                is com.google.cloud.Timestamp -> dateVal.toDate().time
                is Long -> dateVal
                is Number -> dateVal.toLong()
                else -> doc.getLong("dateMillis") ?: 0L
            }

            val proto = DiaryProto(
                id = doc.id,
                ownerId = doc.getString("ownerId") ?: "",
                mood = doc.getString("mood") ?: "Neutral",
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                dateMillis = dateMillis,
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

        val deletedIds = getDeletedIds(userId, "DIARY", sinceMillis)

        logger.info("Delta sync DIARY for user $userId: ${created.size} created, ${updated.size} updated, ${deletedIds.size} deleted")

        DiaryDeltaResponse(
            created = created,
            updated = updated,
            deletedIds = deletedIds,
            serverTime = System.currentTimeMillis(),
        )
    }

    /**
     * Generic delta sync for any entity type.
     * Returns documents as JSON-encoded strings -- client deserializes based on type.
     */
    suspend fun getChangesSince(userId: String, entityType: String, sinceMillis: Long): GenericDeltaResponse = withContext(Dispatchers.IO) {
        val collectionName = collectionMap[entityType]
            ?: return@withContext GenericDeltaResponse(entityType = entityType, serverTime = System.currentTimeMillis())

        val ownerField = ownerFieldFor(collectionName)

        val docs = db.collection(collectionName)
            .whereEqualTo(ownerField, userId)
            .whereGreaterThan("updatedAt", sinceMillis)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .get().get().documents

        val created = mutableListOf<String>()
        val updated = mutableListOf<String>()

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
                        is com.google.cloud.Timestamp -> put(key, value.toDate().time)
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

            val jsonString = jsonObj.toString()
            if (createdAt > sinceMillis) {
                created.add(jsonString)
            } else {
                updated.add(jsonString)
            }
        }

        val deletedIds = getDeletedIds(userId, entityType, sinceMillis)

        logger.info("Delta sync $entityType for user $userId: ${created.size} created, ${updated.size} updated, ${deletedIds.size} deleted")

        GenericDeltaResponse(
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
     *
     * SECURITY: ownerId is ALWAYS forced to the authenticated userId.
     * Ownership is verified before UPDATE and DELETE operations.
     */
    suspend fun applyBatch(
        userId: String,
        operations: List<Triple<String, String, String>>, // (entityType, entityId, operation+payload)
    ): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Boolean>>()

        for ((entityType, entityId, opPayload) in operations) {
            val collectionName = collectionMap[entityType]
            if (collectionName == null) {
                results.add(entityId to false)
                continue
            }

            val ownerField = ownerFieldFor(collectionName)

            try {
                val parts = opPayload.split("|", limit = 2)
                val operation = parts[0]
                val payload = parts.getOrElse(1) { "{}" }

                when (operation) {
                    "CREATE", "UPDATE" -> {
                        val data = json.parseToJsonElement(payload).jsonObject.toFirestoreMap()
                        val now = System.currentTimeMillis()
                        data["updatedAt"] = now
                        if (operation == "CREATE") {
                            data["createdAt"] = now
                        }
                        // Force ownership -- NEVER trust client-supplied ownerId
                        data[ownerField] = userId

                        if (operation == "UPDATE") {
                            // Verify ownership before updating
                            val existing = db.collection(collectionName).document(entityId).get().get()
                            if (existing.exists() && existing.getString(ownerField) != userId) {
                                logger.warn("Ownership check failed for $entityType/$entityId by user $userId")
                                results.add(entityId to false)
                                continue
                            }
                        }

                        db.collection(collectionName).document(entityId).set(data).get()
                        results.add(entityId to true)
                    }
                    "DELETE" -> {
                        // Verify ownership before deleting
                        val existing = db.collection(collectionName).document(entityId).get().get()
                        if (existing.exists() && existing.getString(ownerField) != userId) {
                            logger.warn("Ownership check failed on DELETE for $entityType/$entityId by user $userId")
                            results.add(entityId to false)
                            continue
                        }

                        db.collection(collectionName).document(entityId).delete().get()
                        // Log deletion for other devices' delta sync
                        db.collection("deletion_log").add(
                            hashMapOf<String, Any>(
                                "userId" to userId,
                                "entityType" to entityType,
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

        results
    }

    // ─── Private helpers ──────────────────────────────────────────────

    private fun getDeletedIds(
        userId: String,
        entityType: String,
        sinceMillis: Long,
    ): List<String> {
        return try {
            db.collection("deletion_log")
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
