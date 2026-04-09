package com.lifo.mongo.sync

import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.util.currentTimeMillis
import com.lifo.util.sync.ConnectivityObserver
import com.lifo.util.sync.ConnectivityStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Offline-first sync engine.
 *
 * - Writes go to SQLDelight immediately (0ms latency) + enqueued for server sync
 * - When online: drains the queue, pushing local changes to server
 * - Periodically pulls server changes (delta sync) to keep local DB fresh
 * - Retry with exponential backoff (1s, 2s, 4s, 8s, 16s, max 5 attempts)
 */
class SyncEngine(
    private val database: CalmifyDatabase,
    private val connectivityObserver: ConnectivityObserver,
    private val syncExecutor: SyncExecutor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val logger = LoggerFactory.getLogger(SyncEngine::class.java)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val syncOps get() = database.syncOperationQueries
    private val syncMeta get() = database.syncMetadataQueries

    /**
     * Start the sync loop. Call once on app startup.
     */
    fun start() {
        // Watch connectivity — drain queue when coming online
        scope.launch {
            connectivityObserver.observe().collect { status ->
                if (status == ConnectivityStatus.Available) {
                    logger.info("Network available — draining sync queue")
                    drainQueue()
                }
            }
        }

        // Periodic sync every 5 minutes
        scope.launch {
            while (isActive) {
                delay(5.minutes)
                if (connectivityObserver.isOnline()) {
                    pullChanges()
                }
            }
        }

        // Update pending count
        scope.launch {
            while (isActive) {
                _pendingCount.value = syncOps.countPending().executeAsOne().toInt()
                delay(2.seconds)
            }
        }
    }

    /**
     * Enqueue a sync operation. Called by sync-aware repositories after local writes.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        operation: String,
        payload: String,
    ) {
        syncOps.insert(
            id = Uuid.random().toString(),
            entity_type = entityType,
            entity_id = entityId,
            operation = operation,
            payload = payload,
            status = "PENDING",
            retry_count = 0,
            created_at = currentTimeMillis(),
            last_attempt_at = null,
            error_message = null,
        )
        _pendingCount.value = syncOps.countPending().executeAsOne().toInt()

        // Attempt immediate sync if online
        if (connectivityObserver.isOnline()) {
            scope.launch { drainQueue() }
        }
    }

    /**
     * Process all pending sync operations.
     */
    private suspend fun drainQueue() {
        if (_syncState.value == SyncState.SYNCING) return // Already syncing
        _syncState.value = SyncState.SYNCING

        try {
            val pending = syncOps.getPendingOperations(limit = 50).executeAsList()
            if (pending.isEmpty()) {
                _syncState.value = SyncState.IDLE
                return
            }

            logger.info("Draining ${pending.size} pending sync operations")

            for (op in pending) {
                syncOps.markSyncing(now = currentTimeMillis(), id = op.id)

                try {
                    syncExecutor.execute(op.entity_type, op.entity_id, op.operation, op.payload)
                    syncOps.markCompleted(id = op.id)
                    logger.debug("Synced: ${op.entity_type}/${op.entity_id} [${op.operation}]")
                } catch (e: Exception) {
                    syncOps.markFailed(error = e.message ?: "Unknown error", id = op.id)
                    logger.warn("Sync failed for ${op.entity_type}/${op.entity_id}: ${e.message}")

                    // Exponential backoff for retries
                    if (op.retry_count < 4) {
                        val backoff = (1L shl op.retry_count.toInt()) * 1000L // 1s, 2s, 4s, 8s
                        delay(backoff)
                    }
                }
            }

            _pendingCount.value = syncOps.countPending().executeAsOne().toInt()
            _syncState.value = if (_pendingCount.value > 0) SyncState.ERROR else SyncState.IDLE
        } catch (e: Exception) {
            logger.error("Drain queue failed", e)
            _syncState.value = SyncState.ERROR
        }
    }

    /**
     * Pull changes from server (delta sync).
     */
    private suspend fun pullChanges() {
        try {
            for (entityType in SyncEntityType.entries) {
                val lastSync = syncMeta.getLastSync(entityType.name).executeAsOneOrNull() ?: 0L
                syncExecutor.pullChanges(entityType.name, lastSync)
                syncMeta.upsertLastSync(
                    entityType = entityType.name,
                    lastSyncAt = currentTimeMillis(),
                    serverVersion = null,
                )
            }
        } catch (e: Exception) {
            logger.warn("Pull changes failed: ${e.message}")
        }
    }

    fun stop() {
        scope.cancel()
    }
}

enum class SyncState {
    IDLE,
    SYNCING,
    ERROR,
}

enum class SyncEntityType {
    DIARY,
    CHAT_SESSION,
    CHAT_MESSAGE,
    GRATITUDE,
    HABIT,
    ENERGY,
    SLEEP,
    MEDITATION,
    MOVEMENT,
    REFRAME,
    WELLBEING,
    AWE,
    CONNECTION,
    RECURRING_THOUGHT,
    BLOCK,
    VALUES,
}
