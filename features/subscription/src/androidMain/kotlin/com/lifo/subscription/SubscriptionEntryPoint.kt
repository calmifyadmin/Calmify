package com.lifo.subscription

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.lifo.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SubscriptionRouteContent(
    onNavigateBack: () -> Unit,
) {
    val viewModel: SubscriptionViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val strUnableToPurchase = stringResource(Res.string.toast_unable_to_purchase)
    val strSubscriptionActivated = stringResource(Res.string.toast_subscription_activated)
    val strWaitlistSuccess = stringResource(Res.string.toast_waitlist_success)

    LaunchedEffect(Unit) {
        viewModel.onIntent(SubscriptionContract.Intent.LoadSubscriptionState)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SubscriptionContract.Effect.NavigateBack -> onNavigateBack()
                is SubscriptionContract.Effect.OpenUrl -> {
                    val opened = openCheckout(context, effect.url)
                    if (!opened) {
                        Toast.makeText(context, strUnableToPurchase, Toast.LENGTH_SHORT).show()
                    }
                }
                is SubscriptionContract.Effect.PurchaseSuccess -> {
                    Toast.makeText(context, strSubscriptionActivated, Toast.LENGTH_SHORT).show()
                }
                is SubscriptionContract.Effect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SubscriptionContract.Effect.WaitlistSubmitSuccess -> {
                    Toast.makeText(context, strWaitlistSuccess, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PaywallScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}

/**
 * Open the Stripe checkout URL in a Chrome Custom Tab, falling back to a
 * standard browser intent. Returns false if nothing can handle the URL.
 */
private fun openCheckout(context: android.content.Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    return try {
        val intent = CustomTabsIntent.Builder().build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, uri)
        true
    } catch (e: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (e2: Exception) {
            false
        }
    }
}
