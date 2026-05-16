package com.lifo.meditation.domain

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.repository.BioSignalRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Folds the heart-rate samples collected during a meditation session into a
 * [SessionHrSummary] for the Phase 5.3 outro bio card (Card 2).
 *
 * Per `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`:
 * - No targets, no "you did well" judgements — the chart shows the trajectory,
 *   the user reads meaning.
 * - Silence-by-default: if we have fewer than 3 samples (the minimum for a
 *   meaningful trajectory), return `null` and the card stays hidden.
 *
 * The session window is the last [elapsedMillis] milliseconds — meditation
 * sessions are short enough that wall-clock = session-clock is a safe MVP.
 */
class GetSessionHrSummaryUseCase(
    private val repository: BioSignalRepository,
) {
    suspend operator fun invoke(elapsedMillis: Long): SessionHrSummary? {
        if (elapsedMillis <= 0L) return null

        val now = Clock.System.now()
        val from = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - elapsedMillis)

        val samples = repository
            .getRawSamples(BioSignalDataType.HEART_RATE, from, now)
            .filterIsInstance<BioSignal.HeartRateSample>()
            .sortedBy { it.timestamp.toEpochMilliseconds() }

        if (samples.size < MIN_POINTS_FOR_CHART) return null

        val start = samples.first()
        val end = samples.last()
        val minBpm = samples.minOf { it.bpm }
        val maxBpm = samples.maxOf { it.bpm }
        val sessionStartMillis = start.timestamp.toEpochMilliseconds()

        val plottable = samples.map { sample ->
            HrPoint(
                bpm = sample.bpm,
                elapsedSeconds = ((sample.timestamp.toEpochMilliseconds() - sessionStartMillis) / 1000L).toInt(),
            )
        }

        // Confidence floor across all rendered samples — onestà radicale (Decision 2).
        val confidence = samples
            .map { it.confidence.level }
            .minByOrNull { confidenceRank(it) }
            ?: ConfidenceLevel.LOW

        val primarySource = samples
            .groupingBy { it.source }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: start.source

        // Phase 6.3 — pull user's HR baseline for typical-range chart bands.
        // Null when sample count below the floor; chart falls back to its
        // hardcoded 40%/70% bands in that case (handled inside the atom).
        val baseline = repository.getBaseline(BioSignalDataType.HEART_RATE)

        return SessionHrSummary(
            startBpm = start.bpm,
            endBpm = end.bpm,
            minBpm = minBpm,
            maxBpm = maxBpm,
            points = plottable,
            confidence = confidence,
            source = primarySource,
            typicalLowBpm = baseline?.p10?.toInt(),
            typicalHighBpm = baseline?.p90?.toInt(),
        )
    }

    private fun confidenceRank(level: ConfidenceLevel): Int = when (level) {
        ConfidenceLevel.LOW -> 0
        ConfidenceLevel.MEDIUM -> 1
        ConfidenceLevel.HIGH -> 2
    }

    companion object {
        /**
         * 3 = minimum for "trajectory not a guess". Below this the chart would
         * imply confidence we don't have; per Decision 2 + dogma #4, we'd
         * rather show nothing.
         */
        private const val MIN_POINTS_FOR_CHART = 3
    }
}

/**
 * Fold of the heart-rate samples within a meditation session window.
 *
 * @property startBpm   first sample's bpm
 * @property endBpm     last sample's bpm
 * @property minBpm     lowest sample's bpm — used as the chart's Y floor
 * @property maxBpm     highest sample's bpm — used as the chart's Y ceiling
 * @property points     time-ordered list of [HrPoint] for the line plot
 * @property confidence floor across the materials rendered
 * @property source     most-frequent source device across the materials rendered
 */
data class SessionHrSummary(
    val startBpm: Int,
    val endBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val points: List<HrPoint>,
    val confidence: ConfidenceLevel,
    val source: BioSignalSource,
    /**
     * User's typical HR floor (p10 of trailing 30d), null when no baseline yet.
     * Surfaces as a dotted horizontal band in the chart.
     */
    val typicalLowBpm: Int? = null,
    /**
     * User's typical HR ceiling (p90 of trailing 30d), null when no baseline yet.
     */
    val typicalHighBpm: Int? = null,
) {
    /** Bpm delta from start → end (negative = the session "lowered" HR). */
    val drop: Int get() = startBpm - endBpm

    /** True if the trajectory shows a meaningful HR drop worth narrating. */
    val isMeaningfulDrop: Boolean get() = drop >= MEANINGFUL_DROP_BPM

    companion object {
        /** ≥4 bpm — small enough to register on most wearables, big enough to be honest. */
        private const val MEANINGFUL_DROP_BPM = 4
    }
}

data class HrPoint(
    val bpm: Int,
    val elapsedSeconds: Int,
)
