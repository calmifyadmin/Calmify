package com.lifo.write.domain

import com.lifo.ui.components.biosignal.BioDayOverlay
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.preview.PreviewConfidence
import com.lifo.util.repository.BioSignalRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Folds bio data for a specific calendar day into a [BioDayOverlay] for the
 * Journal × Bio overlay (Phase 9.2.3, 2026-05-17) shown inside DiaryDetailScreen.
 *
 * Re-engineering NOTE: this surface didn't exist in the mockup. It answers a
 * question Claude Design's siloed approach never asked: "what was happening
 * in my body when I wrote this diary entry?" — by joining diary date with the
 * bio raw samples we already store locally.
 *
 * For each metric we also surface a [com.lifo.util.model.BioRangeHint] when
 * the user's baseline is mature enough; that lets the row say "close to your
 * usual" / "below" / "above" without resorting to scores or judgements.
 */
class GetDiaryDayBioOverlayUseCase(
    private val repository: BioSignalRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(diaryDateMillis: Long): BioDayOverlay {
        val tz = TimeZone.currentSystemDefault()
        val date: LocalDate = Instant.fromEpochMilliseconds(diaryDateMillis)
            .toLocalDateTime(tz).date
        val dayStart = date.atStartOfDayIn(tz)
        val dayEnd = dayStart.plus(1.days)
        val morningEnd = dayStart.plus(MORNING_HOURS.hours)

        // Sleep — the session that ENDED that day (overnight from previous evening)
        val sleepWindowStart = dayStart.minus(12.hours)
        val sleepSession = repository
            .getRawSamples(BioSignalDataType.SLEEP, sleepWindowStart, dayEnd)
            .filterIsInstance<BioSignal.SleepSession>()
            .filter {
                val endDate = it.endTimestamp.toLocalDateTime(tz).date
                endDate == date
            }
            .maxByOrNull { it.endTimestamp.toEpochMilliseconds() }
        val sleepMinutes = sleepSession?.let { (it.durationSeconds / 60L).toInt().takeIf { mins -> mins > 0 } }

        // Morning HR — prefer RestingHeartRate for that day, fall back to a
        // morning HR sample average.
        val restingHr = repository
            .getRawSamples(BioSignalDataType.RESTING_HEART_RATE, dayStart, dayEnd)
            .filterIsInstance<BioSignal.RestingHeartRate>()
            .firstOrNull { it.date == date }
        val morningHrBpm: Int? = restingHr?.bpm
            ?: repository
                .getRawSamples(BioSignalDataType.HEART_RATE, dayStart, morningEnd)
                .filterIsInstance<BioSignal.HeartRateSample>()
                .map { it.bpm }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toInt()

        // Steps — total for the day
        val steps = repository
            .getRawSamples(BioSignalDataType.STEPS, dayStart, dayEnd)
            .filterIsInstance<BioSignal.StepCount>()
            .filter { it.date == date }
            .sumOf { it.count }
            .takeIf { it > 0 }

        // No real data? Preview fallback when enabled.
        if (sleepMinutes == null && morningHrBpm == null && steps == null) {
            return if (preview.enabled) BioDayOverlay(
                sleepMinutes = 7 * 60 + 12,
                morningHrBpm = 62,
                steps = 4218,
                sleepHint = com.lifo.util.model.BioRangeHint.WITHIN,
                hrHint = com.lifo.util.model.BioRangeHint.WITHIN,
                stepsHint = com.lifo.util.model.BioRangeHint.WITHIN,
                confidence = PreviewConfidence,
                source = preview.previewSource,
            ) else BioDayOverlay()
        }

        // Baseline hints — null if user's baseline isn't mature yet (cold start).
        val sleepBaseline = repository.getBaseline(BioSignalDataType.SLEEP)
        val hrBaseline = if (restingHr != null) repository.getBaseline(BioSignalDataType.RESTING_HEART_RATE)
                         else repository.getBaseline(BioSignalDataType.HEART_RATE)
        val stepsBaseline = repository.getBaseline(BioSignalDataType.STEPS)

        // Confidence floor + primary source (onestà radicale per Decision 2).
        val rendered: List<BioSignal> = listOfNotNull(sleepSession, restingHr) +
            (steps?.let {
                repository.getRawSamples(BioSignalDataType.STEPS, dayStart, dayEnd)
                    .filterIsInstance<BioSignal.StepCount>().firstOrNull()
            }?.let { listOf(it) } ?: emptyList())
        val confidence = rendered.map { it.confidence.level }.minByOrNull { rank(it) } ?: ConfidenceLevel.LOW
        val primarySource = rendered.firstOrNull()?.source

        return BioDayOverlay(
            sleepMinutes = sleepMinutes,
            morningHrBpm = morningHrBpm,
            steps = steps,
            sleepHint = sleepMinutes?.let { sleepBaseline?.hintFor(it.toDouble()) },
            hrHint = morningHrBpm?.let { hrBaseline?.hintFor(it.toDouble()) },
            stepsHint = steps?.let { stepsBaseline?.hintFor(it.toDouble()) },
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
        private const val MORNING_HOURS = 6L
    }
}
