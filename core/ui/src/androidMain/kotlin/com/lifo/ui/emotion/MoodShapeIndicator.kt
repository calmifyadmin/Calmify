package com.lifo.ui.emotion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.lifo.util.model.Mood

/**
 * M3 Expressive Mood Shape Indicator
 *
 * Renders an animated geometric shape representing the given Mood.
 * Each mood has unique animation behaviour:
 *
 * - Happy: gentle pulse + slow rotation (radiant energy)
 * - Calm: slow breathing scale (serenity)
 * - Neutral: static (equilibrium)
 * - Romantic: heartbeat pulse (love)
 * - Humorous: playful bounce (fun)
 * - Surprised: quick pop-in (shock)
 * - Mysterious: slow rotation (enigma)
 * - Angry: vibrate/shake (intensity)
 * - Tense: subtle tremor (anxiety)
 * - Bored: very slow drift (lethargy)
 * - Depressed: small scale, slow (heaviness)
 * - Disappointed: subtle droop (deflation)
 * - Lonely: isolated wobble (solitude)
 * - Shameful: shy shrink (withdrawal)
 * - Awful: distortion pulse (distress)
 * - Suspicious: side-scan (wariness)
 */
@Composable
fun MoodShapeIndicator(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = true,
    showGlow: Boolean = false
) {
    val polygon = remember(mood) { MoodShapeDefinitions.getShape(mood) }
    val colors = remember(mood) { MoodShapeDefinitions.getColors(mood) }
    val animation = remember(mood, animated) {
        if (animated) MoodAnimationConfig.forMood(mood) else MoodAnimationConfig.NONE
    }

    // Entrance animation
    val entranceScale = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        entranceScale.snapTo(0f)
        entranceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mood_$mood")

    // Breathing/pulse animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (animation.pulseAmount > 0f) 1f + animation.pulseAmount else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animation.pulseDurationMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (animation.rotationDegrees != 0f) animation.rotationDegrees else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(animation.rotationDurationMs, easing = LinearEasing),
            repeatMode = if (animation.rotationReverse) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Horizontal shake/scan
    val offsetX by infiniteTransition.animateFloat(
        initialValue = if (animation.shakeAmount > 0f) -animation.shakeAmount else 0f,
        targetValue = if (animation.shakeAmount > 0f) animation.shakeAmount else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(animation.shakeDurationMs, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow layer
        if (showGlow) {
            Canvas(
                modifier = Modifier
                    .size(size * 1.5f)
                    .blur(10.dp)
                    .graphicsLayer {
                        scaleX = entranceScale.value
                        scaleY = entranceScale.value
                    }
            ) {
                val path = MoodShapeDefinitions.shapeToComposePath(
                    polygon, this.size.width, this.size.height
                )
                drawPath(path, color = colors.glow)
            }
        }

        // Main shape
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = entranceScale.value * pulseScale
                    scaleY = entranceScale.value * pulseScale
                    rotationZ = rotation
                    translationX = offsetX
                }
        ) {
            val path = MoodShapeDefinitions.shapeToComposePath(
                polygon, this.size.width, this.size.height
            )
            val (gradStart, gradEnd) = colors.gradient
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(gradStart, gradEnd),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height)
                )
            )
        }
    }
}

/**
 * Compact mood shape for lists and inline use.
 * No glow, no animation — fast and lightweight.
 */
@Composable
fun MiniMoodShape(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    MoodShapeIndicator(
        mood = mood,
        modifier = modifier,
        size = size,
        animated = false,
        showGlow = false
    )
}

/**
 * Large mood shape for the mood picker (pager).
 * Full glow, full animation.
 */
@Composable
fun LargeMoodShape(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    MoodShapeIndicator(
        mood = mood,
        modifier = modifier,
        size = size,
        animated = true,
        showGlow = true
    )
}

/**
 * Mood shape that morphs between two moods (for transitions).
 */
