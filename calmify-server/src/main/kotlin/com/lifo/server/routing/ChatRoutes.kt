package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.ChatService
import com.lifo.shared.api.*
import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.chatRoutes() {
    val chatService by inject<ChatService>()

    authenticate("firebase") {
        route("/api/v1/chat") {

            // --- Sessions ---

            // GET /api/v1/chat/sessions
            get("/sessions") {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = chatService.getSessions(user.uid, params)
                call.respond(ChatSessionListResponse(data = result.items, meta = result.meta))
            }

            // GET /api/v1/chat/sessions/{id}
            get("/sessions/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                val session = chatService.getSessionById(user.uid, id)
                if (session != null) {
                    call.respond(ChatSessionResponse(data = session))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ChatSessionResponse(error = ApiError(code = "NOT_FOUND", message = "Session not found")),
                    )
                }
            }

            // POST /api/v1/chat/sessions
            post("/sessions") {
                val user = call.principal<UserPrincipal>()!!
                val session = call.receive<ChatSessionProto>()
                val created = chatService.createSession(user.uid, session)
                call.respond(HttpStatusCode.Created, ChatSessionResponse(data = created))
            }

            // DELETE /api/v1/chat/sessions/{id}
            delete("/sessions/{id}") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                if (chatService.deleteSession(user.uid, id)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiError(code = "NOT_FOUND", message = "Session not found"),
                    )
                }
            }

            // --- Messages ---

            // GET /api/v1/chat/sessions/{sessionId}/messages
            get("/sessions/{sessionId}/messages") {
                val user = call.principal<UserPrincipal>()!!
                val sessionId = call.parameters["sessionId"]!!
                val params = PaginationParams.fromCall(call)
                val result = chatService.getMessages(user.uid, sessionId, params)
                call.respond(ChatMessageListResponse(data = result.items, meta = result.meta))
            }

            // POST /api/v1/chat/sessions/{sessionId}/messages
            post("/sessions/{sessionId}/messages") {
                val user = call.principal<UserPrincipal>()!!
                val sessionId = call.parameters["sessionId"]!!
                val message = call.receive<ChatMessageProto>()
                val sent = chatService.sendMessage(user.uid, sessionId, message)
                call.respond(HttpStatusCode.Created, sent)
            }
        }
    }
}
