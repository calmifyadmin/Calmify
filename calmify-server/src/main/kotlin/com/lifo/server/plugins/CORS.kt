package com.lifo.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    val isProduction = System.getenv("K_SERVICE") != null // Cloud Run sets K_SERVICE

    install(CORS) {
        // Methods — only what we actually use
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // Headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        // Origins — strict in production, relaxed in dev
        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",")?.map { it.trim() }
        if (allowedOrigins != null) {
            allowedOrigins.forEach { allowHost(it) }
        } else if (isProduction) {
            // SECURITY: No ALLOWED_ORIGINS in production = deny all cross-origin
            // Mobile app doesn't need CORS, so this is safe
        } else {
            // Development only
            allowHost("localhost:3000")
            allowHost("localhost:8080")
        }

        allowCredentials = true
        maxAgeInSeconds = 3600
    }
}
