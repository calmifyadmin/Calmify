package com.lifo.notifications

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.NotificationRepository

object NotificationsContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadNotifications : Intent
        data class MarkAsRead(val notificationId: String) : Intent
        data object MarkAllRead : Intent
    }

    @Immutable
    data class State(
        val notifications: List<NotificationRepository.Notification> = emptyList(),
        val unreadCount: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
        data class NavigateToUserProfile(val userId: String) : Effect
    }
}
