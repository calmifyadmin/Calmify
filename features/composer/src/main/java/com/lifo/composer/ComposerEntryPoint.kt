package com.lifo.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ComposerRouteContent(
    onNavigateBack: () -> Unit,
    onPostCreated: () -> Unit,
) {
    val viewModel: ComposerViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ComposerContract.Effect.PostCreated -> onPostCreated()
                is ComposerContract.Effect.Discarded -> onNavigateBack()
                is ComposerContract.Effect.ShowError -> {
                    // Error is already handled via state / snackbar
                }
            }
        }
    }

    ComposerScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
    )
}
