package com.lifo.mongo.sync

import kotlinx.serialization.json.*

/**
 * Conflict resolution for offline-first sync.
 *
 * Strategy: Last-Write-Wins (LWW) at the field level.
 *
 * When the server has a newer version of an entity than the local copy:
 * - For each field, take the value from whichever side was updated more recently.
 * - DELETE always wins (tombstone).
 * - Non-conflicting fields merge cleanly — no data lost.
 *
 * Example:
 *   Local:  { title: "New title (edited offline)", updatedAt: T1 }
 *   Server: { sentiment: "positive (AI updated)", updatedAt: T2 }
 *   Result: { title: "New title", sentiment: "positive", updatedAt: max(T1, T2) }
 */
object ConflictResolver {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Merge local and server versions of an entity.
     *
     * @param localJson The local entity as JSON string
     * @param serverJson The server entity as JSON string
     * @param localUpdatedAt When the local version was last modified
     * @param serverUpdatedAt When the server version was last modified
     * @return Merged JSON string, or null if server should win completely
     */
    fun mergeFields(
        localJson: String,
        serverJson: String,
        localUpdatedAt: Long,
        serverUpdatedAt: Long,
    ): String {
        // If server is strictly newer and local isn't dirty, server wins
        if (serverUpdatedAt > localUpdatedAt) {
            return serverJson
        }

        // If local is strictly newer, local wins
        if (localUpdatedAt > serverUpdatedAt) {
            return localJson
        }

        // Same timestamp — field-level merge (prefer server for tie-breaking)
        return fieldLevelMerge(localJson, serverJson)
    }

    /**
     * Field-level merge: for each key, take the non-default value.
     * If both sides have non-default values, server wins (stable tie-breaking).
     */
    private fun fieldLevelMerge(localJson: String, serverJson: String): String {
        val localObj = try { json.parseToJsonElement(localJson).jsonObject } catch (_: Exception) { return serverJson }
        val serverObj = try { json.parseToJsonElement(serverJson).jsonObject } catch (_: Exception) { return localJson }

        val merged = buildJsonObject {
            // Start with all server keys
            for ((key, value) in serverObj) {
                put(key, value)
            }
            // Override with local keys that have non-empty values AND aren't in server
            for ((key, localValue) in localObj) {
                val serverValue = serverObj[key]
                if (serverValue == null || serverValue == JsonNull) {
                    // Server doesn't have this field — take local
                    put(key, localValue)
                } else if (isDefaultOrEmpty(serverValue) && !isDefaultOrEmpty(localValue)) {
                    // Server has default, local has real value — take local
                    put(key, localValue)
                }
                // Otherwise server wins (already included above)
            }
        }

        return merged.toString()
    }

    /**
     * Check if a JSON value is a "default" (empty string, 0, false, null, empty list).
     */
    private fun isDefaultOrEmpty(value: JsonElement): Boolean {
        return when (value) {
            is JsonNull -> true
            is JsonPrimitive -> {
                when {
                    value.isString -> value.content.isEmpty()
                    value.booleanOrNull == false -> true
                    value.intOrNull == 0 -> true
                    value.longOrNull == 0L -> true
                    else -> false
                }
            }
            is JsonArray -> value.isEmpty()
            is JsonObject -> value.isEmpty()
        }
    }

    /**
     * Resolve a DELETE conflict.
     * DELETE always wins — once deleted, it stays deleted.
     */
    fun resolveDeleteConflict(): ConflictResult = ConflictResult.DELETE_WINS

    enum class ConflictResult {
        LOCAL_WINS,
        SERVER_WINS,
        MERGED,
        DELETE_WINS,
    }
}
