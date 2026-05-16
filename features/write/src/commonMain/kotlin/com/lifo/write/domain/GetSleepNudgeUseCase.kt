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
 * - [SleepNudge.ShortNight]  — duration < 6h ⇒ gentle "be kind to yourself" copy
 * - [SleepNudge.SolidRest]   — duration ≥ 7h 30m AND efficiency ≥ 85% ⇒ soft celebration
 * - `null`                   — anything in between, no session, or no data ⇒ silence
 *
 * **Thresholds**: chosen as universal-enough for MVP. Per-user baselines come
 * with Phase 6 (cross-signal correlation). Per the bussola
 * (`memory/feedback_biosignal_plan_as_compass.md`), we never frame the result
 * as a score or target — both buckets are observational.
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

        return when {
            durationMinutes < SHORT_NIGHT_THRESHOLD_MINUTES -> SleepNudge.ShortNight(
                durationMinutes = durationMinutes,
                confidence = lastSleep.confidence.level,
                source = lastSleep.source,
            )
            durationMinutes >= SOLID_REST_THRESHOLD_MINUTES &&
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
        private const val SHORT_NIGHT_THRESHOLD_MINUTES = 360   // 6h 00m
        private const val SOLID_REST_THRESHOLD_MINUTES = 450    // 7h 30m
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
