package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.PresenceService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.builtins.ListSerializer
import org.koin.ktor.ext.inject

private val presenceJson = Json { encodeDefaults = true }

fun Route.presenceRoutes() {
    val presenceService by inject<PresenceService>()

    authenticate("firebase") {
        route("/api/v1/presence") {

            // POST /api/v1/presence/online
            post("/online") {
                val user = call.principal<UserPrincipal>()!!
                presenceService.setOnline(user.uid)
                call.respond(HttpStatusCode.OK)
            }

            // POST /api/v1/presence/offline
            post("/offline") {
                val user = call.principal<UserPrincipal>()!!
                presenceService.setOffline(user.uid)
                call.respond(HttpStatusCode.OK)
            }

            // GET /api/v1/presence/{userId}
            get("/{userId}") {
                val userId = call.parameters["userId"]!!
                val presence = presenceService.getPresence(userId)
                call.respond(presence)
            }

            // GET /api/v1/presence?userIds=id1,id2,id3
            get {
                val userIds = call.request.queryParameters["userIds"]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                if (userIds.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                // Limit to 50 users per request
                val limited = userIds.take(50)
                val results = presenceService.getMultiplePresence(limited)
                val json = buildJsonObject {
                    put("data", presenceJson.encodeToJsonElement(
                        ListSerializer(PresenceService.PresenceData.serializer()), results
                    ))
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }
        }
    }
}
