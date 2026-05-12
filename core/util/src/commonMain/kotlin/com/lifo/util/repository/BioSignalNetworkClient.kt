package com.lifo.util.repository

import com.lifo.shared.model.BioAggregateBatchProto
import com.lifo.shared.model.BioAggregateProto

/**
 * Server-side counterpart of [BioSignalRepository]. The local repository
 * (SQLDelight + Health Connect) delegates ONLY the server operations to this
 * interface — pushing dirty aggregates, fetching server aggregates, GDPR
 * Art.17 fan-out.
 *
 * Raw samples never cross this interface. The server only knows aggregates
 * (per `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` Decision 1).
 *
 * Implementations may be no-op (when the `BIO_REST` flag is off — Phase 4
 * not yet deployed) or a real Ktor REST client.
 */
interface BioSignalNetworkClient {

    /**
     * POST `/api/v1/bio/ingest` — bulk push aggregates marked dirty in local
     * SQLDelight. Returns the count accepted by the server (used by the
     * repository to mark those rows clean).
     */
    suspend fun ingest(batch: BioAggregateBatchProto): IngestResult

    /**
     * GET `/api/v1/bio/aggregate?type=&period=` — fetch aggregates from server.
     * Returns empty list when not authenticated, network unreachable, or the
     * `BIO_REST` flag is off.
     */
    suspend fun getAggregates(type: String?, period: String?): List<BioAggregateProto>

    /**
     * DELETE `/api/v1/bio/all` — GDPR Art.17 server-side fan-out. Called by
     * [BioSignalRepository.deleteAll] in addition to the local wipe.
     * Returns true on confirmed server deletion; false if best-effort failed.
     */
    suspend fun deleteAll(): Boolean

    /**
     * GET `/api/v1/bio/export` — server-side export for GDPR Art.20.
     * Returns the raw JSON body string. Combined with the local export at the
     * repository layer.
     */
    suspend fun exportAll(): String?

    /** Outcome of an [ingest] call. */
    data class IngestResult(
        val acceptedCount: Int,
        val rejectedCount: Int,
        val errors: List<String>,
    ) {
        companion object {
            val EMPTY = IngestResult(0, 0, emptyList())
        }
    }
}

/**
 * No-op implementation — used when `BIO_REST` flag is off, or as default
 * during Phase 0-3 before Phase 4 deployment.
 *
 * Returning empty / false / null on all paths means: the local-first
 * [BioSignalRepository] continues to work exactly as before, with `is_dirty=1`
 * aggregates accumulating until a real client gets wired.
 */
class NoopBioSignalNetworkClient : BioSignalNetworkClient {
    override suspend fun ingest(batch: BioAggregateBatchProto) = BioSignalNetworkClient.IngestResult.EMPTY
    override suspend fun getAggregates(type: String?, period: String?): List<BioAggregateProto> = emptyList()
    override suspend fun deleteAll(): Boolean = false
    override suspend fun exportAll(): String? = null
}
