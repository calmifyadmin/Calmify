package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * SubscriptionRepository — web-first Stripe semantics.
 *
 * Design: the client never touches platform billing SDKs. All purchases flow through
 * a Stripe hosted Checkout Session URL opened in the system browser. Subscription
 * state is authoritative on the server (via webhook); the client only reads it.
 *
 * Products are identified by stable Stripe **lookup keys**, NOT price_ids, so the
 * test→live switch happens without a client redeploy.
 */
interface SubscriptionRepository {

    data class ProductInfo(
        val lookupKey: String,
        val title: String,
        val description: String,
        val priceAmount: Long,
        val currency: String,
        val interval: String,
    )

    enum class SubscriptionTier { FREE, PRO }

    data class SubscriptionState(
        val tier: SubscriptionTier,
        val expiresAt: Long = 0L,
        val isAutoRenewing: Boolean = false,
        val status: String = "none",
    )

    data class CheckoutSession(
        val url: String,
        val sessionId: String,
    )

    /** Fetch the current list of purchasable products (resolved server-side via lookup_keys). */
    suspend fun getAvailableProducts(): RequestState<List<ProductInfo>>

    /**
     * Create a hosted Stripe Checkout Session for the given lookup_key.
     * The client must open the returned [CheckoutSession.url] in the system browser.
     */
    suspend fun createCheckoutSession(lookupKey: String): RequestState<CheckoutSession>

    /**
     * Create a Stripe Billing Portal session so the user can manage their active
     * subscription (cancel, change plan, update card, view invoices) on Stripe-hosted UI.
     * The client must open the returned URL in the system browser.
     * Fails if the user has never completed a checkout (no stripeCustomerId on record).
     */
    suspend fun createBillingPortalSession(): RequestState<String>

    /** Pull the authoritative subscription state from the server. */
    suspend fun refreshSubscriptionState(): RequestState<SubscriptionState>

    /** Observe subscription state changes. Emits the latest known state. */
    fun observeSubscription(): Flow<SubscriptionState>
}
