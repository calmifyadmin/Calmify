package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.SocialService
import com.lifo.shared.api.*
import com.lifo.shared.model.ThreadProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.socialRoutes() {
    val socialService by inject<SocialService>()

    authenticate("firebase") {

        // --- Feed ---

        route("/api/v1/feed") {
            // GET /api/v1/feed/for-you
            get("/for-you") {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = socialService.getFeed(user.uid, params)
                call.respond(ThreadListResponse(success = true, data = result.items, meta = result.meta))
            }

            // GET /api/v1/feed/following
            get("/following") {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = socialService.getFollowingFeed(user.uid, params)
                call.respond(ThreadListResponse(success = true, data = result.items, meta = result.meta))
            }
        }

        // --- Threads ---

        route("/api/v1/threads") {
            // GET /api/v1/threads/{id}
            get("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                val thread = socialService.getThreadById(user.uid, id)
                if (thread != null) {
                    call.respond(ThreadResponse(success = true, data = thread))
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        ThreadResponse(error = ApiError(code = "NOT_FOUND", message = "Thread not found")))
                }
            }

            // GET /api/v1/threads/{id}/replies
            get("/{id}/replies") {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val id = call.parameters["id"]!!
                val result = socialService.getReplies(id, params)
                call.respond(ThreadListResponse(success = true, data = result.items, meta = result.meta))
            }

            // POST /api/v1/threads
            post {
                val user = call.principal<UserPrincipal>()!!
                val thread = call.receive<ThreadProto>()
                val created = socialService.createThread(user.uid, thread)
                call.respond(HttpStatusCode.Created, ThreadResponse(success = true, data = created))
            }

            // DELETE /api/v1/threads/{id}
            delete("/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                if (socialService.deleteThread(user.uid, id)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound,
                        ApiError(code = "NOT_FOUND", message = "Thread not found"))
                }
            }

            // POST /api/v1/threads/{id}/like
            post("/{id}/like") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                socialService.likeThread(user.uid, id)
                call.respond(HttpStatusCode.OK)
            }

            // DELETE /api/v1/threads/{id}/like
            delete("/{id}/like") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                socialService.unlikeThread(user.uid, id)
                call.respond(HttpStatusCode.OK)
            }

            // POST /api/v1/threads/{id}/repost
            post("/{id}/repost") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                socialService.repostThread(user.uid, id)
                call.respond(HttpStatusCode.OK)
            }
        }

        // --- Social Graph ---

        route("/api/v1/social") {
            // POST /api/v1/social/follow/{userId}
            post("/follow/{userId}") {
                val user = call.principal<UserPrincipal>()!!
                val targetId = call.parameters["userId"]!!
                socialService.follow(user.uid, targetId)
                call.respond(HttpStatusCode.OK)
            }

            // DELETE /api/v1/social/follow/{userId}
            delete("/follow/{userId}") {
                val user = call.principal<UserPrincipal>()!!
                val targetId = call.parameters["userId"]!!
                socialService.unfollow(user.uid, targetId)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
