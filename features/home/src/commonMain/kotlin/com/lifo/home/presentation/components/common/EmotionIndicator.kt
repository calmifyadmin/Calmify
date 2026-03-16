package com.lifo.home.presentation.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.home.util.EmotionShapes
import com.lifo.home.util.EmotionAwareColors
import com.lifo.home.util.drawEmotionShape
import com.lifo.util.model.SentimentLabel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Emotion Indicator - Shape-based emotion visualization
 *
 * Replaces emoji-based indicators with Material 3 Expressive shapes
 * that communicate emotional states through geometry and color.
 */
@Composable
fun EmotionIndicator(
    sentiment: SentimentLabel,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = true,
    showGlow: Boolean = true,
    glowIntensity: Float = 0.5f
) {
    val emotionColor = EmotionAwareColors.getSentimentColor(sentiment)
    val glowColor = emotionColor.copy(alpha = glowIntensity * 0.6f)

    // Entrance animation - one-time scale up with bounce (no loop)
    val entranceScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // One-time entrance rotation for very positive (spin once on appear, no loop)
    val entranceRotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (animated && sentiment == SentimentLabel.VERY_POSITIVE) {
            entranceRotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow layer
        if (showGlow) {
            Canvas(
                modifier = Modifier
                    .size(size * 1.4f)
                    .blur(8.dp)
                    .graphicsLayer {
                        scaleX = entranceScale.value * 1.1f
                        scaleY = entranceScale.value * 1.1f
                    }
            ) {
                drawEmotionShape(
                    sentiment = sentiment,
                    color = glowColor,
                    center = Offset(this.size.width / 2, this.size.height / 2),
                    size = this.size.minDimension * 0.7f
                )
            }
        }

        // Main shape
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                    rotationZ = entranceRotation.value
                }
        ) {
            drawEmotionShape(
                sentiment = sentiment,
                color = emotionColor,
                center = Offset(this.size.width / 2, this.size.height / 2),
                size = this.size.minDimension * 0.85f
            )
        }
    }
}

/**
 * Mini Emotion Indicator - Compact version for lists and cards
 */
@Composable
fun MiniEmotionIndicator(
    sentiment: SentimentLabel,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    EmotionIndicator(
        sentiment = sentiment,
        modifier = modifier,
        size = size,
        animated = false,
        showGlow = false
    )
}

/**
 * Emotion Progress Indicator - Shape with progress ring
 */
@Composable
fun EmotionProgressIndicator(
    sentiment: SentimentLabel,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 4.dp
) {
    val emotionColor = EmotionAwareColors.getSentimentColor(sentiment)
    val trackColor = emotionColor.copy(alpha = 0.2f)

    // Animate progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Progress ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val radius = (this.size.minDimension - strokePx) / 2

            // Track
            drawCircle(
                color = trackColor,
                radius = radius,
                style = Stroke(width = strokePx)
            )

            // Progress arc
            drawArc(
                color = emotionColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokePx)
            )
        }

        // Inner shape
        EmotionIndicator(
            sentiment = sentiment,
            size = size * 0.6f,
            animated = false,
            showGlow = false
        )
    }
}

/**
 * Animated flame indicator for streak display
 * Uses Canvas drawing for a realistic flame shape with gradient
 */
@Composable
fun StreakFlameIndicator(
    streakDays: Int,
    size: Dp = 32.dp,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Flame colors based on streak level
    val flameColors = getFlameColors(streakDays, isActive)

    // Subtle animation for active flames
    val infiniteTransition = rememberInfiniteTransition(label = "flame")

    val flickerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flickerScale"
    )

    val flickerOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flickerOffset"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        val width = this.size.width
        val height = this.size.height

        if (isActive) {
            // Draw outer glow for high streaks
            if (streakDays >= 7) {
                drawFlameGlow(
                    width = width,
                    height = height,
                    color = flameColors.outer,
                    scale = flickerScale
                )
            }

            // Draw main flame
            drawFlame(
                width = width,
                height = height,
                colors = flameColors,
                scale = if (isActive) flickerScale else 1f,
                offsetX = if (isActive) flickerOffset else 0f
            )
        } else {
            // Inactive state - gray flame silhouette
            drawInactiveFlame(width, height)
        }
    }
}

private data class FlameColors(
    val outer: Color,
    val middle: Color,
    val inner: Color,
    val core: Color
)

