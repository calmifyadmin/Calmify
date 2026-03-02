package com.lifo.home.presentation.components.insights

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.DominantMood
import com.lifo.home.domain.model.MoodDistribution
import com.lifo.home.domain.model.TimeRange
import com.lifo.home.presentation.components.charts.DonutChart
import com.lifo.home.presentation.components.charts.DonutChartLegend
import com.lifo.home.presentation.components.common.MiniEmotionIndicator
import com.lifo.home.util.EmotionAwareColors

/**
 * Mood Distribution Card - Donut chart with emotional breakdown
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  Distribuzione Emotiva          Ultimi 30 giorni│
 * │                                                 │
 * │       ┌─────────┐      Positivo    45% ████░   │
 * │      /   45%    \      Neutro      30% ███░░   │
 * │     │           │      Negativo    25% ██░░░   │
 * │      \   30%   /                               │
 * │       └───25%──┘      Emozione dominante:      │
 * │                       😊 Sereno                │
 * └─────────────────────────────────────────────────┘
 */
@Composable
fun MoodDistributionCard(
    distribution: MoodDistribution,
    dominantMood: DominantMood,
    timeRange: TimeRange,
    onTimeRangeChange: (TimeRange) -> Unit,
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
            // Header with time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Distribuzione Emotiva",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TimeRangeChip(
                    currentRange = timeRange,
                    onRangeChange = onTimeRangeChange
                )
            }

            // Chart and legend row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut chart
                DonutChart(
                    distribution = distribution,
                    size = 188.dp,
                    strokeWidth = 32.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${(dominantMood.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Legend
                DonutChartLegend(
                    distribution = distribution,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Dominant mood indicator
            DominantMoodIndicator(dominantMood = dominantMood)

            // Entries count
            Text(
                text = "Basato su ${distribution.totalEntries} ${if (distribution.totalEntries == 1) "voce" else "voci"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeChip(
    currentRange: TimeRange,
    onRangeChange: (TimeRange) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(
                    text = currentRange.label,
                    style = MaterialTheme.typography.labelMedium
                )
            },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TimeRange.entries.forEach { range ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = range.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onRangeChange(range)
                        expanded = false
                    },
                    leadingIcon = if (range == currentRange) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun DominantMoodIndicator(dominantMood: DominantMood) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = EmotionAwareColors.getSentimentSurfaceTint(dominantMood.sentiment)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Emozione dominante:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Shape-based indicator instead of emoji (M3 Expressive)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniEmotionIndicator(
                    sentiment = dominantMood.sentiment,
                    size = 20.dp
                )
                Text(
                    text = dominantMood.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = EmotionAwareColors.getSentimentColor(dominantMood.sentiment)
                )
            }
        }
    }
}

/**
 * Compact mood distribution for smaller spaces
 */
@Composable
fun CompactMoodDistributionCard(
    distribution: MoodDistribution,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mini donut
            DonutChart(
                distribution = distribution,
                size = 48.dp,
                strokeWidth = 6.dp,
                animationDurationMs = 500
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Mood",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = getMoodSummary(distribution),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun getMoodSummary(distribution: MoodDistribution): String {
    return when {
        distribution.positive > 0.5f -> "Prevalentemente positivo"
        distribution.negative > 0.4f -> "Qualche difficoltà"
        else -> "Equilibrato"
    }
}
