package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.lifo.chat.domain.model.ChatMessage
import com.lifo.chat.domain.model.MessageStatus
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.math.abs

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }

    // Animations
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
        }
    )

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (message.status == MessageStatus.STREAMING) 0.9f else 1f,
        animationSpec = tween(300)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI Avatar with animation
            AvatarBubble()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isUser) 16.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 16.dp
                ),
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showMenu = !showMenu }
                    .graphicsLayer { alpha = bubbleAlpha }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    when {
                        message.status == MessageStatus.STREAMING && message.content.isEmpty() -> {
                            StreamingIndicator()
                        }
                        message.content.isNotEmpty() -> {
                            MessageContent(
                                content = message.content,
                                isUser = message.isUser
                            )
                        }
                    }

                    // Status indicators with smooth transitions
                    AnimatedVisibility(
                        visible = message.status != MessageStatus.SENT || message.error != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        StatusIndicator(message)
                    }
                }
            }

            // Animated timestamp
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 200)),
                exit = fadeOut()
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Context menu with animations
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCopy()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    }
                )

                if (message.isUser && message.status == MessageStatus.FAILED) {
                    DropdownMenuItem(
                        text = { Text("Retry") },
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
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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

        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AvatarBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    val avatarScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarScale"
    )

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = avatarScale
                scaleY = avatarScale
            }
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "AI",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MessageContent(
    content: String,
    isUser: Boolean
) {
    // Check if content contains markdown elements
    val hasMarkdown = content.contains("**") ||
            content.contains("*") ||
            content.contains("`") ||
            content.contains("#") ||
            content.contains("```")

    if (hasMarkdown && !isUser) {
        RichText(
            modifier = Modifier.fillMaxWidth()
        ) {
            Markdown(content = content)
        }
    } else {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StreamingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "streaming")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0.3f at 0 + (index * 100)
                        1f at 200 + (index * 100)
                        0.3f at 400 + (index * 100)
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "DotAlpha$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
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
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sending...",
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
                    text = message.error ?: "Failed to send",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MessageStatus.STREAMING -> {
                // Already handled in main content
            }
            else -> {}
        }
    }
}

private fun formatTimestamp(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}