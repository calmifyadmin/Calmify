package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.StripeService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.paymentRoutes() {
    val stripe by inject<StripeService>()

    authenticate("firebase") {
        route("/api/v1/payments") {
            // POST /api/v1/payments/checkout-session
            post("/checkout-session") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<CheckoutSessionRequestDto>()

                if (body.lookupKey !in StripeService.LOOKUP_KEYS) {
                    call.respond(HttpStatusCode.BadRequest, ErrorDto("invalid_lookup_key"))
                    return@post
                }

                val result = stripe.createCheckoutSession(
                    userId = user.uid,
                    userEmail = user.email,
                    lookupKey = body.lookupKey,
                    successUrl = body.successUrl.takeIf { it.isNotBlank() }
                        ?: DEFAULT_SUCCESS_URL,
                    cancelUrl = body.cancelUrl.takeIf { it.isNotBlank() }
                        ?: DEFAULT_CANCEL_URL,
                )

                call.respond(
                    HttpStatusCode.Created,
                    CheckoutSessionResponseDto(url = result.url, sessionId = result.sessionId)
                )
            }

            // GET /api/v1/payments/products
            get("/products") {
                val products = stripe.listProducts().map {
                    ProductInfoResponseDto(
                        lookupKey = it.lookupKey,
                        priceId = it.priceId,
                        title = it.title,
                        description = it.description,
                        priceAmount = it.priceAmount,
                        currency = it.currency,
                        interval = it.interval,
                    )
                }
                call.respond(HttpStatusCode.OK, ProductListResponseDto(products))
            }
        }

        // GET /api/v1/subscription/state
        route("/api/v1/subscription") {
            get("/state") {
                val user = call.principal<UserPrincipal>()!!
                val state = stripe.getSubscriptionState(user.uid)
                call.respond(
                    HttpStatusCode.OK,
                    SubscriptionStateResponseDto(
                        tier = state.tier,
                        expiresAt = state.expiresAt,
                        isAutoRenewing = state.isAutoRenewing,
                        status = state.status,
                    )
                )
            }
        }
    }
}

// Deep-link destinations — the browser returns here after Stripe checkout.
// Client deep-link scheme: calmify://subscription/{success|cancel}
private const val DEFAULT_SUCCESS_URL = "https://calmify.app/subscription/success?session_id={CHECKOUT_SESSION_ID}"
private const val DEFAULT_CANCEL_URL = "https://calmify.app/subscription/cancel"

@Serializable
data class CheckoutSessionRequestDto(
    val lookupKey: String = "",
    val successUrl: String = "",
    val cancelUrl: String = "",
)

@Serializable
data class CheckoutSessionResponseDto(
    val url: String = "",
    val sessionId: String = "",
)

@Serializable
data class SubscriptionStateResponseDto(
    val tier: String = "FREE",
    val expiresAt: Long = 0L,
    val isAutoRenewing: Boolean = false,
    val status: String = "none",
)

@Serializable
data class ProductInfoResponseDto(
    val lookupKey: String = "",
    val priceId: String = "",
    val title: String = "",
    val description: String = "",
    val priceAmount: Long = 0L,
    val currency: String = "EUR",
    val interval: String = "month",
)

@Serializable
data class ProductListResponseDto(
    val products: List<ProductInfoResponseDto> = emptyList(),
)

@Serializable
data class ErrorDto(
    val error: String = "",
)
