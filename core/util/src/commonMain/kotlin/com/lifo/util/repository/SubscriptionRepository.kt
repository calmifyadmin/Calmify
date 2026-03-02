package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * SubscriptionRepository Interface
 *
 * Manages Google Play Billing subscriptions, premium tier state,
 * and purchase verification for the Calmify app.
 */
interface SubscriptionRepository {

    data class ProductInfo(
        val productId: String,
        val title: String,
        val description: String,
        val price: String,
        val priceMicros: Long,
        val currencyCode: String,
    )

    enum class SubscriptionTier { FREE, PREMIUM, PRO }

    data class SubscriptionState(
        val tier: SubscriptionTier,
        val expiresAt: Long? = null,
        val isAutoRenewing: Boolean = false,
    )

    /** Get current subscription state for the user. */
    suspend fun getSubscriptionState(userId: String): RequestState<SubscriptionState>

    /** Get available subscription products from Play Store. */
    suspend fun getAvailableProducts(): RequestState<List<ProductInfo>>

    /** Verify and acknowledge a purchase (should be called after billing flow completes). */
    suspend fun acknowledgePurchase(userId: String, purchaseToken: String): RequestState<SubscriptionState>

    /** Restore purchases — query Play Store for existing subscriptions. */
    suspend fun restorePurchases(userId: String): RequestState<SubscriptionState>

    /** Observe subscription changes reactively. */
    fun observeSubscription(userId: String): Flow<SubscriptionState>
}
