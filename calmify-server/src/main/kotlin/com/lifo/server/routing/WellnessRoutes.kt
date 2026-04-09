package com.lifo.server.routing

import com.lifo.server.model.PaginationParams
import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.GenericWellnessService
import com.lifo.shared.api.ApiError
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class WellnessListResponse<T>(
    @ProtoNumber(1) val data: List<T> = emptyList(),
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: com.lifo.shared.api.PaginationMeta? = null,
)

inline fun <reified T : Any> Route.wellnessCrudRoutes(
    service: GenericWellnessService<T>,
) {
    authenticate("firebase") {
        // GET /
        get {
            val user = call.principal<UserPrincipal>()!!
            val params = PaginationParams.fromCall(call)
            val result = service.list(user.uid, params)
            call.respond(mapOf("data" to result.items, "meta" to result.meta))
        }

        // GET /{id}
        get("/{id}") {
            val user = call.principal<UserPrincipal>()!!
            val id = call.parameters["id"]!!
            val item = service.getById(user.uid, id)
            if (item != null) {
                call.respond(item)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiError(code = "NOT_FOUND", message = "Not found"),
                )
            }
        }

        // GET /day/{dayKey}
        get("/day/{dayKey}") {
            val user = call.principal<UserPrincipal>()!!
            val dayKey = call.parameters["dayKey"]!!
            val items = service.getByDayKey(user.uid, dayKey)
            call.respond(mapOf("data" to items))
        }

        // POST /
        post {
            val user = call.principal<UserPrincipal>()!!
            val item = call.receive<T>()
            val created = service.create(user.uid, item)
            call.respond(HttpStatusCode.Created, created)
        }

        // PUT /{id}
        put("/{id}") {
            val user = call.principal<UserPrincipal>()!!
            val id = call.parameters["id"]!!
            val item = call.receive<T>()
            val updated = service.update(user.uid, id, item)
            if (updated != null) {
                call.respond(updated)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiError(code = "NOT_FOUND", message = "Not found"),
                )
            }
        }

        // DELETE /{id}
        delete("/{id}") {
            val user = call.principal<UserPrincipal>()!!
            val id = call.parameters["id"]!!
            if (service.delete(user.uid, id)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ApiError(code = "NOT_FOUND", message = "Not found"),
                )
            }
        }
    }
}

/**
 * Habit-specific routes with completion tracking.
 */
fun Route.habitCompletionRoutes(
    habitService: GenericWellnessService<com.lifo.shared.model.HabitProto>,
    db: com.google.cloud.firestore.Firestore,
) {
    authenticate("firebase") {
        // POST /api/v1/wellness/habits/{habitId}/toggle?dayKey=2026-04-10
        post("/{habitId}/toggle") {
            val user = call.principal<UserPrincipal>()!!
            val habitId = call.parameters["habitId"]!!
            val dayKey = call.parameters["dayKey"]
                ?: throw IllegalArgumentException("Missing dayKey parameter")

            val firestore = db
            val completionId = "${habitId}_$dayKey"
            val docRef = firestore.collection("habitCompletions").document(completionId)
            val existing = docRef.get().get()

            if (existing.exists()) {
                docRef.delete().get()
                call.respond(mapOf("completed" to false))
            } else {
                docRef.set(
                    hashMapOf<String, Any>(
                        "habitId" to habitId,
                        "ownerId" to user.uid,
                        "dayKey" to dayKey,
                        "completedAt" to System.currentTimeMillis(),
                    ),
                ).get()
                call.respond(mapOf("completed" to true))
            }
        }

        // GET /api/v1/wellness/habits/completions/day/{dayKey}
        get("/completions/day/{dayKey}") {
            val user = call.principal<UserPrincipal>()!!
            val dayKey = call.parameters["dayKey"]!!
            val firestore = db

            val completions = firestore.collection("habitCompletions")
                .whereEqualTo("ownerId", user.uid)
                .whereEqualTo("dayKey", dayKey)
                .get().get().documents

            val items = completions.map { doc ->
                mapOf(
                    "id" to doc.id,
                    "habitId" to (doc.getString("habitId") ?: ""),
                    "dayKey" to (doc.getString("dayKey") ?: ""),
                    "completedAt" to (doc.getLong("completedAt") ?: 0L),
                )
            }
            call.respond(mapOf("data" to items))
        }

        // GET /api/v1/wellness/habits/{habitId}/completions?limit=30
        get("/{habitId}/completions") {
            val user = call.principal<UserPrincipal>()!!
            val habitId = call.parameters["habitId"]!!
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 30
            val firestore = db

            val completions = firestore.collection("habitCompletions")
                .whereEqualTo("ownerId", user.uid)
                .whereEqualTo("habitId", habitId)
                .orderBy("completedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get().get().documents

            val items = completions.map { doc ->
                mapOf(
                    "id" to doc.id,
                    "habitId" to (doc.getString("habitId") ?: ""),
                    "dayKey" to (doc.getString("dayKey") ?: ""),
                    "completedAt" to (doc.getLong("completedAt") ?: 0L),
                )
            }
            call.respond(mapOf("data" to items))
        }
    }
}
