package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.SyncService
import io.ktor.server.auth.*
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
                        val delta = syncService.getDiaryChangesSince(user.uid, since)
                        call.respond(delta)
                    }
                    else -> {
                        // Other entity types will be added as needed
                        call.respond(mapOf("created" to emptyList<Any>(), "updated" to emptyList<Any>(), "deletedIds" to emptyList<String>(), "serverTime" to System.currentTimeMillis()))
                    }
                }
            }
        }
    }
}
