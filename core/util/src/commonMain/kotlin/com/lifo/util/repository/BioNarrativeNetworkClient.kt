package com.lifo.util.repository

import com.lifo.shared.model.BioNarrativeRequestProto
import com.lifo.shared.model.BioNarrativeResponseProto

/**
 * Server-side AI narrative client (Phase 8.4, 2026-05-17).
 *
 * Talks to the Cloud Run server's `POST /api/v1/bio/narrative` endpoint
 * (Phase 8.3 — Gemini-backed weekly insight, 24h Firestore cache).
 *
 * Kept separate from [BioSignalNetworkClient] (single responsibility, easier
 * to mock + wire independently). The local use case
 * `GetWeeklyBioNarrativeUseCase` calls this first; on `null` it falls back to
 * the local templated narrative shipped in Phase 8.2.
 *
 * Implementations swallow auth errors / network failures and return `null` —
 * the local template path stays the safety net.
 */
interface BioNarrativeNetworkClient {

    /**
     * Generate (or fetch cached) AI narrative.
     *
     * @return [BioNarrativeResponseProto] with non-blank `narrative` on success;
     *         a response with empty `narrative` + populated `errorCode` when
     *         Gemini blocked / rate-limited / errored (caller treats as "use
     *         local fallback"); `null` only on network/auth failure.
     */
    suspend fun generate(request: BioNarrativeRequestProto): BioNarrativeResponseProto?
}

/**
 * No-op implementation — used when `BIO_NARRATIVE_REST` flag is off, or before
 * Phase 8.3 server is deployed. Always returns `null` so the local template
 * (Phase 8.2 `GetWeeklyBioNarrativeUseCase`) stays the source of truth.
 */
class NoopBioNarrativeNetworkClient : BioNarrativeNetworkClient {
    override suspend fun generate(request: BioNarrativeRequestProto): BioNarrativeResponseProto? = null
}
