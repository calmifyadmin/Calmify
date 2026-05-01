package com.lifo.home.presentation.components.feed

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.ActivityType
import com.lifo.home.domain.model.EnhancedActivityItem
import com.lifo.home.domain.model.labelRes
import com.lifo.home.util.EmotionAwareColors
import org.jetbrains.compose.resources.stringResource

/**
 * Enhanced Activity Card - Unified card for diary and chat entries
 * Material3 Expressive design with sentiment indicator
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────────┐
 * │ ● 14:30  Riflessione sul lavoro                        │
 * │          "Oggi ho fatto progressi sul progetto..."      │
 * │          😊 Positivo · lavoro, crescita                 │
 * └─────────────────────────────────────────────────────────┘
 */
@Composable
fun EnhancedActivityCard(
    item: EnhancedActivityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0
) {
    val haptic = LocalHapticFeedback.current

    // Staggered entrance animation
    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 50).toLong())
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    val sentimentColor = item.sentimentIndicator?.let {
        EmotionAwareColors.getSentimentColor(it.label)
    } ?: MaterialTheme.colorScheme.outline

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterTransition.value
                translationY = (1f - enterTransition.value) * 20f
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sentiment indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(sentimentColor)
            )

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header row: time, title, type icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type icon
                        Icon(
                            imageVector = when (item.type) {
                                ActivityType.DIARY -> Icons.Default.Edit
                                ActivityType.CHAT_SESSION -> Icons.Default.Chat
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Time
                        Text(
                            text = item.relativeTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Favorite indicator
                    if (item.isFavorite) {
                        Text(
                            text = "⭐",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Preview
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags row: sentiment + topics
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sentiment badge
                    item.sentimentIndicator?.let { sentiment ->
                        Text(
                            text = stringResource(sentiment.label.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = sentimentColor
                        )
                    }

                    // Topics
                    if (item.topics.isNotEmpty()) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.topics.take(2).joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Key insight (if available)
                item.keyInsight?.let { insight ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact activity card for smaller spaces
 */
@Composable
fun CompactActivityCard(
    item: EnhancedActivityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sentimentColor = item.sentimentIndicator?.let {
        EmotionAwareColors.getSentimentColor(it.label)
    } ?: MaterialTheme.colorScheme.outline

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sentiment dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(sentimentColor)
            )

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Type icon
            Icon(
                imageVector = when (item.type) {
                    ActivityType.DIARY -> Icons.Default.Edit
                    ActivityType.CHAT_SESSION -> Icons.Default.Chat
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
