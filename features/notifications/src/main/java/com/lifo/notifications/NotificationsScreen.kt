package com.lifo.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lifo.util.repository.NotificationRepository
import com.lifo.util.repository.NotificationRepository.NotificationType
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    state: NotificationsContract.State,
    onIntent: (NotificationsContract.Intent) -> Unit,
    onNotificationClick: (NotificationRepository.Notification) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        IconButton(
                            onClick = { onIntent(NotificationsContract.Intent.MarkAllRead) },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = "Mark all as read",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        when {
            // Loading state
            state.isLoading && state.notifications.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error state with no data
            state.error != null && state.notifications.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Empty state
            !state.isLoading && state.notifications.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No notifications yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You'll see activity from your community here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // Notifications list
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                ) {
                    items(
                        items = state.notifications,
                        key = { it.id },
                    ) { notification ->
                        val onClickStable = remember(notification.id, notification.isRead) {
                            {
                                if (!notification.isRead) {
                                    onIntent(
                                        NotificationsContract.Intent.MarkAsRead(notification.id)
                                    )
                                }
                                onNotificationClick(notification)
                            }
                        }
                        NotificationItem(
                            notification = notification,
                            onClick = onClickStable,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: NotificationRepository.Notification,
    onClick: () -> Unit,
) {
    val unreadBackground = if (!notification.isRead) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(unreadBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackgroundColor(notification.type)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForType(notification.type),
                contentDescription = notification.type.name,
                modifier = Modifier.size(20.dp),
                tint = iconTintColor(notification.type),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content column
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // Actor name + message
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!notification.actorAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = notification.actorAvatarUrl,
                        contentDescription = "${notification.actorName}'s avatar",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val actorName = notification.actorName
                if (!actorName.isNullOrBlank()) {
                    Text(
                        text = actorName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (!notification.actorName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatRelativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Unread indicator
        if (!notification.isRead) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun iconForType(type: NotificationType): ImageVector = when (type) {
    NotificationType.NEW_FOLLOWER -> Icons.Outlined.PersonAdd
    NotificationType.LIKE -> Icons.Outlined.Favorite
    NotificationType.REPLY -> Icons.Outlined.Reply
    NotificationType.MENTION -> Icons.Outlined.AlternateEmail
    NotificationType.WELLNESS_REMINDER -> Icons.Outlined.SelfImprovement
    NotificationType.OTHER -> Icons.Outlined.Notifications
}

@Composable
private fun iconBackgroundColor(type: NotificationType) = when (type) {
    NotificationType.NEW_FOLLOWER -> MaterialTheme.colorScheme.primaryContainer
    NotificationType.LIKE -> MaterialTheme.colorScheme.errorContainer
    NotificationType.REPLY -> MaterialTheme.colorScheme.secondaryContainer
    NotificationType.MENTION -> MaterialTheme.colorScheme.tertiaryContainer
    NotificationType.WELLNESS_REMINDER -> MaterialTheme.colorScheme.secondaryContainer
    NotificationType.OTHER -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun iconTintColor(type: NotificationType) = when (type) {
    NotificationType.NEW_FOLLOWER -> MaterialTheme.colorScheme.onPrimaryContainer
    NotificationType.LIKE -> MaterialTheme.colorScheme.onErrorContainer
    NotificationType.REPLY -> MaterialTheme.colorScheme.onSecondaryContainer
    NotificationType.MENTION -> MaterialTheme.colorScheme.onTertiaryContainer
    NotificationType.WELLNESS_REMINDER -> MaterialTheme.colorScheme.onSecondaryContainer
    NotificationType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Formats a Unix timestamp (milliseconds) into a human-readable relative time string.
 * Examples: "Just now", "5m ago", "2h ago", "Yesterday", "Mar 1"
 */
private fun formatRelativeTime(timestampMillis: Long): String {
    if (timestampMillis == 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - timestampMillis

    if (diff < 0) return "Just now"

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = timestampMillis
            }
            val month = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())
                .format(calendar.time)
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            "$month $day"
        }
    }
}
