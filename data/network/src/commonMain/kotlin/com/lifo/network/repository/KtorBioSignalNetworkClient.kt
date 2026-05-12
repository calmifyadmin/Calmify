package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.BioAggregateBatchProto
import com.lifo.shared.model.BioAggregateProto
import com.lifo.util.repository.BioSignalNetworkClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ktor-based [BioSignalNetworkClient] implementation (Phase 4, 2026-05-11).
 *
 * Talks to the Cloud Run server's `/api/v1/bio/...` endpoints. The local
 * [com.lifo.util.repository.BioSignalRepository] continues to be the source
 * of truth for raw samples (Decision 1 — never sent over the wire).
 *
 * All methods are resilient — they swallow auth errors / network failures and
 * return defaults (empty list / false / null). The local repository continues
 * to function offline; dirty aggregates simply accumulate until the next
 * successful ingest.
 */
class KtorBioSignalNetworkClient(
    private val api: KtorApiClient,
) : BioSignalNetworkClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    override suspend fun ingest(
        batch: BioAggregateBatchProto,
    ): BioSignalNetworkClient.IngestResult {
        if (batch.aggregates.isEmpty()) return BioSignalNetworkClient.IngestResult.EMPTY
        val token = api.getIdToken() ?: return BioSignalNetworkClient.IngestResult.EMPTY
        return runCatching {
            val response = api.client.post("/api/v1/bio/ingest") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(batch)
            }
            if (!response.status.isSuccess()) {
                return@runCatching BioSignalNetworkClient.IngestResult(
                    acceptedCount = 0,
                    rejectedCount = batch.aggregates.size,
                    errors = listOf("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}"),
                )
            }
            val dto: IngestResultDto = response.body()
            BioSignalNetworkClient.IngestResult(
                acceptedCount = dto.acceptedCount,
                rejectedCount = dto.rejectedCount,
                errors = dto.errors,
            )
        }.getOrElse {
            BioSignalNetworkClient.IngestResult(
                acceptedCount = 0,
                rejectedCount = batch.aggregates.size,
                errors = listOf("ingest network error: ${it.message ?: "unknown"}"),
            )
        }
    }

    override suspend fun getAggregates(
        type: String?,
        period: String?,
    ): List<BioAggregateProto> {
        val token = api.getIdToken() ?: return emptyList()
        return runCatching {
            val url = URLBuilder("/api/v1/bio/aggregate").apply {
                if (!type.isNullOrBlank()) parameters.append("type", type)
                if (!period.isNullOrBlank()) parameters.append("period", period)
            }.buildString()
            val response = api.client.get(url) {
                header(HttpHeaders.Authorization, "Bearer $token")
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) return@runCatching emptyList()
            val wrapper: AggregateResponseDto = response.body()
            wrapper.aggregates
        }.getOrElse { emptyList() }
    }

    override suspend fun deleteAll(): Boolean {
        val token = api.getIdToken() ?: return false
        return runCatching {
            val response = api.client.delete("/api/v1/bio/all") {
                header(HttpHeaders.Authorization, "Bearer $token")
                accept(ContentType.Application.Json)
            }
            response.status.isSuccess()
        }.getOrElse { false }
    }

    override suspend fun exportAll(): String? {
        val token = api.getIdToken() ?: return null
        return runCatching {
            val response = api.client.get("/api/v1/bio/export") {
                header(HttpHeaders.Authorization, "Bearer $token")
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) null else response.bodyAsText()
        }.getOrNull()
    }

    /** Mirror of server's `BioSignalService.IngestResultDto`. */
    @Serializable
    private data class IngestResultDto(
        @SerialName("acceptedCount") val acceptedCount: Int = 0,
        @SerialName("rejectedCount") val rejectedCount: Int = 0,
        @SerialName("errors") val errors: List<String> = emptyList(),
    )

    /** Mirror of server's response wrapper for GET /aggregate. */
    @Serializable
    private data class AggregateResponseDto(
        val aggregates: List<BioAggregateProto> = emptyList(),
        val cacheControl: String = "",
    )
}
