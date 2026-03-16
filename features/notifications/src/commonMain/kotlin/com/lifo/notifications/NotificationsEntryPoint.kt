package com.lifo.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.lifo.util.repository.NotificationRepository.NotificationType
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point composable for the Notifications screen.
 * Wires the ViewModel, dispatches initial load, handles effects, and renders the screen.
 */
@Composable
fun NotificationsRouteContent(
    onNavigateBack: () -> Unit = {},
    onThreadClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val viewModel: NotificationsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(NotificationsContract.Intent.LoadNotifications)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NotificationsContract.Effect.NavigateToThread -> onThreadClick(effect.threadId)
                is NotificationsContract.Effect.NavigateToUserProfile -> onUserClick(effect.userId)
                is NotificationsContract.Effect.ShowError -> { /* TODO: show snackbar */ }
            }
        }
    }

    NotificationsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNotificationClick = { notification ->
            when (notification.type) {
                NotificationType.NEW_FOLLOWER -> {
                    onUserClick(notification.actorId)
                }
                NotificationType.LIKE,
                NotificationType.REPLY,
                NotificationType.MENTION -> {
                    notification.threadId?.let { onThreadClick(it) }
                }
                NotificationType.WELLNESS_REMINDER,
                NotificationType.OTHER -> {
                    // No specific navigation for these types
                }
            }
        },
        onNavigateBack = onNavigateBack,
    )
}
