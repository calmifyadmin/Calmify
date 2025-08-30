package com.lifo.chat.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.ChatEmotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium push-to-talk button with advanced animations and haptic feedback.
 * Features:
 * - Pressure-sensitive press detection
 * - Haptic feedback on press/release
 * - Scale animations with physics-based easing
 * - Pulsing border when recording
 * - Ripple effects on interaction
 * - Accessibility support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushToTalkButton(
    isPressed: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    size: Dp = 88.dp,
    showWaveRings: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isInteractionPressed by interactionSource.collectIsPressedAsState()
    
    // Combined press state from both prop and interaction
    val actuallyPressed = isPressed || isInteractionPressed
    
    // Animation values
    val scale by animateFloatAsState(
        targetValue = if (actuallyPressed) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (actuallyPressed) 16.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "button_elevation"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.6f,
        animationSpec = tween(200),
        label = "button_alpha"
    )
    
    // Pulsing animation for recording state
    val pulseAnimation by rememberInfiniteTransition(
        label = "pulse_transition"
    ).animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    // Wave ring animation
    val waveAnimation by rememberInfiniteTransition(
        label = "wave_transition"
    ).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )
    
    // Press state handling
    var isLocalPressed by remember { mutableStateOf(false) }
    
    LaunchedEffect(actuallyPressed) {
        if (actuallyPressed && isEnabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        } else if (!actuallyPressed && isEnabled) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .semantics {
                contentDescription = if (actuallyPressed) "Recording voice message" else "Press and hold to record voice message"
            },
        contentAlignment = Alignment.Center
    ) {
        // Background wave rings
        if (showWaveRings && actuallyPressed) {
            Canvas(
                modifier = Modifier
                    .size(size * 1.8f)
            ) {
                drawWaveRings(
                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                    progress = waveAnimation,
                    emotion = emotion,
                    ringCount = 3
                )
            }
        }
        
        // Main button surface
        Surface(
            onClick = { /* Handled by interaction source */ },
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isLocalPressed = true
                            if (isEnabled) {
                                onPressStart()
                                tryAwaitRelease()
                                isLocalPressed = false
                                onPressEnd()
                            }
                        }
                    )
                },
            enabled = isEnabled,
            shape = CircleShape,
            color = getPrimaryButtonColor(emotion, actuallyPressed),
            shadowElevation = elevation,
            interactionSource = interactionSource
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Pulsing border for recording state
                if (actuallyPressed) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawPulsingBorder(
                            size = this.size,
                            pulse = pulseAnimation,
                            emotion = emotion
                        )
                    }
                }
                
                // Microphone icon
                AnimatedMicrophoneIcon(
                    isRecording = actuallyPressed,
                    isEnabled = isEnabled,
                    emotion = emotion,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // Recording indicator text
        if (actuallyPressed) {
            Text(
                text = "Recording...",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = emotion.primaryColor,
                modifier = Modifier
                    .offset(y = size / 2 + 16.dp)
                    .alpha(0.8f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Draw animated wave rings around the button
 */
private fun DrawScope.drawWaveRings(
    center: Offset,
    progress: Float,
    emotion: ChatEmotion,
    ringCount: Int = 3
) {
    for (i in 0 until ringCount) {
        val ringProgress = ((progress + i * 0.3f) % 1f)
        val alpha = 1f - ringProgress
        val radius = size.minDimension / 4f + (ringProgress * size.minDimension / 2f)
        
        if (alpha > 0f) {
            drawCircle(
                color = emotion.primaryColor.copy(alpha = alpha * 0.3f),
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Draw pulsing border effect
 */
private fun DrawScope.drawPulsingBorder(
    size: androidx.compose.ui.geometry.Size,
    pulse: Float,
    emotion: ChatEmotion
) {
    val strokeWidth = 4.dp.toPx() * pulse
    val radius = (size.minDimension / 2f) - strokeWidth / 2f
    
    drawCircle(
        color = emotion.secondaryColor.copy(alpha = 0.6f),
        radius = radius,
        center = size.center,
        style = Stroke(width = strokeWidth)
    )
    
    // Inner glow effect
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                emotion.primaryColor.copy(alpha = 0.2f * pulse),
                Color.Transparent
            ),
            center = size.center,
            radius = radius * 0.8f
        ),
        radius = radius * 0.8f,
        center = size.center
    )
}

/**
 * Get button color based on emotion and state
 */
private fun getPrimaryButtonColor(emotion: ChatEmotion, isPressed: Boolean): Color {
    return if (isPressed) {
        emotion.primaryColor.copy(alpha = 0.9f)
    } else {
        emotion.primaryColor.copy(alpha = 0.8f)
    }
}



/**
 * Compact push-to-talk button for smaller spaces
 */
@Composable
fun CompactPushToTalkButton(
    isPressed: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL
) {
    PushToTalkButton(
        isPressed = isPressed,
        onPressStart = onPressStart,
        onPressEnd = onPressEnd,
        modifier = modifier,
        isEnabled = isEnabled,
        emotion = emotion,
        size = 64.dp,
        showWaveRings = false
    )
}

/**
 * Voice recording button with duration display
 */
@Composable
fun VoiceRecordingButton(
    isRecording: Boolean,
    recordingDuration: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
    maxDuration: Long = 60_000L // 60 seconds
) {
    val progress = recordingDuration.toFloat() / maxDuration.toFloat()
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Duration indicator
        if (isRecording) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp),
                color = ChatEmotion.ANXIOUS.primaryColor,
                trackColor = ChatEmotion.ANXIOUS.primaryColor.copy(alpha = 0.3f)
            )
            
            Text(
                text = "${recordingDuration / 1000}s",
                style = MaterialTheme.typography.labelSmall,
                color = ChatEmotion.ANXIOUS.primaryColor
            )
        }
        
        // Main button
        PushToTalkButton(
            isPressed = isRecording,
            onPressStart = onStartRecording,
            onPressEnd = onStopRecording,
            emotion = if (progress > 0.8f) ChatEmotion.ANXIOUS else ChatEmotion.NEUTRAL,
            isEnabled = progress < 1f
        )
    }
}