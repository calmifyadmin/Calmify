package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.chat.domain.model.ChatMessage
import com.lifo.chat.domain.model.MessageStatus
import java.time.format.DateTimeFormatter
import java.time.ZoneId

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (message.status == MessageStatus.STREAMING) 0.8f else 1f,
        animationSpec = tween(300),
        label = "BubbleAlpha"
    )

    var showMenu by remember { mutableStateOf(false) }

    // DEBUG: Log message content
    LaunchedEffect(message) {
        println("ChatBubble - Rendering message: ${message.id}, isUser: ${message.isUser}, content: '${message.content}', status: ${message.status}")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI Avatar
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Bottom)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = "AI",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
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
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showMenu = !showMenu }
                    .graphicsLayer { alpha = animatedAlpha }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // SUPER SIMPLE TEXT RENDERING FOR DEBUG
                    when {
                        message.status == MessageStatus.STREAMING && message.content.isEmpty() -> {
                            TypingIndicator()
                        }
                        message.content.isNotEmpty() -> {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (message.isUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        else -> {
                            // DEBUG: Show placeholder if content is empty
                            Text(
                                text = "[Empty message]",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                color = if (message.isUser) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        }
                    }

                    // Status indicators
                    AnimatedVisibility(
                        visible = message.status != MessageStatus.SENT || message.error != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
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
                                        strokeWidth = 1.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sending...",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                MessageStatus.FAILED -> {
                                    Icon(
                                        imageVector = Icons.Outlined.Warning,
                                        contentDescription = "Failed",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = message.error ?: "Failed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                MessageStatus.STREAMING -> {
                                    StreamingIndicator()
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
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
                            onRetry()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDelete()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
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
private fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.3f at 0 + (index * 100)
                        1f at 300 + (index * 100)
                        0.3f at 600 + (index * 100)
                    }
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
private fun StreamingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StreamAlpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private fun formatTimestamp(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}