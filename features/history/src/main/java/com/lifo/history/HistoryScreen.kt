package com.lifo.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState
import com.lifo.util.model.HomeContentItem

/**
 * Main History screen composable
 * Displays chat and diary history in a Google-like activity feed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    onMenuClicked: () -> Unit,
    onChatClick: (HomeContentItem.ChatItem) -> Unit,
    onDiaryClick: (HomeContentItem.DiaryItem) -> Unit,
    onChatHistoryHeaderClick: () -> Unit,
    onDiaryHistoryHeaderClick: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            HistoryTopBar(
                scrollBehavior = scrollBehavior,
                onMenuClicked = onMenuClicked
            )
        }
    ) { paddingValues ->
        HistoryContent(
            uiState = uiState,
            paddingValues = paddingValues,
            onChatClick = onChatClick,
            onDiaryClick = onDiaryClick,
            onChatHistoryHeaderClick = onChatHistoryHeaderClick,
            onDiaryHistoryHeaderClick = onDiaryHistoryHeaderClick,
            onRefresh = { viewModel.refreshHistory() }
        )
    }
}
