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
import androidx.compose.material.icons.filled.Check
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
    val colorScheme = MaterialTheme.colorScheme

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
                    containerColor = colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            val mainThread = (state.thread as? RequestState.Success)?.data
            val replyTarget = state.replyingToReply ?: mainThread
            val replyAuthorName = state.replyingToReply?.authorDisplayName
                ?: state.replyingToReply?.authorId
                ?: mainThread?.authorDisplayName
                ?: mainThread?.authorId

            // Reply input bar — M3 Expressive
            Surface(
                color = colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    // Reply context preview
                    if (replyTarget != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Vertical accent line
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .background(colorScheme.primary, RoundedCornerShape(2.dp)),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Rispondendo a ${replyAuthorName ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (replyTarget.text.isNotBlank()) {
                                        Text(
                                            text = replyTarget.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (state.replyingToReply != null) {
                                    IconButton(
                                        onClick = { onIntent(ThreadDetailContract.Intent.ClearReplyTarget) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Cancel",
                                            modifier = Modifier.size(16.dp),
                                            tint = colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UserAvatar(
                            avatarUrl = null,
                            displayName = "You",
                            size = 32.dp,
                            showBorder = false,
                        )
                        OutlinedTextField(
                            value = state.replyText,
                            onValueChange = { onIntent(ThreadDetailContract.Intent.UpdateReplyText(it)) },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (state.replyingToReply != null)
                                        "Rispondi a ${state.replyingToReply.authorDisplayName ?: ""}..."
                                    else "Scrivi una risposta...",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.5f),
                                focusedContainerColor = colorScheme.surfaceContainerLowest,
                                unfocusedContainerColor = colorScheme.surfaceContainerLowest,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        )
                        // Send button — pill
                        Surface(
                            onClick = { onIntent(ThreadDetailContract.Intent.SubmitReply) },
                            enabled = state.replyText.isNotBlank() && !state.isSendingReply,
                            shape = RoundedCornerShape(16.dp),
                            color = if (state.replyText.isNotBlank()) colorScheme.primary
                                    else colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                if (state.isSendingReply) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = "Send",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (state.replyText.isNotBlank()) colorScheme.onPrimary
                                               else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    }

                    // Media attachment icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, top = 4.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val stubTint = colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        listOf(
                            Icons.Outlined.CameraAlt to "Camera",
                            Icons.Outlined.Image to "Image",
                            Icons.Outlined.Gif to "GIF",
                            Icons.Outlined.Mic to "Mic",
                        ).forEach { (icon, desc) ->
                            IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                                Icon(icon, contentDescription = desc, modifier = Modifier.size(18.dp), tint = stubTint)
                            }
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        when (val threadState = state.thread) {
            is RequestState.Loading -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RequestState.Error -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Errore nel caricamento", color = colorScheme.error)
                }
            }
            is RequestState.Success -> {
                val thread = threadState.data
                if (thread == null) {
                    Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        Text("Thread non trovato")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    ) {
                        // Main thread
                        item {
                            ThreadPostCard(
                                thread = thread,
                                onLike = {
                                    if (thread.isLikedByCurrentUser)
                                        onIntent(ThreadDetailContract.Intent.UnlikeThread(thread.threadId))
                                    else
                                        onIntent(ThreadDetailContract.Intent.LikeThread(thread.threadId))
                                },
                                onReply = { },
                                onRepost = { },
                                onShare = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, "${thread.text}\n\n-- shared via Calmify")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onUserClick = { onUserClick(thread.authorId) },
                                onOptions = { },
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        // Sort selector — M3 Expressive chips
                        item {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    ReplySortOrder.MostPopular to "Popolari",
                                    ReplySortOrder.MostRecent to "Recenti",
                                ).forEach { (order, label) ->
                                    FilterChip(
                                        selected = state.sortOrder == order,
                                        onClick = { onIntent(ThreadDetailContract.Intent.ChangeSortOrder(order)) },
                                        label = { Text(label) },
                                        leadingIcon = if (state.sortOrder == order) {
                                            {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.height(36.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.primaryContainer,
                                            selectedLabelColor = colorScheme.onPrimaryContainer,
                                            selectedLeadingIconColor = colorScheme.onPrimaryContainer,
                                            containerColor = colorScheme.surfaceContainerLow,
                                        ),
                                    )
                                }
                            }
                        }

                        // Replies header
                        item {
                            Text(
                                text = "Risposte",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }

                        // Replies
                        when (val repliesState = state.replies) {
                            is RequestState.Success -> {
                                val replies = repliesState.data
                                if (replies.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(24.dp),
                                                color = colorScheme.surfaceContainerLow,
                                                modifier = Modifier.size(80.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Outlined.ChatBubbleOutline,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(36.dp),
                                                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                "Nessuna risposta",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.onSurface,
                                            )
                                            Text(
                                                "Sii il primo a rispondere",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                } else {
                                    items(replies, key = { it.threadId }) { reply ->
                                        Column {
                                            Box {
                                                ThreadPostCard(
                                                    thread = reply,
                                                    onLike = {
                                                        if (reply.isLikedByCurrentUser)
                                                            onIntent(ThreadDetailContract.Intent.UnlikeThread(reply.threadId))
                                                        else
                                                            onIntent(ThreadDetailContract.Intent.LikeThread(reply.threadId))
                                                    },
                                                    onReply = { onIntent(ThreadDetailContract.Intent.ReplyToReply(reply)) },
                                                    onRepost = { },
                                                    onShare = {
                                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                            putExtra(Intent.EXTRA_TEXT, "${reply.text}\n\n-- shared via Calmify")
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
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(colorScheme.primary),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Add,
                                                        contentDescription = "Follow",
                                                        modifier = Modifier.size(9.dp),
                                                        tint = colorScheme.onPrimary,
                                                    )
                                                }
                                            }

                                            // Nested replies toggle
                                            if (reply.replyCount > 0) {
                                                val isExpanded = reply.threadId in state.expandedReplies
                                                Surface(
                                                    onClick = { onIntent(ThreadDetailContract.Intent.ToggleNestedReplies(reply.threadId)) },
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = colorScheme.surfaceContainerLow,
                                                    modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
                                                ) {
                                                    Text(
                                                        text = if (isExpanded) "Nascondi risposte"
                                                               else "Mostra ${formatCount(reply.replyCount)} risposte",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                    )
                                                }
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
                                                                Row(modifier = Modifier.padding(start = 20.dp)) {
                                                                    // Thread connector line
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .width(2.dp)
                                                                            .height(48.dp)
                                                                            .background(
                                                                                colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                                                RoundedCornerShape(1.dp),
                                                                            ),
                                                                    )
                                                                    Box(modifier = Modifier.weight(1f)) {
                                                                        ThreadPostCard(
                                                                            thread = nested,
                                                                            onLike = {
                                                                                if (nested.isLikedByCurrentUser)
                                                                                    onIntent(ThreadDetailContract.Intent.UnlikeThread(nested.threadId))
                                                                                else
                                                                                    onIntent(ThreadDetailContract.Intent.LikeThread(nested.threadId))
                                                                            },
                                                                            onReply = { onIntent(ThreadDetailContract.Intent.ReplyToReply(nested)) },
                                                                            onRepost = { },
                                                                            onShare = {
                                                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                                                    putExtra(Intent.EXTRA_TEXT, "${nested.text}\n\n-- shared via Calmify")
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
                                                                text = "Errore nel caricamento",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = colorScheme.error,
                                                                modifier = Modifier.padding(start = 56.dp, bottom = 8.dp),
                                                            )
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
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
                                            "Errore nel caricamento risposte",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.error,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            onClick = {
                                                onIntent(ThreadDetailContract.Intent.LoadThread(
                                                    (state.thread as? RequestState.Success)?.data?.threadId ?: ""
                                                ))
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            color = colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                "Riprova",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            )
                                        }
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
