package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.NotificationService
import com.lifo.server.service.NotificationProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.ktor.ext.inject

private val notifJson = Json { encodeDefaults = true }

fun Route.notificationRoutes() {
    val notificationService by inject<NotificationService>()

    authenticate("firebase") {
        route("/api/v1/notifications") {

            // GET /api/v1/notifications — JSON (list wrapper can't be Protobuf)
            get {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = notificationService.getNotifications(user.uid, params)
                val json = buildJsonObject {
                    put("data", notifJson.encodeToJsonElement(ListSerializer(NotificationProto.serializer()), result.items))
                    put("meta", notifJson.encodeToJsonElement(result.meta))
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // GET /api/v1/notifications/unread-count
            get("/unread-count") {
                val user = call.principal<UserPrincipal>()!!
                val count = notificationService.getUnreadCount(user.uid)
                call.respond(UnreadCountResponse(count = count))
            }

            // POST /api/v1/notifications/{id}/read
            post("/{id}/read") {
                val user = call.principal<UserPrincipal>()!!
                val id = call.parameters["id"]!!
                notificationService.markAsRead(user.uid, id)
                call.respond(HttpStatusCode.OK)
            }

            // POST /api/v1/notifications/read-all
            post("/read-all") {
                val user = call.principal<UserPrincipal>()!!
                notificationService.markAllAsRead(user.uid)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

@Serializable
data class UnreadCountResponse(
    @ProtoNumber(1) val count: Int = 0,
)
