package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * FeedRepository Interface
 *
 * Manages personalized feed generation.
 * Two feed types: ForYou (Vertex AI semantic ranking) and Following (chronological).
 */
interface FeedRepository {
    fun getForYouFeed(userId: String, pageSize: Int = 20, cursor: String? = null): Flow<RequestState<FeedPage>>
    fun getFollowingFeed(userId: String, pageSize: Int = 20, cursor: String? = null): Flow<RequestState<FeedPage>>
    suspend fun refreshFeed(userId: String): RequestState<Boolean>

    data class FeedPage(
        val items: List<ThreadRepository.Thread> = emptyList(),
        val nextCursor: String? = null,
        val hasMore: Boolean = false
    )
}
