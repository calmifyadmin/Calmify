package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.repository.SubscriptionRepository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * REST-backed SubscriptionRepository that talks to the Calmify server's
 * payment and subscription endpoints.
 *
 * The server owns all Stripe interactions. The client never sees API keys,
 * price_ids, or card data — only lookup_keys, URLs, and the resulting state.
 */
class KtorSubscriptionRepository(
    private val api: KtorApiClient,
) : SubscriptionRepository {

    private val _state = MutableStateFlow(SubscriptionState(tier = SubscriptionTier.FREE))

    override suspend fun getAvailableProducts(): RequestState<List<ProductInfo>> {
        val response = api.get<ProductListDto>("/api/v1/payments/products")
        return when (val data = response.getDataOrNull()) {
            null -> when (response) {
                is RequestState.Error -> response
                else -> RequestState.Error(Exception("Failed to load products"))
            }
            else -> RequestState.Success(data.products.map {
                ProductInfo(
                    lookupKey = it.lookupKey,
                    title = it.title,
                    description = it.description,
                    priceAmount = it.priceAmount,
                    currency = it.currency,
                    interval = it.interval,
                )
            })
        }
    }

    override suspend fun createCheckoutSession(lookupKey: String): RequestState<CheckoutSession> {
        val response = api.post<CheckoutSessionResponseDto>(
            path = "/api/v1/payments/checkout-session",
            body = CheckoutSessionRequestDto(lookupKey = lookupKey),
        )
        return when (val data = response.getDataOrNull()) {
            null -> when (response) {
                is RequestState.Error -> response
                else -> RequestState.Error(Exception("Checkout session creation failed"))
            }
            else -> RequestState.Success(CheckoutSession(url = data.url, sessionId = data.sessionId))
        }
    }

    override suspend fun refreshSubscriptionState(): RequestState<SubscriptionState> {
        val response = api.get<SubscriptionStateResponseDto>("/api/v1/subscription/state")
        return when (val data = response.getDataOrNull()) {
            null -> when (response) {
                is RequestState.Error -> response
                else -> RequestState.Error(Exception("Failed to read subscription state"))
            }
            else -> {
                val state = SubscriptionState(
                    tier = if (data.tier == "PRO") SubscriptionTier.PRO else SubscriptionTier.FREE,
                    expiresAt = data.expiresAt,
                    isAutoRenewing = data.isAutoRenewing,
                    status = data.status,
                )
                _state.value = state
                RequestState.Success(state)
            }
        }
    }

    override fun observeSubscription(): Flow<SubscriptionState> = _state.asStateFlow()
}

@Serializable
private data class CheckoutSessionRequestDto(
    val lookupKey: String = "",
    val successUrl: String = "",
    val cancelUrl: String = "",
)

@Serializable
private data class CheckoutSessionResponseDto(
    val url: String = "",
    val sessionId: String = "",
)

@Serializable
private data class SubscriptionStateResponseDto(
    val tier: String = "FREE",
    val expiresAt: Long = 0L,
    val isAutoRenewing: Boolean = false,
    val status: String = "none",
)

@Serializable
private data class ProductInfoResponseDto(
    val lookupKey: String = "",
    val priceId: String = "",
    val title: String = "",
    val description: String = "",
    val priceAmount: Long = 0L,
    val currency: String = "EUR",
    val interval: String = "month",
)

@Serializable
private data class ProductListDto(
    val products: List<ProductInfoResponseDto> = emptyList(),
)
