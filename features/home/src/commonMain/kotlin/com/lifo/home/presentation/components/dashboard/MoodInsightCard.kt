package com.lifo.home.presentation.components.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import com.lifo.home.domain.model.DominantMood
import com.lifo.home.domain.model.MoodDistribution
import com.lifo.home.domain.model.TimeRange

import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.ui.theme.Sage
import com.lifo.ui.theme.SageSoft
import com.lifo.ui.theme.SageMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoodInsightCard(
    distribution: MoodDistribution,
    dominantMood: DominantMood,
    timeRange: TimeRange,
    onTimeRangeChange: (TimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val totalEntries = distribution.totalEntries
    val dominantPct = (dominantMood.percentage * 100).toInt()

    val goalEntries = when (timeRange) {
        TimeRange.WEEK -> 7
        TimeRange.MONTH -> 30
        TimeRange.QUARTER -> 90
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with time range chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Il tuo mood",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                        )
                    }
                }
            }

            // Main content: Donut + Side cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: Large donut chart
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    FitnessStyleDonut(
                        positive = distribution.positive,
                        neutral = distribution.neutral,
                        negative = distribution.negative,
                        dominantPercentage = dominantPct,
                        dominantLabel = dominantMood.displayLabel,
                        totalEntries = totalEntries,
                        goalEntries = goalEntries,
                        size = 160.dp,
                        strokeWidth = 22.dp
                    )
                }

                // RIGHT: 3 stacked metric cards
                Column(
                    modifier = Modifier.width(144.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoodMetricChip(
                        label = "Positivo",
                        percentage = distribution.positive,
                        accentColor = Sage,
                        icon = Icons.Rounded.SentimentVerySatisfied
                    )
                    MoodMetricChip(
                        label = "Neutro",
                        percentage = distribution.neutral,
                        accentColor = SageSoft,
                        icon = Icons.Rounded.SentimentNeutral
                    )
                    MoodMetricChip(
                        label = "Negativo",
                        percentage = distribution.negative,
                        accentColor = SageMedium,
                        icon = Icons.Rounded.SentimentVeryDissatisfied
                    )
                }
            }

            // Bottom: dominant mood indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = CircleShape,
                    color = colorScheme.primary
                ) {}
                Text(
                    text = "${dominantMood.displayLabel} — dominante",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ==================== FITNESS-STYLE DONUT ====================

@Composable
private fun FitnessStyleDonut(
    positive: Float,
    neutral: Float,
    negative: Float,
    dominantPercentage: Int,
    dominantLabel: String,
    totalEntries: Int,
    goalEntries: Int,
    size: Dp,
    strokeWidth: Dp
) {
    val colorScheme = MaterialTheme.colorScheme

    val positiveColor = Sage
    val neutralColor = SageSoft
    val negativeColor = SageMedium
    val trackColor = colorScheme.surfaceContainerHighest

    // Animate segments
    val animPositive by animateFloatAsState(
        targetValue = positive,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "positive"
    )
    val animNeutral by animateFloatAsState(
        targetValue = neutral,
        animationSpec = tween(800, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "neutral"
    )
    val animNegative by animateFloatAsState(
        targetValue = negative,
        animationSpec = tween(800, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "negative"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // Track background (full circle)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // Gap between segments
            val gapDegrees = 6f
            val segments = listOf(
                animPositive to positiveColor,
                animNeutral to neutralColor,
                animNegative to negativeColor
            ).filter { it.first > 0f }

            val availableDegrees = 360f - (gapDegrees * segments.size)

            var startAngle = -90f // Start from top
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
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
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
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = (-1).sp
                ),
                color = colorScheme.onSurface
            )
            Text(
                text = dominantLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$totalEntries di $goalEntries",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        // Badge "+N" at top-right
        if (totalEntries > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .background(
                        color = colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "+$totalEntries",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    color = colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// ==================== SIDE METRIC CHIP ====================

@Composable
private fun MoodMetricChip(
    label: String,
    percentage: Float,
    accentColor: Color,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalmifyRadius.lg),
        color = accentColor.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(CalmifySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.18f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = "${(percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = accentColor
                )
            }
        }
    }
}
