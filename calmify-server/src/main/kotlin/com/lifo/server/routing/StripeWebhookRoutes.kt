package com.lifo.server.routing

import com.lifo.server.service.StripeService
import com.stripe.exception.SignatureVerificationException
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("StripeWebhook")

/**
 * Stripe webhook receiver.
 *
 * - NOT protected by Firebase auth — Stripe signs the payload with the shared webhook secret.
 * - The signing secret is NEVER the API secret key; it comes from the Stripe Dashboard
 *   webhook endpoint and is hot-swappable via Cloud Run Secret Manager.
 * - Test and live modes both POST here; only the secret differs.
 */
fun Route.stripeWebhookRoutes() {
    val stripe by inject<StripeService>()
    val webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET") ?: ""

    post("/api/v1/webhooks/stripe") {
        if (webhookSecret.isBlank()) {
            log.error("STRIPE_WEBHOOK_SECRET not configured — rejecting webhook")
            call.respond(HttpStatusCode.ServiceUnavailable, "webhook_not_configured")
            return@post
        }

        val signature = call.request.headers["Stripe-Signature"]
        if (signature.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "missing_signature")
            return@post
        }

        val payload = call.receiveText()

        val event = try {
            stripe.parseAndVerifyEvent(payload, signature, webhookSecret)
        } catch (e: SignatureVerificationException) {
            log.warn("Invalid Stripe signature: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "invalid_signature")
            return@post
        } catch (e: Exception) {
            log.error("Failed to parse Stripe event", e)
            call.respond(HttpStatusCode.BadRequest, "invalid_payload")
            return@post
        }

        try {
            stripe.applyEvent(event)
        } catch (e: Exception) {
            log.error("Failed to apply Stripe event ${event.id} type=${event.type}", e)
            // 500 → Stripe retries automatically for up to 72h
            call.respond(HttpStatusCode.InternalServerError, "processing_failed")
            return@post
        }

        call.respond(HttpStatusCode.OK, "ok")
    }
}
