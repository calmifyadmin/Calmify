package com.lifo.threaddetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ThreadDetailRouteContent(
    threadId: String,
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onThreadClick: (String) -> Unit,
) {
    val viewModel: ThreadDetailViewModel = koinViewModel(
        key = threadId,
        parameters = { parametersOf(threadId) }
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ThreadDetailContract.Effect.ReplyPosted -> { /* Could scroll to bottom */ }
                is ThreadDetailContract.Effect.ShareIntent -> { }
                is ThreadDetailContract.Effect.ShowError -> { }
            }
        }
    }

    ThreadDetailScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onUserClick = onUserClick,
        onThreadClick = onThreadClick,
    )
}