private fun getFlameColors(streakDays: Int, isActive: Boolean): FlameColors {
    if (!isActive) {
        return FlameColors(
            outer = Color(0xFF9E9E9E),
            middle = Color(0xFFBDBDBD),
            inner = Color(0xFFE0E0E0),
            core = Color(0xFFF5F5F5)
        )
    }

    return when {
        // Legendary (30+ days) - Blue/Purple flame
        streakDays >= 30 -> FlameColors(
            outer = Color(0xFF7C4DFF),
            middle = Color(0xFF536DFE),
            inner = Color(0xFF448AFF),
            core = Color(0xFFFFFFFF)
        )
        // Epic (14+ days) - Red/Orange intense flame
        streakDays >= 14 -> FlameColors(
            outer = Color(0xFFD32F2F),
            middle = Color(0xFFFF5722),
            inner = Color(0xFFFF9800),
            core = Color(0xFFFFEB3B)
        )
        // Good (7+ days) - Orange/Yellow flame
        streakDays >= 7 -> FlameColors(
            outer = Color(0xFFE65100),
            middle = Color(0xFFFF9800),
            inner = Color(0xFFFFC107),
            core = Color(0xFFFFEB3B)
        )
        // Starting (1+ days) - Yellow/Orange flame
        else -> FlameColors(
            outer = Color(0xFFFF9800),
            middle = Color(0xFFFFA726),
            inner = Color(0xFFFFCA28),
            core = Color(0xFFFFEE58)
        )
    }
}

private fun DrawScope.drawFlameGlow(
    width: Float,
    height: Float,
    color: Color,
    scale: Float
) {
    val glowRadius = (width * 0.6f) * scale
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.4f),
                color.copy(alpha = 0.1f),
                Color.Transparent
            ),
            center = Offset(width / 2, height * 0.55f),
            radius = glowRadius
        ),
        center = Offset(width / 2, height * 0.55f),
        radius = glowRadius
    )
}

private fun DrawScope.drawFlame(
    width: Float,
    height: Float,
    colors: FlameColors,
    scale: Float,
    offsetX: Float
) {
    val centerX = width / 2 + offsetX * 0.5f
    val baseY = height * 0.92f

    // Outer flame layer
    val outerPath = createFlamePath(
        centerX = centerX,
        baseY = baseY,
        flameWidth = width * 0.75f * scale,
        flameHeight = height * 0.85f * scale,
        tipOffset = offsetX
    )

    drawPath(
        path = outerPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.outer,
                colors.middle,
                colors.inner
            ),
            startY = height * 0.1f,
            endY = baseY
        )
    )

    // Middle flame layer
    val middlePath = createFlamePath(
        centerX = centerX,
        baseY = baseY,
        flameWidth = width * 0.52f * scale,
        flameHeight = height * 0.65f * scale,
        tipOffset = offsetX * 0.7f
    )

    drawPath(
        path = middlePath,
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.middle,
                colors.inner,
                colors.core
            ),
            startY = height * 0.25f,
            endY = baseY
        )
    )

    // Inner core (hottest part)
    val innerPath = createFlamePath(
        centerX = centerX,
        baseY = baseY - height * 0.05f,
        flameWidth = width * 0.28f * scale,
        flameHeight = height * 0.38f * scale,
        tipOffset = offsetX * 0.4f
    )

    drawPath(
        path = innerPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                colors.inner,
                colors.core,
                colors.core
            ),
            startY = height * 0.4f,
            endY = baseY - height * 0.05f
        )
    )
}

private fun createFlamePath(
    centerX: Float,
    baseY: Float,
    flameWidth: Float,
    flameHeight: Float,
    tipOffset: Float
): Path {
    return Path().apply {
        val halfWidth = flameWidth / 2
        val tipY = baseY - flameHeight

        // Start at bottom center
        moveTo(centerX, baseY)

        // Left side curve
        cubicTo(
            x1 = centerX - halfWidth * 0.4f,
            y1 = baseY - flameHeight * 0.1f,
            x2 = centerX - halfWidth * 1.1f,
            y2 = baseY - flameHeight * 0.4f,
            x3 = centerX - halfWidth * 0.7f,
            y3 = baseY - flameHeight * 0.6f
        )

        // Left side to tip
        cubicTo(
            x1 = centerX - halfWidth * 0.5f,
            y1 = baseY - flameHeight * 0.75f,
            x2 = centerX - halfWidth * 0.2f + tipOffset,
            y2 = baseY - flameHeight * 0.9f,
            x3 = centerX + tipOffset,
            y3 = tipY
        )

        // Tip to right side
        cubicTo(
            x1 = centerX + halfWidth * 0.2f + tipOffset,
            y1 = baseY - flameHeight * 0.9f,
            x2 = centerX + halfWidth * 0.5f,
            y2 = baseY - flameHeight * 0.75f,
            x3 = centerX + halfWidth * 0.7f,
            y3 = baseY - flameHeight * 0.6f
        )

        // Right side curve back to bottom
        cubicTo(
            x1 = centerX + halfWidth * 1.1f,
            y1 = baseY - flameHeight * 0.4f,
            x2 = centerX + halfWidth * 0.4f,
            y2 = baseY - flameHeight * 0.1f,
            x3 = centerX,
            y3 = baseY
        )

        close()
    }
}

