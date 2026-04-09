package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.NotificationRepository
import com.lifo.util.repository.NotificationRepository.Notification
import com.lifo.util.repository.NotificationRepository.NotificationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

class KtorNotificationRepository(
    private val api: KtorApiClient,
) : NotificationRepository {

    override fun getNotifications(userId: String, limit: Int): Flow<RequestState<List<Notification>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<NotificationsApiResponse>("/api/v1/notifications?limit=$limit")
        emit(result.map { response ->
            response.data.map { it.toDomain() }
        })
    }

    override fun getUnreadCount(userId: String): Flow<Int> = flow {
        val result = api.get<UnreadCountApiResponse>("/api/v1/notifications/unread-count")
        emit(result.getDataOrNull()?.count ?: 0)
    }

    override suspend fun markAsRead(notificationId: String): RequestState<Boolean> {
        return api.postNoBody("/api/v1/notifications/$notificationId/read")
    }

    override suspend fun markAllAsRead(userId: String): RequestState<Boolean> {
        return api.postNoBody("/api/v1/notifications/read-all")
    }

    override suspend fun createNotification(notification: Notification): RequestState<String> {
        return RequestState.Error(Exception("Client cannot create notifications — server-side only"))
    }
}

@Serializable
private data class NotificationsApiResponse(
    val data: List<NotificationDto> = emptyList(),
)

@Serializable
private data class NotificationDto(
    val id: String = "",
    val userId: String = "",
    val type: String = "OTHER",
    val actorId: String = "",
    val actorName: String? = null,
    val actorAvatarUrl: String? = null,
    val threadId: String? = null,
    val message: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = 0,
) {
    fun toDomain() = Notification(
        id = id,
        userId = userId,
        type = try { NotificationType.valueOf(type) } catch (_: Exception) { NotificationType.OTHER },
        actorId = actorId,
        actorName = actorName,
        actorAvatarUrl = actorAvatarUrl,
        threadId = threadId,
        message = message,
        isRead = isRead,
        createdAt = createdAt,
    )
}

@Serializable
private data class UnreadCountApiResponse(val count: Int = 0)
