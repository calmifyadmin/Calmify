package com.lifo.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Security hardening plugin.
 *
 * - Security headers (HSTS, X-Content-Type-Options, X-Frame-Options, CSP, Referrer-Policy)
 * - Request body size limit (1MB default, 256KB for AI endpoints)
 * - Request ID injection for traceability
 * - HTTPS enforcement in production
 */
@OptIn(ExperimentalUuidApi::class)
fun Application.configureSecurity() {
    val isProduction = System.getenv("K_SERVICE") != null // Cloud Run sets K_SERVICE

    // Security headers via DefaultHeaders plugin
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "0") // Modern browsers: CSP replaces this
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        header(
            "Content-Security-Policy",
            "default-src 'none'; frame-ancestors 'none'"
        )
        if (isProduction) {
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        }
    }

    // Request ID + body size limit + HTTPS enforcement
    intercept(ApplicationCallPipeline.Plugins) {
        // Inject request ID for tracing
        val requestId = Uuid.random().toString()
        call.response.header("X-Request-Id", requestId)
        call.attributes.put(RequestIdKey, requestId)

        // HTTPS enforcement in production (Cloud Run terminates TLS, so check X-Forwarded-Proto)
        if (isProduction) {
            val proto = call.request.header("X-Forwarded-Proto")
            if (proto != null && proto != "https") {
                call.respond(HttpStatusCode.Forbidden, "HTTPS required")
                return@intercept
            }
        }

        // Body size limit — reject oversized payloads before parsing
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        val path = call.request.path()
        val maxBodySize = when {
            path.startsWith("/api/v1/ai/") -> 256L * 1024      // 256KB for AI
            path.startsWith("/api/v1/sync/batch") -> 512L * 1024 // 512KB for batch sync
            else -> 1L * 1024 * 1024                              // 1MB default
        }
        if (contentLength != null && contentLength > maxBodySize) {
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                mapOf("error" to "Request body too large (max ${maxBodySize / 1024}KB)")
            )
            return@intercept
        }
    }
}

val RequestIdKey = io.ktor.util.AttributeKey<String>("RequestId")
