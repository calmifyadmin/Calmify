package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * ThreadRepository Interface
 *
 * Manages social threads (posts) in Spanner Graph.
 * Threads can be top-level posts or replies to other threads.
 */
interface ThreadRepository {
    fun getThreadById(threadId: String): Flow<RequestState<Thread?>>
    fun getThreadsByAuthor(authorId: String, limit: Int = 50): Flow<RequestState<List<Thread>>>
    fun getReplies(parentThreadId: String): Flow<RequestState<List<Thread>>>
    suspend fun createThread(thread: Thread): RequestState<String>
    suspend fun deleteThread(threadId: String): RequestState<Boolean>
    suspend fun likeThread(userId: String, threadId: String): RequestState<Boolean>
    suspend fun unlikeThread(userId: String, threadId: String): RequestState<Boolean>
    suspend fun isLikedByUser(userId: String, threadId: String): Boolean
    suspend fun repostThread(userId: String, threadId: String): RequestState<Boolean>
    suspend fun unrepostThread(userId: String, threadId: String): RequestState<Boolean>

    data class Thread(
        val threadId: String = "",
        val authorId: String = "",
        val parentThreadId: String? = null,
        val text: String = "",
        val likeCount: Long = 0,
        val replyCount: Long = 0,
        val repostCount: Long = 0,
        val visibility: String = "public",
        val moodTag: String? = null,
        val isFromJournal: Boolean = false,
        val createdAt: Long = 0,
        val updatedAt: Long? = null,
        val authorDisplayName: String? = null,
        val authorUsername: String? = null,
        val authorAvatarUrl: String? = null,
        val authorIsVerified: Boolean = false,
        val mediaUrls: List<String> = emptyList(),
        val isLikedByCurrentUser: Boolean = false,
        val isRepostedByCurrentUser: Boolean = false,
        val replyPreviewAvatars: List<String> = emptyList(),
        val viewCount: Long = 0,
        val shareCount: Long = 0,
        val postCategory: String? = null,
    )
}
