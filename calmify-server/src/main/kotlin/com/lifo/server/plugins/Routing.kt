package com.lifo.server.plugins

import com.lifo.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        healthRoutes()
        diaryRoutes()
        chatRoutes()
        insightRoutes()
        profileRoutes()
        // wellnessRoutes() — will be wired when WellnessServiceFactory is ready
        // socialRoutes()
        // mediaRoutes()
        // notificationRoutes()
        // realtimeRoutes()
    }
}
