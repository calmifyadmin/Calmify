package com.lifo.server.plugins

import com.lifo.shared.api.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
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

        // AI-specific exceptions
        exception<com.lifo.server.ai.ContentPolicyException> { call, cause ->
            logger.warn("Content policy violation: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "CONTENT_POLICY", message = cause.message ?: "Content policy violation"),
            )
        }

        exception<com.lifo.server.ai.QuotaExceededException> { call, cause ->
            logger.info("Quota exceeded: ${cause.message}")
            call.respond(
                HttpStatusCode.TooManyRequests,
                ApiError(code = "QUOTA_EXCEEDED", message = cause.message ?: "Quota exceeded"),
            )
        }

        // Generic exceptions — never expose stack traces
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Bad request: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError(code = "BAD_REQUEST", message = cause.message ?: "Invalid request"),
            )
        }

        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "INTERNAL_ERROR", message = "An unexpected error occurred"),
            )
        }
    }
}
