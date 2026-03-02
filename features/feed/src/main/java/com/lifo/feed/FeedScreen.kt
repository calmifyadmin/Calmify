package com.lifo.feed

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.util.repository.ThreadRepository
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedContract.State,
    onIntent: (FeedContract.Intent) -> Unit,
    onThreadClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onComposeClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onMessagingClick: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Detect when the user scrolls near the bottom to trigger load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && state.hasMore && !state.isLoadingMore && !state.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onIntent(FeedContract.Intent.LoadMore)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Feed",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                        )
                    }
                    IconButton(onClick = onNotificationsClick) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                        )
                    }
                    IconButton(onClick = onMessagingClick) {
                        Icon(
                            imageVector = Icons.Default.MailOutline,
                            contentDescription = "Messages",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onComposeClick,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Compose new post",
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = state.selectedTab == FeedContract.FeedTab.FOR_YOU,
                    onClick = { onIntent(FeedContract.Intent.SelectTab(FeedContract.FeedTab.FOR_YOU)) },
                    text = {
                        Text(
                            text = "For You",
                            fontWeight = if (state.selectedTab == FeedContract.FeedTab.FOR_YOU)
                                FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                )
                Tab(
                    selected = state.selectedTab == FeedContract.FeedTab.FOLLOWING,
                    onClick = { onIntent(FeedContract.Intent.SelectTab(FeedContract.FeedTab.FOLLOWING)) },
                    text = {
                        Text(
                            text = "Following",
                            fontWeight = if (state.selectedTab == FeedContract.FeedTab.FOLLOWING)
                                FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                )
            }

            // Feed Content with Pull-to-Refresh
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(FeedContract.Intent.RefreshFeed) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // Initial loading state
                    state.isLoading && state.threads.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // Error state with no data
                    state.error != null && state.threads.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Something went wrong",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = state.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Empty state
                    !state.isLoading && state.threads.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No posts yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (state.selectedTab == FeedContract.FeedTab.FOLLOWING)
                                        "Follow others to see their posts here"
                                    else
                                        "Check back later for personalized content",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Threads list
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = state.threads,
                                key = { it.threadId },
                            ) { thread ->
                                val onThreadClickStable = remember(thread.threadId) {
                                    { onThreadClick(thread.threadId) }
                                }
                                val onUserClickStable = remember(thread.authorId) {
                                    { onUserClick(thread.authorId) }
                                }
                                val onLikeClickStable = remember(thread.threadId) {
                                    { onIntent(FeedContract.Intent.LikeThread(thread.threadId)) }
                                }
                                val onUnlikeClickStable = remember(thread.threadId) {
                                    { onIntent(FeedContract.Intent.UnlikeThread(thread.threadId)) }
                                }
                                ThreadCard(
                                    thread = thread,
                                    onThreadClick = onThreadClickStable,
                                    onUserClick = onUserClickStable,
                                    onLikeClick = onLikeClickStable,
                                    onUnlikeClick = onUnlikeClickStable,
                                )
                            }

                            // Loading more indicator
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(
    thread: ThreadRepository.Thread,
    onThreadClick: () -> Unit,
    onUserClick: () -> Unit,
    onLikeClick: () -> Unit,
    onUnlikeClick: () -> Unit,
) {
    // Simple liked tracking per card instance for toggle behavior
    var isLiked by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onThreadClick)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thread.authorId.take(8),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onUserClick),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatRelativeTime(thread.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Thread text
            Text(
                text = thread.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )

            // Mood tag chip
            val moodTag = thread.moodTag
            if (!moodTag.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            text = moodTag,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SentimentSatisfied,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(4.dp))

            // Actions row: likes, replies
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Like button
                IconButton(
                    onClick = {
                        if (isLiked) {
                            isLiked = false
                            onUnlikeClick()
                        } else {
                            isLiked = true
                            onLikeClick()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = formatCount(thread.likeCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Reply count
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "Replies",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatCount(thread.replyCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Journal badge
                if (thread.isFromJournal) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "from journal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

/**
 * Formats a count value for display (e.g. 1234 -> "1.2K").
 */
private fun formatCount(count: Long): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> String.format("%.1fK", count / 1_000.0)
    else -> String.format("%.1fM", count / 1_000_000.0)
}

/**
 * Formats a Unix timestamp (milliseconds) into a human-readable relative time string.
 */
private fun formatRelativeTime(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis

    if (diff < 0) return "just now"

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        seconds < 60 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else -> "${days / 365}y ago"
    }
}
