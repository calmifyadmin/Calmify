package com.lifo.server.plugins

import com.lifo.shared.api.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        // 401 Unauthorized
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiError(code = "UNAUTHORIZED", message = "Invalid or missing authentication token"),
            )
        }

        // 403 Forbidden
        status(HttpStatusCode.Forbidden) { call, _ ->
            call.respond(
                HttpStatusCode.Forbidden,
                ApiError(code = "FORBIDDEN", message = "You do not have permission to access this resource"),
            )
        }

        // 404 Not Found
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiError(code = "NOT_FOUND", message = "Resource not found"),
            )
        }

        // 429 Too Many Requests
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiError(code = "RATE_LIMITED", message = "Too many requests. Please try again later."),
            )
        }

        // AI-specific exceptions — safe messages, no internal details
        exception<com.lifo.server.ai.ContentPolicyException> { call, cause ->
            val requestId = call.attributes.getOrNull(RequestIdKey) ?: "unknown"
            logger.warn("[{}] Content policy violation on {}", requestId, call.request.path())
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "CONTENT_POLICY", message = "Content policy violation"),
            )
        }

        exception<com.lifo.server.ai.QuotaExceededException> { call, cause ->
            val requestId = call.attributes.getOrNull(RequestIdKey) ?: "unknown"
            logger.info("[{}] Quota exceeded on {}", requestId, call.request.path())
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiError(code = "QUOTA_EXCEEDED", message = "AI usage quota exceeded"),
            )
        }

        // Bad request — sanitize: only expose safe validation messages
        exception<IllegalArgumentException> { call, cause ->
            val requestId = call.attributes.getOrNull(RequestIdKey) ?: "unknown"
            // Only log the sanitized message, never user-supplied data
            logger.warn("[{}] Bad request on {}: {}", requestId, call.request.path(), cause.message)
            // Only pass through messages that we control (from our own validation)
            val safeMessage = cause.message?.takeIf { it.length < 200 } ?: "Invalid request"
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "BAD_REQUEST", message = safeMessage),
            )
        }

        // Catch-all — NEVER expose internal details
        exception<Throwable> { call, cause ->
            val requestId = call.attributes.getOrNull(RequestIdKey) ?: "unknown"
            // Log class name + message only — no full stack trace in structured logs
            // Full stack trace goes to stderr for Cloud Logging
            logger.error(
                "[{}] Unhandled {} on {}: {}",
                requestId,
                cause::class.simpleName,
                call.request.path(),
                cause.message,
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "INTERNAL_ERROR", message = "An unexpected error occurred"),
            )
        }
    }
}
