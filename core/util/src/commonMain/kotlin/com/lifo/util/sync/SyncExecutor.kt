package com.lifo.util.sync

/**
 * Executes sync operations against the server.
 *
 * This interface decouples the SyncEngine from the specific HTTP client
 * implementation (KtorApiClient). Implemented in data/network.
 */
interface SyncExecutor {
    /**
     * Push a local change to the server.
     *
     * @param entityType e.g. "DIARY", "GRATITUDE"
     * @param entityId the entity's ID
     * @param operation "CREATE", "UPDATE", or "DELETE"
     * @param payload serialized entity data (JSON)
     * @throws Exception if the server rejects the operation
     */
    suspend fun execute(entityType: String, entityId: String, operation: String, payload: String)

    /**
     * Pull changes from server since [sinceMillis].
     * Applies changes directly to the local SQLDelight database.
     */
    suspend fun pullChanges(entityType: String, sinceMillis: Long)
}
