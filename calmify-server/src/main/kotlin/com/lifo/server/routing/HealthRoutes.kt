package com.lifo.server.routing

import com.google.firebase.FirebaseApp
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

fun Route.healthRoutes() {
    // No auth required — Cloud Run uses this for liveness/readiness probes
    // SECURITY: Only expose status, no version or internal service info
    get("/health") {
        val firebaseReady = FirebaseApp.getApps().isNotEmpty()
        val status = if (firebaseReady) "healthy" else "degraded"
        val httpStatus = if (firebaseReady) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

        call.respond(httpStatus, HealthResponse(
            status = status,
            firebase = firebaseReady,
        ))
    }
}

@Serializable
data class HealthResponse(
    @ProtoNumber(1) val status: String,
    @ProtoNumber(2) val firebase: Boolean,
)
