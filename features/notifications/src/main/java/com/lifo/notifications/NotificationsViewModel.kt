package com.lifo.notifications

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<NotificationsContract.Intent, NotificationsContract.State, NotificationsContract.Effect>(
    initialState = NotificationsContract.State()
) {

    private val currentUserId: String?
        get() = authProvider.currentUserId

    private var notificationsJob: Job? = null
    private var unreadCountJob: Job? = null

    override fun handleIntent(intent: NotificationsContract.Intent) {
        when (intent) {
            is NotificationsContract.Intent.LoadNotifications -> loadNotifications()
            is NotificationsContract.Intent.MarkAsRead -> markAsRead(intent.notificationId)
            is NotificationsContract.Intent.MarkAllRead -> markAllRead()
            is NotificationsContract.Intent.SelectFilter -> selectFilter(intent.filter)
        }
    }

    private fun selectFilter(filter: NotificationsContract.NotificationFilter) {
        updateState { copy(selectedFilter = filter) }
    }

    private fun loadNotifications() {
        val userId = currentUserId ?: run {
            sendEffect(NotificationsContract.Effect.ShowError("User not authenticated"))
            return
        }

        // Cancel previous collection jobs to avoid duplicates
        notificationsJob?.cancel()
        unreadCountJob?.cancel()

        notificationsJob = scope.launch {
            notificationRepository.getNotifications(userId).collect { requestState ->
                when (requestState) {
                    is RequestState.Idle -> { /* no-op */ }
                    is RequestState.Loading -> {
                        updateState { copy(isLoading = true, error = null) }
                    }
                    is RequestState.Success -> {
                        updateState {
                            copy(
                                notifications = requestState.data,
                                isLoading = false,
                                error = null,
                            )
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            copy(
                                isLoading = false,
                                error = requestState.message,
                            )
                        }
                        sendEffect(NotificationsContract.Effect.ShowError(requestState.message))
                    }
                }
            }
        }

        unreadCountJob = scope.launch {
            notificationRepository.getUnreadCount(userId).collect { count ->
                updateState { copy(unreadCount = count) }
            }
        }
    }

    private fun markAsRead(notificationId: String) {
        // Optimistic update
        updateState {
            copy(
                notifications = notifications.map { notification ->
                    if (notification.id == notificationId) {
                        notification.copy(isRead = true)
                    } else {
                        notification
                    }
                },
                unreadCount = (unreadCount - 1).coerceAtLeast(0),
            )
        }

        scope.launch {
            when (val result = notificationRepository.markAsRead(notificationId)) {
                is RequestState.Error -> {
                    // Revert optimistic update
                    updateState {
                        copy(
                            notifications = notifications.map { notification ->
                                if (notification.id == notificationId) {
                                    notification.copy(isRead = false)
                                } else {
                                    notification
                                }
                            },
                            unreadCount = unreadCount + 1,
                        )
                    }
                    sendEffect(NotificationsContract.Effect.ShowError(result.message))
                }
                else -> { /* Success, Loading, Idle -- no-op */ }
            }
        }
    }

    private fun markAllRead() {
        val userId = currentUserId ?: return

        // Optimistic update
        val previousNotifications = currentState.notifications
        val previousUnreadCount = currentState.unreadCount

        updateState {
            copy(
                notifications = notifications.map { it.copy(isRead = true) },
                unreadCount = 0,
            )
        }

        scope.launch {
            when (val result = notificationRepository.markAllAsRead(userId)) {
                is RequestState.Error -> {
                    // Revert optimistic update
                    updateState {
                        copy(
                            notifications = previousNotifications,
                            unreadCount = previousUnreadCount,
                        )
                    }
                    sendEffect(NotificationsContract.Effect.ShowError(result.message))
                }
                else -> { /* Success, Loading, Idle -- no-op */ }
            }
        }
    }
}
