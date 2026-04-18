package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.IkigaiService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Ikigai routes — "Il Tuo Ikigai" guided exploration.
 *
 *   GET    /api/v1/ikigai       → caller's exploration (204 if none)
 *   PUT    /api/v1/ikigai       → upsert exploration
 *   DELETE /api/v1/ikigai/{id}  → delete (id must equal caller uid)
 */
fun Route.ikigaiRoutes() {
    val service by inject<IkigaiService>()

    authenticate("firebase") {
        route("/api/v1/ikigai") {

            get {
                val user = call.principal<UserPrincipal>()!!
                val exploration = service.getExploration(user.uid)
                if (exploration == null) call.respond(HttpStatusCode.NoContent)
                else call.respond(exploration)
            }

            put {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<IkigaiService.ExplorationDto>()
                call.respond(service.saveExploration(user.uid, body))
            }

            delete("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val ok = service.deleteExploration(user.uid, id)
                if (ok) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, IkigaiErrorDto("not found"))
            }
        }
    }
}

@Serializable
private data class IkigaiErrorDto(val error: String = "")