private fun DrawScope.drawInactiveFlame(
    width: Float,
    height: Float
) {
    val centerX = width / 2
    val baseY = height * 0.92f

    val path = createFlamePath(
        centerX = centerX,
        baseY = baseY,
        flameWidth = width * 0.65f,
        flameHeight = height * 0.75f,
        tipOffset = 0f
    )

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF757575),
                Color(0xFF9E9E9E),
                Color(0xFFBDBDBD)
            ),
            startY = height * 0.15f,
            endY = baseY
        )
    )
}






/**
 * Badge Shape Indicator - For achievements
 */
@Composable
fun BadgeShapeIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    isNew: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isNew) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow for new badges
        if (isNew) {
            Canvas(
                modifier = Modifier
                    .size(size * 1.4f)
                    .blur(8.dp)
            ) {
                drawBadgeShape(
                    color = color.copy(alpha = glowAlpha),
                    center = Offset(this.size.width / 2, this.size.height / 2),
                    size = this.size.minDimension * 0.7f
                )
            }
        }

        // Main badge
        Canvas(modifier = Modifier.size(size)) {
            drawBadgeShape(
                color = color,
                center = Offset(this.size.width / 2, this.size.height / 2),
                size = this.size.minDimension * 0.85f
            )
        }
    }
}

/**
 * Heart Shape Indicator - For wellbeing
 */
@Composable
fun HeartShapeIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = Color(0xFFE91E63),
    isPulsing: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = heartScale
                    scaleY = heartScale
                }
        ) {
            drawHeartShape(
                color = color,
                center = Offset(this.size.width / 2, this.size.height / 2),
                size = this.size.minDimension * 0.85f
            )
        }
    }
}

// ==================== DRAWING HELPERS ====================

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlame(
    color: Color,
    center: Offset,
    size: Float
) {
    val path = Path().apply {
        val width = size
        val height = size * 1.2f
        val startX = center.x - width / 2
        val startY = center.y - height / 2

        moveTo(center.x, startY)  // Top point
        cubicTo(
            center.x + width * 0.2f, startY + height * 0.2f,
            startX + width * 0.9f, startY + height * 0.4f,
            startX + width * 0.8f, startY + height * 0.6f
        )
        cubicTo(
            startX + width * 0.9f, startY + height * 0.8f,
            startX + width * 0.7f, startY + height,
            center.x, startY + height
        )
        cubicTo(
            startX + width * 0.3f, startY + height,
            startX + width * 0.1f, startY + height * 0.8f,
            startX + width * 0.2f, startY + height * 0.6f
        )
        cubicTo(
            startX + width * 0.1f, startY + height * 0.4f,
            center.x - width * 0.2f, startY + height * 0.2f,
            center.x, startY
        )
        close()
    }
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBadgeShape(
    color: Color,
    center: Offset,
    size: Float
) {
    val path = Path()
    val notches = 12
    val outerRadius = size / 2
    val innerRadius = outerRadius * 0.85f

    for (i in 0 until notches * 2) {
        val angle = (i * PI / notches - PI / 2).toFloat()
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)

        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeartShape(
    color: Color,
    center: Offset,
    size: Float
) {
    val path = Path().apply {
        val width = size
        val height = size
        val startX = center.x - width / 2
        val startY = center.y - height / 2

        moveTo(center.x, startY + height * 0.25f)
        // Left curve
        cubicTo(
            startX + width * 0.1f, startY + height * 0.1f,
            startX, startY + height * 0.4f,
            startX + width * 0.1f, startY + height * 0.55f
        )
        cubicTo(
            startX + width * 0.2f, startY + height * 0.7f,
            center.x, startY + height,
            center.x, startY + height
        )
        // Right curve
        cubicTo(
            center.x, startY + height,
            startX + width * 0.8f, startY + height * 0.7f,
            startX + width * 0.9f, startY + height * 0.55f
        )
        cubicTo(
            startX + width, startY + height * 0.4f,
            startX + width * 0.9f, startY + height * 0.1f,
            center.x, startY + height * 0.25f
        )
        close()
    }
    drawPath(path, color)
}
