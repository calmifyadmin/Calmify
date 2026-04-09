package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.ThreadListResponse
import com.lifo.shared.api.ThreadResponse
import com.lifo.shared.model.ThreadProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ThreadRepository
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorThreadRepository(
    private val api: KtorApiClient,
) : ThreadRepository {

    override fun getThreadById(threadId: String): Flow<RequestState<Thread?>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ThreadResponse>("/api/v1/threads/$threadId")
        emit(result.map { it.data?.toDomain() })
    }

    override fun getThreadsByAuthor(authorId: String, limit: Int): Flow<RequestState<List<Thread>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ThreadListResponse>("/api/v1/threads?authorId=$authorId&limit=$limit")
        emit(result.map { it.data.map { t -> t.toDomain() } })
    }

    override fun getReplies(parentThreadId: String): Flow<RequestState<List<Thread>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ThreadListResponse>("/api/v1/threads/$parentThreadId/replies")
        emit(result.map { it.data.map { t -> t.toDomain() } })
    }

    override suspend fun createThread(thread: Thread): RequestState<String> {
        val result = api.post<ThreadResponse>("/api/v1/threads", thread.toProto())
        return result.map { it.data?.threadId ?: "" }
    }

    override suspend fun deleteThread(threadId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/threads/$threadId")
    }

    override suspend fun likeThread(userId: String, threadId: String): RequestState<Boolean> {
        return api.postNoBody("/api/v1/threads/$threadId/like")
    }

    override suspend fun unlikeThread(userId: String, threadId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/threads/$threadId/like")
    }

    override suspend fun isLikedByUser(userId: String, threadId: String): Boolean {
        return false // Would need a server endpoint — check via thread data instead
    }

    override suspend fun repostThread(userId: String, threadId: String): RequestState<Boolean> {
        return api.postNoBody("/api/v1/threads/$threadId/repost")
    }

    override suspend fun unrepostThread(userId: String, threadId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/threads/$threadId/repost")
    }
}
