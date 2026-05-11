package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Protobuf-safe DTOs for bio-signal data (server contract).
 *
 * **Hard rule (CLAUDE.md regola 3)**: every field is non-nullable with a
 * default value. `kotlinx.serialization.protobuf` does NOT support `T? = null`
 * — runtime crash on encoding.
 *
 * **Privacy guarantee**: raw samples are NEVER serialized to the server. Only
 * `BioAggregateProto` is sent over the wire (see `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`
 * Decision 1: "aggregates server + raw locale"). Raw `BioSignal*Proto` types
 * are reserved for the optional opt-in raw-upload windows (PRO advanced
 * correlations) and for local SQLDelight serialization.
 *
 * Domain mappers live in `data/mongo` and `data/network` (Domain ↔ Proto).
 */

// ──────────────────────────────────────────────────────────────────────────
// Raw samples (local-only by default, opt-in server upload for PRO)
// ──────────────────────────────────────────────────────────────────────────

@Serializable
data class HeartRateSampleProto(
    @ProtoNumber(1) val timestampMillis: Long = 0L,
    @ProtoNumber(2) val bpm: Int = 0,
    @ProtoNumber(3) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(4) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

@Serializable
data class HrvSampleProto(
    @ProtoNumber(1) val timestampMillis: Long = 0L,
    @ProtoNumber(2) val rmssdMillis: Double = 0.0,
    @ProtoNumber(3) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(4) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

@Serializable
data class SleepSessionProto(
    @ProtoNumber(1) val startMillis: Long = 0L,
    @ProtoNumber(2) val endMillis: Long = 0L,
    @ProtoNumber(3) val stages: List<SleepStageProto> = emptyList(),
    @ProtoNumber(4) val efficiencyPercent: Double = 0.0,
    @ProtoNumber(5) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(6) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

@Serializable
data class SleepStageProto(
    @ProtoNumber(1) val startMillis: Long = 0L,
    @ProtoNumber(2) val endMillis: Long = 0L,
    @ProtoNumber(3) val kind: String = "UNKNOWN",       // SleepStageKind.name
)

@Serializable
data class StepCountProto(
    @ProtoNumber(1) val timestampMillis: Long = 0L,
    @ProtoNumber(2) val dateIso: String = "",           // ISO LocalDate
    @ProtoNumber(3) val count: Int = 0,
    @ProtoNumber(4) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(5) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

@Serializable
data class RestingHeartRateProto(
    @ProtoNumber(1) val timestampMillis: Long = 0L,
    @ProtoNumber(2) val dateIso: String = "",           // ISO LocalDate
    @ProtoNumber(3) val bpm: Int = 0,
    @ProtoNumber(4) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(5) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

@Serializable
data class OxygenSaturationSampleProto(
    @ProtoNumber(1) val timestampMillis: Long = 0L,
    @ProtoNumber(2) val percent: Double = 0.0,
    @ProtoNumber(3) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(4) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

@Serializable
data class ActivitySessionProto(
    @ProtoNumber(1) val startMillis: Long = 0L,
    @ProtoNumber(2) val endMillis: Long = 0L,
    @ProtoNumber(3) val activityType: String = "OTHER",  // ActivityType.name
    @ProtoNumber(4) val activeCalories: Double = 0.0,
    @ProtoNumber(5) val distanceMeters: Double = 0.0,
    @ProtoNumber(6) val source: BioSignalSourceProto = BioSignalSourceProto(),
    @ProtoNumber(7) val confidence: DataConfidenceProto = DataConfidenceProto(),
)

// ──────────────────────────────────────────────────────────────────────────
// Server-bound aggregate — the only thing the server normally receives
// ──────────────────────────────────────────────────────────────────────────

/**
 * Rolling aggregate stored in Firestore at
 * `bio_aggregates/{userId}/{daily|weekly|monthly}/{periodKey}`.
 * Composite indexes pre-deployed in `firestore.indexes.json` (lezione Phase 5
 * dell'audit backend — index drift causa query 9-error a runtime).
 */
@Serializable
data class BioAggregateProto(
    @ProtoNumber(1) val ownerId: String = "",
    @ProtoNumber(2) val type: String = "",              // BioSignalDataType.name
    @ProtoNumber(3) val period: String = "DAILY",       // AggregatePeriod.name
    @ProtoNumber(4) val periodKey: String = "",         // ISO format depending on period
    @ProtoNumber(5) val mean: Double = 0.0,
    @ProtoNumber(6) val p10: Double = 0.0,
    @ProtoNumber(7) val p90: Double = 0.0,
    @ProtoNumber(8) val count: Int = 0,
    @ProtoNumber(9) val confidenceWeightedMean: Double = 0.0,
    /** Device-name → sample-count breakdown for transparency. */
    @ProtoNumber(10) val sourceMix: Map<String, Int> = emptyMap(),
    @ProtoNumber(11) val computedAtMillis: Long = 0L,
)

/** Batch wrapper for `POST /api/v1/bio/ingest`. Server clamps to 500 per CLAUDE.md regola 8. */
@Serializable
data class BioAggregateBatchProto(
    @ProtoNumber(1) val aggregates: List<BioAggregateProto> = emptyList(),
    @ProtoNumber(2) val clientTimezone: String = "",    // user's IANA timezone (regola 7)
)

@Serializable
data class BioAggregateResponseProto(
    @ProtoNumber(1) val aggregates: List<BioAggregateProto> = emptyList(),
    @ProtoNumber(2) val cacheControl: String = "",      // "max-age=300" etc.
)

// ──────────────────────────────────────────────────────────────────────────
// Supporting Proto types
// ──────────────────────────────────────────────────────────────────────────

@Serializable
data class BioSignalSourceProto(
    @ProtoNumber(1) val kind: String = "WEARABLE",      // SourceKind.name
    @ProtoNumber(2) val deviceName: String = "",
    @ProtoNumber(3) val appName: String = "",
)

@Serializable
data class DataConfidenceProto(
    @ProtoNumber(1) val level: String = "MEDIUM",       // ConfidenceLevel.name
    @ProtoNumber(2) val reasoning: String = "",
)

// ──────────────────────────────────────────────────────────────────────────
// Consent log — every grant/revoke is auditable (GDPR Art.7 demonstrability)
// ──────────────────────────────────────────────────────────────────────────

@Serializable
data class BioConsentEventProto(
    @ProtoNumber(1) val ownerId: String = "",
    @ProtoNumber(2) val timestampMillis: Long = 0L,
    @ProtoNumber(3) val action: String = "GRANT",       // GRANT / REVOKE
    @ProtoNumber(4) val dataType: String = "",          // BioSignalDataType.name (or "*" for bulk)
    @ProtoNumber(5) val clientVersion: String = "",
    @ProtoNumber(6) val timezone: String = "",
)
