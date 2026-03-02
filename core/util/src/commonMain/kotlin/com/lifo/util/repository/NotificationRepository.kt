package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * NotificationRepository Interface
 *
 * Manages social notifications (new follower, like, reply, mention).
 * Integrates with existing wellness notifications.
 */
interface NotificationRepository {
    fun getNotifications(userId: String, limit: Int = 50): Flow<RequestState<List<Notification>>>
    fun getUnreadCount(userId: String): Flow<Int>
    suspend fun markAsRead(notificationId: String): RequestState<Boolean>
    suspend fun markAllAsRead(userId: String): RequestState<Boolean>

    data class Notification(
        val id: String = "",
        val userId: String = "",
        val type: NotificationType = NotificationType.OTHER,
        val actorId: String = "",
        val actorName: String? = null,
        val actorAvatarUrl: String? = null,
        val threadId: String? = null,
        val message: String = "",
        val isRead: Boolean = false,
        val createdAt: Long = 0
    )

    enum class NotificationType {
        NEW_FOLLOWER, LIKE, REPLY, MENTION, WELLNESS_REMINDER, OTHER
    }
}
