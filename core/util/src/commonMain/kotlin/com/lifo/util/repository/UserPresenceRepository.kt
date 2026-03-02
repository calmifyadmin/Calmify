package com.lifo.util.repository

import kotlinx.coroutines.flow.Flow

/**
 * UserPresenceRepository Interface
 *
 * Manages user online/offline presence via Firebase Realtime Database.
 * Provides real-time presence status and last-seen timestamps.
 */
interface UserPresenceRepository {
    suspend fun setOnline(userId: String)
    suspend fun setOffline(userId: String)
    fun observePresence(userId: String): Flow<PresenceStatus>
    fun observeMultiplePresence(userIds: List<String>): Flow<Map<String, PresenceStatus>>

    data class PresenceStatus(
        val userId: String = "",
        val isOnline: Boolean = false,
        val lastSeenAt: Long = 0
    )
}
