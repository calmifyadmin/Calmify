package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.SleepMoodCorrelation
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.SleepLog
import kotlin.math.roundToInt

private const val SLEEP_THRESHOLD_HOURS = 7.0f
private const val MIN_SAMPLES = 5

/**
 * Computes the correlation between sleep duration and diary sentiment.
 *
 * Pairs [SleepLog.sleepHours] with [DiaryInsight.sentimentPolarity] matched by dayKey.
 * Splits at a fixed threshold of 7h and compares average mood scores (mapped from
 * sentimentPolarity −1..+1 → 0..10). Returns null when fewer than [MIN_SAMPLES]
 * matched pairs exist.
 */
class GetSleepMoodCorrelationUseCase {

    operator fun invoke(
        sleepLogs: List<SleepLog>,
        insights: List<DiaryInsight>
    ): SleepMoodCorrelation? {
        // Build dayKey → avg mood score (0-10) from sentiment polarity
        val moodByDay = insights
            .groupBy { it.dayKey }
            .mapValues { (_, dayInsights) ->
                val avgPolarity = dayInsights.map { it.sentimentPolarity }.average().toFloat()
                (avgPolarity + 1f) * 5f   // map -1..+1 → 0..10
            }

        data class SleepMoodPair(val hours: Float, val mood: Float)
        val pairs = sleepLogs.mapNotNull { log ->
            moodByDay[log.dayKey]?.let { mood -> SleepMoodPair(log.sleepHours, mood) }
        }

        if (pairs.size < MIN_SAMPLES) return null

        val above = pairs.filter { it.hours >= SLEEP_THRESHOLD_HOURS }
        val below = pairs.filter { it.hours < SLEEP_THRESHOLD_HOURS }

        if (above.isEmpty() || below.isEmpty()) return null

        val avgAbove = above.map { it.mood }.average().toFloat()
        val avgBelow = below.map { it.mood }.average().toFloat()
        val improvementPercent = if (avgBelow > 0f) (avgAbove - avgBelow) / avgBelow * 100f else 0f

        return SleepMoodCorrelation(
            sleepThresholdHours = SLEEP_THRESHOLD_HOURS,
            moodAboveThreshold = avgAbove,
            moodBelowThreshold = avgBelow,
            improvementPercent = improvementPercent,
            sampleSize = pairs.size,
            narrative = buildNarrative(SLEEP_THRESHOLD_HOURS, improvementPercent)
        )
    }

    private fun buildNarrative(thresholdH: Float, improvementPercent: Float): String {
        val h = thresholdH.toInt()
        return when {
            improvementPercent >= 5f ->
                "Quando dormi più di ${h}h, il tuo umore migliora del ${improvementPercent.roundToInt()}%"
            improvementPercent > 0f ->
                "Dormire più di ${h}h tende ad associarsi a un umore leggermente migliore"
            improvementPercent < -5f ->
                "Curiosità: dormi di più nei giorni difficili — il corpo cerca recupero"
            else ->
                "Registra ancora il sonno per scoprire come influenza il tuo umore"
        }
    }
}
