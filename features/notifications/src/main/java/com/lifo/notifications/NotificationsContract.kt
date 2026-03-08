package com.lifo.notifications

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.NotificationRepository

object NotificationsContract {

    /**
     * Filter categories for the notification list.
     */
    enum class NotificationFilter {
        ALL, FOLLOWS, REPLIES, MENTIONS
    }

    /**
     * Grouped notification: collapses multiple same-type notifications
     * about the same thread within 24h into one item.
     */
    data class GroupedNotification(
        val primary: NotificationRepository.Notification,
        val otherActors: List<String> = emptyList(),
        val totalCount: Int = 1,
        val allIds: List<String> = listOf(primary.id),
    ) {
        val displayMessage: String
            get() {
                if (otherActors.isEmpty()) return primary.message
                val othersCount = otherActors.size
                val actorName = primary.actorName ?: "Qualcuno"
                return when (primary.type) {
                    NotificationRepository.NotificationType.LIKE ->
                        "$actorName e altri $othersCount hanno apprezzato il tuo post"
                    NotificationRepository.NotificationType.NEW_FOLLOWER ->
                        "$actorName e altri $othersCount hanno iniziato a seguirti"
                    else -> primary.message
                }
            }
    }

    sealed interface Intent : MviContract.Intent {
        data object LoadNotifications : Intent
        data class MarkAsRead(val notificationId: String) : Intent
        data object MarkAllRead : Intent
        data class SelectFilter(val filter: NotificationFilter) : Intent
    }

    @Immutable
    data class State(
        val notifications: List<NotificationRepository.Notification> = emptyList(),
        val unreadCount: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null,
        val selectedFilter: NotificationFilter = NotificationFilter.ALL,
    ) : MviContract.State {

        val filteredNotifications: List<NotificationRepository.Notification>
            get() = when (selectedFilter) {
                NotificationFilter.ALL -> notifications
                NotificationFilter.FOLLOWS -> notifications.filter {
                    it.type == NotificationRepository.NotificationType.NEW_FOLLOWER
                }
                NotificationFilter.REPLIES -> notifications.filter {
                    it.type == NotificationRepository.NotificationType.REPLY
                }
                NotificationFilter.MENTIONS -> notifications.filter {
                    it.type == NotificationRepository.NotificationType.MENTION
                }
            }

        val groupedNotifications: List<GroupedNotification>
            get() {
                val grouped = mutableListOf<GroupedNotification>()
                val used = mutableSetOf<String>()
                val twentyFourHours = 24 * 60 * 60 * 1000L

                for (notif in filteredNotifications) {
                    if (notif.id in used) continue

                    // Only group LIKE and NEW_FOLLOWER types
                    if (notif.type == NotificationRepository.NotificationType.LIKE ||
                        notif.type == NotificationRepository.NotificationType.NEW_FOLLOWER
                    ) {
                        val similar = filteredNotifications.filter { other ->
                            other.id !in used &&
                                other.id != notif.id &&
                                other.type == notif.type &&
                                other.threadId == notif.threadId &&
                                kotlin.math.abs(other.createdAt - notif.createdAt) < twentyFourHours
                        }

                        if (similar.isNotEmpty()) {
                            val allIds = listOf(notif.id) + similar.map { it.id }
                            allIds.forEach { used.add(it) }
                            grouped.add(
                                GroupedNotification(
                                    primary = notif,
                                    otherActors = similar.mapNotNull { it.actorName },
                                    totalCount = allIds.size,
                                    allIds = allIds,
                                )
                            )
                            continue
                        }
                    }

                    used.add(notif.id)
                    grouped.add(GroupedNotification(primary = notif))
                }
                return grouped
            }
    }

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
        data class NavigateToUserProfile(val userId: String) : Effect
    }
}
