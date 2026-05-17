package com.lifo.biocontext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BioPatternFeedRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: BioPatternFeedViewModel = koinViewModel(key = "biopatternfeed_vm")
    val state by viewModel.state.collectAsState()
    BioPatternFeedScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = navigateBack,
    )
}
