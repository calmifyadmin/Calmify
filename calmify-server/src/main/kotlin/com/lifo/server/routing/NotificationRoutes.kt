package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.NotificationService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import org.koin.ktor.ext.inject

fun Route.notificationRoutes() {
    val notificationService by inject<NotificationService>()

    authenticate("firebase") {
        route("/api/v1/notifications") {

            // GET /api/v1/notifications
            get {
                val user = call.principal<UserPrincipal>()!!
                val params = PaginationParams.fromCall(call)
                val result = notificationService.getNotifications(user.uid, params)
                call.respond(mapOf("data" to result.items, "meta" to result.meta))
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
