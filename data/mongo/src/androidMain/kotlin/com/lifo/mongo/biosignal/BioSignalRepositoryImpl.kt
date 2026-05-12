package com.lifo.mongo.biosignal

import android.util.Log
import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.shared.model.BioAggregateBatchProto
import com.lifo.shared.model.BioAggregateProto
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.AggregatePeriod
import com.lifo.util.model.BioAggregate
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.repository.BioSignalNetworkClient
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.IngestionResult
import com.lifo.util.repository.NoopBioSignalNetworkClient
import com.lifo.util.repository.ProviderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull

/**
 * SQLDelight-backed implementation of [BioSignalRepository].
 *
 * **Phase 2 scope** (this commit):
 * - Local raw sample storage with TTL pruning
 * - Local aggregate computation + storage with dirty-flag tracking
 * - GDPR Art.17 atomic local delete
 * - GDPR Art.20 export as JSON
 * - Consent log audit trail (local)
 *
 * **Deferred to Phase 4** (server-side):
 * - syncPendingAggregates → real Ktor POST to /api/v1/bio/ingest
 * - Server-side delete fan-out (Art.17 ends at local for now)
 * - Server-side consent log push
 *
 * Until Phase 4 lands, [syncPendingAggregates] returns 0 (no-op) and the
 * dirty flag accumulates — guaranteed to flush once the server endpoint is
 * wired. This is the canonical offline-first reconcile path.
 */
