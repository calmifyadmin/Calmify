package com.lifo.mongo.sync

import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.util.currentTimeMillis
import com.lifo.util.model.RequestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generic sync-aware wellness repository.
 *
 * Backs all 13 wellness entity types via a single `wellness_entries` table.
 * Each entity is stored as JSON in the `payload` column, typed via [serializer].
 *
 * READ: Always from SQLDelight (instant, offline).
 * WRITE: SQLDelight first (optimistic) + enqueue for server sync.
 */
class SyncWellnessRepository<T : Any>(
    private val database: CalmifyDatabase,
    private val syncEngine: SyncEngine,
    private val entityType: SyncEntityType,
    private val serializer: KSerializer<T>,
    private val getId: (T) -> String,
    private val getOwnerId: (T) -> String,
    private val getDayKey: (T) -> String,
    private val getTimestamp: (T) -> Long,
    private val userId: () -> String?,
) {
    private val queries get() = database.wellnessEntryQueries
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // ─── READ ─────────────────────────────────────────────────────────

    fun getAll(): Flow<RequestState<List<T>>> = flow {
        val uid = userId() ?: run { emit(RequestState.Error(Exception("Not authenticated"))); return@flow }
        val entries = queries.getAllByUserAndType(uid, entityType.name).executeAsList()
        emit(RequestState.Success(entries.map { deserialize(it.payload) }))
    }

    fun getByDayKey(dayKey: String): Flow<RequestState<List<T>>> = flow {
        val uid = userId() ?: run { emit(RequestState.Error(Exception("Not authenticated"))); return@flow }
        val entries = queries.getByDayKey(uid, entityType.name, dayKey).executeAsList()
        emit(RequestState.Success(entries.map { deserialize(it.payload) }))
    }

    fun getToday(dayKey: String): Flow<RequestState<T?>> = flow {
        val uid = userId() ?: run { emit(RequestState.Error(Exception("Not authenticated"))); return@flow }
        val entries = queries.getByDayKey(uid, entityType.name, dayKey).executeAsList()
        emit(RequestState.Success(entries.firstOrNull()?.let { deserialize(it.payload) }))
    }

    fun getById(id: String): Flow<RequestState<T>> = flow {
        val entry = queries.getById(id).executeAsOneOrNull()
        if (entry != null) {
            emit(RequestState.Success(deserialize(entry.payload)))
        } else {
            emit(RequestState.Error(Exception("Entry not found")))
        }
    }

    fun getInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<T>>> = flow {
        val uid = userId() ?: run { emit(RequestState.Error(Exception("Not authenticated"))); return@flow }
        val entries = queries.getByDateRange(uid, entityType.name, startMillis, endMillis).executeAsList()
        emit(RequestState.Success(entries.map { deserialize(it.payload) }))
    }

    // ─── WRITE ────────────────────────────────────────────────────────

    @OptIn(ExperimentalUuidApi::class)
    suspend fun upsert(entity: T): RequestState<String> = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        val id = getId(entity).ifEmpty { Uuid.random().toString() }
        val payload = serialize(entity)

        queries.upsert(
            id = id,
            owner_id = getOwnerId(entity),
            entity_type = entityType.name,
            day_key = getDayKey(entity),
            timestamp_millis = getTimestamp(entity),
            payload = payload,
            is_dirty = 1,
            created_at = now,
            updated_at = now,
        )

        syncEngine.enqueue(
            entityType = entityType.name,
            entityId = id,
            operation = "CREATE",
            payload = payload,
        )

        RequestState.Success(id)
    }

    suspend fun delete(id: String): RequestState<Boolean> = withContext(Dispatchers.Default) {
        queries.deleteById(id)

        syncEngine.enqueue(
            entityType = entityType.name,
            entityId = id,
            operation = "DELETE",
            payload = "",
        )

        RequestState.Success(true)
    }

    suspend fun deleteAll(): RequestState<Boolean> = withContext(Dispatchers.Default) {
        val uid = userId() ?: return@withContext RequestState.Error(Exception("Not authenticated"))
        queries.deleteAllByUserAndType(uid, entityType.name)
        RequestState.Success(true)
    }

    // ─── Delta sync: apply server changes to local DB ─────────────────

    suspend fun applyServerChanges(
        created: List<T>,
        updated: List<T>,
        deletedIds: List<String>,
    ) = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        for (entity in created + updated) {
            queries.upsert(
                id = getId(entity),
                owner_id = getOwnerId(entity),
                entity_type = entityType.name,
                day_key = getDayKey(entity),
                timestamp_millis = getTimestamp(entity),
                payload = serialize(entity),
                is_dirty = 0, // Server data is clean
                created_at = now,
                updated_at = now,
            )
        }
        for (id in deletedIds) {
            queries.deleteById(id)
        }
    }

    // ─── Serialization ────────────────────────────────────────────────

    private fun serialize(entity: T): String = json.encodeToString(serializer, entity)
    private fun deserialize(payload: String): T = json.decodeFromString(serializer, payload)
}
