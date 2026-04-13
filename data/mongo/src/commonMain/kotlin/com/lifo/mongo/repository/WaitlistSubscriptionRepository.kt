package com.lifo.mongo.repository

import com.lifo.util.model.RequestState
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.repository.SubscriptionRepository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Waitlist-mode subscription repository.
 *
 * Active when Remote Config `subscription_enabled = false` — all users are FREE,
 * checkout attempts throw WaitlistException so the UI shows the wishlist dialog
 * instead of opening a Stripe checkout URL.
 *
 * When the flag flips to true, Koin injects KtorSubscriptionRepository instead.
 */
class WaitlistSubscriptionRepository : SubscriptionRepository {

    class WaitlistException : Exception("Premium not yet available — join waitlist")

    override suspend fun getAvailableProducts(): RequestState<List<ProductInfo>> {
        // Indicative prices for the wishlist paywall. Real prices come from Stripe
        // once `subscription_enabled = true`.
        return RequestState.Success(
            listOf(
                ProductInfo(
                    lookupKey = "calmify_premium_monthly",
                    title = "Calmify PRO (Waitlist Preview)",
                    description = "Accesso illimitato a tutte le funzionalità",
                    priceAmount = 499L,
                    currency = "EUR",
                    interval = "month",
                ),
                ProductInfo(
                    lookupKey = "calmify_premium_yearly",
                    title = "Calmify PRO Annuale (Waitlist Preview)",
                    description = "Risparmia 33% con il piano annuale",
                    priceAmount = 3999L,
                    currency = "EUR",
                    interval = "year",
                ),
            )
        )
    }

    override suspend fun createCheckoutSession(lookupKey: String): RequestState<CheckoutSession> {
        return RequestState.Error(WaitlistException())
    }

    override suspend fun createBillingPortalSession(): RequestState<String> {
        return RequestState.Error(WaitlistException())
    }

    override suspend fun refreshSubscriptionState(): RequestState<SubscriptionState> {
        return RequestState.Success(SubscriptionState(tier = SubscriptionTier.FREE))
    }

    override fun observeSubscription(): Flow<SubscriptionState> =
        flowOf(SubscriptionState(tier = SubscriptionTier.FREE))
}
