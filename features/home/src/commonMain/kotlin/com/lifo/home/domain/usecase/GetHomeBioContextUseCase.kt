package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.HomeBioContext
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.preview.PreviewConfidence
import com.lifo.util.repository.BioSignalRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Reads the last ~24h of bio samples from the local store and folds them into a
 * [HomeBioContext] for the Home "Today" narrative card.
 *
 * Returns `null` when the user has no bio data at all — the card should not
 * appear in that case (silence, not an error state).
 *
 * Per Phase 5 plan: this runs on Home refresh + on init. It does NOT trigger a
 * Health Connect read; that path is owned by WorkManager and the explicit
 * "Sync now" button in `:features:biocontext`.
 */
class GetHomeBioContextUseCase(
    private val repository: BioSignalRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(): HomeBioContext? {
        val now = Clock.System.now()
        val windowFrom = now.minus(LOOKBACK_HOURS.hours)
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

        val sleepSamples = repository.getRawSamples(BioSignalDataType.SLEEP, windowFrom, now)
        val hrSamples = repository.getRawSamples(BioSignalDataType.HEART_RATE, windowFrom, now)
        val restingHrSamples = repository.getRawSamples(BioSignalDataType.RESTING_HEART_RATE, windowFrom, now)
        val stepSamples = repository.getRawSamples(BioSignalDataType.STEPS, windowFrom, now)

        if (sleepSamples.isEmpty() && hrSamples.isEmpty() && restingHrSamples.isEmpty() && stepSamples.isEmpty()) {
            // Phase 9.2.4 — preview fallback so fresh-install users see the card.
            return if (preview.enabled) HomeBioContext(
                sleepDurationMinutes = 7 * 60 + 24,   // 7h 24m
                heartRateBpm = 64,
                stepsToday = 3127,
                confidenceFloor = PreviewConfidence,
                primarySource = preview.previewSource,
            ) else null
        }

        val lastSleep = sleepSamples
            .filterIsInstance<BioSignal.SleepSession>()
            .maxByOrNull { it.endTimestamp.toEpochMilliseconds() }

        // Prefer a fresh resting-HR (medical-grade); fall back to a morning average of HR samples.
        val morningWindowStart = now.minus(MORNING_LOOKBACK_HOURS.hours).toEpochMilliseconds()
        val freshRestingHr = restingHrSamples
            .filterIsInstance<BioSignal.RestingHeartRate>()
            .maxByOrNull { it.timestamp.toEpochMilliseconds() }
        val morningHrAvg = hrSamples
            .filterIsInstance<BioSignal.HeartRateSample>()
            .filter { it.timestamp.toEpochMilliseconds() >= morningWindowStart }
            .map { it.bpm }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toInt()
        val heartRateBpm: Int? = freshRestingHr?.bpm ?: morningHrAvg

        val stepsToday = stepSamples
            .filterIsInstance<BioSignal.StepCount>()
            .filter { it.date == today }
            .sumOf { it.count }
            .takeIf { it > 0 }

        // Confidence floor = lowest among materials we render. Onestà radicale.
        val rendered: List<BioSignal> = buildList {
            lastSleep?.let { add(it) }
            if (heartRateBpm != null) {
                val candidate: BioSignal? = freshRestingHr
                    ?: hrSamples.filterIsInstance<BioSignal.HeartRateSample>()
                        .maxByOrNull { it.timestamp.toEpochMilliseconds() }
                candidate?.let { add(it) }
            }
            if (stepsToday != null) {
                stepSamples.filterIsInstance<BioSignal.StepCount>()
                    .lastOrNull()
                    ?.let { add(it) }
            }
        }

        val confidenceFloor = rendered
            .map { it.confidence.level }
            .minByOrNull { confidenceRank(it) }
            ?: ConfidenceLevel.LOW

        val primarySource: BioSignalSource? = rendered
            .groupingBy { it.source }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val sleepDurationMinutes = lastSleep?.let { (it.durationSeconds / 60L).toInt() }

        // ── Phase 6.2 — personalized range hints (null when no baseline yet) ──
        val sleepBaseline = repository.getBaseline(BioSignalDataType.SLEEP)
        val hrBaselineType = if (freshRestingHr != null) BioSignalDataType.RESTING_HEART_RATE
                             else BioSignalDataType.HEART_RATE
        val hrBaseline = repository.getBaseline(hrBaselineType)
        val stepsBaseline = repository.getBaseline(BioSignalDataType.STEPS)

        return HomeBioContext(
            sleepDurationMinutes = sleepDurationMinutes,
            heartRateBpm = heartRateBpm,
            stepsToday = stepsToday,
            confidenceFloor = confidenceFloor,
            primarySource = primarySource,
            sleepHint = sleepDurationMinutes?.let { sleepBaseline?.hintFor(it.toDouble()) },
            heartRateHint = heartRateBpm?.let { hrBaseline?.hintFor(it.toDouble()) },
            stepsHint = stepsToday?.let { stepsBaseline?.hintFor(it.toDouble()) },
        )
    }

    private fun confidenceRank(level: ConfidenceLevel): Int = when (level) {
        ConfidenceLevel.LOW -> 0
        ConfidenceLevel.MEDIUM -> 1
        ConfidenceLevel.HIGH -> 2
    }

    companion object {
        private const val LOOKBACK_HOURS = 24L
        private const val MORNING_LOOKBACK_HOURS = 6L
    }
}
