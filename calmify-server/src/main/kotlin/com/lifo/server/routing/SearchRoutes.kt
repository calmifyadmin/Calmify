package com.lifo.server.routing

import com.lifo.server.service.SearchService
import com.lifo.shared.api.ThreadListResponse
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.builtins.ListSerializer
import org.koin.ktor.ext.inject

private val searchJson = Json { encodeDefaults = true }

fun Route.searchRoutes() {
    val searchService by inject<SearchService>()

    authenticate("firebase") {
        route("/api/v1/search") {

            // GET /api/v1/search/threads?q=...&limit=20
            get("/threads") {
                val query = call.request.queryParameters["q"] ?: ""
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val results = searchService.searchThreads(query, limit.coerceIn(1, 50))
                call.respond(ThreadListResponse(success = true, data = results))
            }

            // GET /api/v1/search/users?q=...&limit=20
            get("/users") {
                val query = call.request.queryParameters["q"] ?: ""
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val results = searchService.searchUsers(query, limit.coerceIn(1, 50))
                val json = buildJsonObject {
                    put("data", searchJson.encodeToJsonElement(
                        ListSerializer(SearchService.UserResult.serializer()), results
                    ))
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }
        }
    }
}
