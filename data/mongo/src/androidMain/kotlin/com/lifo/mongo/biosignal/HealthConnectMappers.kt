package com.lifo.mongo.biosignal

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import com.lifo.util.model.ActivityType
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.model.DataConfidence
import com.lifo.util.model.SleepStage
import com.lifo.util.model.SleepStageKind
import com.lifo.util.model.SourceKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Mappers from Health Connect [Record] types to Calmify domain [BioSignal] types.
 *
 * **Read-only one-way**: this file maps platform → domain. We never map back
 * (Calmify never writes to Health Connect).
 *
 * **Confidence inference**: see [inferConfidence]. Sparse sample streams (Mi
 * Band HRV, manual entries) get LOW; consistent wearable data gets MEDIUM/HIGH.
 * Honest defaults — no inflation (CLAUDE.md regola NASA-level).
 */
internal object HealthConnectMappers {

    // ──────────────────────────────────────────────────────────────────────
    // Heart Rate — each HeartRateRecord has multiple samples
    // ──────────────────────────────────────────────────────────────────────

    fun fromHeartRate(record: HeartRateRecord): List<BioSignal.HeartRateSample> {
        val source = sourceFrom(record.metadata)
        // HR samples in a HeartRateRecord typically span a few seconds; LOW
        // expected reliability if sample count is tiny.
        val baseConfidence = when {
            record.samples.size >= 60 -> ConfidenceLevel.HIGH
            record.samples.size >= 10 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
        return record.samples.map { sample ->
            BioSignal.HeartRateSample(
                timestamp = sample.time.toKotlinInstant(),
                bpm = sample.beatsPerMinute.toInt(),
                source = source,
                confidence = DataConfidence(
                    level = baseConfidence,
                    reasoning = "${record.samples.size} samples in record",
                ),
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // HRV (RMSSD) — typically sparse, single sample per record
    // ──────────────────────────────────────────────────────────────────────

    fun fromHrv(record: HeartRateVariabilityRmssdRecord): BioSignal.HrvSample {
        // HRV from cheap wearables (Mi Band) is sparse + occasionally unreliable;
        // mid-tier (Galaxy Watch) is intermittent but consistent; premium
        // (Oura/Whoop) is continuous-ish. Conservative default.
        return BioSignal.HrvSample(
            timestamp = record.time.toKotlinInstant(),
            rmssdMillis = record.heartRateVariabilityMillis,
            source = sourceFrom(record.metadata),
            confidence = DataConfidence(
                level = ConfidenceLevel.MEDIUM,
                reasoning = "Single HRV reading (typical for HC providers)",
            ),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Sleep Session — start/end + stages
    // ──────────────────────────────────────────────────────────────────────

    fun fromSleepSession(record: SleepSessionRecord, timezone: TimeZone): BioSignal.SleepSession {
        val start = record.startTime.toKotlinInstant()
        val end = record.endTime.toKotlinInstant()
        val totalDurationSec = (end.epochSeconds - start.epochSeconds).coerceAtLeast(0)

        val stages = record.stages.map { st ->
            SleepStage(
                startTimestamp = st.startTime.toKotlinInstant(),
                endTimestamp = st.endTime.toKotlinInstant(),
                kind = mapSleepStageKind(st.stage),
            )
        }
        // Sleep efficiency = (total - awake) / total
        val awakeSec = stages
            .filter { it.kind == SleepStageKind.AWAKE }
            .sumOf { it.durationSeconds }
        val efficiency = if (totalDurationSec > 0) {
            ((totalDurationSec - awakeSec).toDouble() / totalDurationSec.toDouble()) * 100.0
        } else null

        return BioSignal.SleepSession(
            timestamp = start,
            endTimestamp = end,
            stages = stages,
            efficiencyPercent = efficiency,
            source = sourceFrom(record.metadata),
            confidence = if (stages.isNotEmpty()) {
                DataConfidence(ConfidenceLevel.HIGH, "${stages.size} stages tracked")
            } else {
                DataConfidence(ConfidenceLevel.LOW, "No stage data — duration only")
            },
        )
    }

    private fun mapSleepStageKind(hcStage: Int): SleepStageKind = when (hcStage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageKind.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageKind.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageKind.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageKind.REM
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageKind.LIGHT  // umbrella → conservative
        else -> SleepStageKind.UNKNOWN
    }

    // ──────────────────────────────────────────────────────────────────────
    // Steps — daily aggregate
    // ──────────────────────────────────────────────────────────────────────

    fun fromSteps(record: StepsRecord, timezone: TimeZone): BioSignal.StepCount {
        val ts = record.startTime.toKotlinInstant()
        val date = ts.toLocalDateTime(timezone).date
        return BioSignal.StepCount(
            timestamp = ts,
            date = date,
            count = record.count.toInt(),
            source = sourceFrom(record.metadata),
            confidence = DataConfidence(
                level = ConfidenceLevel.HIGH,        // step counts are the most reliable HC metric
                reasoning = "Pedometer-grade",
            ),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Resting Heart Rate — typically nightly average from wearable
    // ──────────────────────────────────────────────────────────────────────

    fun fromRestingHeartRate(record: RestingHeartRateRecord, timezone: TimeZone): BioSignal.RestingHeartRate {
        val ts = record.time.toKotlinInstant()
        return BioSignal.RestingHeartRate(
            timestamp = ts,
            date = ts.toLocalDateTime(timezone).date,
            bpm = record.beatsPerMinute.toInt(),
            source = sourceFrom(record.metadata),
            confidence = DataConfidence(
                level = ConfidenceLevel.HIGH,
                reasoning = "Daily resting HR (wearable-computed)",
            ),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // SpO2 — spot reading
    // ──────────────────────────────────────────────────────────────────────

    fun fromOxygenSaturation(record: OxygenSaturationRecord): BioSignal.OxygenSaturationSample {
        return BioSignal.OxygenSaturationSample(
            timestamp = record.time.toKotlinInstant(),
            percent = record.percentage.value,        // Percentage.value → Double
            source = sourceFrom(record.metadata),
            confidence = DataConfidence(
                level = ConfidenceLevel.MEDIUM,
                reasoning = "Spot SpO2 reading",
            ),
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Exercise / Activity Session
    // ──────────────────────────────────────────────────────────────────────

    fun fromExerciseSession(record: ExerciseSessionRecord): BioSignal.ActivitySession {
        return BioSignal.ActivitySession(
            timestamp = record.startTime.toKotlinInstant(),
            endTimestamp = record.endTime.toKotlinInstant(),
            activityType = mapActivityType(record.exerciseType),
            activeCalories = null,                   // computed externally if needed via ExerciseSessionRecord aggregation
            distanceMeters = null,
            source = sourceFrom(record.metadata),
            confidence = DataConfidence(
                level = ConfidenceLevel.HIGH,
                reasoning = "Tracked exercise session",
            ),
        )
    }

    private fun mapActivityType(hcType: Int): ActivityType = when (hcType) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.WALKING
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ActivityType.RUNNING
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ActivityType.CYCLING
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> ActivityType.SWIMMING
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> ActivityType.YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> ActivityType.STRENGTH
        else -> ActivityType.OTHER
    }

    // ──────────────────────────────────────────────────────────────────────
    // Metadata → BioSignalSource
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Extract device + app provenance from Health Connect Metadata.
     * Defaults are conservative when fields are missing.
     */
    fun sourceFrom(metadata: Metadata): BioSignalSource {
        val device = metadata.device
        val kind = when (device?.type) {
            null -> SourceKind.PHONE
            else -> SourceKind.WEARABLE     // anything Metadata.device flags is wearable-like
        }
        val deviceName = device?.let {
            listOfNotNull(it.manufacturer, it.model).joinToString(" ").ifBlank { "Unknown device" }
        } ?: "Phone"
        val appName = metadata.dataOrigin.packageName
        return BioSignalSource(
            kind = kind,
            deviceName = deviceName,
            appName = appName,
        )
    }
}

/**
 * Infer overall confidence for a *time window* based on sample density.
 * Used by HealthConnectProvider read* functions to compute confidence for
 * aggregated/cumulative outputs (e.g. heart-rate "average over the day").
 */
internal fun inferConfidence(
    sampleCount: Int,
    expectedMinSamples: Int,
    reasoning: String,
): DataConfidence = when {
    sampleCount >= expectedMinSamples * 2 -> DataConfidence(ConfidenceLevel.HIGH, reasoning)
    sampleCount >= expectedMinSamples -> DataConfidence(ConfidenceLevel.MEDIUM, reasoning)
    else -> DataConfidence(ConfidenceLevel.LOW, reasoning)
}
