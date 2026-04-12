package com.lifo.subscription

import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.SubscriptionRepository

object SubscriptionContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadSubscriptionState : Intent
        /** User tapped "Subscribe" for the given Stripe lookup_key (e.g. "calmify_premium_monthly"). */
        data class PurchaseSubscription(val lookupKey: String) : Intent
        /** User returned from checkout — pull canonical state from server. */
        data object RefreshSubscriptionState : Intent
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
        /** True when running with `subscription_enabled = false` — WaitlistSubscriptionRepository is bound. */
        val isWaitlistMode: Boolean = false,
    ) : MviContract.State {
        val isPro: Boolean
            get() = subscriptionTier == SubscriptionRepository.SubscriptionTier.PRO
    }

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class PurchaseSuccess(val tier: SubscriptionRepository.SubscriptionTier) : Effect
        data object NavigateBack : Effect
        /** Open the given URL in the system browser (Stripe hosted checkout). */
        data class OpenUrl(val url: String) : Effect
        data object WaitlistSubmitSuccess : Effect
    }
}
