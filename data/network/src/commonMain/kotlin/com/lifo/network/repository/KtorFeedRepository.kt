package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.ThreadListResponse
import com.lifo.util.mapper.toDomain
import com.lifo.util.model.RequestState
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.FeedRepository.FeedPage
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorFeedRepository(
    private val api: KtorApiClient,
) : FeedRepository {

    override fun getForYouFeed(
        userId: String,
        pageSize: Int,
        cursor: String?,
    ): Flow<RequestState<FeedPage>> = flow {
        emit(RequestState.Loading)
        val cursorParam = if (cursor != null) "&cursor=$cursor" else ""
        val result = api.get<ThreadListResponse>("/api/v1/feed/for-you?limit=$pageSize$cursorParam")
        emit(result.map { response ->
            FeedPage(
                items = response.data.map { it.toDomain() },
                nextCursor = response.meta?.cursor?.takeIf { it.isNotEmpty() },
                hasMore = response.meta?.hasMore ?: false,
            )
        })
    }

    override fun getFollowingFeed(
        userId: String,
        pageSize: Int,
        cursor: String?,
    ): Flow<RequestState<FeedPage>> = flow {
        emit(RequestState.Loading)
        val cursorParam = if (cursor != null) "&cursor=$cursor" else ""
        val result = api.get<ThreadListResponse>("/api/v1/feed/following?limit=$pageSize$cursorParam")
        emit(result.map { response ->
            FeedPage(
                items = response.data.map { it.toDomain() },
                nextCursor = response.meta?.cursor?.takeIf { it.isNotEmpty() },
                hasMore = response.meta?.hasMore ?: false,
            )
        })
    }

    override suspend fun refreshFeed(userId: String): RequestState<Boolean> {
        return RequestState.Success(true) // Feeds are always fresh from server
    }
}
