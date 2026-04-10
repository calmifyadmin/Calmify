package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.DashboardService
import com.lifo.server.service.FeatureFlagService
import com.lifo.shared.api.HomeDashboardResponse
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.dashboardRoutes() {
    val dashboardService by inject<DashboardService>()
    val featureFlagService by inject<FeatureFlagService>()

    authenticate("firebase") {
        // GET /api/v1/home/dashboard — single call replaces 5+ client calls
        get("/api/v1/home/dashboard") {
            val user = call.principal<UserPrincipal>()!!
            val timezone = call.parameters["timezone"] ?: "UTC"
            val dashboard = dashboardService.getHomeDashboard(user.uid, timezone)
            call.respond(HomeDashboardResponse(success = true, data = dashboard))
        }
    }

    // GET /api/v1/feature-flags — no auth needed, cached 5min
    get("/api/v1/feature-flags") {
        val flags = featureFlagService.getFlags()
        val json = kotlinx.serialization.json.buildJsonObject {
            put("flags", kotlinx.serialization.json.JsonObject(flags.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))
        }
        call.respondText(json.toString(), io.ktor.http.ContentType.Application.Json)
    }
}
