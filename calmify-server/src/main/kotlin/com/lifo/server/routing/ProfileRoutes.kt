package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.ProfileService
import com.lifo.shared.api.ApiError
import com.lifo.shared.model.ProfileSettingsProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.profileRoutes() {
    val profileService by inject<ProfileService>()

    authenticate("firebase") {
        route("/api/v1/profile") {

            // GET /api/v1/profile
            get {
                val user = call.principal<UserPrincipal>()!!
                val profile = profileService.getProfile(user.uid)
                if (profile != null) {
                    call.respond(profile)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(code = "NOT_FOUND", message = "Profile not found"),
                    )
                }
            }

            // PUT /api/v1/profile
            put {
                val user = call.principal<UserPrincipal>()!!
                val profile = call.receive<ProfileSettingsProto>()
                val updated = profileService.updateProfile(user.uid, profile)
                call.respond(updated)
            }
        }
    }
}
