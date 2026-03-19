package com.lifo.subscription

import android.app.Activity
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SubscriptionRouteContent(
    onNavigateBack: () -> Unit,
) {
    val viewModel: SubscriptionViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onIntent(SubscriptionContract.Intent.LoadSubscriptionState)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SubscriptionContract.Effect.NavigateBack -> onNavigateBack()
                is SubscriptionContract.Effect.LaunchBillingFlow -> {
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.launchBillingFlow(activity, effect.productId)
                    } else {
                        Toast.makeText(context, "Unable to launch purchase", Toast.LENGTH_SHORT).show()
                    }
                }
                is SubscriptionContract.Effect.PurchaseSuccess -> {
                    Toast.makeText(context, "Abbonamento attivato!", Toast.LENGTH_SHORT).show()
                }
                is SubscriptionContract.Effect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SubscriptionContract.Effect.ShowRestoreResult -> {
                    val msg = if (effect.count > 0) "Abbonamento ripristinato!" else "Nessun abbonamento trovato"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                is SubscriptionContract.Effect.WaitlistSubmitSuccess -> {
                    Toast.makeText(context, "Iscrizione completata!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PaywallScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

/** Walk up the Context chain to find the Activity. */
private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
