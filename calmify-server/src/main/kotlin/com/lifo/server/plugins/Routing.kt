package com.lifo.server.plugins

import com.lifo.server.routing.healthRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        healthRoutes()
        // Future route groups will be added here:
        // diaryRoutes()
        // chatRoutes()
        // insightRoutes()
        // profileRoutes()
        // wellnessRoutes()
        // socialRoutes()
        // mediaRoutes()
        // notificationRoutes()
        // realtimeRoutes()
    }
}
