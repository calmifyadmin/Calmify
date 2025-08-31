package com.lifo.chat.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Audio Level Indicator - Real-time visualization of microphone input levels
 * 
 * Features:
 * - Circular ring design that expands with audio level
 * - Multiple rings for depth effect
 * - Smooth animations with spring physics
 * - Color transitions based on recording state
 * - Optimized for 60 FPS performance
 */
@Composable
fun AudioLevelIndicator(
    audioLevel: Float, // 0.0 to 1.0
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ringCount: Int = 3
) {
    // Smooth audio level animation
    val smoothAudioLevel by animateFloatAsState(
        targetValue = if (isRecording) audioLevel else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "audio_level_smooth"
    )
    
    // Recording state animation
    val recordingAlpha by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0.3f,
        animationSpec = tween(durationMillis = 300),
        label = "recording_alpha"
    )
    
    // Pulsing animation for recording state
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 4f
            
            // Draw audio level rings
            drawAudioLevelRings(
                center = center,
                baseRadius = baseRadius,
                audioLevel = smoothAudioLevel,
                isRecording = isRecording,
                ringCount = ringCount,
                pulseScale = if (isRecording) pulseAnimation else 1f,
                alpha = recordingAlpha
            )
            
            // Draw center indicator
            drawCenterIndicator(
                center = center,
                radius = baseRadius * 0.3f,
                audioLevel = smoothAudioLevel,
                isRecording = isRecording,
                alpha = recordingAlpha
            )
        }
    }
}

/**
 * Draw concentric audio level rings
 */
private fun DrawScope.drawAudioLevelRings(
    center: Offset,
    baseRadius: Float,
    audioLevel: Float,
    isRecording: Boolean,
    ringCount: Int,
    pulseScale: Float,
    alpha: Float
) {
    val maxRadius = this.size.minDimension / 2f * 0.8f
    
    for (i in 0 until ringCount) {
        val ringProgress = (audioLevel + (i * 0.2f)).coerceIn(0f, 1f)
        val ringRadius = baseRadius + (ringProgress * (maxRadius - baseRadius))
        val ringAlpha = alpha * (1f - (i * 0.3f)).coerceIn(0.2f, 1f)
        val strokeWidth = (4f - i * 1f).coerceAtLeast(1f)
        
        // Color based on recording state and audio level
        val ringColor = if (isRecording) {
            lerp(
                Color(0xFF4CAF50), // Green
                Color(0xFFFF5722), // Red-Orange
                (audioLevel * 0.7f).coerceIn(0f, 1f)
            )
        } else {
            Color(0xFF9E9E9E) // Gray
        }
        
        // Apply pulse scaling to outer rings
        val scaledRadius = ringRadius * (1f + ((pulseScale - 1f) * (i + 1f) / ringCount))
        
        if (ringProgress > 0f && ringAlpha > 0f) {
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = scaledRadius,
                center = center,
                style = Stroke(width = strokeWidth.dp.toPx())
            )
        }
    }
}

/**
 * Draw center indicator dot
 */
private fun DrawScope.drawCenterIndicator(
    center: Offset,
    radius: Float,
    audioLevel: Float,
    isRecording: Boolean,
    alpha: Float
) {
    val indicatorRadius = radius * (1f + audioLevel * 0.5f)
    
    val indicatorColor = if (isRecording) {
        when {
            audioLevel < 0.3f -> Color(0xFF4CAF50) // Green - Low
            audioLevel < 0.7f -> Color(0xFFFF9800) // Orange - Medium  
            else -> Color(0xFFFF5722) // Red-Orange - High
        }
    } else {
        Color(0xFF757575) // Gray - Not recording
    }
    
    // Draw center dot with gradient
    val centerBrush = Brush.radialGradient(
        colors = listOf(
            indicatorColor.copy(alpha = alpha),
            indicatorColor.copy(alpha = alpha * 0.6f),
            Color.Transparent
        ),
        center = center,
        radius = indicatorRadius * 1.5f
    )
    
    drawCircle(
        brush = centerBrush,
        radius = indicatorRadius * 1.5f,
        center = center
    )
    
    // Draw solid center
    drawCircle(
        color = indicatorColor.copy(alpha = alpha * 0.9f),
        radius = indicatorRadius,
        center = center
    )
}

/**
 * Compact version for smaller spaces
 */
@Composable
fun CompactAudioLevelIndicator(
    audioLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    AudioLevelIndicator(
        audioLevel = audioLevel,
        isRecording = isRecording,
        modifier = modifier,
        size = 32.dp,
        ringCount = 2
    )
}

/**
 * Linear audio level bar for horizontal layouts
 */
@Composable
fun LinearAudioLevelIndicator(
    audioLevel: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 8
) {
    val smoothAudioLevel by animateFloatAsState(
        targetValue = if (isRecording) audioLevel else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "linear_audio_level"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val barThreshold = (i + 1f) / barCount
            val barActive = smoothAudioLevel >= barThreshold
            val barAlpha = if (barActive) 1f else 0.3f
            
            val barColor = when {
                i < barCount * 0.5f -> Color(0xFF4CAF50) // Green - Low
                i < barCount * 0.8f -> Color(0xFFFF9800) // Orange - Medium
                else -> Color(0xFFFF5722) // Red - High
            }
            
            Canvas(
                modifier = Modifier
                    .width(4.dp)
                    .height((8 + i * 2).dp)
            ) {
                val barHeight = if (barActive) {
                    this.size.height
                } else {
                    this.size.height * 0.3f
                }
                
                drawRoundRect(
                    color = barColor.copy(alpha = barAlpha),
                    size = androidx.compose.ui.geometry.Size(
                        width = this.size.width,
                        height = barHeight
                    ),
                    topLeft = Offset(0f, this.size.height - barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        x = 2.dp.toPx(),
                        y = 2.dp.toPx()
                    )
                )
            }
        }
    }
}

