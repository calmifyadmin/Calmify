package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.ChecklistCategory
import com.lifo.util.model.ChecklistItem
import com.lifo.util.model.EnvironmentChecklist
import com.lifo.util.model.RoutineStep
import com.lifo.util.model.defaultChecklist
import com.lifo.util.model.defaultEveningRoutine
import com.lifo.util.model.defaultMorningRoutine
import com.lifo.util.repository.EnvironmentRepository
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * KMP REST implementation of EnvironmentRepository.
 *
 *   GET /api/v1/environment  → current checklist (204 if not set)
 *   PUT /api/v1/environment  → upsert checklist
 *
 * Realtime Flow semantics preserved via single-emission Flow (no snapshot listener
 * server-side — callers that need live updates re-fetch on screen re-open).
 */
class KtorEnvironmentRepository(
    private val api: KtorApiClient,
) : EnvironmentRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Serializable
    private data class ChecklistItemDto(
        val id: String = "",
        val text: String = "",
        val isCompleted: Boolean = false,
        val category: String = "GENERALE",
    )

    @Serializable
    private data class RoutineStepDto(
        val id: String = "",
        val text: String = "",
        val durationMinutes: Int = 5,
        val isCompleted: Boolean = false,
    )

    @Serializable
    private data class ChecklistDto(
        val id: String = "",
        val ownerId: String = "",
        val items: List<ChecklistItemDto> = emptyList(),
        val morningRoutine: List<RoutineStepDto> = emptyList(),
        val eveningRoutine: List<RoutineStepDto> = emptyList(),
        val detoxTimerMinutes: Int = 60,
    )

    override fun getChecklist(userId: String): Flow<EnvironmentChecklist?> = flow {
        val token = api.getIdToken()
        if (token == null) {
            emit(null)
            return@flow
        }
        val response = api.client.get("/api/v1/environment") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NoContent || !response.status.isSuccess()) {
            emit(null)
            return@flow
        }
        val dto = json.decodeFromString(ChecklistDto.serializer(), response.bodyAsText())
        emit(dto.toDomain(userId))
    }

    override suspend fun saveChecklist(checklist: EnvironmentChecklist): Result<Unit> = runCatching {
        val token = api.getIdToken() ?: error("not authenticated")
        val dto = checklist.toDto()
        val response = api.client.put("/api/v1/environment") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            error("environment save failed: HTTP ${response.status.value} ${response.bodyAsText().take(200)}")
        }
    }

    private fun EnvironmentChecklist.toDto(): ChecklistDto = ChecklistDto(
        id = id,
        ownerId = ownerId,
        items = items.map {
            ChecklistItemDto(
                id = it.id,
                text = it.text,
                isCompleted = it.isCompleted,
                category = it.category.name,
            )
        },
        morningRoutine = morningRoutine.map {
            RoutineStepDto(
                id = it.id,
                text = it.text,
                durationMinutes = it.durationMinutes,
                isCompleted = it.isCompleted,
            )
        },
        eveningRoutine = eveningRoutine.map {
            RoutineStepDto(
                id = it.id,
                text = it.text,
                durationMinutes = it.durationMinutes,
                isCompleted = it.isCompleted,
            )
        },
        detoxTimerMinutes = detoxTimerMinutes,
    )

    private fun ChecklistDto.toDomain(userId: String): EnvironmentChecklist {
        val mappedItems = items.map {
            ChecklistItem(
                id = it.id,
                text = it.text,
                isCompleted = it.isCompleted,
                category = runCatching { ChecklistCategory.valueOf(it.category) }
                    .getOrDefault(ChecklistCategory.GENERALE),
            )
        }
        val mappedMorning = morningRoutine.map {
            RoutineStep(
                id = it.id,
                text = it.text,
                durationMinutes = it.durationMinutes,
                isCompleted = it.isCompleted,
            )
        }
        val mappedEvening = eveningRoutine.map {
            RoutineStep(
                id = it.id,
                text = it.text,
                durationMinutes = it.durationMinutes,
                isCompleted = it.isCompleted,
            )
        }
        return EnvironmentChecklist(
            id = id,
            ownerId = if (ownerId.isNotEmpty()) ownerId else userId,
            items = mappedItems.ifEmpty { defaultChecklist() },
            morningRoutine = mappedMorning.ifEmpty { defaultMorningRoutine() },
            eveningRoutine = mappedEvening.ifEmpty { defaultEveningRoutine() },
            detoxTimerMinutes = detoxTimerMinutes,
        )
    }
}
