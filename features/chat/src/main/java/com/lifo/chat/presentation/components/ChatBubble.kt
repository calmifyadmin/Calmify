package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
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

    // Animations con chiave stabile
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

    // STILE GEMINI: AI senza bubble, USER con bubble
    if (!message.isUser) {
        // AI MESSAGE - Stile Gemini (no background, full width)
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
                }
                .clickable { showMenu = !showMenu }
        ) {
            // AI Avatar
            AvatarBubble()

            Spacer(modifier = Modifier.width(12.dp))

            // Content - occupa tutto lo spazio disponibile
            Column(modifier = Modifier.weight(1f)) {
                // AI Name
                Text(
                    text = "Lifo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Message content
                MessageContent(
                    content = message.content,
                    isUser = false
                )

                // Status indicators
                AnimatedVisibility(
                    visible = message.status != MessageStatus.SENT || message.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    StatusIndicator(message)
                }
            }
        }

        // Context menu
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
    } else {
        // USER MESSAGE - Con bubble
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp) // Stesso padding dell'AI
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
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    ),
                    color = MaterialTheme.colorScheme.primary,
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
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Content
                        MessageContent(
                            content = message.content,
                            isUser = true
                        )

                        // Status indicators
                        AnimatedVisibility(
                            visible = message.status != MessageStatus.SENT || message.error != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            StatusIndicator(message)
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
        }
    }
}

@Composable
private fun AvatarBubble() {
    // RIMOSSO: Animazione pulsante dell'avatar
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(32.dp)
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
    // Memorizza content per evitare re-parsing
    val memoizedContent = remember(content) { content }

    // Check if content contains markdown elements
    val hasMarkdown = memoizedContent.contains("**") ||
            memoizedContent.contains("*") ||
            memoizedContent.contains("`") ||
            memoizedContent.contains("#") ||
            memoizedContent.contains("```")

    if (hasMarkdown && !isUser) {
        RichText(
            modifier = Modifier.fillMaxWidth()
        ) {
            Markdown(content = memoizedContent)
        }
    } else {
        Text(
            text = memoizedContent,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface // Testo normale per AI
            }
        )
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
            else -> {}
        }
    }
}

private fun formatTimestamp(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}