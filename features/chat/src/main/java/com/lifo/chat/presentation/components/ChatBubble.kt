package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.lifo.mongo.repository.ChatMessage
import com.lifo.mongo.repository.MessageStatus
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    onSpeak: () -> Unit = {},
    isSpeaking: Boolean = false,
    currentEmotion: String = "NEUTRAL",
    voiceNaturalness: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }

    // Animazioni fluide
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = {
            if (abs(offsetX) > 100) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
            offsetX = 0f
        },
        label = "offsetX"
    )

    // Animazione parlato per AI
    val speakingAnimation = rememberInfiniteTransition(label = "speaking")
    val speakingScale by speakingAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (currentEmotion) {
                    "EXCITED" -> 400
                    "CALM", "THOUGHTFUL" -> 1200
                    else -> 800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakingScale"
    )

    // Colore basato su emozione
    val emotionColor = when (currentEmotion) {
        "HAPPY", "EXCITED" -> MaterialTheme.colorScheme.tertiary
        "SAD" -> MaterialTheme.colorScheme.secondary
        "THOUGHTFUL" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        "EMPHATIC" -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.primary
    }

    val animatedEmotionColor by animateColorAsState(
        targetValue = if (isSpeaking) emotionColor else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(600),
        label = "emotionColor"
    )

    if (!message.isUser) {
        // AI MESSAGE - Con indicatori di naturalezza
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationX = animatedOffsetX
                    alpha = 1f - (abs(animatedOffsetX) / 300f)
                    // Rimosso l'effetto di scala quando parla
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (abs(offsetX) > 60) {
                                if (offsetX > 0) 200f else -200f
                            } else 0f
                        }
                    ) { _, dragAmount ->
                        offsetX += dragAmount * 0.5f
                    }
                }
                .clickable { showMenu = !showMenu }
        ) {
            // AI Avatar animato
            NaturalAvatarBubble(
                isSpeaking = isSpeaking,
                emotion = currentEmotion,
                color = animatedEmotionColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Content con indicatori
            Column(modifier = Modifier.weight(1f)) {
                // AI Name con stato emotivo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Lifo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Emotion indicator
                    if (isSpeaking) {
                        EmotionIndicator(emotion = currentEmotion)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message content con highlighting emotivo
                Box {
                    MessageContent(
                        content = message.content,
                        isUser = false,
                        isSpeaking = isSpeaking,
                        emotion = currentEmotion
                    )

                    // Speaking overlay effect
                    if (isSpeaking) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            emotionColor.copy(alpha = 0.05f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                }

                // Voice naturalness indicator
                if (isSpeaking && voiceNaturalness > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    MiniNaturalnessIndicator(
                        naturalness = voiceNaturalness,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }

                // Status indicators
                AnimatedVisibility(
                    visible = message.status != MessageStatus.SENT || message.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    StatusIndicator(message)
                }
            }

            // Voice control button
            AnimatedVisibility(
                visible = !message.isUser,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                VoiceControlButton(
                    isSpeaking = isSpeaking,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSpeak()
                    }
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

            DropdownMenuItem(
                text = { Text("Ascolta") },
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
    } else {
        // USER MESSAGE - Design pulito
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .graphicsLayer {
                    translationX = animatedOffsetX
                    alpha = 1f - (abs(animatedOffsetX) / 300f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (abs(offsetX) > 60) {
                                if (offsetX > 0) 200f else -200f
                            } else 0f
                        }
                    ) { _, dragAmount ->
                        offsetX += dragAmount * 0.5f
                    }
                },
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 4.dp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .shadow(
                            elevation = 3.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showMenu = !showMenu }
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        )
                    ) {
                        MessageContent(
                            content = message.content,
                            isUser = true
                        )

                        AnimatedVisibility(
                            visible = message.status != MessageStatus.SENT || message.error != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            StatusIndicator(message)
                        }
                    }
                }

                // Timestamp con animazione
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(delayMillis = 200))
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
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

                    if (message.status == MessageStatus.FAILED) {
                        DropdownMenuItem(
                            text = { Text("Riprova") },
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRetry()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
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
    }
}

@Composable
private fun NaturalAvatarBubble(
    isSpeaking: Boolean,
    emotion: String,
    color: Color
) {
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeaking) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp)
    ) {
        // Outer glow when speaking
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .scale(pulseScale * 1.2f)
                    .background(
                        color = color.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = color,
            modifier = Modifier
                .size(32.dp)
                .scale(if (isSpeaking) pulseScale else 1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (emotion) {
                        "HAPPY", "EXCITED" -> Icons.Outlined.Mood
                        "SAD" -> Icons.Outlined.SentimentDissatisfied
                        "THOUGHTFUL" -> Icons.Outlined.Psychology
                        else -> Icons.Outlined.AutoAwesome
                    },
                    contentDescription = "AI",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EmotionIndicator(emotion: String) {
    val emotionEmoji = when (emotion) {
        "HAPPY" -> "😊"
        "SAD" -> "😢"
        "EXCITED" -> "🎉"
        "THOUGHTFUL" -> "🤔"
        "CALM" -> "😌"
        "EMPHATIC" -> "💪"
        else -> "💬"
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.size(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = emotionEmoji,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun VoiceControlButton(
    isSpeaking: Boolean,
    onClick: () -> Unit
) {
    // Rimossa l'animazione di rotazione
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(32.dp)
    ) {
        Icon(
            imageVector = if (isSpeaking) {
                Icons.Filled.StopCircle
            } else {
                Icons.Outlined.PlayCircle
            },
            contentDescription = if (isSpeaking) "Stop" else "Ascolta",
            modifier = Modifier.size(24.dp),
            tint = if (isSpeaking) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
private fun MiniNaturalnessIndicator(
    naturalness: Float,
    modifier: Modifier = Modifier
) {
    val shimmerAnimation = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by shimmerAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "shimmer"
    )

    Box(
        modifier = modifier
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(naturalness)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        startX = shimmerProgress * 1000f - 500f,
                        endX = shimmerProgress * 1000f
                    )
                )
        )
    }
}

@Composable
private fun MessageContent(
    content: String,
    isUser: Boolean,
    isSpeaking: Boolean = false,
    emotion: String = "NEUTRAL"
) {
    val memoizedContent = remember(content) { content }

    // Animazione testo durante parlato
    val textAlpha by animateFloatAsState(
        targetValue = if (isSpeaking) 0.95f else 1f,
        animationSpec = tween(300),
        label = "textAlpha"
    )

    val hasMarkdown = memoizedContent.contains("**") ||
            memoizedContent.contains("*") ||
            memoizedContent.contains("`") ||
            memoizedContent.contains("#") ||
            memoizedContent.contains("```")

    Box(
        modifier = Modifier.alpha(textAlpha)
    ) {
        if (hasMarkdown && !isUser) {
            RichText(
                modifier = Modifier.fillMaxWidth()
            ) {
                Markdown(content = memoizedContent)
            }
        } else {
            Text(
                text = memoizedContent,
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = if (isSpeaking && emotion == "THOUGHTFUL") 0.5.sp else 0.sp
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun StatusIndicator(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (message.status) {
            MessageStatus.SENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.dp,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Invio...",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
            MessageStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = message.error ?: "Invio fallito",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            else -> {}
        }
    }
}

private fun formatTimestamp(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}