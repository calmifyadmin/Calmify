package com.lifo.notifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.socialui.avatar.UserAvatar
import com.lifo.util.repository.NotificationRepository
import com.lifo.util.repository.NotificationRepository.NotificationType
import com.lifo.util.currentTimeMillis

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
                        text = "Notifiche",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter tabs row
            NotificationFilterTabs(
                selectedFilter = state.selectedFilter,
                onFilterSelected = { onIntent(NotificationsContract.Intent.SelectFilter(it)) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            when {
                // Loading state
                state.isLoading && state.notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Error state with no data
                state.error != null && state.notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                !state.isLoading && state.filteredNotifications.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (state.selectedFilter == NotificationsContract.NotificationFilter.ALL) {
                                    "No notifications yet"
                                } else {
                                    "No ${state.selectedFilter.name.lowercase()} notifications"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You'll see activity from your community here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                // Notifications list (grouped)
                else -> {
                    val grouped = state.groupedNotifications
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = grouped,
                            key = { it.primary.id },
                        ) { group ->
                            val onClickStable = remember(group.primary.id, group.primary.isRead) {
                                {
                                    // Mark all grouped notifications as read
                                    group.allIds.forEach { id ->
                                        onIntent(NotificationsContract.Intent.MarkAsRead(id))
                                    }
                                    onNotificationClick(group.primary)
                                }
                            }
                            NotificationItem(
                                notification = group.primary,
                                displayMessage = group.displayMessage,
                                extraActorCount = group.otherActors.size,
                                onClick = onClickStable,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationFilterTabs(
    selectedFilter: NotificationsContract.NotificationFilter,
    onFilterSelected: (NotificationsContract.NotificationFilter) -> Unit,
) {
    val chipShape = RoundedCornerShape(20.dp)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedFilter == NotificationsContract.NotificationFilter.ALL,
                onClick = { onFilterSelected(NotificationsContract.NotificationFilter.ALL) },
                label = { Text("All") },
                shape = chipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == NotificationsContract.NotificationFilter.FOLLOWS,
                onClick = { onFilterSelected(NotificationsContract.NotificationFilter.FOLLOWS) },
                label = { Text("Follows") },
                shape = chipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == NotificationsContract.NotificationFilter.REPLIES,
                onClick = { onFilterSelected(NotificationsContract.NotificationFilter.REPLIES) },
                label = { Text("Replies") },
                shape = chipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == NotificationsContract.NotificationFilter.MENTIONS,
                onClick = { onFilterSelected(NotificationsContract.NotificationFilter.MENTIONS) },
                label = { Text("Mentions") },
                shape = chipShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun NotificationItem(
    notification: NotificationRepository.Notification,
    displayMessage: String = notification.message,
    extraActorCount: Int = 0,
    onClick: () -> Unit,
) {
    // Animated background for mark-as-read transition
    val targetBackground = if (!notification.isRead) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val animatedBackground by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 400),
        label = "unread_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(animatedBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Avatar with type badge overlay
        Box {
            UserAvatar(
                avatarUrl = notification.actorAvatarUrl,
                displayName = notification.actorName,
                size = 48.dp,
                showBorder = false,
            )

            // Small type icon badge at bottom-right of avatar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor(notification.type)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForType(notification.type),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = iconTintColor(notification.type),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content column
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // Actor name
            val actorName = notification.actorName
            if (!actorName.isNullOrBlank()) {
                Text(
                    text = actorName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Notification message (may be grouped)
            Text(
                text = displayMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (notification.isRead) 0.7f else 1f
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp
            Text(
                text = formatRelativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Unread indicator dot (right side)
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
    NotificationType.REPLY -> Icons.AutoMirrored.Filled.Reply
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

    val now = currentTimeMillis()
    val diff = now - timestampMillis

    if (diff < 0L) return "Just now"

    val seconds = diff / 1_000L
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L

    return when {
        seconds < 60L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7L -> "${days}d ago"
        else -> "${days}d ago"
    }
}
