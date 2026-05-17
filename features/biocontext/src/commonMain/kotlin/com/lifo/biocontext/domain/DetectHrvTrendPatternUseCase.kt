package com.lifo.biocontext.domain

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.preview.PreviewConfidence
import com.lifo.util.repository.BioSignalRepository
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.time.Duration.Companion.days

/**
 * Detects whether the user's HRV median has shifted meaningfully compared
 * with a snapshot from [DAYS_AGO] days ago — Phase 9.2.2 (HRV trend).
 *
 * Uses [BioSignalRepository.getBaselineDaysAgo] (Phase 9.2.5 historical
 * baseline table) for the past snapshot, and re-derives the current p50
 * from the latest [WINDOW_DAYS] of raw samples.
 */
class DetectHrvTrendPatternUseCase(
    private val bioRepository: BioSignalRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(): BioPattern.HrvTrend? {
        val past = bioRepository.getBaselineDaysAgo(BioSignalDataType.HRV, DAYS_AGO)
            ?: return previewOrNull()
        val now = Clock.System.now()
        val samples = bioRepository.getRawSamples(
            BioSignalDataType.HRV, now.minus(WINDOW_DAYS.toLong().days), now,
        ).filterIsInstance<BioSignal.HrvSample>()
        if (samples.size < MIN_SAMPLES_NOW) return previewOrNull()

        val currentMedian = samples.map { it.rmssdMillis }.sorted().let { sorted ->
            val mid = sorted.size / 2
            if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
            else sorted[mid]
        }
        if (past.p50 <= 0.0) return previewOrNull()
        val delta = ((currentMedian - past.p50) / past.p50 * 100.0).toInt()
        if (abs(delta) < MEANINGFUL_DELTA_PERCENT) return null

        val confidence = samples
            .map { it.confidence.level }
            .minByOrNull { rank(it) } ?: ConfidenceLevel.LOW
        val primarySource = samples
            .groupingBy { it.source }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: samples.first().source

        return BioPattern.HrvTrend(
            currentMedianMs = currentMedian,
            pastMedianMs = past.p50,
            deltaPercent = delta,
            daysAgo = DAYS_AGO,
            confidence = confidence,
            source = primarySource,
        )
    }

    private fun previewOrNull(): BioPattern.HrvTrend? {
        if (!preview.enabled) return null
        return BioPattern.HrvTrend(
            currentMedianMs = 48.0,
            pastMedianMs = 42.0,
            deltaPercent = 14,
            daysAgo = DAYS_AGO,
            confidence = PreviewConfidence,
            source = preview.previewSource,
        )
    }

    private fun rank(level: ConfidenceLevel): Int = when (level) {
        ConfidenceLevel.LOW -> 0
        ConfidenceLevel.MEDIUM -> 1
        ConfidenceLevel.HIGH -> 2
    }

    companion object {
        private const val DAYS_AGO = 60
        private const val WINDOW_DAYS = 14
        private const val MIN_SAMPLES_NOW = 5
        private const val MEANINGFUL_DELTA_PERCENT = 5
    }
}
