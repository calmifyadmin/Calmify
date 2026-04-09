package com.lifo.server.plugins

import com.lifo.server.security.UserPrincipal
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimiting() {
    install(RateLimit) {
        // Default: 100 requests per minute per user
        register(RateLimitName("default")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<UserPrincipal>()?.uid ?: call.request.local.remoteAddress
            }
        }
        // Write operations: 30 per minute
        register(RateLimitName("write")) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<UserPrincipal>()?.uid ?: call.request.local.remoteAddress
            }
        }
        // Search: 20 per minute
        register(RateLimitName("search")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<UserPrincipal>()?.uid ?: call.request.local.remoteAddress
            }
        }
        // AI endpoints: 10 per minute (expensive)
        register(RateLimitName("ai")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<UserPrincipal>()?.uid ?: call.request.local.remoteAddress
            }
        }
        // Health check: no auth needed, generous limit
        register(RateLimitName("health")) {
            rateLimiter(limit = 60, refillPeriod = 30.seconds)
            requestKey { call ->
                call.request.local.remoteAddress
            }
        }
    }
}
