package com.lifo.home.presentation.components.hero

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.TrendDirection
import com.lifo.home.util.EmotionAwareColors
import com.lifo.util.model.SentimentLabel

/**
 * Today Pulse Indicator - Circular gauge showing today's emotional state
 * Material3 Expressive animated visualization
 */
@Composable
fun TodayPulseIndicator(
    score: Float,  // 0-10
    dominantEmotion: SentimentLabel,
    trend: TrendDirection,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val sentimentColor = EmotionAwareColors.getSentimentColor(dominantEmotion)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    // Animated progress
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(score) {
        animate(
            initialValue = 0f,
            targetValue = score / 10f,
            animationSpec = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            )
        ) { value, _ ->
            animatedProgress = value
        }
    }

    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Circular progress
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background arc
            drawArc(
                color = backgroundColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Glow effect
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        sentimentColor.copy(alpha = glowAlpha),
                        sentimentColor.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                color = sentimentColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Sentiment icon
            Icon(
                imageVector = dominantEmotion.icon(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = sentimentColor
            )

            // Score
            Text(
                text = String.format("%.1f", score),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = sentimentColor
            )

            // Trend arrow
            TrendArrow(
                direction = trend,
                color = sentimentColor
            )
        }
    }
}

@Composable
private fun TrendArrow(
    direction: TrendDirection,
    color: Color
) {
    val rotation = when (direction) {
        TrendDirection.UP -> -45f
        TrendDirection.DOWN -> 45f
        TrendDirection.STABLE -> 0f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "arrow")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (direction != TrendDirection.STABLE) 4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )

    Text(
        text = when (direction) {
            TrendDirection.UP -> "↑"
            TrendDirection.DOWN -> "↓"
            TrendDirection.STABLE -> "→"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.graphicsLayer {
            translationY = if (direction == TrendDirection.UP) -bounce else bounce
            rotationZ = rotation
        }
    )
}

/**
 * Mini pulse indicator for compact spaces
 */
@Composable
fun MiniPulseIndicator(
    score: Float,
    dominantEmotion: SentimentLabel,
    modifier: Modifier = Modifier
) {
    val sentimentColor = EmotionAwareColors.getSentimentColor(dominantEmotion)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(score) {
        animate(
            initialValue = 0f,
            targetValue = score / 10f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedProgress = value
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mini circular progress
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background
                drawArc(
                    color = backgroundColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress
                drawArc(
                    color = sentimentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Icon(
                imageVector = dominantEmotion.icon(),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = sentimentColor
            )
        }

        // Score
        Text(
            text = String.format("%.1f", score),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = sentimentColor
        )
    }
}

/**
 * Horizontal bar pulse indicator
 */
@Composable
fun HorizontalPulseBar(
    score: Float,
    dominantEmotion: SentimentLabel,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val sentimentColor = EmotionAwareColors.getSentimentColor(dominantEmotion)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(score) {
        animate(
            initialValue = 0f,
            targetValue = score / 10f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedProgress = value
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val cornerRadius = height.toPx() / 2

        // Background
        drawRoundRect(
            color = backgroundColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        // Progress
        drawRoundRect(
            color = sentimentColor,
            size = Size(this.size.width * animatedProgress, this.size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )
    }
}

private fun SentimentLabel.icon(): ImageVector = when (this) {
    SentimentLabel.VERY_NEGATIVE -> Icons.Outlined.SentimentVeryDissatisfied
    SentimentLabel.NEGATIVE -> Icons.Outlined.SentimentDissatisfied
    SentimentLabel.NEUTRAL -> Icons.Outlined.SentimentNeutral
    SentimentLabel.POSITIVE -> Icons.Outlined.SentimentSatisfied
    SentimentLabel.VERY_POSITIVE -> Icons.Outlined.SentimentVerySatisfied
}
