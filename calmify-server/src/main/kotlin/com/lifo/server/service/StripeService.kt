package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.Price
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.PriceListParams
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StripeService — web-first subscription lifecycle.
 *
 * Prices are resolved via lookup_keys so test→live switch preserves the same identifiers.
 * Subscription state is persisted in Firestore `subscriptions/{userId}` and written exclusively
 * by the webhook handler (source of truth). The client reads state from `GET /subscription/state`.
 */
class StripeService(
    private val apiKey: String,
    private val db: Firestore,
) {
    init {
        if (apiKey.isNotEmpty()) {
            Stripe.apiKey = apiKey
        }
    }

    suspend fun createCheckoutSession(
        userId: String,
        userEmail: String?,
        lookupKey: String,
        successUrl: String,
        cancelUrl: String,
    ): CheckoutSessionResult = withContext(Dispatchers.IO) {
        val price = fetchPriceByLookupKey(lookupKey)
            ?: error("No Stripe Price found for lookup_key=$lookupKey")

        val paramsBuilder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(price.id)
                    .setQuantity(1L)
                    .build()
            )
            .setClientReferenceId(userId)
            .putMetadata("userId", userId)
            .setAllowPromotionCodes(true)

        userEmail?.takeIf { it.isNotBlank() }?.let { paramsBuilder.setCustomerEmail(it) }

        val session = Session.create(paramsBuilder.build())
        CheckoutSessionResult(
            url = session.url ?: error("Stripe returned session without url"),
            sessionId = session.id,
        )
    }

    suspend fun getSubscriptionState(userId: String): SubscriptionStateDto = withContext(Dispatchers.IO) {
        val snap = db.collection("subscriptions").document(userId).get().get()
        if (!snap.exists()) {
            SubscriptionStateDto(tier = "FREE", expiresAt = 0L, isAutoRenewing = false, status = "none")
        } else {
            SubscriptionStateDto(
                tier = snap.getString("tier") ?: "FREE",
                expiresAt = snap.getLong("expiresAt") ?: 0L,
                isAutoRenewing = snap.getBoolean("isAutoRenewing") ?: false,
                status = snap.getString("status") ?: "none",
            )
        }
    }

    suspend fun listProducts(): List<ProductInfoDto> = withContext(Dispatchers.IO) {
        LOOKUP_KEYS.mapNotNull { lookupKey ->
            val price = fetchPriceByLookupKey(lookupKey) ?: return@mapNotNull null
            val productName = runCatching {
                price.productObject?.name ?: lookupKey
            }.getOrNull() ?: lookupKey
            val productDesc = runCatching {
                price.productObject?.description ?: ""
            }.getOrNull() ?: ""
            ProductInfoDto(
                lookupKey = lookupKey,
                priceId = price.id,
                title = productName,
                description = productDesc,
                priceAmount = price.unitAmount ?: 0L,
                currency = price.currency?.uppercase() ?: "EUR",
                interval = price.recurring?.interval ?: "month",
            )
        }
    }

    fun parseAndVerifyEvent(payload: String, signature: String, webhookSecret: String): Event =
        Webhook.constructEvent(payload, signature, webhookSecret)

    suspend fun applyEvent(event: Event): Unit = withContext(Dispatchers.IO) {
        val eventRef = db.collection("stripe_events").document(event.id)
        if (eventRef.get().get().exists()) return@withContext

        when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session ?: return@withContext
                val userId = session.clientReferenceId
                    ?: session.metadata?.get("userId")
                    ?: return@withContext
                val subscriptionId = session.subscription ?: return@withContext
                val sub = Subscription.retrieve(subscriptionId)
                writeSubscriptionState(userId, sub)
            }
            "customer.subscription.created",
            "customer.subscription.updated" -> {
                val sub = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription ?: return@withContext
                val userId = sub.metadata?.get("userId")
                    ?: resolveUserIdFromCustomer(sub.customer)
                    ?: return@withContext
                writeSubscriptionState(userId, sub)
            }
            "customer.subscription.deleted" -> {
                val sub = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription ?: return@withContext
                val userId = sub.metadata?.get("userId")
                    ?: resolveUserIdFromCustomer(sub.customer)
                    ?: return@withContext
                db.collection("subscriptions").document(userId).set(
                    mapOf(
                        "tier" to "FREE",
                        "expiresAt" to 0L,
                        "isAutoRenewing" to false,
                        "status" to "canceled",
                        "updatedAt" to System.currentTimeMillis(),
                    )
                ).get()
            }
            "invoice.payment_failed" -> {
                val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice ?: return@withContext
                val subscriptionId = invoice.subscription ?: return@withContext
                val sub = Subscription.retrieve(subscriptionId)
                val userId = sub.metadata?.get("userId")
                    ?: resolveUserIdFromCustomer(sub.customer)
                    ?: return@withContext
                db.collection("subscriptions").document(userId).set(
                    mapOf("status" to "past_due", "updatedAt" to System.currentTimeMillis()),
                    SetOptions.merge()
                ).get()
            }
        }

        eventRef.set(
            mapOf(
                "type" to event.type,
                "processedAt" to System.currentTimeMillis(),
            )
        ).get()
    }

    private fun fetchPriceByLookupKey(lookupKey: String): Price? {
        val params = PriceListParams.builder()
            .addLookupKey(lookupKey)
            .addExpand("data.product")
            .setLimit(1L)
            .build()
        return Price.list(params).data.firstOrNull()
    }

    private fun writeSubscriptionState(userId: String, sub: Subscription) {
        val active = sub.status in ACTIVE_STATUSES
        val expiresMillis = (sub.currentPeriodEnd ?: 0L) * 1000L
        db.collection("subscriptions").document(userId).set(
            mapOf(
                "tier" to if (active) "PRO" else "FREE",
                "expiresAt" to expiresMillis,
                "isAutoRenewing" to (sub.cancelAtPeriodEnd == false && active),
                "status" to (sub.status ?: "none"),
                "stripeSubscriptionId" to sub.id,
                "stripeCustomerId" to (sub.customer ?: ""),
                "updatedAt" to System.currentTimeMillis(),
            )
        ).get()
    }

    private fun resolveUserIdFromCustomer(customerId: String?): String? {
        if (customerId.isNullOrBlank()) return null
        val query = db.collection("subscriptions")
            .whereEqualTo("stripeCustomerId", customerId)
            .limit(1)
            .get()
            .get()
        return query.documents.firstOrNull()?.id
    }

    companion object {
        val LOOKUP_KEYS = listOf("calmify_premium_monthly", "calmify_premium_yearly")
        private val ACTIVE_STATUSES = setOf("active", "trialing")
    }
}

data class CheckoutSessionResult(
    val url: String,
    val sessionId: String,
)

data class SubscriptionStateDto(
    val tier: String,
    val expiresAt: Long,
    val isAutoRenewing: Boolean,
    val status: String,
)

data class ProductInfoDto(
    val lookupKey: String,
    val priceId: String,
    val title: String,
    val description: String,
    val priceAmount: Long,
    val currency: String,
    val interval: String,
)
