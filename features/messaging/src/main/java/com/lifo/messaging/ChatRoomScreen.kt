package com.lifo.messaging

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.util.repository.SocialMessagingRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Chat room screen for a specific conversation.
 *
 * Messages from the current user are right-aligned with primary colour bubbles;
 * messages from others are left-aligned with surface-variant bubbles. Includes
 * date separators, read indicators, a typing animation, and a bottom input bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    state: MessagingContract.State,
    currentUserId: String,
    onIntent: (MessagingContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive or after sending
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            ChatRoomTopBar(
                conversationId = state.currentConversationId.orEmpty(),
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            ChatInputBar(
                draftText = state.draftText,
                isSending = state.isSending,
                onTextChange = { text ->
                    onIntent(MessagingContract.Intent.UpdateDraft(text))
                    onIntent(MessagingContract.Intent.SetTyping(text.isNotEmpty()))
                },
                onSend = {
                    onIntent(MessagingContract.Intent.SendMessage(state.draftText))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            when {
                state.isLoadingMessages && state.messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    MessageList(
                        messages = state.messages,
                        currentUserId = currentUserId,
                        typingUsers = state.typingUsers,
                        listState = listState,
                        onUserClick = onUserClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomTopBar(
    conversationId: String,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = conversationId.take(12).ifEmpty { "Chat" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun MessageList(
    messages: List<SocialMessagingRepository.Message>,
    currentUserId: String,
    typingUsers: List<String>,
    listState: LazyListState,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Group messages with date separators
    val messagesWithSeparators = remember(messages) {
        buildMessageListItems(messages)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Typing indicator at the top (which is bottom visually due to reverseLayout)
        if (typingUsers.isNotEmpty()) {
            item(key = "typing_indicator") {
                TypingIndicator(
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
        }

        items(
            items = messagesWithSeparators,
            key = { item ->
                when (item) {
                    is MessageListItem.MessageItem -> item.message.id.ifEmpty {
                        "msg_${item.message.createdAt}_${item.message.senderId}"
                    }
                    is MessageListItem.DateSeparator -> "sep_${item.dateLabel}"
                }
            }
        ) { item ->
            when (item) {
                is MessageListItem.DateSeparator -> {
                    DateSeparatorRow(label = item.dateLabel)
                }
                is MessageListItem.MessageItem -> {
                    val isMine = item.message.senderId == currentUserId
                    val onUserClickStable = remember(item.message.senderId) {
                        { onUserClick(item.message.senderId) }
                    }
                    MessageBubble(
                        message = item.message,
                        isMine = isMine,
                        onUserClick = onUserClickStable
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: SocialMessagingRepository.Message,
    isMine: Boolean,
    onUserClick: () -> Unit,
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMine) 16.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 16.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            // Avatar for received messages
            val senderInitial = message.senderId.firstOrNull()?.uppercaseChar() ?: '?'
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = onUserClick)
                    .align(Alignment.Bottom),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = senderInitial.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isMine)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formatMessageTimestamp(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMine)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = if (message.isRead) "Read" else "Sent",
                            modifier = Modifier.size(14.dp),
                            tint = if (message.isRead)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSeparatorRow(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing_alpha"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Someone is typing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = "...",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha.value)
        )
    }
}

@Composable
private fun ChatInputBar(
    draftText: String,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = draftText,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = "Type a message...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            singleLine = false,
            maxLines = 4
        )

        Spacer(modifier = Modifier.width(8.dp))

        val canSend = draftText.isNotBlank() && !isSending
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// -- Helper models & formatters -----------------------------------------------

/**
 * Represents an item in the message list: either a message or a date separator.
 */
private sealed interface MessageListItem {
    data class MessageItem(val message: SocialMessagingRepository.Message) : MessageListItem
    data class DateSeparator(val dateLabel: String) : MessageListItem
}

/**
 * Builds a list interleaving [MessageListItem.DateSeparator] entries between
 * messages from different days. Because the LazyColumn uses `reverseLayout`,
 * messages are ordered newest-first.
 */
private fun buildMessageListItems(
    messages: List<SocialMessagingRepository.Message>
): List<MessageListItem> {
    if (messages.isEmpty()) return emptyList()

    // Sort newest first (for reversed LazyColumn, item 0 = newest = bottom)
    val sorted = messages.sortedByDescending { it.createdAt }
    val result = mutableListOf<MessageListItem>()
    var lastDateLabel: String? = null

    for (message in sorted) {
        val dateLabel = formatDateLabel(message.createdAt)
        result.add(MessageListItem.MessageItem(message))
        if (dateLabel != lastDateLabel) {
            result.add(MessageListItem.DateSeparator(dateLabel))
            lastDateLabel = dateLabel
        }
    }

    return result
}

private fun formatDateLabel(timestampMs: Long): String {
    val messageCalendar = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val todayCalendar = Calendar.getInstance()

    val isSameDay = messageCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
            messageCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)

    if (isSameDay) return "Today"

    todayCalendar.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = messageCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
            messageCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) return "Yesterday"

    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return format.format(Date(timestampMs))
}

private fun formatMessageTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(timestampMs))
}
