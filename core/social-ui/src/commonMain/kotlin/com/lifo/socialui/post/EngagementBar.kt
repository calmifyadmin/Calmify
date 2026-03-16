package com.lifo.socialui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LikeRed = Color(0xFFFF3040)
private val RepostGreen = Color(0xFF00BA7C)

/**
 * Threads-style engagement bar: Like, Reply, Repost, Share.
 * Color-only like toggle (no animations).
 */
@Composable
fun EngagementBar(
    isLiked: Boolean,
    likeCount: Long,
    replyCount: Long,
    repostCount: Long,
    isReposted: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onRepost: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like button — color-only toggle
        LikeButton(
            isLiked = isLiked,
            count = likeCount,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLike()
            }
        )

        Spacer(Modifier.width(12.dp))

        // Reply
        EngagementAction(
            icon = Icons.Outlined.ChatBubbleOutline,
            count = replyCount,
            contentDescription = "Reply",
            onClick = onReply
        )

        Spacer(Modifier.width(12.dp))

        // Repost
        EngagementAction(
            icon = Icons.Outlined.Repeat,
            count = repostCount,
            contentDescription = "Repost",
            tint = if (isReposted) RepostGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRepost()
            }
        )

        Spacer(Modifier.width(12.dp))

        // Share
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onShare
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Share",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LikeButton(
    isLiked: Boolean,
    count: Long,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            if (count > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLiked) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EngagementAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Long,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = tint
            )

            if (count > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun formatCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> {
        val k = count / 100
        "${k / 10}.${k % 10}K"
    }
    else -> {
        val m = count / 100_000
        "${m / 10}.${m % 10}M"
    }
}
