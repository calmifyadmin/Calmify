package com.lifo.composer

import com.lifo.util.currentTimeMillis
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ComposerRouteContent(
    onNavigateBack: () -> Unit,
    onPostCreated: () -> Unit,
    parentThreadId: String? = null,
    replyToAuthorName: String? = null,
    prefilledContent: String? = null,
) {
    // Unique key per composer invocation to prevent stale state from previous sessions
    val composerKey = remember { "composer_${currentTimeMillis()}" }
    val viewModel: ComposerViewModel = koinViewModel(
        key = composerKey,
    ) {
        parametersOf(parentThreadId, replyToAuthorName, prefilledContent)
    }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ComposerContract.Effect.PostCreated -> onPostCreated()
                is ComposerContract.Effect.Discarded -> onNavigateBack()
                is ComposerContract.Effect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    ComposerScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    )
}
