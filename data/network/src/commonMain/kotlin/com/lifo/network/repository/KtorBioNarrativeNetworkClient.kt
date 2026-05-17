package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.BioNarrativeRequestProto
import com.lifo.shared.model.BioNarrativeResponseProto
import com.lifo.util.repository.BioNarrativeNetworkClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Ktor-based [BioNarrativeNetworkClient] implementation (Phase 8.4, 2026-05-17).
 *
 * Wired in by `KoinModules.repositoryModule` when
 * `BackendConfig.BIO_NARRATIVE_REST == true`. Otherwise the no-op default from
 * `bioSignalModule` keeps the local template as the source of truth.
 *
 * **Resilience**: every failure path returns `null` — the caller
 * `GetWeeklyBioNarrativeUseCase` interprets `null` as "use the local
 * template". So a server outage degrades to Phase 8.2 behavior, not to a
 * blank card.
 */
class KtorBioNarrativeNetworkClient(
    private val api: KtorApiClient,
) : BioNarrativeNetworkClient {

    override suspend fun generate(request: BioNarrativeRequestProto): BioNarrativeResponseProto? {
        val token = api.getIdToken() ?: return null
        return runCatching {
            val response = api.client.post("/api/v1/bio/narrative") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                // Server returned a real error (e.g. 5xx). Logging the snippet
                // helps debug without exposing PII (the response body for our
                // endpoint is the narrative DTO, no user data).
                return@runCatching null
            }
            val dto: BioNarrativeResponseProto = response.body()
            dto
        }.getOrElse { null }
    }
}
