package com.lifo.server.routing

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.MessagingEvent
import com.lifo.server.service.MessagingHub
import com.lifo.server.service.MessagingService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

/**
 * Messaging routes — REST (authenticated) + WebSocket fan-out.
 *
 *  REST under /api/v1/messaging:
 *    GET    /conversations
 *    POST   /conversations
 *    GET    /conversations/{id}/messages?limit=50
 *    POST   /conversations/{id}/messages
 *    POST   /conversations/{id}/read
 *    POST   /conversations/{id}/typing
 *    GET    /conversations/{id}/typing
 *
 *  WebSocket:
 *    /api/v1/messaging/ws?token={firebaseIdToken}
 *    JWT in query param (browser WS handshake has no custom headers).
 *    Incoming frames (JSON):
 *      {type:"auth.refresh", token:"..."}   — rotate JWT mid-connection
 *      {type:"ping"}                          — keepalive
 */
fun Route.messagingRoutes() {
    val service by inject<MessagingService>()
    val hub by inject<MessagingHub>()
    val log = LoggerFactory.getLogger("MessagingRoutes")

    val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // REST — authenticated with Firebase bearer
    authenticate("firebase") {
        route("/api/v1/messaging") {

            get("/conversations") {
                val user = call.principal<UserPrincipal>()!!
                val list = service.listConversations(user.uid)
                call.respond(MessagingService.ConversationsResponse(list))
            }

            post("/conversations") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<CreateConversationRequest>()
                if (body.participantIds.size < 2) {
                    call.respond(HttpStatusCode.BadRequest, MsgErrorDto("participantIds must have >= 2"))
                    return@post
                }
                val conv = service.createConversation(user.uid, body.participantIds)
                call.respond(conv)
            }

            get("/conversations/{id}/messages") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val list = service.listMessages(user.uid, id, limit)
                call.respond(MessagingService.MessagesResponse(list))
            }

            post("/conversations/{id}/messages") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val body = call.receive<SendMessageRequest>()
                if (body.text.isBlank() && body.imageUrls.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, MsgErrorDto("message must have text or images"))
                    return@post
                }
                val result = service.sendMessage(user.uid, id, body.text, body.imageUrls)

                // Fan out message + conversation update to all participants
                hub.broadcast(
                    MessagingEvent.MessageCreated(conversationId = id, message = result.message),
                    result.participantIds,
                )
                hub.broadcast(
                    MessagingEvent.ConversationUpdated(
                        conversationId = id,
                        lastMessage = result.lastMessagePreview,
                        lastMessageAt = result.message.createdAt,
                    ),
                    result.participantIds,
                )
                call.respond(result.message)
            }

            post("/conversations/{id}/read") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val count = service.markRead(user.uid, id)
                call.respond(MarkReadResponse(markedCount = count))
            }

            post("/conversations/{id}/typing") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val body = call.receive<TypingRequest>()
                val participants = service.setTyping(user.uid, id, body.isTyping)
                hub.broadcast(
                    MessagingEvent.TypingUpdated(
                        conversationId = id,
                        userId = user.uid,
                        isTyping = body.isTyping,
                    ),
                    participants,
                )
                call.respond(HttpStatusCode.NoContent)
            }

            get("/conversations/{id}/typing") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"].orEmpty()
                val ids = service.getTypingUserIds(user.uid, id)
                call.respond(MessagingService.TypingStatusResponse(ids))
            }
        }
    }

    // WebSocket — auth via query-param token (handshake limitation)
    webSocket("/api/v1/messaging/ws") {
        val token = call.request.queryParameters["token"]
        if (token.isNullOrBlank()) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "missing token"))
            return@webSocket
        }
        val uid = verifyFirebaseToken(token)
        if (uid == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
            return@webSocket
        }

        var currentUid: String = uid
        hub.register(currentUid, this)
        runCatching {
            send(Frame.Text(json.encodeToString(MessagingEvent.serializer(), MessagingEvent.AuthOk(currentUid))))
        }

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val txt = frame.readText()
                val parsed = runCatching { json.decodeFromString<ClientFrame>(txt) }.getOrNull() ?: continue
                when (parsed) {
                    is ClientFrame.AuthRefresh -> {
                        val newUid = verifyFirebaseToken(parsed.token)
                        if (newUid == null) {
                            send(Frame.Text(json.encodeToString(MessagingEvent.serializer(), MessagingEvent.Error("invalid token"))))
                        } else if (newUid != currentUid) {
                            // Token now binds a different uid — rebind
                            hub.unregister(currentUid, this)
                            currentUid = newUid
                            hub.register(currentUid, this)
                            send(Frame.Text(json.encodeToString(MessagingEvent.serializer(), MessagingEvent.AuthOk(currentUid))))
                        } else {
                            send(Frame.Text(json.encodeToString(MessagingEvent.serializer(), MessagingEvent.AuthOk(currentUid))))
                        }
                    }
                    is ClientFrame.Ping -> {
                        send(Frame.Text("{\"type\":\"pong\"}"))
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("ws.loop.error ${e.message}")
        } finally {
            hub.unregister(currentUid, this)
        }
    }
}

private fun verifyFirebaseToken(token: String): String? = try {
    FirebaseAuth.getInstance().verifyIdToken(token).uid
} catch (e: FirebaseAuthException) {
    null
} catch (e: Exception) {
    null
}

@Serializable
private sealed class ClientFrame {
    @Serializable
    @SerialName("auth.refresh")
    data class AuthRefresh(val token: String) : ClientFrame()

    @Serializable
    @SerialName("ping")
    data object Ping : ClientFrame()
}

@Serializable
data class CreateConversationRequest(val participantIds: List<String> = emptyList())

@Serializable
data class SendMessageRequest(
    val text: String = "",
    val imageUrls: List<String> = emptyList(),
)

@Serializable
data class TypingRequest(val isTyping: Boolean = false)

@Serializable
data class MarkReadResponse(val markedCount: Int = 0)

@Serializable
private data class MsgErrorDto(val error: String = "")
