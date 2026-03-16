package com.lifo.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point for the Search screen.
 * Wires the ViewModel to the stateless SearchScreen composable.
 */
@Composable
fun SearchRouteContent(
    onNavigateBack: () -> Unit = {},
    onThreadClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val viewModel: SearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchContract.Effect.NavigateToThread -> onThreadClick(effect.threadId)
                is SearchContract.Effect.NavigateToUserProfile -> onUserClick(effect.userId)
                is SearchContract.Effect.ShowError -> {
                    // Error is already displayed in the state; this effect can be
                    // used to show a Snackbar in the future if desired.
                }
            }
        }
    }

    SearchScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onThreadClick = onThreadClick,
        onUserClick = onUserClick,
        onNavigateBack = onNavigateBack
    )
}
