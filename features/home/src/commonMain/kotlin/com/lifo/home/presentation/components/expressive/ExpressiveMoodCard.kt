package com.lifo.home.presentation.components.expressive

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.domain.model.DominantMood
import com.lifo.home.domain.model.MoodDistribution
import com.lifo.home.domain.model.TimeRange
import com.lifo.ui.theme.Sage
import com.lifo.ui.theme.SageMedium
import com.lifo.ui.theme.SageSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressiveMoodCard(
    distribution: MoodDistribution,
    dominantMood: DominantMood,
    timeRange: TimeRange,
    onTimeRangeChange: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val dominantPct = (dominantMood.percentage * 100).toInt()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Il tuo mood",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimeRange.entries.forEach { range ->
                        FilterChip(
                            selected = range == timeRange,
                            onClick = { onTimeRangeChange(range) },
                            label = {
                                Text(
                                    text = range.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Centered donut — larger, simpler
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveDonut(
                    positive = distribution.positive,
                    neutral = distribution.neutral,
                    negative = distribution.negative,
                    dominantPercentage = dominantPct,
                    dominantLabel = dominantMood.displayLabel,
                    totalEntries = distribution.totalEntries,
                    size = 180.dp
                )
            }

            // 3 metric pills in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MoodPill(
                    label = "Positivo",
                    percentage = distribution.positive,
                    color = Sage,
                    icon = Icons.Rounded.SentimentVerySatisfied,
                    modifier = Modifier.weight(1f)
                )
                MoodPill(
                    label = "Neutro",
                    percentage = distribution.neutral,
                    color = SageSoft,
                    icon = Icons.Rounded.SentimentNeutral,
                    modifier = Modifier.weight(1f)
                )
                MoodPill(
                    label = "Negativo",
                    percentage = distribution.negative,
                    color = SageMedium,
                    icon = Icons.Rounded.SentimentVeryDissatisfied,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==================== EXPRESSIVE DONUT ====================

@Composable
private fun ExpressiveDonut(
    positive: Float,
    neutral: Float,
    negative: Float,
    dominantPercentage: Int,
    dominantLabel: String,
    totalEntries: Int,
    size: androidx.compose.ui.unit.Dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val trackColor = colorScheme.surfaceContainerHighest

    val animPositive by animateFloatAsState(
        targetValue = positive,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "positive"
    )
    val animNeutral by animateFloatAsState(
        targetValue = neutral,
        animationSpec = tween(900, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "neutral"
    )
    val animNegative by animateFloatAsState(
        targetValue = negative,
        animationSpec = tween(900, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "negative"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 24f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val gapDegrees = 8f
            val segments = listOf(
                animPositive to Sage,
                animNeutral to SageSoft,
                animNegative to SageMedium
            ).filter { it.first > 0f }

            val availableDegrees = 360f - (gapDegrees * segments.size)
            var startAngle = -90f

            segments.forEach { (value, color) ->
                val sweep = value * availableDegrees
                if (sweep > 0f) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep + gapDegrees
                }
            }
        }

        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${dominantPercentage}%",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp
                ),
                color = colorScheme.onSurface
            )
            Text(
                text = dominantLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$totalEntries entries",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ==================== MOOD PILL ====================

@Composable
private fun MoodPill(
    label: String,
    percentage: Float,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Text(
                text = "${(percentage * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
