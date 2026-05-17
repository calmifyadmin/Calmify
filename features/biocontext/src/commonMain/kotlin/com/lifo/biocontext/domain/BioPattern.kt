package com.lifo.biocontext.domain

import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel

/**
 * Unified pattern model for the Pattern Feed (Phase 9.2.2, 2026-05-17).
 *
 * Every detection use case returns a [BioPattern]? — null means no honest
 * signal exists yet (cold start, sparse data, or detected magnitude below
 * the meaningfulness floor). This silence-by-default behavior preserves
 * Decision 2 (DataConfidence always visible) + dogma #4 (data sovereignty):
 * we never invent a pattern to fill the surface.
 *
 * The view layer renders each [BioPattern] using [BioCorrelationBars]
 * (two-row bar chart) + a narrative + a confidence footer.
 */
sealed class BioPattern {
    abstract val confidence: ConfidenceLevel
    abstract val source: BioSignalSource
    abstract val kind: Kind

    enum class Kind { SLEEP_MOOD, HRV_TREND, SLEEP_DRIFT }

    /**
     * Sleep × Mood — emotion intensity on days after good sleep vs after poor sleep.
     *
     * @property windowDays observation window
     * @property goodSleepNightsCount nights ≥ threshold in window
     * @property poorSleepNightsCount nights below threshold
     * @property goodSleepNextDayMoodAvg avg emotionIntensity (1..10) on days after good sleep
     * @property poorSleepNextDayMoodAvg avg emotionIntensity on days after poor sleep
     * @property liftPercent percentage by which good-sleep days score higher
     */
    data class SleepMood(
        val windowDays: Int,
        val goodSleepNightsCount: Int,
        val poorSleepNightsCount: Int,
        val goodSleepNextDayMoodAvg: Double,
        val poorSleepNextDayMoodAvg: Double,
        val liftPercent: Int,
        val sleepHoursThreshold: Double,
        override val confidence: ConfidenceLevel,
        override val source: BioSignalSource,
    ) : BioPattern() {
        override val kind = Kind.SLEEP_MOOD
    }

    /**
     * HRV trend — current 7d-window median vs 60-days-ago median (snapshot).
     *
     * @property currentMedianMs current HRV p50
     * @property pastMedianMs HRV p50 from snapshot 60 days ago
     * @property deltaPercent positive = HRV up (good), negative = down
     */
    data class HrvTrend(
        val currentMedianMs: Double,
        val pastMedianMs: Double,
        val deltaPercent: Int,
        val daysAgo: Int,
        override val confidence: ConfidenceLevel,
        override val source: BioSignalSource,
    ) : BioPattern() {
        override val kind = Kind.HRV_TREND
    }

    /**
     * Sleep duration drift — same shape as HrvTrend but for sleep minutes.
     *
     * @property currentMedianMinutes current sleep p50 (minutes)
     * @property pastMedianMinutes sleep p50 from snapshot N days ago
     * @property deltaPercent positive = sleeping more, negative = sleeping less
     */
    data class SleepDrift(
        val currentMedianMinutes: Double,
        val pastMedianMinutes: Double,
        val deltaPercent: Int,
        val daysAgo: Int,
        override val confidence: ConfidenceLevel,
        override val source: BioSignalSource,
    ) : BioPattern() {
        override val kind = Kind.SLEEP_DRIFT
    }
}
