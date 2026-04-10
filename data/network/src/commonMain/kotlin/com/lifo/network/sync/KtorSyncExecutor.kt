package com.lifo.network.sync

import com.lifo.util.sync.SyncExecutor
import com.lifo.network.KtorApiClient
import com.lifo.shared.api.BatchSyncRequest
import com.lifo.shared.api.BatchSyncResponse
import com.lifo.shared.api.GenericDeltaResponse
import com.lifo.shared.api.SyncOperationDto
import com.lifo.util.model.RequestState
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * SyncExecutor implementation that talks to the Ktor server's /sync endpoints.
 *
 * - execute(): pushes a single operation via POST /api/v1/sync/batch (batch of 1)
 * - pullChanges(): pulls delta via GET /api/v1/sync/changes?entity=TYPE&since=TIMESTAMP
 *
 * Note: pullChanges only fetches — applying changes to SQLDelight is handled
 * by the caller (SyncEngine/SyncDiaryRepository/SyncWellnessRepository).
 */
class KtorSyncExecutor(
    private val apiClient: KtorApiClient,
    private val onDeltaReceived: suspend (entityType: String, delta: GenericDeltaResponse) -> Unit,
) : SyncExecutor {

    override suspend fun execute(
        entityType: String,
        entityId: String,
        operation: String,
        payload: String,
    ) {
        val request = BatchSyncRequest(
            operations = listOf(
                SyncOperationDto(
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    payload = payload,
                )
            )
        )

        val result = apiClient.post<BatchSyncResponse>(
            path = "/api/v1/sync/batch",
            body = request,
        )

        when (result) {
            is RequestState.Success -> {
                val response = result.data
                val failed = response.results.filter { !it.success }
                if (failed.isNotEmpty()) {
                    throw SyncException(
                        "Sync failed for ${failed.size} operation(s): " +
                            failed.joinToString { "${it.entityId}: ${it.error.ifEmpty { "unknown" }}" }
                    )
                }
            }
            is RequestState.Error -> throw result.error
            is RequestState.Loading, is RequestState.Idle -> { /* shouldn't happen */ }
        }
    }

    override suspend fun pullChanges(entityType: String, sinceMillis: Long) {
        val token = apiClient.authProvider.getIdToken()
            ?: throw SyncException("Not authenticated — cannot pull changes")

        val response = apiClient.client.get("/api/v1/sync/changes") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("entity", entityType)
            parameter("since", sinceMillis.toString())
        }

        if (!response.status.isSuccess()) {
            throw SyncException("Pull changes failed: HTTP ${response.status.value}")
        }

        val delta = response.body<GenericDeltaResponse>()
        onDeltaReceived(entityType, delta)
    }
}

class SyncException(message: String) : Exception(message)
