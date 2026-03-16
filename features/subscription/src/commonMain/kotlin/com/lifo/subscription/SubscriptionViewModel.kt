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

    init {
        observePurchaseUpdates()
    }

    override fun handleIntent(intent: SubscriptionContract.Intent) {
        when (intent) {
            is SubscriptionContract.Intent.LoadSubscriptionState -> loadSubscriptionState()
            is SubscriptionContract.Intent.PurchaseSubscription -> purchaseSubscription(intent.productId)
            is SubscriptionContract.Intent.RestorePurchases -> restorePurchases()
            is SubscriptionContract.Intent.DismissPaywall -> sendEffect(SubscriptionContract.Effect.NavigateBack)
        }
    }

    /**
     * Called by the UI after receiving LaunchBillingFlow effect.
     * Passes the Activity context to the repository to launch Google Play's billing sheet.
     */
    fun launchBillingFlow(activityContext: Any, productId: String) {
        scope.launch {
            when (val result = subscriptionRepository.launchPurchaseFlow(activityContext, productId)) {
                is RequestState.Error -> {
                    updateState { copy(isLoading = false, error = result.message) }
                    sendEffect(SubscriptionContract.Effect.ShowError(result.message))
                }
                else -> { /* Billing flow launched — result comes via purchaseUpdates */ }
            }
        }
    }

    private fun observePurchaseUpdates() {
        scope.launch {
            subscriptionRepository.purchaseUpdates.collect { purchaseResult ->
                if (purchaseResult.isSuccess) {
                    handlePurchaseCompleted(purchaseResult.purchaseToken)
                } else if (purchaseResult.errorMessage != null) {
                    updateState { copy(isLoading = false) }
                    sendEffect(SubscriptionContract.Effect.ShowError(purchaseResult.errorMessage ?: "Purchase failed"))
                }
            }
        }
    }

    private suspend fun handlePurchaseCompleted(purchaseToken: String) {
        val userId = currentUserId ?: return

        when (val result = subscriptionRepository.acknowledgePurchase(userId, purchaseToken)) {
            is RequestState.Success -> {
                updateState {
                    copy(
                        subscriptionTier = result.data.tier,
                        isLoading = false,
                    )
                }
                sendEffect(SubscriptionContract.Effect.PurchaseSuccess(result.data.tier))
            }
            is RequestState.Error -> {
                updateState { copy(isLoading = false, error = result.message) }
                sendEffect(SubscriptionContract.Effect.ShowError(result.message))
            }
            else -> {}
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
                else -> {}
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
                else -> {}
            }
        }
    }

    private fun purchaseSubscription(productId: String) {
        currentUserId ?: run {
            sendEffect(SubscriptionContract.Effect.ShowError("User not authenticated"))
            return
        }

        updateState { copy(isLoading = true, error = null) }
        sendEffect(SubscriptionContract.Effect.LaunchBillingFlow(productId))
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
                else -> {}
            }
        }
    }
}
