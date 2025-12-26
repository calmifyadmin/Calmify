package com.lifo.home.presentation.components.insights

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.TopicFrequency
import com.lifo.home.domain.model.TopicTrend
import com.lifo.home.util.EmotionAwareColors
import kotlinx.coroutines.delay

/**
 * Topics Cloud Card - Visual representation of recurring themes
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  I tuoi temi                                    │
 * │                                                 │
 * │     lavoro        famiglia                      │
 * │          salute                                 │
 * │    relazioni    creatività    sport            │
 * │         sonno       amici                       │
 * │                                                 │
 * │  Tema emergente: 📈 creatività (+40%)          │
 * └─────────────────────────────────────────────────┘
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopicsCloudCard(
    topics: List<TopicFrequency>,
    emergingTopic: TopicTrend?,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Entrance animation
    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterTransition.value
                translationY = (1f - enterTransition.value) * 30f
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "I tuoi temi",
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Topics flow layout (word cloud)
            if (topics.isEmpty()) {
                EmptyTopicsState()
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    topics.forEachIndexed { index, topic ->
                        TopicChip(
                            topic = topic,
                            index = index,
                            onClick = { onTopicClick(topic.topic) }
                        )
                    }
                }
            }

            // Emerging topic highlight
            emergingTopic?.let { trend ->
                EmergingTopicBanner(trend = trend)
            }
        }
    }
}

@Composable
private fun TopicChip(
    topic: TopicFrequency,
    index: Int,
    onClick: () -> Unit
) {
    // Staggered entrance animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 50).toLong())
        isVisible = true
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "chipAlpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chipScale"
    )

    // Size based on frequency
    val chipSize = getChipSize(topic.frequency)
    val chipColor = EmotionAwareColors.getTopicChipBackground(topic.sentimentAverage)
    val textColor = EmotionAwareColors.getColorForScore(topic.sentimentAverage)

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = animatedAlpha
            scaleX = animatedScale
            scaleY = animatedScale
        }
    ) {
        SuggestionChip(
            onClick = onClick,
            label = {
                Text(
                    text = topic.topic,
                    style = when (chipSize) {
                        ChipSize.LARGE -> MaterialTheme.typography.bodyLarge
                        ChipSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                        ChipSize.SMALL -> MaterialTheme.typography.bodySmall
                    },
                    fontWeight = if (topic.isEmerging) FontWeight.Bold else FontWeight.Normal,
                    color = textColor
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = chipColor
            ),
            border = if (topic.isEmerging) {
                SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = textColor.copy(alpha = 0.5f)
                )
            } else null,
            shape = RoundedCornerShape(chipSize.cornerRadius)
        )
    }
}

private enum class ChipSize(val cornerRadius: androidx.compose.ui.unit.Dp) {
    SMALL(12.dp),
    MEDIUM(14.dp),
    LARGE(16.dp)
}

private fun getChipSize(frequency: Int): ChipSize {
    return when {
        frequency >= 10 -> ChipSize.LARGE
        frequency >= 5 -> ChipSize.MEDIUM
        else -> ChipSize.SMALL
    }
}

@Composable
private fun EmergingTopicBanner(trend: TopicTrend) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tema emergente:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = trend.topic,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "(+${trend.changePercent.toInt()}%)",
                style = MaterialTheme.typography.labelMedium,
                color = EmotionAwareColors.positiveLight
            )
        }
    }
}

@Composable
private fun EmptyTopicsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudQueue,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(
            text = "Nessun tema rilevato",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "I tuoi temi appariranno qui dopo qualche diario",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact topics row for smaller spaces
 */
@Composable
fun CompactTopicsRow(
    topics: List<TopicFrequency>,
    maxTopics: Int = 5,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        topics.take(maxTopics).forEach { topic ->
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = topic.topic,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = EmotionAwareColors.getTopicChipBackground(topic.sentimentAverage)
                )
            )
        }

        if (topics.size > maxTopics) {
            Text(
                text = "+${topics.size - maxTopics}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}
