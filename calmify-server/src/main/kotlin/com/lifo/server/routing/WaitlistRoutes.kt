package com.lifo.server.routing

import com.google.cloud.firestore.Firestore
import com.lifo.server.security.UserPrincipal
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.waitlistRoutes() {
    val db by inject<Firestore>()

    authenticate("firebase") {
        route("/api/v1/waitlist") {
            // POST /api/v1/waitlist — save waitlist email signup
            post {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<WaitlistRequestDto>()

                withContext(Dispatchers.IO) {
                    val data = hashMapOf<String, Any>(
                        "email" to body.email,
                        "userId" to (body.userId.takeIf { it.isNotEmpty() } ?: user.uid),
                        "source" to body.source,
                        "createdAt" to System.currentTimeMillis(),
                    )
                    db.collection("waitlist").document(user.uid).set(data).get()
                }

                call.respond(HttpStatusCode.Created, WaitlistResponseDto(success = true))
            }
        }
    }
}

@Serializable
data class WaitlistRequestDto(
    val email: String = "",
    val userId: String = "",
    val source: String = "",
)

@Serializable
data class WaitlistResponseDto(
    val success: Boolean = true,
)
