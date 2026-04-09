package com.lifo.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        // Methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)

        // Headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        // Origins — restrict in production
        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(",")
        if (allowedOrigins != null) {
            allowedOrigins.forEach { allowHost(it.trim()) }
        } else {
            // Development: allow localhost
            allowHost("localhost:3000")
            allowHost("localhost:8080")
        }

        allowCredentials = true
        maxAgeInSeconds = 3600
    }
}
