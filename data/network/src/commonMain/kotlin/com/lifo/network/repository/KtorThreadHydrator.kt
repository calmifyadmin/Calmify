package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.ThreadResponse
import com.lifo.util.mapper.toDomain
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ThreadHydrator
import com.lifo.util.repository.ThreadRepository.Thread

class KtorThreadHydrator(
    private val api: KtorApiClient,
) : ThreadHydrator {

    override suspend fun hydrate(threads: List<Thread>, currentUserId: String): List<Thread> {
        return threads.map { thread -> hydrateSingle(thread, currentUserId) }
    }

    override suspend fun hydrateSingle(thread: Thread, currentUserId: String): Thread {
        // Server GET /api/v1/threads/{id} already returns fully hydrated thread
        // (author info, like/repost status for current user)
        val result = api.get<ThreadResponse>("/api/v1/threads/${thread.threadId}")
        return when (result) {
            is RequestState.Success -> result.data.data.toDomain()
            else -> thread // Return original if server fails
        }
    }
}
