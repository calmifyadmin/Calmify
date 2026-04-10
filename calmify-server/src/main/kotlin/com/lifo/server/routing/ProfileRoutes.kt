package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.ProfileService
import com.lifo.shared.api.ApiError
import com.lifo.shared.model.ProfileSettingsProto
import com.lifo.shared.model.PsychologicalProfileProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.koin.ktor.ext.inject

private val profileJson = Json { encodeDefaults = true }

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

            // GET /api/v1/profile/psychological?weeks=4
            get("/psychological") {
                val user = call.principal<UserPrincipal>()!!
                val weeks = call.parameters["weeks"]?.toIntOrNull() ?: 4
                val profiles = profileService.getPsychologicalProfiles(user.uid, weeks)
                val json = buildJsonObject {
                    put("data", profileJson.encodeToJsonElement(ListSerializer(PsychologicalProfileProto.serializer()), profiles))
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // GET /api/v1/profile/psychological/latest
            get("/psychological/latest") {
                val user = call.principal<UserPrincipal>()!!
                val profile = profileService.getLatestPsychologicalProfile(user.uid)
                if (profile != null) {
                    call.respond(profile)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(code = "NOT_FOUND", message = "No psychological profile found"),
                    )
                }
            }

            // GET /api/v1/profile/psychological/{weekNumber}/{year}
            get("/psychological/{weekNumber}/{year}") {
                val user = call.principal<UserPrincipal>()!!
                val weekNumber = call.parameters["weekNumber"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid weekNumber")
                val year = call.parameters["year"]?.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid year")
                val profile = profileService.getPsychologicalProfileByWeek(user.uid, weekNumber, year)
                if (profile != null) {
                    call.respond(profile)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(code = "NOT_FOUND", message = "Profile not found for week $weekNumber/$year"),
                    )
                }
            }
        }
    }
}
