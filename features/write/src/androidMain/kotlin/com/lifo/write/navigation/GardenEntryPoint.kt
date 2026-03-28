package com.lifo.write.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lifo.write.GardenScreen
import com.lifo.write.GardenViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GardenRouteContent(
    onActivityClick: (String) -> Unit,
) {
    val viewModel: GardenViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    GardenScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onActivityClick = onActivityClick,
    )
}
