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
 * Detects whether the user's sleep median has shifted meaningfully compared
 * with a snapshot from [DAYS_AGO] days ago — Phase 9.2.2 (sleep drift).
 *
 * Sleep median values from [BioSignalRepository.getBaseline] are stored in
 * minutes; we compare current 14-day p50 against the snapshot to surface
 * slow rhythm changes the user might not feel day-to-day.
 */
class DetectSleepDriftPatternUseCase(
    private val bioRepository: BioSignalRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(): BioPattern.SleepDrift? {
        val past = bioRepository.getBaselineDaysAgo(BioSignalDataType.SLEEP, DAYS_AGO)
            ?: return previewOrNull()
        val now = Clock.System.now()
        val samples = bioRepository.getRawSamples(
            BioSignalDataType.SLEEP, now.minus(WINDOW_DAYS.toLong().days), now,
        ).filterIsInstance<BioSignal.SleepSession>()
        if (samples.size < MIN_SAMPLES_NOW) return previewOrNull()

        val currentMedian = samples.map { it.durationSeconds / 60.0 }.sorted().let { sorted ->
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

        return BioPattern.SleepDrift(
            currentMedianMinutes = currentMedian,
            pastMedianMinutes = past.p50,
            deltaPercent = delta,
            daysAgo = DAYS_AGO,
            confidence = confidence,
            source = primarySource,
        )
    }

    private fun previewOrNull(): BioPattern.SleepDrift? {
        if (!preview.enabled) return null
        return BioPattern.SleepDrift(
            currentMedianMinutes = 412.0,
            pastMedianMinutes = 451.0,
            deltaPercent = -9,
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
