package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.IkigaiExploration
import com.lifo.util.repository.IkigaiRepository
import io.ktor.client.request.accept
import io.ktor.client.request.delete
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
 * KMP REST implementation of IkigaiRepository.
 *
 *   GET    /api/v1/ikigai      → caller's exploration (204 if none)
 *   PUT    /api/v1/ikigai      → upsert
 *   DELETE /api/v1/ikigai/{id} → delete
 *
 * Flow<IkigaiExploration?> is single-emission — screens re-subscribe on re-open.
 */
class KtorIkigaiRepository(
    private val api: KtorApiClient,
) : IkigaiRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Serializable
    private data class ExplorationDto(
        val id: String = "",
        val ownerId: String = "",
        val passionItems: List<String> = emptyList(),
        val talentItems: List<String> = emptyList(),
        val missionItems: List<String> = emptyList(),
        val professionItems: List<String> = emptyList(),
        val aiInsight: String = "",
        val createdAtMillis: Long = 0L,
        val updatedAtMillis: Long = 0L,
    )

    override fun getExploration(userId: String): Flow<IkigaiExploration?> = flow {
        val token = api.getIdToken()
        if (token == null) { emit(null); return@flow }
        val response = api.client.get("/api/v1/ikigai") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NoContent || !response.status.isSuccess()) {
            emit(null)
            return@flow
        }
        val dto = json.decodeFromString(ExplorationDto.serializer(), response.bodyAsText())
        emit(dto.toDomain(userId))
    }

    override suspend fun saveExploration(exploration: IkigaiExploration): Result<Unit> = runCatching {
        val token = api.getIdToken() ?: error("not authenticated")
        val dto = exploration.toDto()
        val response = api.client.put("/api/v1/ikigai") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            error("ikigai save failed: HTTP ${response.status.value} ${response.bodyAsText().take(200)}")
        }
    }

    override suspend fun deleteExploration(explorationId: String): Result<Unit> = runCatching {
        val token = api.getIdToken() ?: error("not authenticated")
        val response = api.client.delete("/api/v1/ikigai/$explorationId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NoContent) {
            error("ikigai delete failed: HTTP ${response.status.value}")
        }
    }

    private fun IkigaiExploration.toDto(): ExplorationDto = ExplorationDto(
        id = id,
        ownerId = ownerId,
        passionItems = passionItems,
        talentItems = talentItems,
        missionItems = missionItems,
        professionItems = professionItems,
        aiInsight = aiInsight,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun ExplorationDto.toDomain(userId: String): IkigaiExploration = IkigaiExploration(
        id = id,
        ownerId = if (ownerId.isNotEmpty()) ownerId else userId,
        passionItems = passionItems,
        talentItems = talentItems,
        missionItems = missionItems,
        professionItems = professionItems,
        aiInsight = aiInsight,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
