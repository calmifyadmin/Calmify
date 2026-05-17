package com.lifo.util.repository

import com.lifo.util.model.AggregatePeriod
import com.lifo.util.model.BioAggregate
import com.lifo.util.model.BioBaseline
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Bio-signal repository — local-first ingestion + server-bound aggregates.
 *
 * **Storage model** (per `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` Decision 1):
 * - Raw samples → local SQLDelight only (TTL default 30d, configurable per type).
 * - Aggregates (7/30/90-day rolling) → server (`bio_aggregates/{userId}/...`).
 * - Optional opt-in raw upload windows for PRO-tier advanced correlations.
 *
 * **Read paths**:
 * - [observeRawSamples] — Flow over local SQLDelight (reactive UI).
 * - [getAggregate] — one-shot pull from server for trend/dashboard views.
 *
 * **Write path**:
 * - [ingestFromProvider] — daily sync trigger, pulls from [HealthDataProvider]
 *   into local DB, computes aggregates, pushes aggregates to server.
 *
 * **GDPR**:
 * - [exportAll] — Art.20 portability (raw + aggregates as JSON).
 * - [deleteAll] — Art.17 atomic delete (local + server in one logical op).
 */
interface BioSignalRepository {

    // ──────────────────────────────────────────────────────────────────────
    // Read — raw samples (local-only)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Reactive observation of raw samples within a time window for a specific type.
     * UI surfaces (Journal contextual card, Meditation post-session, etc.) use
     * this to react to fresh data.
     */
    fun observeRawSamples(
        type: BioSignalDataType,
        from: Instant,
        until: Instant,
    ): Flow<List<BioSignal>>

    /** One-shot raw read for a specific type. Used by ingestion + export. */
    suspend fun getRawSamples(
        type: BioSignalDataType,
        from: Instant,
        until: Instant,
    ): List<BioSignal>

    // ──────────────────────────────────────────────────────────────────────
    // Read — aggregates (server-backed, falls back to local computation)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Get a rolling aggregate. PRO-gated: FREE tier returns local 7d aggregate only.
     * Server returns 30/90-day historical trends.
     */
    suspend fun getAggregate(
        type: BioSignalDataType,
        period: AggregatePeriod,
        periodKey: String,        // ISO format depending on period
    ): BioAggregate?

    /** Reactive aggregate observation — used by trend charts on Home/Profile. */
    fun observeAggregate(
        type: BioSignalDataType,
        period: AggregatePeriod,
    ): Flow<BioAggregate?>

    // ──────────────────────────────────────────────────────────────────────
    // Baselines (Phase 6, 2026-05-17) — per-user rolling distribution
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Get the user's rolling baseline for a data type. Used by Phase 5+ cards
     * to personalize thresholds (e.g. Card 1 sleep-banner thresholds become
     * `p25` / `p75` of the user's last 30d of sleep instead of universal 6h / 7h30m).
     *
     * Returns `null` when:
     *   - no baseline has been computed yet (cold start), OR
     *   - the underlying sample count is below the caller's `minSamples` floor
     *
     * Callers MUST fall back to universal thresholds when `null` — per
     * Decision 2 + dogma #4 we never claim personalization we can't back.
     */
    suspend fun getBaseline(
        type: BioSignalDataType,
        periodDays: Int = 30,
        minSamples: Int = 7,
    ): BioBaseline?

    /**
     * Recompute baselines for every [BioSignalDataType] from the local store
     * and upsert. Idempotent. Called at the end of [ingestFromProvider] and
     * (Android) by the daily WorkManager cron as a fallback.
     *
     * Skips a type when its sample count is below the use case's MIN_SAMPLES
     * floor — better no baseline than a noisy one.
     */
    suspend fun recomputeBaselines(periodDays: Int = 30): Int   // returns count of baselines written

    /**
     * Phase 9.2.5 — get a baseline snapshot at or before `daysAgo` days ago.
     * Used to compute drift ("your HRV median is +12% vs 60 days ago"). Returns
     * `null` if no snapshot is old enough — drift is silent rather than guessed.
     *
     * Snapshots are auto-written by [recomputeBaselines] (one per type per day),
     * see `bio_baseline_history.sq` in `:data:mongo`.
     */
    suspend fun getBaselineDaysAgo(type: BioSignalDataType, daysAgo: Int): BioBaseline?

    // ──────────────────────────────────────────────────────────────────────
    // Write / sync
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Pull fresh data from the platform provider, store raw locally, compute
     * aggregates, push aggregates to server. Idempotent on the (timestamp, type, source)
     * tuple — safe to call multiple times for the same window.
     *
     * Typically invoked by WorkManager daily reconcile worker on Android, or
     * BGTaskScheduler on iOS.
     */
    suspend fun ingestFromProvider(provider: HealthDataProvider, since: Instant): IngestionResult

    /**
     * Manually push aggregates that were computed locally but never made it
     * to the server (offline-first reconcile path).
     */
    suspend fun syncPendingAggregates(): Int           // returns count pushed

    // ──────────────────────────────────────────────────────────────────────
    // GDPR
    // ──────────────────────────────────────────────────────────────────────

    /** Art.20 — full export (raw locale + server aggregates + consent log) as JSON. */
    suspend fun exportAll(): String

    /** Art.17 — atomic delete of all bio-signal data, local + server. Idempotent. */
    suspend fun deleteAll()

    // ──────────────────────────────────────────────────────────────────────
    // TTL management — local raw samples retention
    // ──────────────────────────────────────────────────────────────────────

    /** Prune raw samples older than the per-type TTL. Daily cron. */
    suspend fun pruneExpiredSamples(): Int             // returns count pruned

    /** Force-prune raw samples for a specific date range — used by GDPR partial delete. */
    suspend fun pruneSamplesInRange(from: LocalDate, until: LocalDate)
}

/** Outcome of a single [BioSignalRepository.ingestFromProvider] call. */
data class IngestionResult(
    val rawSamplesIngested: Int,
    val aggregatesComputed: Int,
    val aggregatesPushed: Int,
    val errors: List<String>,
)
