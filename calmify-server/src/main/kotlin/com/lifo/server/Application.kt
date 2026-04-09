package com.lifo.server

import com.lifo.server.di.serverModule
import com.lifo.server.firebase.FirebaseAdmin
import com.lifo.server.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    // Firebase Admin SDK — must init before auth middleware
    FirebaseAdmin.init()

    // DI
    install(Koin) {
        slf4jLogger()
        modules(serverModule)
    }

    // Security — must be early in pipeline (headers, body limits, HTTPS enforcement)
    configureSecurity()

    // Plugins
    configureAuthentication()
    configureSerialization()
    configureRateLimiting()
    configureCORS()
    configureMonitoring()
    configureStatusPages()

    // Audit logging — after auth so we can extract userId
    configureAuditLog()

    // Routes
    configureRouting()
}
