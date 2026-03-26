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
 * Growth leaf indicator for streak display
 * A monochrome leaf (seed/primary color) that grows based on writing streak.
 * Stages: seed (0) → sprout (1-6) → young leaf (7-13) → full leaf (14-29) → lush (30+)
 */
@Composable
fun GrowthLeafIndicator(
    streakDays: Int,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    // Growth factor: 0→0.3, 1-6→0.4-0.6, 7-13→0.65-0.8, 14-29→0.85-0.95, 30+→1.0
    val growthFactor = when {
        streakDays <= 0 -> 0.3f
        streakDays < 7 -> 0.4f + (streakDays / 6f) * 0.2f
        streakDays < 14 -> 0.65f + ((streakDays - 7) / 6f) * 0.15f
        streakDays < 30 -> 0.85f + ((streakDays - 14) / 15f) * 0.1f
        else -> 1f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "leaf")

    // Gentle breathing sway for active leaves
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leafSway"
    )

    // Glow pulse for 30+ days
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leafGlow"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = if (streakDays > 0) swayAngle else 0f }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Glow for lush stage (30+)
        if (streakDays >= 30) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha),
                        primaryColor.copy(alpha = glowAlpha * 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(w / 2, h * 0.45f),
                    radius = w * 0.7f
                ),
                center = Offset(w / 2, h * 0.45f),
                radius = w * 0.7f
            )
        }

        if (streakDays <= 0) {
            // Dormant seed — small circle at bottom
            drawLeafSeed(w, h, primaryColor.copy(alpha = 0.35f))
        } else {
            // Draw stem
            drawLeafStem(w, h, primaryColor, growthFactor)
            // Draw leaf body
            drawLeafBody(w, h, primaryColor, growthFactor)
            // Draw vein for 7+ days
            if (streakDays >= 7) {
                drawLeafVein(w, h, primaryColor, growthFactor)
            }
        }
    }
}

// ==================== LEAF DRAWING HELPERS ====================

/** Dormant state: small seed circle */
private fun DrawScope.drawLeafSeed(w: Float, h: Float, color: Color) {
    val radius = w * 0.12f
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(w / 2, h * 0.85f)
    )
    // Tiny stem upward
    val stemPath = Path().apply {
        moveTo(w / 2, h * 0.85f - radius)
        lineTo(w / 2, h * 0.7f)
    }
    drawPath(stemPath, color = color, style = Stroke(width = w * 0.04f))
}

/** Curved stem from bottom center */
private fun DrawScope.drawLeafStem(w: Float, h: Float, color: Color, growth: Float) {
    val stemPath = Path().apply {
        moveTo(w * 0.5f, h * 0.95f)
        cubicTo(
            x1 = w * 0.5f, y1 = h * 0.8f,
            x2 = w * 0.48f, y2 = h * (0.95f - growth * 0.5f),
            x3 = w * 0.5f, y3 = h * (0.95f - growth * 0.6f)
        )
    }
    drawPath(
        stemPath,
        color = color.copy(alpha = 0.7f),
        style = Stroke(width = w * 0.045f * growth)
    )
}

/** Main leaf body — asymmetric, organic bezier shape */
private fun DrawScope.drawLeafBody(w: Float, h: Float, color: Color, growth: Float) {
    val leafW = w * 0.4f * growth
    val leafH = h * 0.55f * growth

    // Leaf origin point (where stem meets leaf)
    val ox = w * 0.5f
    val oy = h * (0.95f - growth * 0.6f)

    // Tip of the leaf (tilted slightly right for organic feel)
    val tipX = ox + leafW * 0.1f
    val tipY = oy - leafH

    val leafPath = Path().apply {
        moveTo(ox, oy)

        // Left curve (wider belly)
        cubicTo(
            x1 = ox - leafW * 0.9f, y1 = oy - leafH * 0.25f,
            x2 = ox - leafW * 0.7f, y2 = oy - leafH * 0.75f,
            x3 = tipX, y3 = tipY
        )

        // Right curve (slimmer)
        cubicTo(
            x1 = ox + leafW * 0.7f, y1 = oy - leafH * 0.7f,
            x2 = ox + leafW * 0.85f, y2 = oy - leafH * 0.2f,
            x3 = ox, y3 = oy
        )

        close()
    }

    // Fill with gradient from dark (base) to light (tip)
    drawPath(
        leafPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.95f),
                color.copy(alpha = 0.65f)
            ),
            startY = tipY,
            endY = oy
        )
    )
}

/** Central vein + side veins for mature leaves (7+ days) */
private fun DrawScope.drawLeafVein(w: Float, h: Float, color: Color, growth: Float) {
    val ox = w * 0.5f
    val oy = h * (0.95f - growth * 0.6f)
    val leafH = h * 0.55f * growth
    val tipY = oy - leafH
    val leafW = w * 0.4f * growth

    val veinColor = color.copy(alpha = 0.3f)
    val veinWidth = w * 0.025f

    // Central vein
    val centralVein = Path().apply {
        moveTo(ox, oy)
        cubicTo(
            x1 = ox, y1 = oy - leafH * 0.3f,
            x2 = ox + leafW * 0.05f, y2 = oy - leafH * 0.7f,
            x3 = ox + leafW * 0.1f, y3 = tipY
        )
    }
    drawPath(centralVein, color = veinColor, style = Stroke(width = veinWidth))

    // Side veins (2-3 pairs depending on growth)
    val veinPairs = if (growth > 0.85f) 3 else 2
    for (i in 1..veinPairs) {
        val t = i.toFloat() / (veinPairs + 1)
        val startY = oy - leafH * t
        val startX = ox + leafW * 0.05f * t

        // Left vein
        val leftVein = Path().apply {
            moveTo(startX, startY)
            quadraticTo(
                x1 = startX - leafW * 0.3f, y1 = startY - leafH * 0.06f,
                x2 = startX - leafW * 0.5f, y2 = startY - leafH * 0.12f
            )
        }
        drawPath(leftVein, color = veinColor, style = Stroke(width = veinWidth * 0.7f))

        // Right vein
        val rightVein = Path().apply {
            moveTo(startX, startY)
            quadraticTo(
                x1 = startX + leafW * 0.28f, y1 = startY - leafH * 0.05f,
                x2 = startX + leafW * 0.45f, y2 = startY - leafH * 0.1f
            )
        }
        drawPath(rightVein, color = veinColor, style = Stroke(width = veinWidth * 0.7f))
    }
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
