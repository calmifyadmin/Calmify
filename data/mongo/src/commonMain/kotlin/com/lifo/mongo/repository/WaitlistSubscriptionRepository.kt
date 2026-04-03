package com.lifo.mongo.repository

import com.lifo.util.model.RequestState
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.repository.SubscriptionRepository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Waitlist-mode subscription repository.
 * Used when `premium_enabled = false` — all users are FREE,
 * purchase attempts emit a WaitlistException so the UI can show
 * the waitlist dialog instead of billing.
 *
 * When `premium_enabled` is flipped to true, Koin injects
 * PlayBillingSubscriptionRepository instead — zero code changes.
 */
class WaitlistSubscriptionRepository : SubscriptionRepository {

    class WaitlistException : Exception("Premium not yet available — join waitlist")

    private val _purchaseUpdates = MutableSharedFlow<PurchaseResult>()
    override val purchaseUpdates: Flow<PurchaseResult> = _purchaseUpdates

    override suspend fun getSubscriptionState(userId: String): RequestState<SubscriptionState> {
        return RequestState.Success(SubscriptionState(tier = SubscriptionTier.FREE))
    }

    override suspend fun getAvailableProducts(): RequestState<List<ProductInfo>> {
        // PLACEHOLDER prices — NOT real Play Store products.
        // These are shown only during waitlist mode (premium_enabled = false) so the
        // PaywallScreen renders something meaningful. Prices are indicative only and
        // will be replaced by real Play Store SKUs once premium_enabled = true.
        // The "waitlist_" prefix in productId is used by SubscriptionViewModel to detect
        // waitlist mode and render appropriate UI (no real purchase button).
        return RequestState.Success(
            listOf(
                ProductInfo(
                    productId = "waitlist_calmify_pro_monthly",
                    title = "Calmify PRO (Waitlist Preview)",
                    description = "Accesso illimitato a tutte le funzionalità",
                    price = "€5,99/mese",
                    priceMicros = 5_990_000,
                    currencyCode = "EUR",
                ),
                ProductInfo(
                    productId = "waitlist_calmify_pro_yearly",
                    title = "Calmify PRO Annuale (Waitlist Preview)",
                    description = "Risparmia 30% con il piano annuale",
                    price = "€49,99/anno",
                    priceMicros = 49_990_000,
                    currencyCode = "EUR",
                ),
            )
        )
    }

    override suspend fun launchPurchaseFlow(activityContext: Any, productId: String): RequestState<Unit> {
        // Don't launch billing — signal waitlist mode
        return RequestState.Error(WaitlistException())
    }

    override suspend fun acknowledgePurchase(userId: String, purchaseToken: String): RequestState<SubscriptionState> {
        return RequestState.Success(SubscriptionState(tier = SubscriptionTier.FREE))
    }

    override suspend fun restorePurchases(userId: String): RequestState<SubscriptionState> {
        return RequestState.Success(SubscriptionState(tier = SubscriptionTier.FREE))
    }

    override fun observeSubscription(userId: String): Flow<SubscriptionState> {
        return flowOf(SubscriptionState(tier = SubscriptionTier.FREE))
    }
}
