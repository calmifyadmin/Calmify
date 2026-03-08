package com.lifo.feed

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FeedRouteContent(
    onThreadClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onComposeClick: () -> Unit = {},
    onReplyClick: (threadId: String, authorName: String) -> Unit = { _, _ -> },
    onSearchClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onMessagingClick: () -> Unit = {},
) {
    val viewModel: FeedViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onIntent(FeedContract.Intent.LoadFeed)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedContract.Effect.NavigateToThread -> onThreadClick(effect.threadId)
                is FeedContract.Effect.NavigateToUserProfile -> onUserClick(effect.userId)
                is FeedContract.Effect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is FeedContract.Effect.ShowSuccess -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    FeedScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onThreadClick = onThreadClick,
        onUserClick = onUserClick,
        onComposeClick = onComposeClick,
        onReplyClick = onReplyClick,
        onSearchClick = onSearchClick,
        onNotificationsClick = onNotificationsClick,
        onMessagingClick = onMessagingClick,
    )
}
