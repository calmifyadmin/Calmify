package com.lifo.history.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.history.ChatHistoryFullScreen
import com.lifo.history.DiaryHistoryFullScreen
import com.lifo.history.HistoryScreen
import com.lifo.history.HistoryViewModel

/**
 * Public entry point composable for the History main screen.
 * Wraps the internal HistoryScreen with ViewModel setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRouteContent(
    onChatClick: (String) -> Unit,
    onDiaryClick: (String) -> Unit,
    onChatHistoryHeaderClick: () -> Unit,
    onDiaryHistoryHeaderClick: () -> Unit,
    onMenuClicked: () -> Unit
) {
    val viewModel: HistoryViewModel = koinViewModel()

    HistoryScreen(
        onMenuClicked = onMenuClicked,
        onChatClick = { chat -> onChatClick(chat.id) },
        onDiaryClick = { diary -> onDiaryClick(diary.id) },
        onChatHistoryHeaderClick = onChatHistoryHeaderClick,
        onDiaryHistoryHeaderClick = onDiaryHistoryHeaderClick,
        viewModel = viewModel
    )
}

/**
 * Public entry point for the full Chat History screen.
 */
@Composable
fun ChatHistoryFullRouteContent(
    onBackClick: () -> Unit,
    onChatClick: (String) -> Unit
) {
    val viewModel: HistoryViewModel = koinViewModel()

    ChatHistoryFullScreen(
        onBackClick = onBackClick,
        onChatClick = { chat -> onChatClick(chat.id) },
        viewModel = viewModel
    )
}

/**
 * Public entry point for the full Diary History screen.
 */
@Composable
fun DiaryHistoryFullRouteContent(
    onBackClick: () -> Unit,
    onDiaryClick: (String) -> Unit,
    onInsightClick: (String) -> Unit
) {
    val viewModel: HistoryViewModel = koinViewModel()

    DiaryHistoryFullScreen(
        onBackClick = onBackClick,
        onDiaryClick = { diary -> onDiaryClick(diary.id) },
        onInsightClick = onInsightClick,
        viewModel = viewModel
    )
}
