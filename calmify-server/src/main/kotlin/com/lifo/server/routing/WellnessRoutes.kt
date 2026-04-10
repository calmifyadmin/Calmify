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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer

/**
 * JSON instance for wellness list responses.
 * List/day endpoints MUST use JSON because:
 * - They wrap items in mapOf("data" to ...) which Protobuf can't serialize
 * - Generic List<T> has no Protobuf schema
 * Single-item endpoints (get by ID, create, update) use Protobuf normally.
 */
@PublishedApi internal val wellnessJson = Json { encodeDefaults = true }

inline fun <reified T : Any> Route.wellnessCrudRoutes(
    service: GenericWellnessService<T>,
) {
    authenticate("firebase") {
        // GET / — JSON response (list + pagination meta can't be Protobuf)
        get {
            val user = call.principal<UserPrincipal>()!!
            val params = PaginationParams.fromCall(call)
            val result = service.list(user.uid, params)
            val json = buildJsonObject {
                put("data", wellnessJson.encodeToJsonElement(ListSerializer(serializer<T>()), result.items))
                put("meta", wellnessJson.encodeToJsonElement(result.meta))
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }

        // GET /{id} — single item, Protobuf OK
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

        // GET /day/{dayKey} — JSON response (list wrapper)
        get("/day/{dayKey}") {
            val user = call.principal<UserPrincipal>()!!
            val dayKey = call.parameters["dayKey"]!!
            val items = service.getByDayKey(user.uid, dayKey)
            val json = buildJsonObject {
                put("data", wellnessJson.encodeToJsonElement(ListSerializer(serializer<T>()), items))
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }

        // POST / — single item, Protobuf OK
        post {
            val user = call.principal<UserPrincipal>()!!
            val item = call.receive<T>()
            val created = service.create(user.uid, item)
            call.respond(HttpStatusCode.Created, created)
        }

        // PUT /{id} — single item, Protobuf OK
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

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val completionId = "${habitId}_$dayKey"
                val docRef = db.collection("habit_completions").document(completionId)
                val existing = docRef.get().get()

                if (existing.exists()) {
                    docRef.delete().get()
                    call.respondText("""{"completed":false}""", ContentType.Application.Json)
                } else {
                    docRef.set(
                        hashMapOf<String, Any>(
                            "habitId" to habitId,
                            "ownerId" to user.uid,
                            "dayKey" to dayKey,
                            "completedAt" to System.currentTimeMillis(),
                        ),
                    ).get()
                    call.respondText("""{"completed":true}""", ContentType.Application.Json)
                }
            }
        }

        // GET /api/v1/wellness/habits/completions/day/{dayKey}
        get("/completions/day/{dayKey}") {
            val user = call.principal<UserPrincipal>()!!
            val dayKey = call.parameters["dayKey"]!!

            val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.collection("habit_completions")
                    .whereEqualTo("ownerId", user.uid)
                    .whereEqualTo("dayKey", dayKey)
                    .get().get().documents.map { doc ->
                        CompletionJson(
                            id = doc.id,
                            habitId = doc.getString("habitId") ?: "",
                            dayKey = doc.getString("dayKey") ?: "",
                            completedAt = doc.getLong("completedAt") ?: 0L,
                        )
                    }
            }
            val json = buildJsonObject {
                put("data", wellnessJson.encodeToJsonElement(items))
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }

        // GET /api/v1/wellness/habits/{habitId}/completions?limit=30
        get("/{habitId}/completions") {
            val user = call.principal<UserPrincipal>()!!
            val habitId = call.parameters["habitId"]!!
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 30

            val items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.collection("habit_completions")
                    .whereEqualTo("ownerId", user.uid)
                    .whereEqualTo("habitId", habitId)
                    .orderBy("completedAt", com.google.cloud.firestore.Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().get().documents.map { doc ->
                        CompletionJson(
                            id = doc.id,
                            habitId = doc.getString("habitId") ?: "",
                            dayKey = doc.getString("dayKey") ?: "",
                            completedAt = doc.getLong("completedAt") ?: 0L,
                        )
                    }
            }
            val json = buildJsonObject {
                put("data", wellnessJson.encodeToJsonElement(items))
            }
            call.respondText(json.toString(), ContentType.Application.Json)
        }
    }
}

/** Typed completion DTO for JSON serialization (no mapOf nonsense). */
@kotlinx.serialization.Serializable
private data class CompletionJson(
    val id: String = "",
    val habitId: String = "",
    val dayKey: String = "",
    val completedAt: Long = 0L,
)
