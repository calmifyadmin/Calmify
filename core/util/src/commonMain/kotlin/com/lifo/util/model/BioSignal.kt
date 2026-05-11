package com.lifo.util.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Bio-signal domain — wearable + phone sensor data as **context** for mental wellness.
 *
 * This package is read-only: Calmify NEVER writes back to Health Connect / HealthKit.
 * All values are sourced from external providers via [HealthDataProvider] and
 * surfaced as context inside existing flows (journal, meditation, insight, home),
 * NOT in a separate "Fitness" tab.
 *
 * Design source-of-truth: `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` + the 7 HTML
 * mockups in `design/biosignal/`. Ethical positioning: see
 * `memory/feedback_calmify_values.md` (7 dogmas: not competitive, ethical,
 * helpful-not-optimizing, data sovereignty, NASA-quality, accessible, KMP-first,
 * sustainable organism).
 */
sealed class BioSignal {
    abstract val timestamp: Instant
    abstract val source: BioSignalSource
    abstract val confidence: DataConfidence

    /** Continuous or spot heart-rate sample in BPM. */
    data class HeartRateSample(
        override val timestamp: Instant,
        val bpm: Int,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal()

    /** Heart-rate variability (RMSSD) sample in milliseconds. Higher = lower stress. */
    data class HrvSample(
        override val timestamp: Instant,
        val rmssdMillis: Double,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal()

    /** A full sleep session with start/end + sleep stages. */
    data class SleepSession(
        override val timestamp: Instant,        // session start
        val endTimestamp: Instant,
        val stages: List<SleepStage>,
        val efficiencyPercent: Double?,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal() {
        val durationSeconds: Long get() = (endTimestamp.epochSeconds - timestamp.epochSeconds)
    }

    /** Daily step count for a given local date. */
    data class StepCount(
        override val timestamp: Instant,        // start of the day in user TZ
        val date: LocalDate,
        val count: Int,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal()

    /** Resting heart rate measurement (typically nightly average). */
    data class RestingHeartRate(
        override val timestamp: Instant,
        val date: LocalDate,
        val bpm: Int,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal()

    /** Blood-oxygen saturation (SpO2) spot reading, 0..100 %. */
    data class OxygenSaturationSample(
        override val timestamp: Instant,
        val percent: Double,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal()

    /** Exercise / activity session (walking, running, cycling, etc.). */
    data class ActivitySession(
        override val timestamp: Instant,        // session start
        val endTimestamp: Instant,
        val activityType: ActivityType,
        val activeCalories: Double?,
        val distanceMeters: Double?,
        override val source: BioSignalSource,
        override val confidence: DataConfidence,
    ) : BioSignal() {
        val durationSeconds: Long get() = (endTimestamp.epochSeconds - timestamp.epochSeconds)
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Supporting types
// ──────────────────────────────────────────────────────────────────────────

/** Logical "type" of a bio-signal — used for granular permission + provider routing. */
enum class BioSignalDataType {
    HEART_RATE,
    HRV,
    SLEEP,
    STEPS,
    RESTING_HEART_RATE,
    OXYGEN_SATURATION,
    ACTIVITY,
}

/**
 * Data confidence — onestà radicale.
 *
 * Every metric surfaced in UI must show source + level. NEVER inflate a sparse
 * Mi Band reading to look as authoritative as a Whoop continuous stream.
 * See `memory/feedback_calmify_values.md` dogma #4 (data sovereignty + transparency).
 */
data class DataConfidence(
    val level: ConfidenceLevel,
    val reasoning: String,                      // e.g. "12 samples in last 7d (typical: 200+)"
)

enum class ConfidenceLevel {
    HIGH,    // continuous reliable stream (Oura/Whoop/Apple Watch/Pixel Watch)
    MEDIUM,  // intermittent but consistent (Galaxy Watch, Mi Band steps/HR)
    LOW,     // sparse or single-source (Mi Band HRV, manual entry)
}

/**
 * Provider source identity — which device + app produced this data.
 * Displayed to the user in [DataConfidence] footer ("📊 From Mi Band 10 via Mi Fitness").
 */
data class BioSignalSource(
    val kind: SourceKind,
    val deviceName: String,                     // e.g. "Mi Band 10"
    val appName: String,                        // e.g. "Mi Fitness"
)

enum class SourceKind {
    WEARABLE,     // bracelet / watch / ring
    PHONE,        // phone sensors (Samsung Health, Pixel)
    MANUAL,       // user-entered
    DERIVED,      // computed by Calmify from other signals
}

// ──────────────────────────────────────────────────────────────────────────
// Sleep + Activity sub-types
// ──────────────────────────────────────────────────────────────────────────

data class SleepStage(
    val startTimestamp: Instant,
    val endTimestamp: Instant,
    val kind: SleepStageKind,
) {
    val durationSeconds: Long get() = (endTimestamp.epochSeconds - startTimestamp.epochSeconds)
}

enum class SleepStageKind {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    UNKNOWN,
}

enum class ActivityType {
    WALKING,
    RUNNING,
    CYCLING,
    SWIMMING,
    YOGA,
    STRENGTH,
    OTHER,
}

// ──────────────────────────────────────────────────────────────────────────
// Server-bound aggregate contract
// ──────────────────────────────────────────────────────────────────────────

/**
 * Rolling aggregate for a single bio-signal data type over a period.
 * **Server stores ONLY aggregates, NEVER raw samples** (privacy by design —
 * see `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` Decision 1).
 *
 * Raw samples stay local in SQLDelight with TTL (default 30 days). Optional
 * opt-in raw upload windows for PRO-tier advanced correlations.
 */
data class BioAggregate(
    val type: BioSignalDataType,
    val period: AggregatePeriod,
    val periodKey: String,                      // ISO format: "2026-W19" / "2026-05-11" / "2026-05"
    val mean: Double,
    val p10: Double,
    val p90: Double,
    val count: Int,
    val confidenceWeightedMean: Double,         // mean weighted by per-sample confidence
    val sourceMix: Map<String, Int>,            // device → sample count breakdown
)

enum class AggregatePeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
}
