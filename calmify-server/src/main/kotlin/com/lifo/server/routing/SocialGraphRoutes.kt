package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.SocialGraphService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.koin.ktor.ext.inject

/**
 * SocialGraph routes under /api/v1/social-graph.
 *
 *   POST   follow/{targetId}                   → follow target (caller = follower)
 *   DELETE follow/{targetId}                   → unfollow
 *   POST   block/{targetId}                    → block
 *   DELETE block/{targetId}                    → unblock
 *   GET    follow/{targetId}/check             → {following: Bool}
 *   GET    block/{targetId}/check              → {blocked: Bool}
 *   GET    followers/{userId}?limit=50         → UsersResponse
 *   GET    following/{userId}?limit=50         → UsersResponse
 *   GET    suggestions?limit=20                → UsersResponse (for caller)
 *   GET    profiles/{userId}                   → SocialUserDto
 *   PATCH  profiles/me                         → whitelisted field update
 *   GET    profiles/username-available?username=X → {available: Bool}
 *   GET    profiles/resolve-username?username=X   → {userId: String} (empty if none)
 */
fun Route.socialGraphRoutes() {
    val service by inject<SocialGraphService>()

    authenticate("firebase") {
        route("/api/v1/social-graph") {

            post("/follow/{targetId}") {
                val caller = call.principal<UserPrincipal>()!!
                val target = call.parameters["targetId"].orEmpty()
                if (target.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("targetId required"))
                    return@post
                }
                if (target == caller.uid) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("cannot follow self"))
                    return@post
                }
                service.follow(caller.uid, target)
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/follow/{targetId}") {
                val caller = call.principal<UserPrincipal>()!!
                val target = call.parameters["targetId"].orEmpty()
                if (target.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("targetId required"))
                    return@delete
                }
                service.unfollow(caller.uid, target)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/block/{targetId}") {
                val caller = call.principal<UserPrincipal>()!!
                val target = call.parameters["targetId"].orEmpty()
                if (target.isBlank() || target == caller.uid) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("invalid targetId"))
                    return@post
                }
                service.block(caller.uid, target)
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/block/{targetId}") {
                val caller = call.principal<UserPrincipal>()!!
                val target = call.parameters["targetId"].orEmpty()
                if (target.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("targetId required"))
                    return@delete
                }
                service.unblock(caller.uid, target)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/follow/{targetId}/check") {
                val caller = call.principal<UserPrincipal>()!!
                val target = call.parameters["targetId"].orEmpty()
                val following = if (target.isBlank()) false else service.isFollowing(caller.uid, target)
                call.respond(SocialGraphService.CheckFollowingResponse(following = following))
            }

            get("/block/{targetId}/check") {
                val caller = call.principal<UserPrincipal>()!!
                val target = call.parameters["targetId"].orEmpty()
                val blocked = if (target.isBlank()) false else service.isBlocked(caller.uid, target)
                call.respond(SocialGraphService.CheckBlockedResponse(blocked = blocked))
            }

            get("/followers/{userId}") {
                val userId = call.parameters["userId"].orEmpty()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                val users = if (userId.isBlank()) emptyList() else service.getFollowers(userId, limit)
                call.respond(SocialGraphService.UsersResponse(data = users))
            }

            get("/following/{userId}") {
                val userId = call.parameters["userId"].orEmpty()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 50
                val users = if (userId.isBlank()) emptyList() else service.getFollowing(userId, limit)
                call.respond(SocialGraphService.UsersResponse(data = users))
            }

            get("/suggestions") {
                val caller = call.principal<UserPrincipal>()!!
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
                val users = service.getSuggestions(caller.uid, limit)
                call.respond(SocialGraphService.UsersResponse(data = users))
            }

            get("/profiles/{userId}") {
                val userId = call.parameters["userId"].orEmpty()
                if (userId.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("userId required"))
                    return@get
                }
                call.respond(service.getProfile(userId))
            }

            patch("/profiles/me") {
                val caller = call.principal<UserPrincipal>()!!
                val body = call.receive<JsonObject>()
                val updates = body.toPrimitiveMap()
                call.respond(service.updateProfile(caller.uid, updates))
            }

            get("/profiles/username-available") {
                val username = call.request.queryParameters["username"].orEmpty()
                if (username.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("username required"))
                    return@get
                }
                call.respond(SocialGraphService.UsernameAvailableResponse(available = service.isUsernameAvailable(username)))
            }

            get("/profiles/resolve-username") {
                val username = call.request.queryParameters["username"].orEmpty()
                if (username.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, SgErrorDto("username required"))
                    return@get
                }
                call.respond(SocialGraphService.ResolveUsernameResponse(userId = service.resolveUsername(username)))
            }
        }
    }
}

@Serializable
private data class SgErrorDto(val error: String = "")

private fun JsonObject.toPrimitiveMap(): Map<String, Any?> = mapValues { (_, v) -> v.unwrap() }

private fun kotlinx.serialization.json.JsonElement.unwrap(): Any? = when (this) {
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> boolean
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
    is JsonArray -> map { it.unwrap() }
    is JsonObject -> toPrimitiveMap()
}