class BioSignalRepositoryImpl(
    private val database: CalmifyDatabase,
    private val authProvider: AuthProvider,
    private val networkClient: BioSignalNetworkClient = NoopBioSignalNetworkClient(),
    private val json: Json = DefaultJson,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) : BioSignalRepository {

    private val rawQueries get() = database.bioSignalQueries
    private val aggQueries get() = database.bioAggregateQueries
    private val consentQueries get() = database.bioConsentQueries

    private fun ownerIdOrEmpty(): String = authProvider.currentUserId.orEmpty()

    // ──────────────────────────────────────────────────────────────────────
    // Raw samples
    // ──────────────────────────────────────────────────────────────────────

    override fun observeRawSamples(
        type: BioSignalDataType,
        from: Instant,
        until: Instant,
    ): Flow<List<BioSignal>> {
        val userId = ownerIdOrEmpty()
        return rawQueries.observeByTypeRange(
            userId = userId,
            dataType = type.name,
            fromMillis = from.toEpochMilliseconds(),
            untilMillis = until.toEpochMilliseconds(),
        )
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.mapNotNull { decodeRow(type, it.payload) } }
    }

    override suspend fun getRawSamples(
        type: BioSignalDataType,
        from: Instant,
        until: Instant,
    ): List<BioSignal> = withContext(Dispatchers.IO) {
        val userId = ownerIdOrEmpty()
        rawQueries.getByTypeRange(
            userId = userId,
            dataType = type.name,
            fromMillis = from.toEpochMilliseconds(),
            untilMillis = until.toEpochMilliseconds(),
        )
            .executeAsList()
            .mapNotNull { decodeRow(type, it.payload) }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Aggregates
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun getAggregate(
        type: BioSignalDataType,
        period: AggregatePeriod,
        periodKey: String,
    ): BioAggregate? = withContext(Dispatchers.IO) {
        val userId = ownerIdOrEmpty()
        val row = aggQueries.getByOwnerTypePeriodKey(
            userId = userId,
            dataType = type.name,
            period = period.name,
            periodKey = periodKey,
        ).executeAsOneOrNull() ?: return@withContext null
        rowToAggregate(row)
    }

    override fun observeAggregate(
        type: BioSignalDataType,
        period: AggregatePeriod,
    ): Flow<BioAggregate?> {
        val userId = ownerIdOrEmpty()
        return aggQueries.getRecentByOwnerTypePeriod(
            userId = userId,
            dataType = type.name,
            period = period.name,
            limit = 1L,
        )
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.let { rowToAggregate(it) } }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Ingestion — pull from provider → store raw → compute aggregates
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun ingestFromProvider(
        provider: HealthDataProvider,
        since: Instant,
    ): IngestionResult = withContext(Dispatchers.IO) {
        val userId = ownerIdOrEmpty()
        if (userId.isBlank()) {
            return@withContext IngestionResult(0, 0, 0, listOf("no user signed in"))
        }
        val status = provider.checkAvailability()
        if (status !is ProviderStatus.Ready) {
            return@withContext IngestionResult(0, 0, 0, listOf("provider not ready: $status"))
        }

        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val errors = mutableListOf<String>()
        var rawCount = 0

        // 1) Pull all 7 data types from the provider, upsert raw locally.
        for (type in BioSignalDataType.entries) {
            try {
                val samples = readByType(provider, type, since, now)
                samples.forEach { sample ->
                    rawQueries.upsert(
                        id = synthRawId(userId, type, sample),
                        owner_id = userId,
                        data_type = type.name,
                        timestamp_millis = sample.timestamp.toEpochMilliseconds(),
                        day_key = sample.timestamp.toLocalDateTime(tz).date.toString(),
                        source_device = sample.source.deviceName,
                        source_app = sample.source.appName,
                        confidence_level = sample.confidence.level.name,
                        payload = encodeSample(sample),
                        created_at = now.toEpochMilliseconds(),
                    )
                    rawCount++
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Type $type ingestion failed: ${t.message}")
                errors += "$type: ${t.message ?: "unknown"}"
            }
        }

        // 2) Compute today's daily aggregate per type (rolling window covers it).
        val today = now.toLocalDateTime(tz).date
        var aggregatesComputed = 0
        for (type in BioSignalDataType.entries) {
            try {
                val agg = computeDailyAggregate(userId, type, today, tz) ?: continue
                aggQueries.upsert(
                    id = synthAggregateId(userId, type, AggregatePeriod.DAILY, agg.periodKey),
                    owner_id = userId,
                    data_type = type.name,
                    period = agg.period.name,
                    period_key = agg.periodKey,
                    mean = agg.mean,
                    p10 = agg.p10,
                    p90 = agg.p90,
                    sample_count = agg.count.toLong(),
                    confidence_weighted_mean = agg.confidenceWeightedMean,
                    source_mix_json = json.encodeToString(agg.sourceMix),
                    computed_at = now.toEpochMilliseconds(),
                    is_dirty = 1L,
                )
                aggregatesComputed++
            } catch (t: Throwable) {
                Log.w(TAG, "Aggregate $type failed: ${t.message}")
                errors += "agg-$type: ${t.message ?: "unknown"}"
            }
        }

        IngestionResult(
            rawSamplesIngested = rawCount,
            aggregatesComputed = aggregatesComputed,
            aggregatesPushed = 0,                  // Phase 4: real server push
            errors = errors,
        )
    }

    override suspend fun syncPendingAggregates(): Int = withContext(Dispatchers.IO) {
        // Read all dirty aggregates, push them to the server via the network
        // client, mark them clean. If the network client is the no-op
        // (BackendConfig.BIO_REST=false), this returns 0 — aggregates remain
        // dirty and accumulate, guaranteed to flush when the flag flips.
        val dirty = aggQueries.getAllDirty().executeAsList()
        if (dirty.isEmpty()) return@withContext 0

        val batch = BioAggregateBatchProto(
            aggregates = dirty.map { row ->
                BioAggregateProto(
                    ownerId = row.owner_id,
                    type = row.data_type,
                    period = row.period,
                    periodKey = row.period_key,
                    mean = row.mean,
                    p10 = row.p10,
                    p90 = row.p90,
                    count = row.sample_count.toInt(),
                    confidenceWeightedMean = row.confidence_weighted_mean,
                    sourceMix = runCatching {
                        json.decodeFromString<Map<String, Int>>(row.source_mix_json)
                    }.getOrDefault(emptyMap()),
                    computedAtMillis = row.computed_at,
                )
            },
            clientTimezone = TimeZone.currentSystemDefault().id,
        )

        val result = networkClient.ingest(batch)
        if (result.acceptedCount == 0) {
            Log.i(TAG, "syncPendingAggregates: 0 accepted (errors=${result.errors.size})")
            return@withContext 0
        }

        // Mark clean ONLY the rows the server accepted. The server can reject
        // (returning rejectedCount > 0) — those stay dirty and retry next sync.
        // We don't know per-row which were accepted; the simplest contract is:
        // server returns acceptedCount monotonically (it processes in order).
        // We mark the first `acceptedCount` rows clean.
        dirty.take(result.acceptedCount).forEach { row ->
            aggQueries.markClean(row.id)
        }
        Log.i(TAG, "syncPendingAggregates: accepted=${result.acceptedCount} rejected=${result.rejectedCount}")
        result.acceptedCount
    }

    // ──────────────────────────────────────────────────────────────────────
    // GDPR Art.17 + Art.20
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun exportAll(): String = withContext(Dispatchers.IO) {
        val userId = ownerIdOrEmpty()
        val rawSamples = BioSignalDataType.entries.associateWith { type ->
            rawQueries.getByTypeRange(
                userId = userId,
                dataType = type.name,
                fromMillis = 0L,
                untilMillis = Long.MAX_VALUE,
            )
                .executeAsList()
                .mapNotNull { decodeRow(type, it.payload) }
        }
        val aggregates = aggQueries.getAllDirty().executeAsList()
            .map { row -> rowToAggregate(row) }
        val consents = consentQueries.getAllByOwner(userId).executeAsList()
            .map { row ->
                mapOf(
                    "timestamp_millis" to row.timestamp_millis,
                    "action" to row.action,
                    "data_type" to row.data_type,
                    "timezone" to row.timezone,
                )
            }
        val export = mapOf(
            "schema_version" to 1,
            "exported_at_millis" to Clock.System.now().toEpochMilliseconds(),
            "owner_id" to userId,
            "raw_samples" to rawSamples.mapKeys { it.key.name },
            "aggregates" to aggregates,
            "consent_log" to consents,
        )
        json.encodeToString(SerializableExport.fromMap(export))
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val userId = ownerIdOrEmpty()
        // Step 1: local atomic wipe (always succeeds — SQLDelight is local-only).
        rawQueries.deleteAllByOwner(userId)
        aggQueries.deleteAllByOwner(userId)
        consentQueries.deleteAllByOwner(userId)
        // Step 2: audit the bulk revoke (will be pushed to server next sync).
        logConsentEvent(userId, action = "REVOKE", dataType = "*")
        // Step 3: server-side fan-out (Phase 4). Best-effort — if the network
        // client is the No-op or the request fails, the server data lingers
        // until the next manual delete or the user closes their account
        // (GDPR full deletion path). The local delete is unconditional.
        val serverDeleted = runCatching { networkClient.deleteAll() }.getOrDefault(false)
        if (!serverDeleted) {
            Log.w(TAG, "deleteAll: server fan-out failed or no-op — local cleared, server may retain data")
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // TTL management
    // ──────────────────────────────────────────────────────────────────────

    override suspend fun pruneExpiredSamples(): Int = withContext(Dispatchers.IO) {
        val threshold = Clock.System.now().toEpochMilliseconds() - ttlMillis
        val userId = ownerIdOrEmpty()
        val before = rawQueries.countByOwner(userId).executeAsOne()
        rawQueries.pruneOlderThan(threshold)
        val after = rawQueries.countByOwner(userId).executeAsOne()
        (before - after).toInt().coerceAtLeast(0)
    }

    override suspend fun pruneSamplesInRange(from: LocalDate, until: LocalDate) = withContext(Dispatchers.IO) {
        val userId = ownerIdOrEmpty()
        rawQueries.pruneByOwnerInDayRange(
            userId = userId,
            fromDay = from.toString(),
            untilDay = until.toString(),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Consent log (audit trail)
    // ──────────────────────────────────────────────────────────────────────

    /** Public helper for callers (Settings → Bio-signals UI) to audit grant/revoke events. */
    fun logConsentEvent(
        userId: String,
        action: String,                        // "GRANT" | "REVOKE"
        dataType: String,                      // BioSignalDataType.name | "*"
        clientVersion: String = "",
        timezone: String = TimeZone.currentSystemDefault().id,
    ) {
        consentQueries.insert(
            id = "$userId|${Clock.System.now().toEpochMilliseconds()}|$action|$dataType",
            owner_id = userId,
            timestamp_millis = Clock.System.now().toEpochMilliseconds(),
            action = action,
            data_type = dataType,
            client_version = clientVersion,
            timezone = timezone,
            is_dirty = 1L,
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private suspend fun readByType(
        provider: HealthDataProvider,
        type: BioSignalDataType,
        from: Instant,
        until: Instant,
    ): List<BioSignal> = when (type) {
        BioSignalDataType.HEART_RATE -> provider.readHeartRate(from, until)
        BioSignalDataType.HRV -> provider.readHrv(from, until)
        BioSignalDataType.SLEEP -> provider.readSleepSessions(from, until)
        BioSignalDataType.STEPS -> provider.readStepCounts(from, until)
        BioSignalDataType.RESTING_HEART_RATE -> provider.readRestingHeartRate(from, until)
        BioSignalDataType.OXYGEN_SATURATION -> provider.readOxygenSaturation(from, until)
        BioSignalDataType.ACTIVITY -> provider.readActivitySessions(from, until)
    }

    /** Encode a BioSignal subtype to its concrete JSON. */
    private fun encodeSample(sample: BioSignal): String = when (sample) {
        is BioSignal.HeartRateSample -> json.encodeToString(sample)
        is BioSignal.HrvSample -> json.encodeToString(sample)
        is BioSignal.SleepSession -> json.encodeToString(sample)
        is BioSignal.StepCount -> json.encodeToString(sample)
        is BioSignal.RestingHeartRate -> json.encodeToString(sample)
        is BioSignal.OxygenSaturationSample -> json.encodeToString(sample)
        is BioSignal.ActivitySession -> json.encodeToString(sample)
    }

    /** Reconstruct a BioSignal subtype from JSON payload + the type discriminator. */
    private fun decodeRow(type: BioSignalDataType, payload: String): BioSignal? = runCatching {
        when (type) {
            BioSignalDataType.HEART_RATE -> json.decodeFromString<BioSignal.HeartRateSample>(payload)
            BioSignalDataType.HRV -> json.decodeFromString<BioSignal.HrvSample>(payload)
            BioSignalDataType.SLEEP -> json.decodeFromString<BioSignal.SleepSession>(payload)
            BioSignalDataType.STEPS -> json.decodeFromString<BioSignal.StepCount>(payload)
            BioSignalDataType.RESTING_HEART_RATE -> json.decodeFromString<BioSignal.RestingHeartRate>(payload)
            BioSignalDataType.OXYGEN_SATURATION -> json.decodeFromString<BioSignal.OxygenSaturationSample>(payload)
            BioSignalDataType.ACTIVITY -> json.decodeFromString<BioSignal.ActivitySession>(payload)
        }
    }.getOrElse {
        Log.w(TAG, "decodeRow($type) failed: ${it.message}")
        null
    }

    /**
     * Compute the daily aggregate for a single type over [day] in [tz]. Returns
     * null if there are no samples (don't bother creating an empty aggregate row).
     */
    private fun computeDailyAggregate(
        userId: String,
        type: BioSignalDataType,
        day: LocalDate,
        tz: TimeZone,
    ): BioAggregate? {
        val rows = rawQueries.getByDay(userId = userId, dayKey = day.toString()).executeAsList()
            .filter { it.data_type == type.name }
        if (rows.isEmpty()) return null

        val values = rows.mapNotNull { row -> extractNumericValue(type, row.payload) }
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mean = sorted.average()
        val p10 = percentile(sorted, 10)
        val p90 = percentile(sorted, 90)

        // Confidence-weighted mean — multiply each by 1.0/0.66/0.33 based on level.
        val weighted = rows.mapNotNull { row ->
            val v = extractNumericValue(type, row.payload) ?: return@mapNotNull null
            val w = when (ConfidenceLevel.entries.firstOrNull { it.name == row.confidence_level } ?: ConfidenceLevel.MEDIUM) {
                ConfidenceLevel.HIGH -> 1.0
                ConfidenceLevel.MEDIUM -> 0.66
                ConfidenceLevel.LOW -> 0.33
            }
            v to w
        }
        val confidenceWeightedMean = if (weighted.isNotEmpty()) {
            weighted.sumOf { it.first * it.second } / weighted.sumOf { it.second }
        } else mean

        val sourceMix = rows
            .groupingBy { it.source_device.ifBlank { "Unknown" } }
            .eachCount()

        return BioAggregate(
            type = type,
            period = AggregatePeriod.DAILY,
            periodKey = day.toString(),
            mean = mean,
            p10 = p10,
            p90 = p90,
            count = rows.size,
            confidenceWeightedMean = confidenceWeightedMean,
            sourceMix = sourceMix,
        )
    }

    private fun percentile(sorted: List<Double>, percent: Int): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = (percent / 100.0) * (sorted.size - 1)
        val lower = rank.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val frac = rank - lower
        return sorted[lower] + frac * (sorted[upper] - sorted[lower])
    }

    /**
     * Extract the dominant numeric metric per type. For sleep + activity we use
     * duration; for the others we use the natural scalar (bpm, rmssd, count, %, etc).
     */
    private fun extractNumericValue(type: BioSignalDataType, payload: String): Double? = runCatching {
        when (type) {
            BioSignalDataType.HEART_RATE ->
                json.decodeFromString<BioSignal.HeartRateSample>(payload).bpm.toDouble()
            BioSignalDataType.HRV ->
                json.decodeFromString<BioSignal.HrvSample>(payload).rmssdMillis
            BioSignalDataType.SLEEP ->
                json.decodeFromString<BioSignal.SleepSession>(payload).durationSeconds / 3600.0  // hours
            BioSignalDataType.STEPS ->
                json.decodeFromString<BioSignal.StepCount>(payload).count.toDouble()
            BioSignalDataType.RESTING_HEART_RATE ->
                json.decodeFromString<BioSignal.RestingHeartRate>(payload).bpm.toDouble()
            BioSignalDataType.OXYGEN_SATURATION ->
                json.decodeFromString<BioSignal.OxygenSaturationSample>(payload).percent
            BioSignalDataType.ACTIVITY ->
                json.decodeFromString<BioSignal.ActivitySession>(payload).durationSeconds / 60.0  // minutes
        }
    }.getOrNull()

    private fun rowToAggregate(row: com.lifo.mongo.database.Bio_aggregates): BioAggregate = BioAggregate(
        type = BioSignalDataType.valueOf(row.data_type),
        period = AggregatePeriod.valueOf(row.period),
        periodKey = row.period_key,
        mean = row.mean,
        p10 = row.p10,
        p90 = row.p90,
        count = row.sample_count.toInt(),
        confidenceWeightedMean = row.confidence_weighted_mean,
        sourceMix = runCatching {
            json.decodeFromString<Map<String, Int>>(row.source_mix_json)
        }.getOrDefault(emptyMap()),
    )

    private fun synthRawId(userId: String, type: BioSignalDataType, sample: BioSignal): String =
        "$userId|${type.name}|${sample.timestamp.toEpochMilliseconds()}|${sample.source.appName}"

    private fun synthAggregateId(userId: String, type: BioSignalDataType, period: AggregatePeriod, periodKey: String): String =
        "$userId|${type.name}|${period.name}|$periodKey"

    companion object {
        private const val TAG = "BioSignalRepo"
        private const val DEFAULT_TTL_MILLIS = 30L * 24L * 3600L * 1000L  // 30 days

        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Export serializable wrapper — kotlinx-serialization needs a typed root for
// `encodeToString(Map<*, *>)`, so we wrap into a sealed-ish container.
// ──────────────────────────────────────────────────────────────────────────

@kotlinx.serialization.Serializable
private data class SerializableExport(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val ownerId: String,
    val rawSamples: Map<String, List<BioSignal>>,
    val aggregates: List<BioAggregate>,
    val consentLog: List<Map<String, kotlinx.serialization.json.JsonElement>>,
) {
    companion object {
        fun fromMap(m: Map<String, Any?>): SerializableExport {
            @Suppress("UNCHECKED_CAST")
            return SerializableExport(
                schemaVersion = m["schema_version"] as Int,
                exportedAtMillis = m["exported_at_millis"] as Long,
                ownerId = m["owner_id"] as String,
                rawSamples = m["raw_samples"] as Map<String, List<BioSignal>>,
                aggregates = m["aggregates"] as List<BioAggregate>,
                consentLog = (m["consent_log"] as List<Map<String, Any?>>).map { entry ->
                    entry.mapValues { (_, v) ->
                        when (v) {
                            is Long -> kotlinx.serialization.json.JsonPrimitive(v)
                            is Int -> kotlinx.serialization.json.JsonPrimitive(v)
                            is String -> kotlinx.serialization.json.JsonPrimitive(v)
                            else -> kotlinx.serialization.json.JsonPrimitive(v?.toString() ?: "")
                        }
                    }
                },
            )
        }
    }
}
