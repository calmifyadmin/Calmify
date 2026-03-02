package com.lifo.subscription

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.SubscriptionRepository
import kotlinx.coroutines.launch

class SubscriptionViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<SubscriptionContract.Intent, SubscriptionContract.State, SubscriptionContract.Effect>(
    initialState = SubscriptionContract.State()
) {

    private val currentUserId: String?
        get() = authProvider.currentUserId

    override fun handleIntent(intent: SubscriptionContract.Intent) {
        when (intent) {
            is SubscriptionContract.Intent.LoadSubscriptionState -> loadSubscriptionState()
            is SubscriptionContract.Intent.PurchaseSubscription -> purchaseSubscription(intent.productId)
            is SubscriptionContract.Intent.RestorePurchases -> restorePurchases()
            is SubscriptionContract.Intent.DismissPaywall -> sendEffect(SubscriptionContract.Effect.NavigateBack)
        }
    }

    private fun loadSubscriptionState() {
        val userId = currentUserId ?: run {
            sendEffect(SubscriptionContract.Effect.ShowError("User not authenticated"))
            return
        }

        updateState { copy(isLoading = true, error = null) }

        scope.launch {
            // Load current subscription state
            when (val result = subscriptionRepository.getSubscriptionState(userId)) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            subscriptionTier = result.data.tier,
                            isLoading = false,
                        )
                    }
                }
                is RequestState.Error -> {
                    updateState { copy(isLoading = false, error = result.message) }
                    sendEffect(SubscriptionContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading/Idle — no-op */ }
            }

            // Load available products
            when (val productsResult = subscriptionRepository.getAvailableProducts()) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            availableProducts = productsResult.data,
                            isLoading = false,
                        )
                    }
                }
                is RequestState.Error -> {
                    updateState { copy(isLoading = false) }
                    sendEffect(SubscriptionContract.Effect.ShowError(productsResult.message))
                }
                else -> { /* Loading/Idle — no-op */ }
            }
        }
    }

    private fun purchaseSubscription(productId: String) {
        val userId = currentUserId ?: run {
            sendEffect(SubscriptionContract.Effect.ShowError("User not authenticated"))
            return
        }

        updateState { copy(isLoading = true, error = null) }

        scope.launch {
            // The actual billing flow is launched by the Activity via BillingClient.launchBillingFlow().
            // Here we acknowledge the purchase after the billing flow completes.
            // For now, we query the subscription state to reflect any changes.
            when (val result = subscriptionRepository.getSubscriptionState(userId)) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            subscriptionTier = result.data.tier,
                            isLoading = false,
                        )
                    }
                    if (result.data.tier != SubscriptionRepository.SubscriptionTier.FREE) {
                        sendEffect(SubscriptionContract.Effect.PurchaseSuccess(result.data.tier))
                    }
                }
                is RequestState.Error -> {
                    updateState { copy(isLoading = false, error = result.message) }
                    sendEffect(SubscriptionContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading/Idle — no-op */ }
            }
        }
    }

    private fun restorePurchases() {
        val userId = currentUserId ?: run {
            sendEffect(SubscriptionContract.Effect.ShowError("User not authenticated"))
            return
        }

        updateState { copy(isLoading = true, error = null) }

        scope.launch {
            when (val result = subscriptionRepository.restorePurchases(userId)) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            subscriptionTier = result.data.tier,
                            isLoading = false,
                        )
                    }
                    val restoredCount = if (result.data.tier != SubscriptionRepository.SubscriptionTier.FREE) 1 else 0
                    sendEffect(SubscriptionContract.Effect.ShowRestoreResult(restoredCount))
                }
                is RequestState.Error -> {
                    updateState { copy(isLoading = false, error = result.message) }
                    sendEffect(SubscriptionContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading/Idle — no-op */ }
            }
        }
    }
}
