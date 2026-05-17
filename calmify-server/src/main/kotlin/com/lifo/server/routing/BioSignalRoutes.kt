package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.BioNarrativeService
import com.lifo.server.service.BioSignalService
import com.lifo.shared.model.BioAggregateBatchProto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Bio-signal routes (Phase 4, 2026-05-11).
 *
 *   POST   /api/v1/bio/ingest        body: BioAggregateBatchProto (JSON)
 *                                    → 200 OK, IngestResultDto
 *   GET    /api/v1/bio/aggregate     ?type=&period=
 *                                    → 200 OK, BioAggregateResponseProto
 *   DELETE /api/v1/bio/all           GDPR Art.17 — atomic
 *                                    → 200 OK, DeleteResultDto
 *   GET    /api/v1/bio/export        GDPR Art.20
 *                                    → 200 OK, ExportDto (application/json)
 *
 * All under the `firebase` auth realm. Every handler overrides any client-
 * supplied `ownerId` with `principal.uid` to defend against IDOR (CLAUDE.md
 * regola 6).
 *
 * Rate limiting (CLAUDE.md regola 11) is applied at the plugin level —
 * `RateLimit.application` covers `/api/v1/bio/...` via the existing default.
 */
fun Route.bioSignalRoutes() {
    val service by inject<BioSignalService>()
    val narrativeService by inject<BioNarrativeService>()

    authenticate("firebase") {
        route("/api/v1/bio") {

            // ── Ingest aggregates ─────────────────────────────────────
            post("/ingest") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<BioAggregateBatchProto>()
                val result = service.ingestAggregates(user.uid, body)
                call.respond(result)
            }

            // ── Query aggregates ──────────────────────────────────────
            get("/aggregate") {
                val user = call.principal<UserPrincipal>()!!
                val type = call.request.queryParameters["type"]
                val period = call.request.queryParameters["period"]
                val response = service.getAggregates(user.uid, type, period)
                call.respond(response)
            }

            // ── GDPR Art.17 — atomic delete all ───────────────────────
            delete("/all") {
                val user = call.principal<UserPrincipal>()!!
                val result = service.deleteAll(user.uid)
                // Phase 8.3 — fan out the Art.17 wipe to the narrative cache too.
                // Best-effort: if the narrative cleanup fails, the aggregates
                // delete has already succeeded and the user's main response is
                // unchanged.
                runCatching { narrativeService.deleteAllForUser(user.uid) }
                call.respond(result)
            }

            // ── GDPR Art.20 — full export ─────────────────────────────
            get("/export") {
                val user = call.principal<UserPrincipal>()!!
                val export = service.exportAll(user.uid)
                call.response.headers.append(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=calmify-bio-${user.uid}-${System.currentTimeMillis()}.json"
                )
                call.respond(export)
            }
        }
    }
}
