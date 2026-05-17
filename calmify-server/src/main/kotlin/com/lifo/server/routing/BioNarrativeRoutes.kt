package com.lifo.server.routing

import com.lifo.server.security.UserPrincipal
import com.lifo.server.service.BioNarrativeService
import com.lifo.shared.model.BioNarrativeRequestProto
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Bio-signal AI narrative route (Phase 8.3, 2026-05-17).
 *
 *   POST  /api/v1/bio/narrative   body: BioNarrativeRequestProto (JSON)
 *                                 → 200 OK, BioNarrativeResponseProto
 *                                 → 200 OK with empty narrative + errorCode
 *                                   when Gemini blocks or fails
 *
 * Under the `firebase` auth realm. The body's `userId` (if any) is ignored —
 * the service uses `principal.uid` for the cache key (CLAUDE.md regola 6,
 * IDOR defense).
 *
 * Server is PRO-tier-agnostic — gating happens client-side via
 * `SubscriptionRepository.observeSubscription()`. The 24h Firestore cache
 * absorbs accidental re-fetches even from non-PRO users who somehow reach
 * the endpoint.
 *
 * Rate limiting (CLAUDE.md regola 11): inherits `RateLimit.application`
 * default like every other `/api/v1` route. The 24h cache is the primary
 * cost control.
 */
fun Route.bioNarrativeRoutes() {
    val service by inject<BioNarrativeService>()

    authenticate("firebase") {
        route("/api/v1/bio") {
            post("/narrative") {
                val user = call.principal<UserPrincipal>()!!
                val body = call.receive<BioNarrativeRequestProto>()
                val response = service.generateNarrative(user.uid, body)
                call.respond(response)
            }
        }
    }
}
