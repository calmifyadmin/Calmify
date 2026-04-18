package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.repository.GardenRepository
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * KMP REST implementation of GardenRepository.
 *
 *   GET  /api/v1/garden                         → current state
 *   POST /api/v1/garden/explored/{activityId}   → mark explored
 *   POST /api/v1/garden/favorites/{activityId}  → toggle favorite
 *
 * Flow<Set<String>> is single-emission (no server push): callers re-subscribe on screen re-open.
 */
class KtorGardenRepository(
    private val api: KtorApiClient,
) : GardenRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Serializable
    private data class GardenStateDto(
        val exploredActivities: List<String> = emptyList(),
        val favorites: List<String> = emptyList(),
    )

    private suspend fun fetchState(): GardenStateDto? {
        val token = api.getIdToken() ?: return null
        val response = api.client.get("/api/v1/garden") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess() || response.status == HttpStatusCode.NoContent) return null
        return json.decodeFromString(GardenStateDto.serializer(), response.bodyAsText())
    }

    override fun getExploredActivities(userId: String): Flow<Set<String>> = flow {
        emit(fetchState()?.exploredActivities?.toSet() ?: emptySet())
    }

    override fun getFavorites(userId: String): Flow<Set<String>> = flow {
        emit(fetchState()?.favorites?.toSet() ?: emptySet())
    }

    override suspend fun markExplored(userId: String, activityId: String) {
        val token = api.getIdToken() ?: return
        api.client.post("/api/v1/garden/explored/$activityId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
    }

    override suspend fun toggleFavorite(userId: String, activityId: String) {
        val token = api.getIdToken() ?: return
        api.client.post("/api/v1/garden/favorites/$activityId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
    }
}
