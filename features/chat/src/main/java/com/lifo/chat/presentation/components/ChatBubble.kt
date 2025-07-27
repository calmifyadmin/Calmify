package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.mongo.repository.ChatMessage

/**
 * Simplified chat bubble with integrated natural voice indicators
 * Similar to Gemini's clean design
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    voiceEmotion: String,
    voiceLatency: Long,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    val isAi = !message.isUser

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showMenu = !showMenu
            },
        horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End
    ) {
        if (isAi) {
            // AI Avatar with integrated speaking indicator
            AiAvatarWithVoice(
                isSpeaking = isSpeaking,
                emotion = voiceEmotion
            )

            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isAi) Alignment.Start else Alignment.End
        ) {
            // Message bubble
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isAi) 4.dp else 18.dp,
                    bottomEnd = if (isAi) 18.dp else 4.dp
                ),
                color = if (isAi) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAi) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Voice button for AI messages only
                    if (isAi) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CompactVoiceButton(
                            isSpeaking = isSpeaking,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSpeak()
                            }
                        )
                    }
                }
            }

            // Voice indicator below message
            if (isAi && isSpeaking) {
                Spacer(modifier = Modifier.height(4.dp))
                CompactVoiceIndicator(
                    emotion = voiceEmotion,
                    latency = voiceLatency
                )
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copia") },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCopy()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                }
            )

            if (isAi) {
                DropdownMenuItem(
                    text = { Text(if (isSpeaking) "Ferma voce" else "Ascolta") },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSpeak()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (isSpeaking) Icons.Filled.Stop else Icons.Outlined.VolumeUp,
                            contentDescription = null
                        )
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Elimina", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
private fun AiAvatarWithVoice(
    isSpeaking: Boolean,
    emotion: String
) {
    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Speaking glow effect
        AnimatedVisibility(
            visible = isSpeaking,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                getEmotionColor(emotion).copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Avatar
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "✨",
                    fontSize = 16.sp
                )
            }
        }

        // Animated speaking dot
        if (isSpeaking) {
            SpeakingDot(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            )
        }
    }
}

@Composable
private fun SpeakingDot(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "speaking")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                Color(0xFF4285F4).copy(alpha = alpha)
            )
    )
}

@Composable
private fun CompactVoiceButton(
    isSpeaking: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSpeaking) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "scale"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Icon(
            imageVector = if (isSpeaking) {
                Icons.Filled.StopCircle
            } else {
                Icons.Outlined.PlayCircle
            },
            contentDescription = if (isSpeaking) "Stop" else "Play",
            modifier = Modifier.size(16.dp),
            tint = if (isSpeaking) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
private fun CompactVoiceIndicator(
    emotion: String,
    latency: Long
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 40.dp)
    ) {
        // Minimal waveform
        VoiceWaveform(
            audioLevel = 0.8f,
            isActive = true,
            modifier = Modifier.height(16.dp)
        )

        if (latency < 50) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${latency}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
    }
}

private fun getEmotionColor(emotion: String): Color {
    return when (emotion) {
        "EXCITED" -> Color(0xFFF9AB00)
        "HAPPY" -> Color(0xFF34A853)
        "SAD" -> Color(0xFF4285F4).copy(alpha = 0.7f)
        "THOUGHTFUL" -> Color(0xFF9C27B0)
        "EMPATHETIC" -> Color(0xFFE91E63)
        "CURIOUS" -> Color(0xFF00ACC1)
        else -> Color(0xFF4285F4)
    }
}