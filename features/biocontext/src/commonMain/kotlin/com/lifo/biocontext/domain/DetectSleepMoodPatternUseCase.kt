package com.lifo.biocontext.domain

import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.model.RequestState
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.preview.PreviewConfidence
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.MongoRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * Detects whether the user's diary emotional intensity is meaningfully higher
 * on days following a good night of sleep — Phase 9.2.2 (sleep × mood).
 *
 * Honesty rules (CLAUDE.md rule 1 + dogma #4):
 * - Need ≥[MIN_GOOD_NIGHTS] good-sleep nights AND ≥[MIN_POOR_NIGHTS] poor-sleep nights
 *   with a same-or-next-day diary entry to make the comparison.
 * - Lift below [MEANINGFUL_LIFT_PERCENT] returns null — claiming noise as
 *   signal would teach the user a false relationship.
 */
class DetectSleepMoodPatternUseCase(
    private val bioRepository: BioSignalRepository,
    private val mongoRepository: MongoRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(): BioPattern.SleepMood? {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val windowFrom = now.minus(WINDOW_DAYS.toLong().days)

        val sleepSamples = bioRepository
            .getRawSamples(BioSignalDataType.SLEEP, windowFrom, now)
            .filterIsInstance<BioSignal.SleepSession>()
        if (sleepSamples.isEmpty()) return previewOrNull()

        val diariesByDate: Map<LocalDate, List<com.lifo.util.model.Diary>> = runCatching {
            (mongoRepository.getAllDiaries().firstOrNull { it !is RequestState.Loading } as? RequestState.Success)
                ?.data
                ?: emptyMap()
        }.getOrDefault(emptyMap())
        if (diariesByDate.isEmpty()) return previewOrNull()

        // Group sleep by the night's "wake date" (end-of-sleep local date)
        val sleepByDate: Map<LocalDate, Double> = sleepSamples
            .groupBy { it.timestamp.toLocalDateTime(tz).date }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds / 3600.0 } }

        val goodMoods = mutableListOf<Int>()
        val poorMoods = mutableListOf<Int>()

        sleepByDate.forEach { (date, hours) ->
            // Mood signal = diary written ON the day of (or the day after) the sleep
            val nextDay = date.plusDays(1)
            val moodsForWindow = (diariesByDate[date].orEmpty() + diariesByDate[nextDay].orEmpty())
                .map { it.emotionIntensity }
            if (moodsForWindow.isEmpty()) return@forEach
            val avgMood = moodsForWindow.average().toInt()
            if (hours >= GOOD_SLEEP_HOURS) goodMoods += avgMood else poorMoods += avgMood
        }

        if (goodMoods.size < MIN_GOOD_NIGHTS || poorMoods.size < MIN_POOR_NIGHTS) return previewOrNull()
        val goodAvg = goodMoods.average()
        val poorAvg = poorMoods.average()
        if (poorAvg <= 0.0) return previewOrNull()
        val lift = ((goodAvg - poorAvg) / poorAvg * 100.0).toInt()
        if (lift < MEANINGFUL_LIFT_PERCENT) return previewOrNull()

        val confidence = sleepSamples
            .map { it.confidence.level }
            .minByOrNull { rank(it) } ?: ConfidenceLevel.LOW
        val primarySource = sleepSamples
            .groupingBy { it.source }
            .eachCount()
            .maxByOrNull { it.value }?.key
            ?: sleepSamples.first().source

        return BioPattern.SleepMood(
            windowDays = WINDOW_DAYS,
            goodSleepNightsCount = goodMoods.size,
            poorSleepNightsCount = poorMoods.size,
            goodSleepNextDayMoodAvg = goodAvg,
            poorSleepNextDayMoodAvg = poorAvg,
            liftPercent = lift,
            sleepHoursThreshold = GOOD_SLEEP_HOURS,
            confidence = confidence,
            source = primarySource,
        )
    }

    private fun previewOrNull(): BioPattern.SleepMood? {
        if (!preview.enabled) return null
        return BioPattern.SleepMood(
            windowDays = WINDOW_DAYS,
            goodSleepNightsCount = 9,
            poorSleepNightsCount = 6,
            goodSleepNextDayMoodAvg = 7.2,
            poorSleepNextDayMoodAvg = 5.8,
            liftPercent = 24,
            sleepHoursThreshold = GOOD_SLEEP_HOURS,
            confidence = PreviewConfidence,
            source = preview.previewSource,
        )
    }

    private fun rank(level: ConfidenceLevel): Int = when (level) {
        ConfidenceLevel.LOW -> 0
        ConfidenceLevel.MEDIUM -> 1
        ConfidenceLevel.HIGH -> 2
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        LocalDate.fromEpochDays(this.toEpochDays() + days)

    companion object {
        private const val WINDOW_DAYS = 30
        private const val GOOD_SLEEP_HOURS = 7.0
        private const val MIN_GOOD_NIGHTS = 3
        private const val MIN_POOR_NIGHTS = 3
        private const val MEANINGFUL_LIFT_PERCENT = 10
    }
}
