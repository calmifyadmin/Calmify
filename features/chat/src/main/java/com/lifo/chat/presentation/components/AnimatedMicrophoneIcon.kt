package com.lifo.chat.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ChatEmotion

/**
 * Animated microphone icon with sound wave indicators.
 * Features dynamic scaling, color transitions, and sound wave animation.
 */
@Composable
fun AnimatedMicrophoneIcon(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    showSoundWaves: Boolean = true
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale"
    )
    
    val iconColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.Gray
            isRecording -> Color.White
            else -> emotion.secondaryColor
        },
        animationSpec = tween(200),
        label = "icon_color"
    )
    
    if (isRecording && showSoundWaves) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left sound waves
            SoundWaveIndicator(
                isActive = isRecording,
                delay = 0,
                emotion = emotion,
                modifier = Modifier.size(8.dp, 16.dp)
            )
            SoundWaveIndicator(
                isActive = isRecording,
                delay = 200,
                emotion = emotion,
                modifier = Modifier.size(8.dp, 20.dp)
            )
            
            // Microphone icon
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Recording",
                tint = iconColor,
                modifier = Modifier
                    .scale(iconScale)
                    .size(24.dp)
            )
            
            // Right sound waves
            SoundWaveIndicator(
                isActive = isRecording,
                delay = 100,
                emotion = emotion,
                modifier = Modifier.size(8.dp, 20.dp)
            )
            SoundWaveIndicator(
                isActive = isRecording,
                delay = 300,
                emotion = emotion,
                modifier = Modifier.size(8.dp, 16.dp)
            )
        }
    } else {
        Icon(
            imageVector = if (isEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = if (isEnabled) "Microphone ready" else "Microphone disabled",
            tint = iconColor,
            modifier = modifier.scale(iconScale)
        )
    }
}

/**
 * Individual sound wave indicator with animated height
 */
@Composable
private fun SoundWaveIndicator(
    isActive: Boolean,
    delay: Int,
    emotion: ChatEmotion,
    modifier: Modifier = Modifier
) {
    val animatedHeight by rememberInfiniteTransition(
        label = "wave_${delay}"
    ).animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 800,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_height"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 0.8f else 0f,
        animationSpec = tween(200),
        label = "wave_alpha"
    )
    
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                color = emotion.tertiaryColor,
                shape = CircleShape
            )
            .fillMaxHeight(if (isActive) animatedHeight else 0.3f)
    )
}

/**
 * Pulsing microphone icon for recording state
 */
@Composable
fun PulsingMicrophoneIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL
) {
    val pulseScale by rememberInfiniteTransition(
        label = "pulse_transition"
    ).animateFloat(
        initialValue = 1f,
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
    
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.6f,
        animationSpec = tween(300),
        label = "mic_alpha"
    )
    
    Icon(
        imageVector = Icons.Default.Mic,
        contentDescription = if (isActive) "Recording active" else "Recording inactive",
        tint = emotion.primaryColor,
        modifier = modifier
            .scale(if (isActive) pulseScale else 1f)
            .alpha(alpha)
    )
}

/**
 * Compact microphone icon without sound waves
 */
@Composable
fun CompactMicrophoneIcon(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL
) {
    AnimatedMicrophoneIcon(
        isRecording = isRecording,
        modifier = modifier,
        isEnabled = isEnabled,
        emotion = emotion,
        showSoundWaves = false
    )
}