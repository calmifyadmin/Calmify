package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * SocialGraphRepository Interface
 *
 * Manages social relationships via Spanner Graph (Follows, Blocks).
 * Supports follow/unfollow, block/unblock, and friend-of-friend suggestions.
 */
interface SocialGraphRepository {
    suspend fun follow(followerId: String, followeeId: String): RequestState<Boolean>
    suspend fun unfollow(followerId: String, followeeId: String): RequestState<Boolean>
    suspend fun block(blockerId: String, blockedId: String): RequestState<Boolean>
    suspend fun unblock(blockerId: String, blockedId: String): RequestState<Boolean>
    fun isFollowing(followerId: String, followeeId: String): Flow<Boolean>
    fun isBlocked(blockerId: String, blockedId: String): Flow<Boolean>
    fun getFollowers(userId: String, limit: Int = 50): Flow<RequestState<List<SocialUser>>>
    fun getFollowing(userId: String, limit: Int = 50): Flow<RequestState<List<SocialUser>>>
    fun getSuggestions(userId: String, limit: Int = 20): Flow<RequestState<List<SocialUser>>>

    data class SocialUser(
        val userId: String = "",
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val bio: String? = null,
        val isVerified: Boolean = false,
        val followerCount: Long = 0,
        val followingCount: Long = 0,
        val threadCount: Long = 0
    )
}
