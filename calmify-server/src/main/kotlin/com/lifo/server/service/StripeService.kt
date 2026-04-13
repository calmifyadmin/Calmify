package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.stripe.Stripe
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.Price
import com.stripe.model.Subscription
import com.stripe.model.billingportal.Session as BillingPortalSession
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.PriceListParams
import com.stripe.param.billingportal.SessionCreateParams as BillingPortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

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
    private val log = LoggerFactory.getLogger(StripeService::class.java)

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
            .setSubscriptionData(
                SessionCreateParams.SubscriptionData.builder()
                    .putMetadata("userId", userId)
                    .build()
            )
            .setAllowPromotionCodes(true)

        userEmail?.takeIf { it.isNotBlank() }?.let { paramsBuilder.setCustomerEmail(it) }

        val session = Session.create(paramsBuilder.build())
        CheckoutSessionResult(
            url = session.url ?: error("Stripe returned session without url"),
            sessionId = session.id,
        )
    }

    /**
     * Create a Stripe Customer Portal session so the user can manage their subscription
     * (cancel, change plan, update card, view invoices) on Stripe-hosted UI.
     *
     * Requires the user to have a stripeCustomerId persisted in `subscriptions/{userId}`
     * (written by the webhook on first successful checkout).
     */
    suspend fun createBillingPortalSession(
        userId: String,
        returnUrl: String,
    ): BillingPortalSessionResult = withContext(Dispatchers.IO) {
        val snap = db.collection("subscriptions").document(userId).get().get()
        val customerId = snap.getString("stripeCustomerId")
            ?: error("No Stripe customer found for user $userId — active subscription required")

        val session = BillingPortalSession.create(
            BillingPortalSessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build()
        )
        BillingPortalSessionResult(url = session.url)
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
        if (eventRef.get().get().exists()) {
            log.info("Stripe event ${event.id} type=${event.type} already processed — skipping")
            return@withContext
        }

        log.info("Stripe event received id=${event.id} type=${event.type} apiVersion=${event.apiVersion}")

        when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session
                if (session == null) {
                    log.warn("checkout.session.completed: dataObjectDeserializer returned null — likely API version mismatch (event=${event.apiVersion}, sdk pinned). Skipping ${event.id}")
                    return@withContext
                }
                val userId = session.clientReferenceId
                    ?: session.metadata?.get("userId")
                if (userId.isNullOrBlank()) {
                    log.warn("checkout.session.completed: no userId resolvable (clientReferenceId+metadata both null) for session=${session.id}, event=${event.id}")
                    return@withContext
                }
                val subscriptionId = session.subscription
                if (subscriptionId.isNullOrBlank()) {
                    log.warn("checkout.session.completed: no subscription on session=${session.id}, mode=${session.mode}, event=${event.id}")
                    return@withContext
                }
                val sub = Subscription.retrieve(subscriptionId)
                writeSubscriptionState(userId, sub)
                log.info("checkout.session.completed processed: user=$userId subscription=$subscriptionId status=${sub.status}")
            }
            "customer.subscription.created",
            "customer.subscription.updated" -> {
                val sub = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription
                if (sub == null) {
                    log.warn("${event.type}: dataObjectDeserializer returned null — API version mismatch likely. Skipping ${event.id}")
                    return@withContext
                }
                val userId = sub.metadata?.get("userId")
                    ?: resolveUserIdFromCustomer(sub.customer)
                if (userId.isNullOrBlank()) {
                    log.warn("${event.type}: no userId in metadata and no doc with stripeCustomerId=${sub.customer}. Skipping ${event.id}")
                    return@withContext
                }
                writeSubscriptionState(userId, sub)
                log.info("${event.type} processed: user=$userId subscription=${sub.id} status=${sub.status}")
            }
            "customer.subscription.deleted" -> {
                val sub = event.dataObjectDeserializer.`object`.orElse(null) as? Subscription
                if (sub == null) {
                    log.warn("customer.subscription.deleted: dataObjectDeserializer returned null. Skipping ${event.id}")
                    return@withContext
                }
                val userId = sub.metadata?.get("userId")
                    ?: resolveUserIdFromCustomer(sub.customer)
                if (userId.isNullOrBlank()) {
                    log.warn("customer.subscription.deleted: no userId resolvable for customer=${sub.customer}. Skipping ${event.id}")
                    return@withContext
                }
                db.collection("subscriptions").document(userId).set(
                    mapOf(
                        "tier" to "FREE",
                        "expiresAt" to 0L,
                        "isAutoRenewing" to false,
                        "status" to "canceled",
                        "updatedAt" to System.currentTimeMillis(),
                    )
                ).get()
                log.info("customer.subscription.deleted processed: user=$userId tier=FREE")
            }
            "invoice.payment_failed" -> {
                val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice
                if (invoice == null) {
                    log.warn("invoice.payment_failed: dataObjectDeserializer returned null. Skipping ${event.id}")
                    return@withContext
                }
                // dahlia (API 2026-03-25): invoice.subscription moved under invoice.parent.subscriptionDetails.
                val subscriptionId = invoice.parent?.subscriptionDetails?.subscription
                if (subscriptionId.isNullOrBlank()) {
                    log.warn("invoice.payment_failed: no subscription on invoice=${invoice.id} (parent=${invoice.parent?.type}). Skipping ${event.id}")
                    return@withContext
                }
                val sub = Subscription.retrieve(subscriptionId)
                val userId = sub.metadata?.get("userId")
                    ?: resolveUserIdFromCustomer(sub.customer)
                if (userId.isNullOrBlank()) {
                    log.warn("invoice.payment_failed: no userId resolvable for customer=${sub.customer}. Skipping ${event.id}")
                    return@withContext
                }
                db.collection("subscriptions").document(userId).set(
                    mapOf("status" to "past_due", "updatedAt" to System.currentTimeMillis()),
                    SetOptions.merge()
                ).get()
                log.info("invoice.payment_failed processed: user=$userId status=past_due")
            }
            else -> {
                log.info("Stripe event ${event.type} not handled — id=${event.id}")
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
        // dahlia (API 2026-03-25): currentPeriodEnd moved from Subscription to SubscriptionItem.
        // Take the max across items so multi-item subscriptions reflect the latest period boundary.
        val periodEndSec = sub.items?.data
            ?.mapNotNull { it.currentPeriodEnd }
            ?.maxOrNull()
            ?: 0L
        val expiresMillis = periodEndSec * 1000L
        val tier = if (active) "PRO" else "FREE"
        db.collection("subscriptions").document(userId).set(
            mapOf(
                "tier" to tier,
                "expiresAt" to expiresMillis,
                "isAutoRenewing" to (sub.cancelAtPeriodEnd == false && active),
                "status" to (sub.status ?: "none"),
                "stripeSubscriptionId" to sub.id,
                "stripeCustomerId" to (sub.customer ?: ""),
                "updatedAt" to System.currentTimeMillis(),
            )
        ).get()
        log.info("writeSubscriptionState: user=$userId tier=$tier status=${sub.status} expiresAt=$expiresMillis customer=${sub.customer}")
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

data class BillingPortalSessionResult(
    val url: String,
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
