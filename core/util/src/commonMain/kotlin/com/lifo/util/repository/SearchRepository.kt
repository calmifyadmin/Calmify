package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * SearchRepository Interface
 *
 * Provides keyword and semantic search via Vertex AI Vector Search.
 * Searches across threads and users.
 */
interface SearchRepository {
    fun searchThreads(query: String, limit: Int = 20): Flow<RequestState<List<ThreadRepository.Thread>>>
    fun searchUsers(query: String, limit: Int = 20): Flow<RequestState<List<SocialGraphRepository.SocialUser>>>
    fun semanticSearch(query: String, limit: Int = 20): Flow<RequestState<List<ThreadRepository.Thread>>>
}
