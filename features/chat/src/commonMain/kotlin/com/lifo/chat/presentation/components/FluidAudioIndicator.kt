package com.lifo.chat.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Fluid audio indicator similar to Gemini's native experience
 * Shows minimal, elegant visual feedback during speech
 */
@Composable
fun FluidAudioIndicator(
    isSpeaking: Boolean,
    emotion: String = "NEUTRAL",
    latencyMs: Long = 0,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio")

    // Dynamic animation speed based on emotion
    val animationDuration = when (emotion) {
        "EXCITED" -> 600
        "THOUGHTFUL" -> 1800
        "SAD" -> 2000
        else -> 1200
    }

    // Primary wave animation
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = LinearEasing)
        ),
        label = "wavePhase"
    )

    // Secondary wave for depth
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((animationDuration * 1.3f).toInt(), easing = LinearEasing)
        ),
        label = "secondaryPhase"
    )

    // Amplitude modulation for natural speech rhythm
    val amplitudeModulation by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )

    // Smooth visibility transition
    val targetAlpha = if (isSpeaking) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (isSpeaking) 150 else 300,
            easing = if (isSpeaking) FastOutSlowInEasing else LinearEasing
        ),
        label = "alpha"
    )

    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.Center
        ) {
            // Subtle background
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                drawFluidWaves(
                    wavePhase = wavePhase,
                    secondaryPhase = secondaryPhase,
                    amplitude = amplitudeModulation,
                    emotion = emotion,
                    alpha = alpha
                )
            }

            // Ultra-low latency indicator
            if (latencyMs < 50 && latencyMs > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
            }
        }
    }
}

/**
 * Minimal speaking indicator for message bubbles
 */
@Composable
fun MinimalSpeakingIndicator(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else 0f,
        animationSpec = tween(200),
        label = "alpha"
    )

    if (alpha > 0f) {
        Canvas(
            modifier = modifier
                .size(32.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            val center = size.center
            val radius = size.minDimension / 2f

            // Outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4285F4).copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.5f
                ),
                radius = radius * 1.5f,
                center = center
            )

            // Inner circle
            drawCircle(
                color = Color(0xFF4285F4),
                radius = radius * 0.4f,
                center = center
            )
        }
    }
}

/**
 * Voice waveform visualization
 */
@Composable
fun VoiceWaveform(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val barCount = 5

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            VoiceBar(
                index = index,
                audioLevel = audioLevel,
                isActive = isActive,
                delayMs = index * 50
            )
        }
    }
}

@Composable
private fun VoiceBar(
    index: Int,
    audioLevel: Float,
    isActive: Boolean,
    delayMs: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bar$index")

    val height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 800
                0.3f at 0 + delayMs
                1f at 200 + delayMs
                0.3f at 400 + delayMs
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(delayMs)
        ),
        label = "height$index"
    )

    val targetAlpha = if (isActive) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(200),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .width(4.dp)
            .height(24.dp)
            .graphicsLayer { this.alpha = alpha }
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight(height * audioLevel)
                .align(Alignment.Center)
                .clip(MaterialTheme.shapes.small)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    )
                )
        )
    }
}

// Extension function to draw fluid waves
private fun DrawScope.drawFluidWaves(
    wavePhase: Float,
    secondaryPhase: Float,
    amplitude: Float,
    emotion: String,
    alpha: Float
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f

    // Emotion-based color
    val waveColor = when (emotion) {
        "EXCITED" -> Color(0xFFF9AB00) // Amber
        "HAPPY" -> Color(0xFF34A853) // Green
        "SAD" -> Color(0xFF4285F4).copy(alpha = 0.7f) // Softer blue
        "THOUGHTFUL" -> Color(0xFF9C27B0) // Purple
        else -> Color(0xFF4285F4) // Google Blue
    }.copy(alpha = alpha * 0.3f)

    // Draw primary wave
    val path = Path()
    path.moveTo(0f, centerY)

    for (x in 0..width.toInt() step 2) {
        val progress = x / width
        val y = centerY + sin(progress * 4 * PI + wavePhase) * 10f * amplitude
        path.lineTo(x.toFloat(), y.toFloat())
    }

    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                waveColor,
                waveColor.copy(alpha = 0f)
            ),
            startY = centerY - 10f,
            endY = height
        )
    )

    // Draw secondary wave for depth
    val secondaryPath = Path()
    secondaryPath.moveTo(0f, centerY)

    for (x in 0..width.toInt() step 2) {
        val progress = x / width
        val y = centerY + sin(progress * 3 * PI + secondaryPhase) * 8f * amplitude
        secondaryPath.lineTo(x.toFloat(), y.toFloat())
    }

    secondaryPath.lineTo(width, height)
    secondaryPath.lineTo(0f, height)
    secondaryPath.close()

    drawPath(
        path = secondaryPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                waveColor.copy(alpha = 0.5f),
                waveColor.copy(alpha = 0f)
            ),
            startY = centerY - 8f,
            endY = height
        )
    )

    // Center line
    drawLine(
        color = waveColor.copy(alpha = 0.6f),
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), wavePhase * 10)
    )
}