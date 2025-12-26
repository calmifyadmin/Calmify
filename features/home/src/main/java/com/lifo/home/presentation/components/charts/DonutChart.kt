package com.lifo.home.presentation.components.charts

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.MoodDistribution
import com.lifo.home.util.EmotionAwareColors

/**
 * Donut Chart - Material3 Expressive animated donut chart
 * For mood distribution visualization
 */
@Composable
fun DonutChart(
    distribution: MoodDistribution,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 16.dp,
    animationDurationMs: Int = 1000,
    gapDegrees: Float =32f, // Spazio tra i segmenti (grande per le estremità arrotondate)
    centerContent: @Composable () -> Unit = {}
) {
    val segments = listOf(
        DonutSegment(distribution.positive, EmotionAwareColors.ChartColors.positiveSegment),
        DonutSegment(distribution.neutral, EmotionAwareColors.ChartColors.neutralSegment),
        DonutSegment(distribution.negative, EmotionAwareColors.ChartColors.negativeSegment)
    ).filter { it.value > 0f }

    // Calcola il gap totale da sottrarre
    val totalGapDegrees = gapDegrees * segments.size
    val availableDegrees = 360f - totalGapDegrees

    // Staggered segment animations
    val animatedSegments = segments.mapIndexed { index, segment ->
        var animatedValue by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(segment.value) {
            kotlinx.coroutines.delay((index * 100).toLong())
            animate(
                initialValue = 0f,
                targetValue = segment.value,
                animationSpec = tween(
                    durationMillis = animationDurationMs,
                    easing = FastOutSlowInEasing
                )
            ) { value, _ ->
                animatedValue = value
            }
        }

        segment.copy(value = animatedValue)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)

            // Draw segments con gap
            var startAngle = -90f // Start from top
            animatedSegments.forEach { segment ->
                // Sweep angle proporzionale allo spazio disponibile (senza i gap)
                val sweepAngle = segment.value * availableDegrees

                if (sweepAngle > 0f) {
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Avanza includendo il gap
                    startAngle += sweepAngle + gapDegrees
                }
            }
        }

        // Center content
        centerContent()
    }
}

private data class DonutSegment(
    val value: Float,
    val color: Color
)

/**
 * Donut Chart with percentage label in center
 */
@Composable
fun DonutChartWithLabel(
    distribution: MoodDistribution,
    dominantPercentage: Float,
    dominantLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    gapDegrees: Float = 8f
) {
    DonutChart(
        distribution = distribution,
        modifier = modifier,
        size = size,
        gapDegrees = gapDegrees
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${(dominantPercentage * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dominantLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Mini donut for compact displays
 */
@Composable
fun MiniDonutChart(
    positive: Float,
    neutral: Float,
    negative: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val distribution = MoodDistribution(
        positive = positive,
        neutral = neutral,
        negative = negative,
        detailedBreakdown = emptyMap(),
        totalEntries = 0,
        timeRange = com.lifo.home.domain.model.TimeRange.WEEK
    )

    DonutChart(
        distribution = distribution,
        modifier = modifier,
        size = size,
        strokeWidth = 6.dp,
        animationDurationMs = 500,
        gapDegrees = 10f
    )
}

/**
 * Horizontal bar chart alternative for mood distribution
 */
@Composable
fun MoodDistributionBar(
    distribution: MoodDistribution,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    gapWidth: Dp = 4.dp
) {
    // Animation
    var animatedPositive by remember { mutableFloatStateOf(0f) }
    var animatedNeutral by remember { mutableFloatStateOf(0f) }
    var animatedNegative by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(distribution) {
        animate(
            initialValue = 0f,
            targetValue = distribution.positive,
            animationSpec = tween(600)
        ) { value: Float, _: Float -> animatedPositive = value }
    }
    LaunchedEffect(distribution) {
        kotlinx.coroutines.delay(100)
        animate(
            initialValue = 0f,
            targetValue = distribution.neutral,
            animationSpec = tween(600)
        ) { value: Float, _: Float -> animatedNeutral = value }
    }
    LaunchedEffect(distribution) {
        kotlinx.coroutines.delay(200)
        animate(
            initialValue = 0f,
            targetValue = distribution.negative,
            animationSpec = tween(600)
        ) { value: Float, _: Float -> animatedNegative = value }
    }

    val activeSegments = listOf(animatedPositive, animatedNeutral, animatedNegative).count { it > 0f }
    val totalGaps = (activeSegments - 1).coerceAtLeast(0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val gap = gapWidth.toPx()
        val totalGapWidth = gap * totalGaps
        val availableWidth = this.size.width - totalGapWidth
        val barHeight = this.size.height
        val cornerRadius = barHeight / 2

        // Background
        drawRoundRect(
            color = EmotionAwareColors.ChartColors.emptySegment,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        var xOffset = 0f

        // Positive segment
        val positiveWidth = availableWidth * animatedPositive
        if (positiveWidth > 0f) {
            drawRoundRect(
                color = EmotionAwareColors.ChartColors.positiveSegment,
                topLeft = Offset(xOffset, 0f),
                size = Size(positiveWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
            xOffset += positiveWidth + gap
        }

        // Neutral segment
        val neutralWidth = availableWidth * animatedNeutral
        if (neutralWidth > 0f) {
            drawRoundRect(
                color = EmotionAwareColors.ChartColors.neutralSegment,
                topLeft = Offset(xOffset, 0f),
                size = Size(neutralWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
            xOffset += neutralWidth + gap
        }

        // Negative segment
        val negativeWidth = availableWidth * animatedNegative
        if (negativeWidth > 0f) {
            drawRoundRect(
                color = EmotionAwareColors.ChartColors.negativeSegment,
                topLeft = Offset(xOffset, 0f),
                size = Size(negativeWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
            )
        }
    }
}

/**
 * Legend for donut chart
 */
@Composable
fun DonutChartLegend(
    distribution: MoodDistribution,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LegendItem(
            color = EmotionAwareColors.ChartColors.positiveSegment,
            label = "Positivo",
            percentage = distribution.positive
        )
        LegendItem(
            color = EmotionAwareColors.ChartColors.neutralSegment,
            label = "Neutro",
            percentage = distribution.neutral
        )
        LegendItem(
            color = EmotionAwareColors.ChartColors.negativeSegment,
            label = "Negativo",
            percentage = distribution.negative
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    percentage: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color)
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${(percentage * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}