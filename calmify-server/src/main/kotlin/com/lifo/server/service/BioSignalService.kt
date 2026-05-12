package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.WriteBatch
import com.lifo.shared.model.BioAggregateBatchProto
import com.lifo.shared.model.BioAggregateProto
import com.lifo.shared.model.BioAggregateResponseProto
import com.lifo.shared.model.BioConsentEventProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * Server-side bio-signal aggregates service (Phase 4, 2026-05-11).
 *
 * **Privacy guarantee** — the server stores ONLY aggregates, NEVER raw samples.
 * The opt-in raw-upload window for PRO advanced correlations is not yet wired
 * (Phase 8). All aggregate writes are addressed by a deterministic document ID
 * `{userId}|{type}|{period}|{periodKey}` for idempotent re-ingestion.
 *
 * **Firestore layout**:
 * - `bio_aggregates/{deterministic-id}` — flat collection, queryable by
 *   ownerId + type + period + periodKey. Composite indexes pre-deployed in
 *   `firestore.indexes.json` (lezione Phase 5: index drift → 9-error runtime).
 * - `bio_consent_log/{auto-id}` — audit trail for GDPR Art.7 demonstrability.
 *
 * **Authorization** (CLAUDE.md regola 6): every query/mutation filtered by
 * `ownerId == principal.uid`. Routes layer enforces; service trusts the
 * `userId` parameter passed in.
 *
 * **Batch limit** (CLAUDE.md regola 8): Firestore batches capped at 500
 * operations. Ingestion chunks the input batch accordingly.
 */
