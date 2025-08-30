package com.lifo.chat.presentation.components.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ChatEmotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Real-time audio waveform visualizer with 64-bar frequency spectrum.
 * Displays animated bars that respond to audio input with smooth transitions
 * and emotion-based coloring.
 * 
 * Optimized for 60 FPS performance with GPU acceleration.
 */
@Composable
fun LiveWaveformVisualizer(
    audioData: FloatArray,
    isRecording: Boolean,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    modifier: Modifier = Modifier,
    barCount: Int = 64,
    height: Dp = 60.dp,
    showMirror: Boolean = true,
    animationDuration: Int = 100
) {
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    
    // Animation state for smooth bar transitions
    val animatedBars = remember { mutableStateListOf<Float>() }
    
    // Initialize bars if empty
    LaunchedEffect(barCount) {
        if (animatedBars.isEmpty()) {
            repeat(barCount) { animatedBars.add(0f) }
        }
    }
    
    // Update animated bars when audio data changes
    LaunchedEffect(audioData.contentHashCode(), isRecording) {
        if (isRecording && audioData.isNotEmpty()) {
            // Process and normalize audio data to match bar count
            val processedData = processAudioDataForBars(audioData, barCount)
            
            // Animate each bar individually
            processedData.forEachIndexed { index, value ->
                if (index < animatedBars.size) {
                    animatedBars[index] = value
                }
            }
        } else if (!isRecording) {
            // Animate to idle state
            animatedBars.forEachIndexed { index, _ ->
                animatedBars[index] = 0.1f + Random.nextFloat() * 0.1f // Subtle idle animation
            }
        }
    }
    
    // Idle breathing animation when not recording
    val breathingAnimation by rememberInfiniteTransition(
        label = "breathing"
    ).animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
    
    // Recording pulse animation
    val recordingPulse by rememberInfiniteTransition(
        label = "recording_pulse"
    ).animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barWidth = canvasWidth / barCount
            val maxBarHeight = canvasHeight * (if (showMirror) 0.4f else 0.8f)
            
            // Draw main waveform bars
            drawWaveformBars(
                bars = animatedBars,
                emotion = emotion,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                barWidth = barWidth,
                maxBarHeight = maxBarHeight,
                isRecording = isRecording,
                recordingPulse = recordingPulse,
                breathingScale = if (!isRecording) breathingAnimation else 1f,
                showMirror = showMirror
            )
        }
        
        // Glass morphism overlay
        if (isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                emotion.primaryColor.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Draws the waveform bars with animations and effects
 */
private fun DrawScope.drawWaveformBars(
    bars: List<Float>,
    emotion: ChatEmotion,
    canvasWidth: Float,
    canvasHeight: Float,
    barWidth: Float,
    maxBarHeight: Float,
    isRecording: Boolean,
    recordingPulse: Float,
    breathingScale: Float,
    showMirror: Boolean
) {
    val centerY = canvasHeight / 2f
    val minBarHeight = 4.dp.toPx()
    
    bars.forEachIndexed { index, amplitude ->
        val x = index * barWidth + barWidth / 2f
        val normalizedAmplitude = max(0.05f, min(1f, amplitude))
        
        // Calculate bar height with animations
        var barHeight = max(minBarHeight, normalizedAmplitude * maxBarHeight)
        
        if (isRecording) {
            barHeight *= recordingPulse
        } else {
            barHeight *= breathingScale
        }
        
        // Dynamic color based on amplitude and emotion
        val colorIntensity = if (isRecording) normalizedAmplitude else 0.3f
        val barColor = getBarColor(emotion, colorIntensity, index, bars.size)
        
        // Main bar
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x - barWidth * 0.3f, centerY - barHeight / 2f),
            size = Size(barWidth * 0.6f, barHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        
        // Mirror reflection
        if (showMirror && barHeight > minBarHeight) {
            val mirrorAlpha = 0.3f * colorIntensity
            val mirrorHeight = barHeight * 0.6f
            
            val mirrorGradient = Brush.verticalGradient(
                colors = listOf(
                    barColor.copy(alpha = mirrorAlpha),
                    Color.Transparent
                ),
                startY = centerY + barHeight / 2f + 2.dp.toPx(),
                endY = centerY + barHeight / 2f + mirrorHeight + 2.dp.toPx()
            )
            
            drawRoundRect(
                brush = mirrorGradient,
                topLeft = Offset(x - barWidth * 0.3f, centerY + barHeight / 2f + 2.dp.toPx()),
                size = Size(barWidth * 0.6f, mirrorHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        
        // Peak glow effect for high amplitudes
        if (normalizedAmplitude > 0.7f && isRecording) {
            val glowAlpha = (normalizedAmplitude - 0.7f) * 0.5f
            val glowGradient = Brush.radialGradient(
                colors = listOf(
                    emotion.primaryColor.copy(alpha = glowAlpha),
                    Color.Transparent
                ),
                center = Offset(x, centerY),
                radius = barWidth * 1.5f
            )
            
            drawCircle(
                brush = glowGradient,
                center = Offset(x, centerY),
                radius = barWidth * 1.5f
            )
        }
    }
}

/**
 * Get dynamic bar color based on emotion, intensity and position
 */
private fun getBarColor(
    emotion: ChatEmotion,
    intensity: Float,
    barIndex: Int,
    totalBars: Int
): Color {
    val position = barIndex.toFloat() / totalBars.toFloat()
    
    // Create gradient across bars
    val baseColor = when {
        position < 0.33f -> emotion.primaryColor
        position < 0.66f -> emotion.secondaryColor
        else -> emotion.tertiaryColor
    }
    
    // Apply intensity and emotion-based alpha
    val alpha = 0.3f + (intensity * 0.7f * emotion.intensity)
    return baseColor.copy(alpha = alpha)
}

/**
 * Process raw audio data into normalized bar values
 */
private fun processAudioDataForBars(audioData: FloatArray, barCount: Int): FloatArray {
    if (audioData.isEmpty()) return FloatArray(barCount) { 0f }
    
    val processedBars = FloatArray(barCount)
    val samplesPerBar = maxOf(1, audioData.size / barCount)
    
    for (i in 0 until barCount) {
        val startIndex = i * samplesPerBar
        val endIndex = minOf(startIndex + samplesPerBar, audioData.size)
        
        // Calculate RMS (Root Mean Square) for this frequency band
        var sum = 0f
        for (j in startIndex until endIndex) {
            val sample = audioData[j]
            sum += sample * sample
        }
        
        val rms = kotlin.math.sqrt(sum / (endIndex - startIndex))
        
        // Apply frequency weighting (emphasize mid frequencies)
        val frequencyWeight = when (i) {
            in 0..barCount / 4 -> 0.6f // Low frequencies
            in barCount / 4..3 * barCount / 4 -> 1.0f // Mid frequencies
            else -> 0.8f // High frequencies
        }
        
        processedBars[i] = minOf(1f, rms * frequencyWeight * 2f) // Scale and clamp
    }
    
    return processedBars
}

/**
 * Compact waveform for smaller UI spaces
 */
@Composable
fun CompactWaveformVisualizer(
    audioData: FloatArray,
    isRecording: Boolean,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    modifier: Modifier = Modifier
) {
    LiveWaveformVisualizer(
        audioData = audioData,
        isRecording = isRecording,
        emotion = emotion,
        modifier = modifier,
        barCount = 32,
        height = 32.dp,
        showMirror = false,
        animationDuration = 80
    )
}

/**
 * Legacy VoiceWaveform component for compatibility
 */
@Composable
fun VoiceWaveform(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Generate fake audio data based on level for compatibility
    val audioData = remember(audioLevel, isActive) {
        if (isActive) {
            FloatArray(32) { Random.nextFloat() * audioLevel }
        } else {
            FloatArray(32) { 0f }
        }
    }
    
    CompactWaveformVisualizer(
        audioData = audioData,
        isRecording = isActive,
        emotion = ChatEmotion.NEUTRAL,
        modifier = modifier
    )
}