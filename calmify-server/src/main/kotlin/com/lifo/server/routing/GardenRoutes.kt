package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.GardenService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Garden routes — explored activities + favorites.
 *
 *   GET  /api/v1/garden                         → current state
 *   POST /api/v1/garden/explored/{activityId}   → mark explored (idempotent)
 *   POST /api/v1/garden/favorites/{activityId}  → toggle favorite
 */
fun Route.gardenRoutes() {
    val service by inject<GardenService>()

    authenticate("firebase") {
        route("/api/v1/garden") {

            get {
                val user = call.principal<UserPrincipal>()!!
                call.respond(service.getState(user.uid))
            }

            post("/explored/{activityId}") {
                val user = call.principal<UserPrincipal>()!!
                val activityId = call.parameters["activityId"].orEmpty()
                if (activityId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, GardenErrorDto("activityId required"))
                    return@post
                }
                call.respond(service.markExplored(user.uid, activityId))
            }

            post("/favorites/{activityId}") {
                val user = call.principal<UserPrincipal>()!!
                val activityId = call.parameters["activityId"].orEmpty()
                if (activityId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, GardenErrorDto("activityId required"))
                    return@post
                }
                call.respond(service.toggleFavorite(user.uid, activityId))
            }
        }
    }
}

@Serializable
private data class GardenErrorDto(val error: String = "")
