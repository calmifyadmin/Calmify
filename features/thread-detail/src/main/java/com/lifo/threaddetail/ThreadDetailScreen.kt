package com.lifo.threaddetail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.socialui.avatar.UserAvatar
import com.lifo.socialui.post.ThreadPostCard
import com.lifo.socialui.post.formatCount
import com.lifo.util.model.RequestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    state: ThreadDetailContract.State,
    onIntent: (ThreadDetailContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onThreadClick: (String) -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thread", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            val mainThread = (state.thread as? RequestState.Success)?.data
            val replyTarget = state.replyingToReply ?: mainThread
            val replyAuthorName = state.replyingToReply?.authorDisplayName
                ?: state.replyingToReply?.authorId
                ?: mainThread?.authorDisplayName
                ?: mainThread?.authorId

            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                ) {
                    // Reply context preview
                    if (replyTarget != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Vertical accent line
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${replyAuthorName ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (replyTarget.text.isNotBlank()) {
                                    Text(
                                        text = replyTarget.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            // Clear reply target (only when replying to a specific reply)
                            if (state.replyingToReply != null) {
                                IconButton(
                                    onClick = { onIntent(ThreadDetailContract.Intent.ClearReplyTarget) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Cancel",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            avatarUrl = null,
                            displayName = "You",
                            size = 28.dp,
                            showBorder = false,
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = state.replyText,
                            onValueChange = { onIntent(ThreadDetailContract.Intent.UpdateReplyText(it)) },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (state.replyingToReply != null)
                                        "Reply to ${state.replyingToReply.authorDisplayName ?: ""}..."
                                    else "Reply..."
                                )
                            },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onIntent(ThreadDetailContract.Intent.SubmitReply) },
                            enabled = state.replyText.isNotBlank() && !state.isSendingReply,
                        ) {
                            if (state.isSendingReply) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = "Send reply",
                                    tint = if (state.replyText.isNotBlank()) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Media attachment stub icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val stubTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Camera", modifier = Modifier.size(18.dp), tint = stubTint)
                        }
                        IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Image, contentDescription = "Image", modifier = Modifier.size(18.dp), tint = stubTint)
                        }
                        IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Gif, contentDescription = "GIF", modifier = Modifier.size(18.dp), tint = stubTint)
                        }
                        IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Mic, contentDescription = "Mic", modifier = Modifier.size(18.dp), tint = stubTint)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        when (val threadState = state.thread) {
            is RequestState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RequestState.Error -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Error loading thread", color = MaterialTheme.colorScheme.error)
                }
            }
            is RequestState.Success -> {
                val thread = threadState.data
                if (thread == null) {
                    Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        Text("Thread not found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // Main thread
                        item {
                            ThreadPostCard(
                                thread = thread,
                                onLike = {
                                    if (thread.isLikedByCurrentUser) {
                                        onIntent(ThreadDetailContract.Intent.UnlikeThread(thread.threadId))
                                    } else {
                                        onIntent(ThreadDetailContract.Intent.LikeThread(thread.threadId))
                                    }
                                },
                                onReply = { /* Focus reply input */ },
                                onRepost = { },
                                onShare = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, "${thread.text}\n\n— shared via Calmify")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onUserClick = { onUserClick(thread.authorId) },
                                onOptions = { },
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }

                        // Sort selector
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.sortOrder == ReplySortOrder.MostPopular,
                                    onClick = { onIntent(ThreadDetailContract.Intent.ChangeSortOrder(ReplySortOrder.MostPopular)) },
                                    label = { Text("Most popular") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                )
                                FilterChip(
                                    selected = state.sortOrder == ReplySortOrder.MostRecent,
                                    onClick = { onIntent(ThreadDetailContract.Intent.ChangeSortOrder(ReplySortOrder.MostRecent)) },
                                    label = { Text("Most recent") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                )
                            }
                        }

                        // Replies header
                        item {
                            Text(
                                text = "Replies",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        // Replies
                        when (val repliesState = state.replies) {
                            is RequestState.Success -> {
                                val replies = repliesState.data
                                if (replies.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                Icons.Outlined.ChatBubbleOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "No replies yet",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Be the first to reply",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                } else {
                                    items(replies, key = { it.threadId }) { reply ->
                                        Column {
                                            // Reply with follow badge overlay on avatar
                                            Box {
                                                ThreadPostCard(
                                                    thread = reply,
                                                    onLike = {
                                                        if (reply.isLikedByCurrentUser) {
                                                            onIntent(ThreadDetailContract.Intent.UnlikeThread(reply.threadId))
                                                        } else {
                                                            onIntent(ThreadDetailContract.Intent.LikeThread(reply.threadId))
                                                        }
                                                    },
                                                    onReply = { onIntent(ThreadDetailContract.Intent.ReplyToReply(reply)) },
                                                    onRepost = { },
                                                    onShare = {
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            putExtra(Intent.EXTRA_TEXT, "${reply.text}\n\n— shared via Calmify")
                                                            type = "text/plain"
                                                        }
                                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                                    },
                                                    onUserClick = { onUserClick(reply.authorId) },
                                                    onOptions = { },
                                                    showThreadLine = reply.replyCount > 0,
                                                )
                                                // Follow badge on reply avatar
                                                Box(
                                                    modifier = Modifier
                                                        .padding(start = 36.dp, top = 36.dp)
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Add,
                                                        contentDescription = "Follow",
                                                        modifier = Modifier.size(8.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }

                                            // Nested replies toggle
                                            if (reply.replyCount > 0) {
                                                val isExpanded = reply.threadId in state.expandedReplies
                                                Text(
                                                    text = if (isExpanded) "Hide replies"
                                                           else "Show ${formatCount(reply.replyCount)} replies",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier
                                                        .padding(start = 56.dp, bottom = 8.dp)
                                                        .clickable {
                                                            onIntent(ThreadDetailContract.Intent.ToggleNestedReplies(reply.threadId))
                                                        }
                                                )
                                                if (isExpanded) {
                                                    val nestedState = state.nestedReplies[reply.threadId]
                                                    when (nestedState) {
                                                        is RequestState.Loading -> {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 56.dp, bottom = 8.dp),
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(20.dp),
                                                                    strokeWidth = 2.dp,
                                                                )
                                                            }
                                                        }
                                                        is RequestState.Success -> {
                                                            nestedState.data.forEach { nested ->
                                                                Row(
                                                                    modifier = Modifier.padding(start = 16.dp),
                                                                ) {
                                                                    // Thread line connecting to parent
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(2.dp)
                                                                            .height(48.dp)
                                                                            .background(
                                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                                                RoundedCornerShape(1.dp)
                                                                            )
                                                                    )
                                                                    Box(modifier = Modifier.weight(1f)) {
                                                                        ThreadPostCard(
                                                                            thread = nested,
                                                                            onLike = {
                                                                                if (nested.isLikedByCurrentUser) {
                                                                                    onIntent(ThreadDetailContract.Intent.UnlikeThread(nested.threadId))
                                                                                } else {
                                                                                    onIntent(ThreadDetailContract.Intent.LikeThread(nested.threadId))
                                                                                }
                                                                            },
                                                                            onReply = { onIntent(ThreadDetailContract.Intent.ReplyToReply(nested)) },
                                                                            onRepost = { },
                                                                            onShare = {
                                                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                                                    putExtra(Intent.EXTRA_TEXT, "${nested.text}\n\n— shared via Calmify")
                                                                                    type = "text/plain"
                                                                                }
                                                                                context.startActivity(Intent.createChooser(sendIntent, null))
                                                                            },
                                                                            onUserClick = { onUserClick(nested.authorId) },
                                                                            onOptions = { },
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        is RequestState.Error -> {
                                                            Text(
                                                                text = "Failed to load replies",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
                                                            )
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp)
                                    }
                                }
                            }
                            is RequestState.Loading -> {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                            is RequestState.Error -> {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            "Failed to load replies",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Tap to retry",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                onIntent(ThreadDetailContract.Intent.LoadThread(
                                                    (state.thread as? RequestState.Success)?.data?.threadId ?: ""
                                                ))
                                            }
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
