package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.EnvironmentService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Environment design routes — checklist + morning/evening routines.
 *
 *   GET  /api/v1/environment           → current user's checklist (or null if not set)
 *   PUT  /api/v1/environment           → upsert checklist (body: ChecklistDto)
 */
fun Route.environmentRoutes() {
    val service by inject<EnvironmentService>()

    authenticate("firebase") {
        route("/api/v1/environment") {

            get {
                val user = call.principal<UserPrincipal>()!!
                val checklist = service.getChecklist(user.uid)
                if (checklist == null) call.respond(HttpStatusCode.NoContent)
                else call.respond(checklist)
            }

            put {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<EnvironmentService.ChecklistDto>()
                val saved = service.saveChecklist(user.uid, body)
                call.respond(saved)
            }
        }
    }
}

@Serializable
private data class EnvErrorDto(val error: String = "")
