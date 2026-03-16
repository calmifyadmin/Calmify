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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
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
import com.lifo.socialui.avatar.UserAvatar
import com.lifo.util.repository.SocialMessagingRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Chat room screen for a specific conversation.
 *
 * Features Threads-like message grouping, adaptive bubble corners,
 * animated typing dots, attachment/camera icons, and compact read indicators.
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
    // Build grouped message list items (date separators + grouped messages)
    val groupedItems = remember(messages) {
        buildGroupedMessageListItems(messages, currentUserId)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        // Typing indicator at the top (bottom visually due to reverseLayout)
        if (typingUsers.isNotEmpty()) {
            item(key = "typing_indicator") {
                TypingDotsIndicator(
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
        }

        items(
            items = groupedItems,
            key = { item ->
                when (item) {
                    is GroupedMessageListItem.MessageItem -> item.message.id.ifEmpty {
                        "msg_${item.message.createdAt}_${item.message.senderId}"
                    }
                    is GroupedMessageListItem.DateSeparator -> "sep_${item.dateLabel}"
                }
            }
        ) { item ->
            when (item) {
                is GroupedMessageListItem.DateSeparator -> {
                    DateSeparatorRow(label = item.dateLabel)
                }
                is GroupedMessageListItem.MessageItem -> {
                    val onUserClickStable = remember(item.message.senderId) {
                        { onUserClick(item.message.senderId) }
                    }
                    GroupedMessageBubble(
                        message = item.message,
                        isMine = item.isMine,
                        groupPosition = item.groupPosition,
                        onUserClick = onUserClickStable
                    )
                }
            }
        }
    }
}

/**
 * Position of a message within a consecutive sender group.
 */
enum class GroupPosition {
    SOLO,  // Only message in group
    FIRST, // First message in group (shows avatar)
    MIDDLE,
    LAST   // Last message in group (shows timestamp)
}

@Composable
private fun GroupedMessageBubble(
    message: SocialMessagingRepository.Message,
    isMine: Boolean,
    groupPosition: GroupPosition,
    onUserClick: () -> Unit,
) {
    // Threads-like adaptive corners
    val bubbleShape = when {
        groupPosition == GroupPosition.SOLO -> RoundedCornerShape(16.dp)
        isMine -> when (groupPosition) {
            GroupPosition.FIRST -> RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            GroupPosition.MIDDLE -> RoundedCornerShape(16.dp, 4.dp, 4.dp, 16.dp)
            GroupPosition.LAST -> RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
            else -> RoundedCornerShape(16.dp)
        }
        else -> when (groupPosition) {
            GroupPosition.FIRST -> RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            GroupPosition.MIDDLE -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 4.dp)
            GroupPosition.LAST -> RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
            else -> RoundedCornerShape(16.dp)
        }
    }

    // Spacing: tighter within groups, wider between groups
    val topPadding = when (groupPosition) {
        GroupPosition.FIRST, GroupPosition.SOLO -> 12.dp
        else -> 2.dp // Tight 4dp total (2dp top + 2dp bottom of previous)
    }
    val bottomPadding = when (groupPosition) {
        GroupPosition.LAST, GroupPosition.SOLO -> 0.dp // next item adds its own top padding
        else -> 2.dp
    }

    val showAvatar = !isMine && (groupPosition == GroupPosition.FIRST || groupPosition == GroupPosition.SOLO)
    val showTimestamp = groupPosition == GroupPosition.LAST || groupPosition == GroupPosition.SOLO

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            if (showAvatar) {
                // Avatar for first message in group
                UserAvatar(
                    avatarUrl = null,
                    displayName = message.senderId,
                    size = 28.dp,
                    showBorder = false,
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .clickable(onClick = onUserClick)
                )
            } else {
                // Placeholder spacer to keep alignment
                Spacer(modifier = Modifier.width(28.dp))
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

                // Only show timestamp on last message of group
                if (showTimestamp) {
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
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = if (message.isRead) "Read" else "Sent",
                                modifier = Modifier.size(12.dp),
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

/**
 * Animated three dots typing indicator (Threads-style).
 */
@Composable
private fun TypingDotsIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    // Three dots with staggered animations
    val dot1Alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot1Alpha.value)
                )
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot2Alpha.value)
                )
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dot3Alpha.value)
                )
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
        // Attachment icon
        IconButton(
            onClick = { /* TODO: attachment picker */ },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = "Attach file",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // Camera icon
        IconButton(
            onClick = { /* TODO: camera capture */ },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CameraAlt,
                contentDescription = "Camera",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Text input
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

        // Send button
        val canSend = draftText.isNotBlank() && !isSending
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(44.dp)
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
 * Represents an item in the grouped message list.
 */
private sealed interface GroupedMessageListItem {
    data class MessageItem(
        val message: SocialMessagingRepository.Message,
        val isMine: Boolean,
        val groupPosition: GroupPosition
    ) : GroupedMessageListItem

    data class DateSeparator(val dateLabel: String) : GroupedMessageListItem
}

/**
 * Builds a list of grouped message items with date separators.
 * Messages from the same sender are grouped together with [GroupPosition] tags
 * for adaptive corner rendering.
 */
private fun buildGroupedMessageListItems(
    messages: List<SocialMessagingRepository.Message>,
    currentUserId: String,
): List<GroupedMessageListItem> {
    if (messages.isEmpty()) return emptyList()

    // Sort newest first (for reversed LazyColumn, item 0 = newest = bottom)
    val sorted = messages.sortedByDescending { it.createdAt }
    val result = mutableListOf<GroupedMessageListItem>()
    var lastDateLabel: String? = null

    // First pass: create flat list with date separators
    data class TaggedMessage(
        val message: SocialMessagingRepository.Message,
        val isMine: Boolean,
    )

    val taggedMessages = mutableListOf<Any>() // TaggedMessage | String (date separator)

    for (message in sorted) {
        val dateLabel = formatDateLabel(message.createdAt)
        taggedMessages.add(TaggedMessage(message, message.senderId == currentUserId))
        if (dateLabel != lastDateLabel) {
            taggedMessages.add(dateLabel)
            lastDateLabel = dateLabel
        }
    }

    // Second pass: compute group positions
    // In the reversed list, "next" in visual order is the previous index
    val items = taggedMessages.filterIsInstance<TaggedMessage>()
    val positionMap = mutableMapOf<SocialMessagingRepository.Message, GroupPosition>()

    for (i in items.indices) {
        val current = items[i]
        val prev = items.getOrNull(i - 1) // visually below (newer)
        val next = items.getOrNull(i + 1) // visually above (older)

        val sameSenderAsPrev = prev != null && prev.message.senderId == current.message.senderId
        val sameSenderAsNext = next != null && next.message.senderId == current.message.senderId

        // Check date boundaries - don't group across date separators
        val prevDateLabel = prev?.let { formatDateLabel(it.message.createdAt) }
        val currentDateLabel = formatDateLabel(current.message.createdAt)
        val nextDateLabel = next?.let { formatDateLabel(it.message.createdAt) }

        val groupedWithPrev = sameSenderAsPrev && prevDateLabel == currentDateLabel
        val groupedWithNext = sameSenderAsNext && nextDateLabel == currentDateLabel

        val position = when {
            groupedWithPrev && groupedWithNext -> GroupPosition.MIDDLE
            groupedWithPrev && !groupedWithNext -> GroupPosition.LAST // visually, last going up
            !groupedWithPrev && groupedWithNext -> GroupPosition.FIRST // visually, first going up
            else -> GroupPosition.SOLO
        }
        positionMap[current.message] = position
    }

    // Final pass: build result list
    lastDateLabel = null
    for (message in sorted) {
        val dateLabel = formatDateLabel(message.createdAt)
        val isMine = message.senderId == currentUserId
        val position = positionMap[message] ?: GroupPosition.SOLO

        result.add(
            GroupedMessageListItem.MessageItem(
                message = message,
                isMine = isMine,
                groupPosition = position
            )
        )
        if (dateLabel != lastDateLabel) {
            result.add(GroupedMessageListItem.DateSeparator(dateLabel))
            lastDateLabel = dateLabel
        }
    }

    return result
}

private fun formatDateLabel(timestampMs: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val messageDate = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date

    if (messageDate == today) return "Today"

    val yesterday = Instant.fromEpochMilliseconds(
        Clock.System.now().toEpochMilliseconds() - 86_400_000L
    ).toLocalDateTime(tz).date
    if (messageDate == yesterday) return "Yesterday"

    val month = messageDate.month.name.lowercase()
        .replaceFirstChar { it.uppercase() }.take(3)
    return "$month ${messageDate.dayOfMonth}, ${messageDate.year}"
}

private fun formatMessageTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    val tz = TimeZone.currentSystemDefault()
    val localTime = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(tz)
    val hour = localTime.hour.toString().padStart(2, '0')
    val minute = localTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
