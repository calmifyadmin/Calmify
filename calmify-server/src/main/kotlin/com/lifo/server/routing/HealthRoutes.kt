package com.lifo.server.routing

import com.google.firebase.FirebaseApp
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

fun Route.healthRoutes() {
    // No auth required — Cloud Run uses this for liveness/readiness probes
    get("/health") {
        val firebaseReady = FirebaseApp.getApps().isNotEmpty()
        val status = if (firebaseReady) "healthy" else "degraded"
        val httpStatus = if (firebaseReady) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

        call.respond(httpStatus, HealthResponse(
            status = status,
            firebase = firebaseReady,
            version = System.getenv("APP_VERSION") ?: "dev",
        ))
    }
}

@Serializable
data class HealthResponse(
    @ProtoNumber(1) val status: String,
    @ProtoNumber(2) val firebase: Boolean,
    @ProtoNumber(3) val version: String,
)
