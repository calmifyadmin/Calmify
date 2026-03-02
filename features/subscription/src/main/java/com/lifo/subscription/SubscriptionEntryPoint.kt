package com.lifo.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SubscriptionRouteContent(
    onNavigateBack: () -> Unit,
) {
    val viewModel: SubscriptionViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(SubscriptionContract.Intent.LoadSubscriptionState)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SubscriptionContract.Effect.NavigateBack -> onNavigateBack()
                is SubscriptionContract.Effect.PurchaseSuccess -> { /* handled by snackbar or navigation */ }
                is SubscriptionContract.Effect.ShowError -> { /* snackbar */ }
                is SubscriptionContract.Effect.ShowRestoreResult -> { /* snackbar */ }
            }
        }
    }

    PaywallScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}
