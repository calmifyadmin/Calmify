package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.AvatarService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Avatar routes — server-mediated 2-stage pipeline.
 *
 *   POST   /api/v1/avatars                → 202 Accepted, body: { avatarId } — triggers async pipeline
 *   GET    /api/v1/avatars                → list caller's avatars (desc by createdAt)
 *   GET    /api/v1/avatars/{id}           → single avatar
 *   DELETE /api/v1/avatars/{id}           → delete
 *   PATCH  /api/v1/avatars/{id}/status    → force status update (admin/edge-case)
 *
 * Clients poll GET endpoints until status ∈ {READY, ERROR}.
 */
fun Route.avatarRoutes() {
    val service by inject<AvatarService>()

    authenticate("firebase") {
        route("/api/v1/avatars") {

            post {
                val user = call.principal<UserPrincipal>()!!
                val form = call.receive<AvatarService.AvatarFormDto>()
                if (form.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AvatarErrorDto("name required"))
                    return@post
                }
                val avatarId = service.createAvatar(user.uid, form)
                call.respond(HttpStatusCode.Accepted, AvatarService.CreateAvatarResponse(avatarId = avatarId))
            }

            get {
                val user = call.principal<UserPrincipal>()!!
                val list = service.listAvatars(user.uid)
                call.respond(AvatarService.AvatarsResponse(data = list))
            }

            get("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                if (id.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AvatarErrorDto("avatar id required"))
                    return@get
                }
                val avatar = service.getAvatar(user.uid, id)
                if (avatar == null) call.respond(HttpStatusCode.NotFound, AvatarErrorDto("not found"))
                else call.respond(avatar)
            }

            delete("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val ok = service.deleteAvatar(user.uid, id)
                if (ok) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, AvatarErrorDto("not found"))
            }

            patch("/{id}/status") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val body = call.receive<AvatarService.UpdateStatusRequest>()
                if (body.status.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, AvatarErrorDto("status required"))
                    return@patch
                }
                val ok = service.updateStatus(user.uid, id, body.status)
                if (ok) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, AvatarErrorDto("not found"))
            }
        }
    }
}

@Serializable
private data class AvatarErrorDto(val error: String = "")
