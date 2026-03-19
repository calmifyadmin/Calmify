package com.lifo.subscription

import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.SubscriptionRepository

object SubscriptionContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadSubscriptionState : Intent
        data class PurchaseSubscription(val productId: String) : Intent
        data object RestorePurchases : Intent
        data object DismissPaywall : Intent
        data class UpdateWaitlistEmail(val email: String) : Intent
        data object SubmitWaitlistEmail : Intent
        data object DismissWaitlistDialog : Intent
    }

    data class State(
        val subscriptionTier: SubscriptionRepository.SubscriptionTier = SubscriptionRepository.SubscriptionTier.FREE,
        val isLoading: Boolean = false,
        val availableProducts: List<SubscriptionRepository.ProductInfo> = emptyList(),
        val error: String? = null,
        val showWaitlistDialog: Boolean = false,
        val waitlistEmail: String = "",
        val waitlistSubmitted: Boolean = false,
    ) : MviContract.State {
        val isPro: Boolean
            get() = subscriptionTier == SubscriptionRepository.SubscriptionTier.PRO
    }

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class PurchaseSuccess(val tier: SubscriptionRepository.SubscriptionTier) : Effect
        data object NavigateBack : Effect
        data class ShowRestoreResult(val count: Int) : Effect
        data class LaunchBillingFlow(val productId: String) : Effect
        data object WaitlistSubmitSuccess : Effect
    }
}
