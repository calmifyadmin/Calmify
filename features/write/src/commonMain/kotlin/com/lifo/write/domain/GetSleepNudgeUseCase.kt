package com.lifo.write.domain

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.repository.BioSignalRepository
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Sleep-aware nudge for the Journal home screen banner (Phase 5.2).
 *
 * Reads the user's last completed sleep session (≤24h window) from the local
 * SQLDelight store and classifies it into one of three buckets:
 *
 * - [SleepNudge.ShortNight]  — duration in the user's bottom-25% range ⇒ gentle copy
 * - [SleepNudge.SolidRest]   — duration in the user's top-25% range AND efficiency ≥ 85% ⇒ soft celebration
 * - `null`                   — anything in between, no session, or no data ⇒ silence
 *
 * **Thresholds (Phase 6, 2026-05-17)**: tries the user's own baseline first
 * via [BioSignalRepository.getBaseline] — p25 of trailing-30d sleep duration
 * for ShortNight, p75 for SolidRest. Falls back to universal thresholds (6h
 * and 7h30m) when no baseline exists yet (cold start). Per
 * `memory/feedback_biosignal_plan_as_compass.md`, both buckets stay
 * observational — never framed as a score or target.
 *
 * Returns the [ConfidenceLevel] + [BioSignalSource] of the source sleep sample
 * so the banner footer can display "From {device} · {level} confidence" (Decision 2
 * in `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` — DataConfidence always visible).
 */
class GetSleepNudgeUseCase(
    private val repository: BioSignalRepository,
) {
    suspend operator fun invoke(): SleepNudge? {
        val now = Clock.System.now()
        val windowFrom = now.minus(LOOKBACK_HOURS.hours)

        val lastSleep = repository
            .getRawSamples(BioSignalDataType.SLEEP, windowFrom, now)
            .filterIsInstance<BioSignal.SleepSession>()
            .maxByOrNull { it.endTimestamp.toEpochMilliseconds() }
            ?: return null

        val durationMinutes = (lastSleep.durationSeconds / 60L).toInt()
        if (durationMinutes <= 0) return null

        // Per-user baseline (Phase 6) → fall back to universal thresholds when cold.
        val baseline = repository.getBaseline(BioSignalDataType.SLEEP, periodDays = 30)
        val shortThreshold = baseline?.p25?.toInt() ?: UNIVERSAL_SHORT_NIGHT_MIN
        val solidThreshold = baseline?.p75?.toInt() ?: UNIVERSAL_SOLID_REST_MIN

        return when {
            durationMinutes < shortThreshold -> SleepNudge.ShortNight(
                durationMinutes = durationMinutes,
                confidence = lastSleep.confidence.level,
                source = lastSleep.source,
            )
            durationMinutes >= solidThreshold &&
                (lastSleep.efficiencyPercent ?: 100.0) >= SOLID_REST_EFFICIENCY_PERCENT -> SleepNudge.SolidRest(
                durationMinutes = durationMinutes,
                confidence = lastSleep.confidence.level,
                source = lastSleep.source,
            )
            else -> null
        }
    }

    companion object {
        private const val LOOKBACK_HOURS = 24L
        /** Universal floor for ShortNight when the user has no baseline yet. */
        private const val UNIVERSAL_SHORT_NIGHT_MIN = 360       // 6h 00m
        /** Universal ceiling for SolidRest when the user has no baseline yet. */
        private const val UNIVERSAL_SOLID_REST_MIN = 450        // 7h 30m
        private const val SOLID_REST_EFFICIENCY_PERCENT = 85.0
    }
}

/**
 * Classified sleep-nudge outcome. `null` from the use case = no banner.
 */
sealed interface SleepNudge {
    val durationMinutes: Int
    val confidence: ConfidenceLevel
    val source: BioSignalSource

    data class ShortNight(
        override val durationMinutes: Int,
        override val confidence: ConfidenceLevel,
        override val source: BioSignalSource,
    ) : SleepNudge

    data class SolidRest(
        override val durationMinutes: Int,
        override val confidence: ConfidenceLevel,
        override val source: BioSignalSource,
    ) : SleepNudge
}