@Composable
fun MorphingMoodShape(
    fromMood: Mood,
    toMood: Mood,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val morph = remember(fromMood, toMood) {
        MoodShapeDefinitions.createMorph(fromMood, toMood)
    }
    val fromColors = remember(fromMood) { MoodShapeDefinitions.getColors(fromMood) }
    val toColors = remember(toMood) { MoodShapeDefinitions.getColors(toMood) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "morph_progress"
    )

    // Interpolate colors
    val currentColor = lerpColor(fromColors.primary, toColors.primary, animatedProgress)

    Canvas(modifier = modifier.size(size)) {
        val path = MoodShapeDefinitions.morphToComposePath(
            morph, animatedProgress, this.size.width, this.size.height
        )
        drawPath(path, color = currentColor)
    }
}

// ══════════════════════════════════════════════════════════
//  ANIMATION CONFIG — Per-mood animation parameters
// ══════════════════════════════════════════════════════════

internal data class MoodAnimationConfig(
    val pulseAmount: Float = 0f,
    val pulseDurationMs: Int = 1000,
    val rotationDegrees: Float = 0f,
    val rotationDurationMs: Int = 3000,
    val rotationReverse: Boolean = false,
    val shakeAmount: Float = 0f,
    val shakeDurationMs: Int = 200
) {
    companion object {
        val NONE = MoodAnimationConfig()

        fun forMood(mood: Mood): MoodAnimationConfig = when (mood) {
            Mood.Happy -> MoodAnimationConfig(
                pulseAmount = 0.06f,
                pulseDurationMs = 1200,
                rotationDegrees = 360f,
                rotationDurationMs = 12000
            )
            Mood.Calm -> MoodAnimationConfig(
                pulseAmount = 0.04f,
                pulseDurationMs = 3000
            )
            Mood.Neutral -> NONE

            Mood.Romantic -> MoodAnimationConfig(
                pulseAmount = 0.1f,
                pulseDurationMs = 800
            )
            Mood.Humorous -> MoodAnimationConfig(
                pulseAmount = 0.08f,
                pulseDurationMs = 600,
                rotationDegrees = 8f,
                rotationDurationMs = 1000,
                rotationReverse = true
            )
            Mood.Surprised -> MoodAnimationConfig(
                pulseAmount = 0.12f,
                pulseDurationMs = 400
            )
            Mood.Mysterious -> MoodAnimationConfig(
                rotationDegrees = 360f,
                rotationDurationMs = 20000
            )
            Mood.Angry -> MoodAnimationConfig(
                shakeAmount = 3f,
                shakeDurationMs = 80,
                pulseAmount = 0.05f,
                pulseDurationMs = 300
            )
            Mood.Tense -> MoodAnimationConfig(
                shakeAmount = 1.5f,
                shakeDurationMs = 150,
                pulseAmount = 0.03f,
                pulseDurationMs = 500
            )
            Mood.Bored -> MoodAnimationConfig(
                pulseAmount = 0.02f,
                pulseDurationMs = 4000,
                rotationDegrees = 5f,
                rotationDurationMs = 6000,
                rotationReverse = true
            )
            Mood.Depressed -> MoodAnimationConfig(
                pulseAmount = 0.02f,
                pulseDurationMs = 5000
            )
            Mood.Disappointed -> MoodAnimationConfig(
                pulseAmount = 0.03f,
                pulseDurationMs = 3500
            )
            Mood.Lonely -> MoodAnimationConfig(
                pulseAmount = 0.04f,
                pulseDurationMs = 2500,
                rotationDegrees = 4f,
                rotationDurationMs = 3000,
                rotationReverse = true
            )
            Mood.Shameful -> MoodAnimationConfig(
                pulseAmount = -0.05f,
                pulseDurationMs = 2000
            )
            Mood.Awful -> MoodAnimationConfig(
                pulseAmount = 0.07f,
                pulseDurationMs = 500,
                shakeAmount = 2f,
                shakeDurationMs = 120
            )
            Mood.Suspicious -> MoodAnimationConfig(
                shakeAmount = 4f,
                shakeDurationMs = 2000,
                rotationDegrees = 6f,
                rotationDurationMs = 2500,
                rotationReverse = true
            )
        }
    }
}

// ══════════════════════════════════════════════════════════
//  UTILITIES
// ══════════════════════════════════════════════════════════

private fun lerpColor(
    start: androidx.compose.ui.graphics.Color,
    stop: androidx.compose.ui.graphics.Color,
    fraction: Float
): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}
