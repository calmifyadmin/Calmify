package com.lifo.home.domain.usecase

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.repository.BioSignalRepository
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * Weekly bio narrative — Phase 8.2 (local fallback for the AI surface).
 *
 * Computes a templated narrative comparing the user's last-7-days HRV against
 * their 30-day baseline. Returns `null` when:
 * - User has no HRV baseline yet (cold start), OR
 * - <3 days of HRV samples in the last 7 (too sparse to compare honestly), OR
 * - The week-vs-baseline delta is below the [MEANINGFUL_DELTA_PERCENT] floor —
 *   below this we'd be inventing patterns from noise (Decision 2 + dogma #4)
 *
 * The narrative output is one of three flavors keyed by [NarrativeFlavor]:
 * - HIGHER  — this week was meaningfully higher than the user's typical
 * - LOWER   — this week was meaningfully lower than the user's typical
 * - STEADY  — used when in-range BUT data density is high enough to make
 *             "still learning your patterns" feel disingenuous
 *
 * **Phase 8.2 contract**: this is a deliberate stepping-stone to the real
 * AI narrative in Phase 8.3/8.4 (Gemini-powered, longer prose). The atom
 * `BioNarrativeCard` is the stable contract; the use case is the swap point.
 */
class GetWeeklyBioNarrativeUseCase(
    private val repository: BioSignalRepository,
) {
    suspend operator fun invoke(): WeeklyBioNarrative? {
        val baseline = repository.getBaseline(BioSignalDataType.HRV) ?: return null
        if (baseline.p50 <= 0.0) return null

        val now = Clock.System.now()
        val weekAgo = now.minus(7.days)
        val samples = repository
            .getRawSamples(BioSignalDataType.HRV, weekAgo, now)
            .filterIsInstance<BioSignal.HrvSample>()

        if (samples.size < MIN_WEEK_SAMPLES) return null

        // Per-day average so heavy days don't dominate
        val dailyAverages = samples
            .groupBy { it.timestamp.toEpochMilliseconds() / DAY_MILLIS }
            .values
            .map { day -> day.map { it.rmssdMillis }.average() }

        if (dailyAverages.size < MIN_DAYS_COVERED) return null

        val weekAvgMs = dailyAverages.average()
        val baselineMedianMs = baseline.p50
        val deltaPercent = ((weekAvgMs - baselineMedianMs) / baselineMedianMs * 100.0).toInt()
        val absDelta = if (deltaPercent < 0) -deltaPercent else deltaPercent

        val flavor = when {
            deltaPercent >= MEANINGFUL_DELTA_PERCENT -> NarrativeFlavor.HIGHER
            deltaPercent <= -MEANINGFUL_DELTA_PERCENT -> NarrativeFlavor.LOWER
            absDelta in STEADY_BAND_MIN..STEADY_BAND_MAX &&
                dailyAverages.size >= STEADY_MIN_DAYS -> NarrativeFlavor.STEADY
            else -> return null
        }

        // Confidence floor across the rendered week's samples (onestà radicale).
        val confidence = samples
            .map { it.confidence.level }
            .minByOrNull { rank(it) }
            ?: ConfidenceLevel.LOW
        val primarySource = samples
            .groupingBy { it.source }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: samples.first().source

        return WeeklyBioNarrative(
            flavor = flavor,
            weekAvgMs = weekAvgMs.toInt(),
            baselineMedianMs = baselineMedianMs.toInt(),
            deltaPercent = deltaPercent,
            daysCovered = dailyAverages.size,
            confidence = confidence,
            source = primarySource,
        )
    }

    private fun rank(level: ConfidenceLevel): Int = when (level) {
        ConfidenceLevel.LOW -> 0
        ConfidenceLevel.MEDIUM -> 1
        ConfidenceLevel.HIGH -> 2
    }

    companion object {
        private const val MIN_WEEK_SAMPLES = 7        // ≥1/day on average
        private const val MIN_DAYS_COVERED = 3        // need ≥3 distinct days
        private const val STEADY_MIN_DAYS = 5         // higher floor for "steady" narrative
        private const val MEANINGFUL_DELTA_PERCENT = 8
        private const val STEADY_BAND_MIN = 0
        private const val STEADY_BAND_MAX = 5
        private const val DAY_MILLIS = 86_400_000L
    }
}

/** Templated weekly HRV narrative — input for [com.lifo.ui.components.biosignal.BioNarrativeCard]. */
data class WeeklyBioNarrative(
    val flavor: NarrativeFlavor,
    val weekAvgMs: Int,
    val baselineMedianMs: Int,
    /** Signed: positive = higher than baseline, negative = lower. */
    val deltaPercent: Int,
    val daysCovered: Int,
    val confidence: ConfidenceLevel,
    val source: BioSignalSource,
)

enum class NarrativeFlavor { HIGHER, LOWER, STEADY }
