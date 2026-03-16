package com.lifo.mongo.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.repository.SubscriptionRepository.ProductInfo
import com.lifo.util.repository.SubscriptionRepository.PurchaseResult
import com.lifo.util.repository.SubscriptionRepository.SubscriptionState
import com.lifo.util.repository.SubscriptionRepository.SubscriptionTier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * Google Play Billing implementation of [SubscriptionRepository].
 *
 * Uses BillingClient for product queries and purchase verification,
 * and Firestore to persist subscription state server-side.
 */
class PlayBillingSubscriptionRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
) : SubscriptionRepository {

    companion object {
        private const val TAG = "PlayBillingSubRepo"
        private const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
        private const val PRODUCT_PRO = "calmify_pro"
    }

    private var billingClient: BillingClient? = null

    /** Cached ProductDetails from the last successful query. */
    private var cachedProductDetails: List<ProductDetails> = emptyList()

    /** SharedFlow that emits purchase results from the billing flow. */
    private val _purchaseUpdates = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 4)
    override val purchaseUpdates: Flow<PurchaseResult> = _purchaseUpdates.asSharedFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        val productId = purchase.products.firstOrNull() ?: ""
                        _purchaseUpdates.tryEmit(
                            PurchaseResult(
                                productId = productId,
                                purchaseToken = purchase.purchaseToken,
                                isSuccess = true,
                            )
                        )
                        Log.d(TAG, "Purchase successful: $productId")
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled billing flow")
            }
            else -> {
                _purchaseUpdates.tryEmit(
                    PurchaseResult(
                        productId = "",
                        purchaseToken = "",
                        isSuccess = false,
                        errorMessage = billingResult.debugMessage,
                    )
                )
                Log.w(TAG, "Billing flow failed: ${billingResult.debugMessage}")
            }
        }
    }

    private suspend fun ensureBillingClientConnected(): BillingClient {
        val client = billingClient ?: BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
            .also { billingClient = it }

        if (client.isReady) return client

        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resume(client)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.w(TAG, "BillingClient disconnected")
                }
            })
        }
    }

    override suspend fun getSubscriptionState(userId: String): RequestState<SubscriptionState> {
        return try {
            val doc = firestore.collection(SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val tierStr = doc.getString("tier") ?: "FREE"
                val expiresAt = doc.getLong("expiresAt")
                val isAutoRenewing = doc.getBoolean("isAutoRenewing") ?: false

                val tier = when (tierStr) {
                    "PRO", "PREMIUM" -> SubscriptionTier.PRO // PREMIUM → PRO backward compat
                    else -> SubscriptionTier.FREE
                }

                val now = System.currentTimeMillis()
                if (expiresAt != null && expiresAt < now) {
                    val expiredState = SubscriptionState(
                        tier = SubscriptionTier.FREE,
                        expiresAt = null,
                        isAutoRenewing = false,
                    )
                    updateFirestoreState(userId, expiredState)
                    RequestState.Success(expiredState)
                } else {
                    RequestState.Success(
                        SubscriptionState(
                            tier = tier,
                            expiresAt = expiresAt,
                            isAutoRenewing = isAutoRenewing,
                        )
                    )
                }
            } else {
                RequestState.Success(SubscriptionState(tier = SubscriptionTier.FREE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subscription state", e)
            RequestState.Error(e)
        }
    }

    override suspend fun getAvailableProducts(): RequestState<List<ProductInfo>> {
        return try {
            val client = ensureBillingClientConnected()

            if (!client.isReady) {
                return RequestState.Error(Exception("Billing service unavailable"))
            }

            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_PRO)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            val (billingResult, detailsList) = queryProductDetailsSuspend(client, params)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Cache ProductDetails for launchBillingFlow
                cachedProductDetails = detailsList ?: emptyList()

                val products = detailsList?.map { details ->
                    val pricingPhase = details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()

                    ProductInfo(
                        productId = details.productId,
                        title = details.title,
                        description = details.description,
                        price = pricingPhase?.formattedPrice ?: "N/A",
                        priceMicros = pricingPhase?.priceAmountMicros ?: 0L,
                        currencyCode = pricingPhase?.priceCurrencyCode ?: "USD",
                    )
                } ?: emptyList()

                RequestState.Success(products)
            } else {
                RequestState.Error(
                    Exception("Failed to query products: ${billingResult.debugMessage}")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available products", e)
            RequestState.Error(e)
        }
    }

    override suspend fun launchPurchaseFlow(
        activityContext: Any,
        productId: String,
    ): RequestState<Unit> {
        return try {
            val activity = activityContext as? Activity
                ?: return RequestState.Error(Exception("Invalid activity context"))

            val client = ensureBillingClientConnected()
            if (!client.isReady) {
                return RequestState.Error(Exception("Billing service unavailable"))
            }

            val productDetails = cachedProductDetails.find { it.productId == productId }
                ?: return RequestState.Error(Exception("Product not found. Please reload."))

            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
                ?: return RequestState.Error(Exception("No offer available for this product."))

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()

            val result = client.launchBillingFlow(activity, billingFlowParams)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                RequestState.Success(Unit)
            } else {
                RequestState.Error(Exception("Failed to launch billing: ${result.debugMessage}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch purchase flow", e)
            RequestState.Error(e)
        }
    }

    override suspend fun acknowledgePurchase(
        userId: String,
        purchaseToken: String,
    ): RequestState<SubscriptionState> {
        return try {
            val client = ensureBillingClientConnected()

            if (!client.isReady) {
                return RequestState.Error(Exception("Billing service unavailable"))
            }

            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()

            val ackResult = acknowledgePurchaseSuspend(client, acknowledgeParams)

            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val tier = determineTierFromPurchases(client)
                val state = SubscriptionState(
                    tier = tier,
                    isAutoRenewing = true,
                )
                updateFirestoreState(userId, state)
                RequestState.Success(state)
            } else {
                RequestState.Error(
                    Exception("Failed to acknowledge purchase: ${ackResult.debugMessage}")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acknowledge purchase", e)
            RequestState.Error(e)
        }
    }

    override suspend fun restorePurchases(userId: String): RequestState<SubscriptionState> {
        return try {
            val client = ensureBillingClientConnected()

            if (!client.isReady) {
                return RequestState.Error(Exception("Billing service unavailable"))
            }

            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val (billingResult, purchases) = queryPurchasesSuspend(client, params)

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchases = purchases.filter { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                // Acknowledge any unacknowledged purchases
                activePurchases
                    .filter { !it.isAcknowledged }
                    .forEach { purchase ->
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        acknowledgePurchaseSuspend(client, ackParams)
                    }

                val tier = determineTierFromPurchaseList(activePurchases)
                val state = SubscriptionState(
                    tier = tier,
                    isAutoRenewing = activePurchases.any { it.isAutoRenewing },
                )
                updateFirestoreState(userId, state)
                RequestState.Success(state)
            } else {
                RequestState.Error(
                    Exception("Failed to restore purchases: ${billingResult.debugMessage}")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore purchases", e)
            RequestState.Error(e)
        }
    }

    override fun observeSubscription(userId: String): Flow<SubscriptionState> = callbackFlow {
        val registration = firestore.collection(SUBSCRIPTIONS_COLLECTION)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore subscription listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val tierStr = snapshot.getString("tier") ?: "FREE"
                    val expiresAt = snapshot.getLong("expiresAt")
                    val isAutoRenewing = snapshot.getBoolean("isAutoRenewing") ?: false

                    val tier = when (tierStr) {
                        "PRO", "PREMIUM" -> SubscriptionTier.PRO
                        else -> SubscriptionTier.FREE
                    }

                    trySend(
                        SubscriptionState(
                            tier = tier,
                            expiresAt = expiresAt,
                            isAutoRenewing = isAutoRenewing,
                        )
                    )
                } else {
                    trySend(SubscriptionState(tier = SubscriptionTier.FREE))
                }
            }

        awaitClose {
            registration.remove()
        }
    }

    // --- Suspend wrappers for BillingClient async methods ---

    private suspend fun queryProductDetailsSuspend(
        client: BillingClient,
        params: QueryProductDetailsParams,
    ): Pair<BillingResult, List<ProductDetails>?> = suspendCancellableCoroutine { continuation ->
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (continuation.isActive) {
                continuation.resume(Pair(billingResult, productDetailsList))
            }
        }
    }

    private suspend fun acknowledgePurchaseSuspend(
        client: BillingClient,
        params: AcknowledgePurchaseParams,
    ): BillingResult = suspendCancellableCoroutine { continuation ->
        client.acknowledgePurchase(params) { billingResult ->
            if (continuation.isActive) {
                continuation.resume(billingResult)
            }
        }
    }

    private suspend fun queryPurchasesSuspend(
        client: BillingClient,
        params: QueryPurchasesParams,
    ): Pair<BillingResult, List<Purchase>> = suspendCancellableCoroutine { continuation ->
        client.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (continuation.isActive) {
                continuation.resume(Pair(billingResult, purchasesList))
            }
        }
    }

    // --- Private helpers ---

    private suspend fun updateFirestoreState(userId: String, state: SubscriptionState) {
        try {
            val data = hashMapOf(
                "tier" to state.tier.name,
                "expiresAt" to state.expiresAt,
                "isAutoRenewing" to state.isAutoRenewing,
                "updatedAt" to System.currentTimeMillis(),
            )
            firestore.collection(SUBSCRIPTIONS_COLLECTION)
                .document(userId)
                .set(data)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firestore subscription state", e)
        }
    }

    private suspend fun determineTierFromPurchases(client: BillingClient): SubscriptionTier {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val (billingResult, purchases) = queryPurchasesSuspend(client, params)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return SubscriptionTier.FREE
        }

        return determineTierFromPurchaseList(purchases)
    }

    private fun determineTierFromPurchaseList(purchases: List<Purchase>): SubscriptionTier {
        val activeProducts = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }

        return if (PRODUCT_PRO in activeProducts) SubscriptionTier.PRO else SubscriptionTier.FREE
    }
}
