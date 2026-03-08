package com.lifo.feed

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.socialui.thread.ThreadOptionsSheet
import com.lifo.socialui.post.ThreadPostCard
import com.lifo.socialui.animation.ThreadPostCardSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedContract.State,
    onIntent: (FeedContract.Intent) -> Unit,
    onThreadClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onComposeClick: () -> Unit,
    onReplyClick: (threadId: String, authorName: String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onMessagingClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Hide tabs when user has scrolled past the first few items
    val showTabs by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex < 2
        }
    }

    // Client-side category + hidden filter
    val filteredThreads by remember(state.threads, state.selectedTab, state.hiddenThreadIds) {
        derivedStateOf {
            val visible = state.threads.filter { it.threadId !in state.hiddenThreadIds }
            when (state.selectedTab) {
                FeedContract.FeedTab.ALL -> visible
                FeedContract.FeedTab.SCOPERTE -> visible.filter { it.postCategory == "scoperta" }
                FeedContract.FeedTab.SFIDE -> visible.filter { it.postCategory == "sfida" }
                FeedContract.FeedTab.DOMANDE -> visible.filter { it.postCategory == "domanda" }
            }
        }
    }

    // Infinite scroll detection
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

    // Options bottom sheet
    val optionsThreadId = state.showOptionsForThreadId
    if (optionsThreadId != null) {
        ThreadOptionsSheet(
            onDismiss = { onIntent(FeedContract.Intent.DismissOptions) },
            onSave = { onIntent(FeedContract.Intent.SaveThread(optionsThreadId)) },
            onHide = { onIntent(FeedContract.Intent.HideThread(optionsThreadId)) },
            onMute = { onIntent(FeedContract.Intent.MuteUser(optionsThreadId)) },
            onBlock = { onIntent(FeedContract.Intent.BlockUser(optionsThreadId)) },
            onReport = { onIntent(FeedContract.Intent.ReportThread(optionsThreadId)) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Community",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNotificationsClick) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onMessagingClick) {
                        Icon(Icons.Default.MailOutline, contentDescription = "Messages")
                    }
                },
            )
        },
        // FAB removed — handled by global contextual FAB in DecomposeApp
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category tabs — hidden during scroll
            AnimatedVisibility(
                visible = showTabs,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp,
            ) {
                FeedContract.FeedTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { onIntent(FeedContract.Intent.SelectTab(tab)) },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
            } // end AnimatedVisibility

            // Feed Content
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(FeedContract.Intent.RefreshFeed) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    // Initial loading — shimmer skeletons
                    state.isLoading && state.threads.isEmpty() -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(6) {
                                ThreadPostCardSkeleton()
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }

                    // Error with no data
                    state.error != null && state.threads.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Qualcosa e' andato storto",
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
                    !state.isLoading && filteredThreads.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Nessun post ancora",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (state.selectedTab) {
                                        FeedContract.FeedTab.SCOPERTE -> "Condividi una scoperta per iniziare!"
                                        FeedContract.FeedTab.SFIDE -> "Racconta una sfida che stai affrontando"
                                        FeedContract.FeedTab.DOMANDE -> "Fai una domanda alla community"
                                        else -> "Torna piu' tardi per contenuti personalizzati"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Threads feed
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(
                                items = filteredThreads,
                                key = { _, thread -> thread.threadId },
                            ) { index, thread ->
                                ThreadPostCard(
                                    thread = thread,
                                    showThreadLine = false,
                                    index = index,
                                    onThreadClick = { onThreadClick(thread.threadId) },
                                    onUserClick = { onUserClick(thread.authorId) },
                                    onLike = {
                                        if (thread.isLikedByCurrentUser) {
                                            onIntent(FeedContract.Intent.UnlikeThread(thread.threadId))
                                        } else {
                                            onIntent(FeedContract.Intent.LikeThread(thread.threadId))
                                        }
                                    },
                                    onReply = {
                                        onReplyClick(
                                            thread.threadId,
                                            thread.authorDisplayName ?: thread.authorUsername ?: thread.authorId
                                        )
                                    },
                                    onRepost = { onIntent(FeedContract.Intent.RepostThread(thread.threadId)) },
                                    onShare = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "${thread.text}\n\n\u2014 shared via Calmify"
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    },
                                    onOptions = { onIntent(FeedContract.Intent.ShowOptions(thread.threadId)) },
                                )

                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
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
