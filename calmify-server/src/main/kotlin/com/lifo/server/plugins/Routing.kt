package com.lifo.server.plugins

import com.lifo.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Infrastructure
        healthRoutes()

        // Core CRUD
        diaryRoutes()
        chatRoutes()
        insightRoutes()
        profileRoutes()

        // Social
        socialRoutes()

        // Aggregated + Config
        dashboardRoutes()
        notificationRoutes()

        // Wellness routes are wired via WellnessServiceFactory in ServerModule
        // Each type gets its own /api/v1/wellness/{type}/ CRUD endpoints
        // See ServerModule.kt for the full wiring
    }
}
