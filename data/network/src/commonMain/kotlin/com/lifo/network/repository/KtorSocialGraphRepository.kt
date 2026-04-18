package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.SocialGraphRepository.SocialUser
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * KMP REST implementation of SocialGraphRepository.
 *
 * All mutation endpoints implicitly scope to caller (server enforces principal.uid
 * as follower/blocker/profile owner). Read endpoints are public within auth scope.
 *
 * Flow<Boolean> (isFollowing/isBlocked) and Flow<RequestState<...>> emit a single
 * value — screens re-subscribe on re-open / refresh action.
 */
class KtorSocialGraphRepository(
    private val api: KtorApiClient,
) : SocialGraphRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Serializable
    private data class SocialUserDto(
        val userId: String = "",
        val username: String? = null,
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val coverPhotoUrl: String? = null,
        val bio: String? = null,
        val isVerified: Boolean = false,
        val followerCount: Long = 0,
        val followingCount: Long = 0,
        val threadCount: Long = 0,
        val interests: List<String> = emptyList(),
        val links: List<String> = emptyList(),
        val followerPreviewAvatars: List<String> = emptyList(),
        val profileViews30Days: Long = 0,
    )

    @Serializable
    private data class UsersResponse(val data: List<SocialUserDto> = emptyList())

    @Serializable
    private data class CheckFollowingResponse(val following: Boolean = false)

    @Serializable
    private data class CheckBlockedResponse(val blocked: Boolean = false)

    @Serializable
    private data class UsernameAvailableResponse(val available: Boolean = false)

    @Serializable
    private data class ResolveUsernameResponse(val userId: String = "")

    // -- Follow / Unfollow --

    override suspend fun follow(followerId: String, followeeId: String): RequestState<Boolean> = runMutation(
        method = "POST",
        path = "/api/v1/social-graph/follow/$followeeId",
    )

    override suspend fun unfollow(followerId: String, followeeId: String): RequestState<Boolean> = runMutation(
        method = "DELETE",
        path = "/api/v1/social-graph/follow/$followeeId",
    )

    override suspend fun block(blockerId: String, blockedId: String): RequestState<Boolean> = runMutation(
        method = "POST",
        path = "/api/v1/social-graph/block/$blockedId",
    )

    override suspend fun unblock(blockerId: String, blockedId: String): RequestState<Boolean> = runMutation(
        method = "DELETE",
        path = "/api/v1/social-graph/block/$blockedId",
    )

    override fun isFollowing(followerId: String, followeeId: String): Flow<Boolean> = flow {
        val token = api.getIdToken()
        if (token == null) { emit(false); return@flow }
        val response = api.client.get("/api/v1/social-graph/follow/$followeeId/check") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        val following = if (!response.status.isSuccess()) false else
            json.decodeFromString(CheckFollowingResponse.serializer(), response.bodyAsText()).following
        emit(following)
    }

    override fun isBlocked(blockerId: String, blockedId: String): Flow<Boolean> = flow {
        val token = api.getIdToken()
        if (token == null) { emit(false); return@flow }
        val response = api.client.get("/api/v1/social-graph/block/$blockedId/check") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        val blocked = if (!response.status.isSuccess()) false else
            json.decodeFromString(CheckBlockedResponse.serializer(), response.bodyAsText()).blocked
        emit(blocked)
    }

    override fun getFollowers(userId: String, limit: Int): Flow<RequestState<List<SocialUser>>> = flow {
        emit(RequestState.Loading)
        emit(fetchUsers("/api/v1/social-graph/followers/$userId?limit=$limit"))
    }

    override fun getFollowing(userId: String, limit: Int): Flow<RequestState<List<SocialUser>>> = flow {
        emit(RequestState.Loading)
        emit(fetchUsers("/api/v1/social-graph/following/$userId?limit=$limit"))
    }

    override fun getSuggestions(userId: String, limit: Int): Flow<RequestState<List<SocialUser>>> = flow {
        emit(RequestState.Loading)
        emit(fetchUsers("/api/v1/social-graph/suggestions?limit=$limit"))
    }

    override fun getProfile(userId: String): Flow<RequestState<SocialUser>> = flow {
        emit(RequestState.Loading)
        val token = api.getIdToken()
        if (token == null) {
            emit(RequestState.Success(SocialUser(userId = userId)))
            return@flow
        }
        val response = api.client.get("/api/v1/social-graph/profiles/$userId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            emit(RequestState.Error(RuntimeException("profile fetch failed HTTP ${response.status.value}")))
            return@flow
        }
        val dto = json.decodeFromString(SocialUserDto.serializer(), response.bodyAsText())
        emit(RequestState.Success(dto.toDomain()))
    }

    override suspend fun updateProfile(
        userId: String,
        updates: Map<String, Any?>,
    ): RequestState<Boolean> {
        val token = api.getIdToken() ?: return RequestState.Error(IllegalStateException("not authenticated"))
        val body = JsonObject(updates.mapValues { (_, v) -> toJsonElement(v) })
        val response = api.client.patch("/api/v1/social-graph/profiles/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(body)
        }
        return if (response.status.isSuccess()) RequestState.Success(true)
        else RequestState.Error(RuntimeException("updateProfile HTTP ${response.status.value}"))
    }

    override suspend fun isUsernameAvailable(username: String): RequestState<Boolean> {
        val token = api.getIdToken() ?: return RequestState.Error(IllegalStateException("not authenticated"))
        val response = api.client.get("/api/v1/social-graph/profiles/username-available?username=$username") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) return RequestState.Error(RuntimeException("HTTP ${response.status.value}"))
        val parsed = json.decodeFromString(UsernameAvailableResponse.serializer(), response.bodyAsText())
        return RequestState.Success(parsed.available)
    }

    override suspend fun resolveUsername(username: String): RequestState<String?> {
        val token = api.getIdToken() ?: return RequestState.Error(IllegalStateException("not authenticated"))
        val response = api.client.get("/api/v1/social-graph/profiles/resolve-username?username=$username") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) return RequestState.Error(RuntimeException("HTTP ${response.status.value}"))
        val parsed = json.decodeFromString(ResolveUsernameResponse.serializer(), response.bodyAsText())
        return RequestState.Success(parsed.userId.ifEmpty { null })
    }

    // -- internals --

    private suspend fun runMutation(method: String, path: String): RequestState<Boolean> {
        val token = api.getIdToken() ?: return RequestState.Error(IllegalStateException("not authenticated"))
        val response = when (method) {
            "POST" -> api.client.post(path) {
                header(HttpHeaders.Authorization, "Bearer $token")
                accept(ContentType.Application.Json)
            }
            "DELETE" -> api.client.delete(path) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            else -> error("unsupported mutation method $method")
        }
        return if (response.status.isSuccess()) RequestState.Success(true)
        else RequestState.Error(RuntimeException("$method $path → HTTP ${response.status.value}"))
    }

    private suspend fun fetchUsers(path: String): RequestState<List<SocialUser>> {
        val token = api.getIdToken() ?: return RequestState.Error(IllegalStateException("not authenticated"))
        val response = api.client.get(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) return RequestState.Error(RuntimeException("$path HTTP ${response.status.value}"))
        val parsed = json.decodeFromString(UsersResponse.serializer(), response.bodyAsText())
        return RequestState.Success(parsed.data.map { it.toDomain() })
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        is Map<*, *> -> JsonObject(
            value.entries
                .filter { it.key is String }
                .associate { (k, v) -> (k as String) to toJsonElement(v) }
        )
        else -> JsonPrimitive(value.toString())
    }

    private fun SocialUserDto.toDomain(): SocialUser = SocialUser(
        userId = userId,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        coverPhotoUrl = coverPhotoUrl,
        bio = bio,
        isVerified = isVerified,
        followerCount = followerCount,
        followingCount = followingCount,
        threadCount = threadCount,
        interests = interests,
        links = links,
        followerPreviewAvatars = followerPreviewAvatars,
        profileViews30Days = profileViews30Days,
    )
}
