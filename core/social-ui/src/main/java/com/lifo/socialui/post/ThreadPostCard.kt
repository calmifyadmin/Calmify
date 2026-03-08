package com.lifo.socialui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.socialui.animation.pressScale
import com.lifo.socialui.animation.staggeredEntrance
import com.lifo.socialui.avatar.ClusteredAvatars
import com.lifo.socialui.avatar.UserAvatar
import com.lifo.socialui.media.MediaGrid
import com.lifo.socialui.thread.ThreadLine
import com.lifo.util.repository.ThreadRepository
import java.util.concurrent.TimeUnit

/**
 * Threads-style post card with 2-column grid layout.
 *
 * Layout (matches Meta's Threads):
 * ┌────────────────────────────────────────────┐
 * │  [Avatar]  username ✓ · 2h         [...]   │
 * │  [  |  ]  Post text content here...        │
 * │  [  |  ]  [2x2 media grid if present]      │
 * │  [  |  ]  ♡ 42  💬 12  🔁 3  ➤            │
 * │  [△△△ ]  47 replies                        │
 * └────────────────────────────────────────────┘
 *
 * Features:
 * - 40dp avatar (Threads standard)
 * - Username · dot · relative timestamp in header
 * - Thread line from avatar bottom when hasReplies
 * - ClusteredAvatars (triangular) below thread line
 * - Reply count text below avatars
 * - Press-scale + staggered entrance + double-tap-to-like
 */
@Composable
fun ThreadPostCard(
    thread: ThreadRepository.Thread,
    showThreadLine: Boolean = false,
    index: Int = 0,
    onThreadClick: () -> Unit = {},
    onUserClick: () -> Unit = {},
    onLike: () -> Unit = {},
    onReply: () -> Unit = {},
    onRepost: () -> Unit = {},
    onShare: () -> Unit = {},
    onOptions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Show thread line only when explicitly requested (e.g. thread detail), not auto-detected
    val hasReplies = showThreadLine

    Row(
        modifier = modifier
            .fillMaxWidth()
            .staggeredEntrance(index)
            .pressScale(onClick = onThreadClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min)
    ) {
        // LEFT COLUMN: Avatar + Thread Line + Clustered Reply Avatars
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(52.dp)
        ) {
            // 40dp avatar (Threads standard)
            UserAvatar(
                avatarUrl = thread.authorAvatarUrl,
                displayName = thread.authorDisplayName ?: thread.authorId,
                size = 40.dp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onUserClick
                )
            )

            // Thread line (fills available height between avatar and clustered avatars)
            if (hasReplies) {
                Spacer(Modifier.height(4.dp))
                ThreadLine(
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(4.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Clustered reply preview avatars (triangular layout)
            if (thread.replyPreviewAvatars.isNotEmpty()) {
                ClusteredAvatars(
                    avatarUrls = thread.replyPreviewAvatars,
                    avatarSize = 20.dp,
                    borderWidth = 2.dp,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // RIGHT COLUMN: Content (double-tap-to-like area)
        Column(
            modifier = Modifier
                .weight(1f)
                .pointerInput(thread.threadId) {
                    detectTapGestures(
                        onTap = { onThreadClick() },
                        onDoubleTap = { onLike() }
                    )
                }
        ) {
            // Header: username + verified + dot + timestamp + options
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Username
                Text(
                    text = thread.authorDisplayName ?: thread.authorId.take(12),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onUserClick
                    )
                )

                // Verified badge
                if (thread.authorIsVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Dot separator + timestamp (Threads style: "username · 2h")
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatRelativeTime(thread.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.weight(1f))

                // Options menu
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOptions
                        ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            // Post text (rich rendering: hashtags, mentions, links, markdown)
            if (thread.text.isNotBlank()) {
                RichPostText(
                    text = thread.text,
                    maxLines = 6,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Mood tag + journal badge
            if (thread.moodTag != null || thread.isFromJournal) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    thread.moodTag?.let { mood ->
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = mood,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(24.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                    if (thread.isFromJournal) {
                        Text(
                            text = "from journal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Media grid (2x2 Threads-style)
            if (thread.mediaUrls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MediaGrid(
                    mediaUrls = thread.mediaUrls,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(6.dp))

            // Engagement bar
            EngagementBar(
                isLiked = thread.isLikedByCurrentUser,
                likeCount = thread.likeCount,
                replyCount = thread.replyCount,
                repostCount = thread.repostCount,
                isReposted = thread.isRepostedByCurrentUser,
                onLike = onLike,
                onReply = onReply,
                onRepost = onRepost,
                onShare = onShare
            )

        }
    }
}

internal fun formatRelativeTime(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis
    if (diff < 0) return "now"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        days < 30 -> "${days / 7}w"
        else -> "${days / 30}mo"
    }
}