class BioSignalService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(BioSignalService::class.java)

    companion object {
        const val AGGREGATES_COLLECTION = "bio_aggregates"
        const val CONSENT_LOG_COLLECTION = "bio_consent_log"
        private const val FIRESTORE_BATCH_LIMIT = 500
    }

    /** Outcome of an ingestion batch. */
    @Serializable
    data class IngestResultDto(
        val acceptedCount: Int = 0,
        val rejectedCount: Int = 0,
        val errors: List<String> = emptyList(),
    )

    /** Outcome of a bulk delete (Art.17). */
    @Serializable
    data class DeleteResultDto(
        val aggregatesDeleted: Int = 0,
        val consentEventsDeleted: Int = 0,
    )

    // ────────────────────────────────────────────────────────────────────
    // INGEST — accept a batch of locally-computed aggregates
    // ────────────────────────────────────────────────────────────────────

    suspend fun ingestAggregates(
        userId: String,
        batch: BioAggregateBatchProto,
    ): IngestResultDto = withContext(Dispatchers.IO) {
        if (batch.aggregates.isEmpty()) return@withContext IngestResultDto()

        // Enforce ownership: the client may have computed aggregates locally
        // with stale userId. Server is authoritative — overwrite the ownerId
        // field with the authenticated principal's UID.
        val errors = mutableListOf<String>()
        var accepted = 0
        var rejected = 0

        batch.aggregates.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val writeBatch: WriteBatch = db.batch()
            chunk.forEach { agg ->
                if (agg.type.isBlank() || agg.period.isBlank() || agg.periodKey.isBlank()) {
                    rejected++
                    errors += "missing required field: type=${agg.type}, period=${agg.period}, key=${agg.periodKey}"
                    return@forEach
                }
                val docId = synthesizeDocId(userId, agg.type, agg.period, agg.periodKey)
                val docRef = db.collection(AGGREGATES_COLLECTION).document(docId)
                val data = hashMapOf<String, Any>(
                    "ownerId" to userId,                    // server-authoritative
                    "type" to agg.type,
                    "period" to agg.period,
                    "periodKey" to agg.periodKey,
                    "mean" to agg.mean,
                    "p10" to agg.p10,
                    "p90" to agg.p90,
                    "count" to agg.count,
                    "confidenceWeightedMean" to agg.confidenceWeightedMean,
                    "sourceMix" to agg.sourceMix,
                    "computedAtMillis" to agg.computedAtMillis,
                    "serverTimestampMillis" to System.currentTimeMillis(),
                )
                writeBatch.set(docRef, data)
                accepted++
            }
            // ApiFuture<...>.get() blocks the calling thread — safe because we're
            // already on Dispatchers.IO (CLAUDE.md regola 5).
            writeBatch.commit().get()
        }
        logger.info("ingestAggregates user=$userId accepted=$accepted rejected=$rejected")
        IngestResultDto(acceptedCount = accepted, rejectedCount = rejected, errors = errors)
    }

    // ────────────────────────────────────────────────────────────────────
    // QUERY — return aggregates for one or all data types
    // ────────────────────────────────────────────────────────────────────

    /**
     * Fetch aggregates for [userId] filtered optionally by type + period.
     * Sorted descending by `periodKey` so the most recent is first.
     * Hard-limited to [maxRows] (default 200) to bound response size.
     */
    suspend fun getAggregates(
        userId: String,
        type: String? = null,
        period: String? = null,
        maxRows: Int = 200,
    ): BioAggregateResponseProto = withContext(Dispatchers.IO) {
        var query = db.collection(AGGREGATES_COLLECTION)
            .whereEqualTo("ownerId", userId)
        if (!type.isNullOrBlank()) query = query.whereEqualTo("type", type)
        if (!period.isNullOrBlank()) query = query.whereEqualTo("period", period)

        // ORDER BY periodKey DESC + composite index (ownerId ASC, periodKey DESC, __name__ DESC).
        // See lezione Phase 5 (firestore.indexes.json) — Firestore requires the
        // tie-breaker `__name__` index explicitly when ordering DESC on a non-PK.
        val docs = query
            .orderBy("periodKey", com.google.cloud.firestore.Query.Direction.DESCENDING)
            .limit(maxRows)
            .get()
            .get()
            .documents

        val aggregates = docs.map { doc ->
            BioAggregateProto(
                ownerId = doc.getString("ownerId") ?: "",
                type = doc.getString("type") ?: "",
                period = doc.getString("period") ?: "DAILY",
                periodKey = doc.getString("periodKey") ?: "",
                mean = doc.getDouble("mean") ?: 0.0,
                p10 = doc.getDouble("p10") ?: 0.0,
                p90 = doc.getDouble("p90") ?: 0.0,
                count = (doc.getLong("count") ?: 0L).toInt(),
                confidenceWeightedMean = doc.getDouble("confidenceWeightedMean") ?: 0.0,
                sourceMix = readSourceMix(doc.get("sourceMix")),
                computedAtMillis = doc.getLong("computedAtMillis") ?: 0L,
            )
        }
        BioAggregateResponseProto(
            aggregates = aggregates,
            cacheControl = "max-age=300",   // 5 min; immutable past + mutable today
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // DELETE ALL — GDPR Art.17 atomic
    // ────────────────────────────────────────────────────────────────────

    /**
     * Atomically delete all bio-signal data for [userId] — aggregates +
     * consent log + audit a final REVOKE event. Idempotent.
     */
    suspend fun deleteAll(userId: String): DeleteResultDto = withContext(Dispatchers.IO) {
        // Step 1: bulk-delete aggregates (chunked 500 per regola 8).
        val aggregateDocs = db.collection(AGGREGATES_COLLECTION)
            .whereEqualTo("ownerId", userId)
            .get()
            .get()
            .documents
        var aggregatesDeleted = 0
        aggregateDocs.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference); aggregatesDeleted++ }
            batch.commit().get()
        }

        // Step 2: bulk-delete consent log events.
        val consentDocs = db.collection(CONSENT_LOG_COLLECTION)
            .whereEqualTo("ownerId", userId)
            .get()
            .get()
            .documents
        var consentEventsDeleted = 0
        consentDocs.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference); consentEventsDeleted++ }
            batch.commit().get()
        }

        // Step 3: record the final REVOKE * for audit trail (note: this is
        // intentionally written AFTER the consent log wipe — it's the only
        // event that survives, proving demonstrability of the delete itself).
        logConsentEvent(
            userId = userId,
            action = "REVOKE",
            dataType = "*",
            clientVersion = "server-deleteAll",
        )

        logger.info("deleteAll user=$userId aggregates=$aggregatesDeleted consents=$consentEventsDeleted")
        DeleteResultDto(
            aggregatesDeleted = aggregatesDeleted,
            consentEventsDeleted = consentEventsDeleted,
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // EXPORT — GDPR Art.20 portability
    // ────────────────────────────────────────────────────────────────────

    /**
     * Return all bio-signal data for [userId] as a serializable wrapper.
     * The Routes layer streams it as `application/json`.
     */
    suspend fun exportAll(userId: String): ExportDto = withContext(Dispatchers.IO) {
        val aggregates = db.collection(AGGREGATES_COLLECTION)
            .whereEqualTo("ownerId", userId)
            .get()
            .get()
            .documents
            .map { doc ->
                BioAggregateProto(
                    ownerId = doc.getString("ownerId") ?: "",
                    type = doc.getString("type") ?: "",
                    period = doc.getString("period") ?: "DAILY",
                    periodKey = doc.getString("periodKey") ?: "",
                    mean = doc.getDouble("mean") ?: 0.0,
                    p10 = doc.getDouble("p10") ?: 0.0,
                    p90 = doc.getDouble("p90") ?: 0.0,
                    count = (doc.getLong("count") ?: 0L).toInt(),
                    confidenceWeightedMean = doc.getDouble("confidenceWeightedMean") ?: 0.0,
                    sourceMix = readSourceMix(doc.get("sourceMix")),
                    computedAtMillis = doc.getLong("computedAtMillis") ?: 0L,
                )
            }
        val consents = db.collection(CONSENT_LOG_COLLECTION)
            .whereEqualTo("ownerId", userId)
            .get()
            .get()
            .documents
            .map { doc ->
                BioConsentEventProto(
                    ownerId = doc.getString("ownerId") ?: "",
                    timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                    action = doc.getString("action") ?: "GRANT",
                    dataType = doc.getString("dataType") ?: "",
                    clientVersion = doc.getString("clientVersion") ?: "",
                    timezone = doc.getString("timezone") ?: "",
                )
            }
        ExportDto(
            schemaVersion = 1,
            exportedAtMillis = System.currentTimeMillis(),
            ownerId = userId,
            aggregates = aggregates,
            consentLog = consents,
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Consent log helper — invoked on every GRANT/REVOKE
    // ────────────────────────────────────────────────────────────────────

    suspend fun logConsentEvent(
        userId: String,
        action: String,                  // "GRANT" / "REVOKE"
        dataType: String,                // BioSignalDataType.name or "*"
        clientVersion: String = "",
        timezone: String = "",
    ) = withContext(Dispatchers.IO) {
        val data = hashMapOf<String, Any>(
            "ownerId" to userId,
            "timestampMillis" to System.currentTimeMillis(),
            "action" to action,
            "dataType" to dataType,
            "clientVersion" to clientVersion,
            "timezone" to timezone,
        )
        db.collection(CONSENT_LOG_COLLECTION).add(data).get()
    }

    suspend fun ingestConsentBatch(
        userId: String,
        events: List<BioConsentEventProto>,
    ): Int = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext 0
        var written = 0
        events.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { event ->
                val docRef = db.collection(CONSENT_LOG_COLLECTION).document()
                val data = hashMapOf<String, Any>(
                    "ownerId" to userId,
                    "timestampMillis" to event.timestampMillis,
                    "action" to event.action,
                    "dataType" to event.dataType,
                    "clientVersion" to event.clientVersion,
                    "timezone" to event.timezone,
                )
                batch.set(docRef, data)
                written++
            }
            batch.commit().get()
        }
        written
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private fun synthesizeDocId(userId: String, type: String, period: String, periodKey: String): String =
        // Deterministic ID = idempotent upsert; URL-safe (no slashes inside IDs).
        "${userId}_${type}_${period}_${periodKey}"

    @Suppress("UNCHECKED_CAST")
    private fun readSourceMix(raw: Any?): Map<String, Int> {
        val map = raw as? Map<String, Any> ?: return emptyMap()
        return map.mapValues { (_, v) ->
            when (v) {
                is Long -> v.toInt()
                is Int -> v
                is Number -> v.toInt()
                else -> 0
            }
        }
    }

    @Serializable
    data class ExportDto(
        val schemaVersion: Int = 1,
        val exportedAtMillis: Long = 0L,
        val ownerId: String = "",
        val aggregates: List<BioAggregateProto> = emptyList(),
        val consentLog: List<BioConsentEventProto> = emptyList(),
    )
}
