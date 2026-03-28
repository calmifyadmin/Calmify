package com.lifo.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lifo.settings.EnvironmentScreen
import com.lifo.settings.EnvironmentViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EnvironmentRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: EnvironmentViewModel = koinViewModel(key = "environment_vm")
    val state by viewModel.state.collectAsState()

    EnvironmentScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackPressed = navigateBack,
    )
}
