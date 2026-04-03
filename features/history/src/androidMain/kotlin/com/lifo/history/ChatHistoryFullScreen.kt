package com.lifo.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lifo.ui.components.CalmifyTopBar
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.model.HomeContentItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full screen showing complete chat history
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatHistoryFullScreen(
    onBackClick: () -> Unit,
    onChatClick: (HomeContentItem.ChatItem) -> Unit,
    viewModel: HistoryViewModel = koinViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val chatHistoryFlow = remember(searchQuery) {
        viewModel.loadFullChatHistory(searchQuery)
    }
    val chatHistory by chatHistoryFlow.collectAsState(initial = emptyList())

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CalmifyTopBar(
                title = "Chat History",
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (chatHistory.isEmpty() && searchQuery.isBlank()) {
                EmptyHistoryContent(
                    message = "No chat conversations yet",
                    modifier = Modifier.padding(paddingValues)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 80.dp, // Space for search bar
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chatHistory.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            EmptySearchResults()
                        }
                    } else {
                        items(
                            items = chatHistory,
                            key = { "chat-full-${it.id}" }
                        ) { chat ->
                            ChatHistoryFullItem(
                                chat = chat,
                                onClick = { onChatClick(chat) }
                            )
                        }
                    }
                }
            }

            // Search bar docked - stays fixed while TopBar scrolls away
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = paddingValues.calculateTopPadding())
                    .zIndex(1f) // Ensures search bar stays on top
            ) {
                HistorySearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClearSearch = { searchQuery = "" },
                    placeholder = "Search chats...",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Individual chat item in full history view
 */
@Composable
private fun ChatHistoryFullItem(
    chat: HomeContentItem.ChatItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clock icon
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Show summary if available
                chat.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = formatTimestamp(chat.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // More options icon
            IconButton(
                onClick = { /* TODO: Show options menu */ },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Empty state for full history
 */
@Composable
private fun EmptyHistoryContent(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HistoryToggleOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty search results
 */
@Composable
private fun EmptySearchResults() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "No results found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Material3 DockedSearchBar for history screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {
                        keyboardController?.hide()
                        expanded = false
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = onClearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
            shape = SearchBarDefaults.dockedShape,
            colors = SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                dividerColor = MaterialTheme.colorScheme.outlineVariant
            ),
            tonalElevation = SearchBarDefaults.TonalElevation,
            shadowElevation = SearchBarDefaults.ShadowElevation
        ) {
            // Empty content - no suggestions needed for now
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Type to search...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    val now = java.time.ZonedDateTime.now()

    return when {
        zonedDateTime.toLocalDate() == now.toLocalDate() -> {
            // Today - show time
            zonedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))
        }
        zonedDateTime.toLocalDate() == now.minusDays(1).toLocalDate() -> {
            // Yesterday
            "Yesterday, ${zonedDateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
        }
        else -> {
            // Other dates
            zonedDateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }
}
