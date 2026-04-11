package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.ThreadListResponse
import com.lifo.util.mapper.toDomain
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SearchRepository
import com.lifo.util.repository.SocialGraphRepository.SocialUser
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

class KtorSearchRepository(
    private val api: KtorApiClient,
) : SearchRepository {

    override fun searchThreads(query: String, limit: Int): Flow<RequestState<List<Thread>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ThreadListResponse>("/api/v1/search/threads?q=$query&limit=$limit")
        emit(result.map { it.data.map { t -> t.toDomain() } })
    }

    override fun searchUsers(query: String, limit: Int): Flow<RequestState<List<SocialUser>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<UserSearchListDto>("/api/v1/search/users?q=$query&limit=$limit")
        emit(result.map { dto ->
            dto.data.map { u ->
                SocialUser(
                    userId = u.userId,
                    username = u.username,
                    displayName = u.displayName,
                    avatarUrl = u.avatarUrl,
                    bio = u.bio,
                    isVerified = u.isVerified,
                    followerCount = u.followerCount,
                )
            }
        })
    }

    override fun semanticSearch(query: String, limit: Int): Flow<RequestState<List<Thread>>> {
        // Semantic search uses the same text search endpoint for now.
        // Future: POST /api/v1/search/semantic with Vertex AI embeddings.
        return searchThreads(query, limit)
    }
}

@Serializable
data class UserSearchDto(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val isVerified: Boolean = false,
    val followerCount: Long = 0,
)

@Serializable
data class UserSearchListDto(
    val data: List<UserSearchDto> = emptyList(),
)
