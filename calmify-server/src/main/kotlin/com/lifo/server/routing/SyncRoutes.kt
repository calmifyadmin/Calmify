package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.SyncService
import com.lifo.shared.api.BatchSyncRequest
import com.lifo.shared.api.BatchSyncResponse
import com.lifo.shared.api.SyncResultDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.syncRoutes() {
    val syncService by inject<SyncService>()

    authenticate("firebase") {
        route("/api/v1/sync") {

            // GET /api/v1/sync/changes?entity=DIARY&since=1712345678000
            get("/changes") {
                val user = call.principal<UserPrincipal>()!!
                val entityType = call.parameters["entity"] ?: "DIARY"
                val since = call.parameters["since"]?.toLongOrNull() ?: 0L

                when (entityType.uppercase()) {
                    "DIARY" -> {
                        // Typed diary response (backwards-compatible)
                        val delta = syncService.getDiaryChangesSince(user.uid, since)
                        call.respond(delta)
                    }
                    else -> {
                        // Generic delta for wellness/chat entities
                        val delta = syncService.getChangesSince(user.uid, entityType.uppercase(), since)
                        call.respond(delta)
                    }
                }
            }

            // POST /api/v1/sync/batch — push multiple changes at once
            post("/batch") {
                val user = call.principal<UserPrincipal>()!!
                val request = call.receive<BatchSyncRequest>()

                if (request.operations.isEmpty()) {
                    call.respond(BatchSyncResponse(serverTime = System.currentTimeMillis()))
                    return@post
                }

                if (request.operations.size > 25) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Max 25 operations per batch"))
                    return@post
                }

                val ops = request.operations.map { op ->
                    Triple(op.entityType, op.entityId, "${op.operation}|${op.payload}")
                }

                val results = syncService.applyBatch(user.uid, ops)

                call.respond(BatchSyncResponse(
                    results = results.map { (entityId, success) ->
                        SyncResultDto(
                            entityId = entityId,
                            success = success,
                            serverVersion = System.currentTimeMillis(),
                        )
                    },
                    serverTime = System.currentTimeMillis(),
                ))
            }
        }
    }
}
