package com.lifo.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedRouteContent(
    onThreadClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onComposeClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onMessagingClick: () -> Unit = {},
) {
    val viewModel: FeedViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(FeedContract.Intent.LoadFeed)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedContract.Effect.NavigateToThread -> onThreadClick(effect.threadId)
                is FeedContract.Effect.NavigateToUserProfile -> onUserClick(effect.userId)
                is FeedContract.Effect.ShowError -> { /* TODO: show snackbar */ }
            }
        }
    }

    FeedScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onThreadClick = onThreadClick,
        onUserClick = onUserClick,
        onComposeClick = onComposeClick,
        onSearchClick = onSearchClick,
        onNotificationsClick = onNotificationsClick,
        onMessagingClick = onMessagingClick,
    )
}
