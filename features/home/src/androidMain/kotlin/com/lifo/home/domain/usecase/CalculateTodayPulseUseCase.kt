package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.TodayPulse
import com.lifo.home.domain.model.TodayPulseResult
import com.lifo.home.domain.model.TrendDirection
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.SentimentLabel
import kotlinx.datetime.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Calculate Today's Pulse Use Case
 *
 * Calculates the "emotional pulse" for today based on
 * diary insights, showing sentiment score and trends
 */
class CalculateTodayPulseUseCase {

    companion object {
        // Sentiment polarity ranges from -1 to +1
        // We convert to 0-10 scale for display
        private const val MIN_SCORE = 0f
        private const val MAX_SCORE = 10f
    }

    /**
     * Calculate today's emotional pulse
     *
     * @param insights List of diary insights (should include recent data)
     * @return TodayPulseResult with score, trend, and comparison data
     */
    operator fun invoke(insights: List<DiaryInsight>): TodayPulseResult {
        if (insights.isEmpty()) {
            return createEmptyResult()
        }

        val nowInstant = Clock.System.now()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Get today's insights
        val todayInsights = filterInsightsByDate(insights, today)
        val yesterdayInsights = filterInsightsByDate(insights, yesterday)

        // Get last 7 days for weekly average
        val weekStart = today.minusDays(6)
        val weekInsights = insights.filter { insight ->
            val date = getInsightDate(insight)
            date != null && !date.isBefore(weekStart)
        }

        // Calculate scores
        val todayScore = calculateAverageScore(todayInsights)
        val yesterdayScore = calculateAverageScore(yesterdayInsights)
        val weeklyAverage = calculateAverageScore(weekInsights)

        // Determine trend
        val (trend, trendDelta) = if (yesterdayScore != null && todayScore != null) {
            val delta = todayScore - yesterdayScore
            val direction = when {
                delta > 0.5f -> TrendDirection.UP
                delta < -0.5f -> TrendDirection.DOWN
                else -> TrendDirection.STABLE
            }
            direction to abs(delta)
        } else {
            TrendDirection.STABLE to 0f
        }

        // Generate week summary
        val weekSummary = generateWeekSummary(weeklyAverage, todayScore)

        // Find dominant emotion today
        val dominantEmotion = findDominantEmotion(todayInsights)

        val pulse = TodayPulse(
            score = todayScore ?: weeklyAverage ?: 5f,
            trend = trend,
            trendDelta = trendDelta,
            weekSummary = weekSummary,
            dominantEmotion = dominantEmotion,
            entriesCount = todayInsights.size
        )

        return TodayPulseResult(
            pulse = pulse,
            yesterdayScore = yesterdayScore,
            weeklyAverage = weeklyAverage ?: 5f,
            calculatedAt = nowInstant
        )
    }

    /**
     * Calculate a quick pulse score without full analysis
     * Useful for mini indicators
     */
    fun quickScore(insights: List<DiaryInsight>): Float {
        val today = LocalDate.now()
        val todayInsights = filterInsightsByDate(insights, today)

        return calculateAverageScore(todayInsights)
            ?: calculateAverageScore(insights.take(5))
            ?: 5f
    }

    private fun filterInsightsByDate(
        insights: List<DiaryInsight>,
        date: LocalDate
    ): List<DiaryInsight> {
        return insights.filter { insight ->
            getInsightDate(insight) == date
        }
    }

    private fun getInsightDate(insight: DiaryInsight): LocalDate? {
        return try {
            if (insight.dayKey.isNotBlank()) {
                LocalDate.parse(insight.dayKey)
            } else {
                java.time.Instant.ofEpochMilli(insight.generatedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateAverageScore(insights: List<DiaryInsight>): Float? {
        if (insights.isEmpty()) return null

        // Calculate weighted average using both polarity and magnitude
        var totalWeight = 0f
        var weightedSum = 0f

        insights.forEach { insight ->
            // Weight by magnitude (how strongly the emotion was felt)
            val weight = insight.sentimentMagnitude.coerceIn(0.1f, 10f)

            // Convert polarity (-1 to +1) to score (0 to 10)
            val score = polarityToScore(insight.sentimentPolarity)

            weightedSum += score * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) {
            (weightedSum / totalWeight).coerceIn(MIN_SCORE, MAX_SCORE)
        } else {
            null
        }
    }

    private fun polarityToScore(polarity: Float): Float {
        // Convert -1..+1 to 0..10
        return ((polarity + 1f) / 2f * 10f).coerceIn(MIN_SCORE, MAX_SCORE)
    }

    private fun generateWeekSummary(weeklyAverage: Float?, todayScore: Float?): String {
        if (weeklyAverage == null) {
            return "Inizia a scrivere per vedere i trend"
        }

        val today = todayScore ?: weeklyAverage

        return when {
            weeklyAverage >= 7f && today >= weeklyAverage -> "Settimana eccellente!"
            weeklyAverage >= 7f -> "Tendenza molto positiva"
            weeklyAverage >= 5.5f && today > weeklyAverage -> "In miglioramento"
            weeklyAverage >= 5.5f -> "Settimana equilibrata"
            weeklyAverage >= 4f && today > weeklyAverage -> "Segnali di ripresa"
            weeklyAverage >= 4f -> "Qualche difficoltà"
            today > weeklyAverage + 1f -> "Oggi va meglio"
            else -> "Settimana impegnativa"
        }
    }

    private fun findDominantEmotion(insights: List<DiaryInsight>): SentimentLabel {
        if (insights.isEmpty()) return SentimentLabel.NEUTRAL

        // Count sentiment labels
        val sentimentCounts = insights
            .map { it.getSentimentLabel() }
            .groupingBy { it }
            .eachCount()

        return sentimentCounts.maxByOrNull { it.value }?.key ?: SentimentLabel.NEUTRAL
    }

    private fun createEmptyResult(): TodayPulseResult {
        return TodayPulseResult(
            pulse = TodayPulse(
                score = 5f,
                trend = TrendDirection.STABLE,
                trendDelta = 0f,
                weekSummary = "Inizia a scrivere per vedere il tuo stato",
                dominantEmotion = SentimentLabel.NEUTRAL,
                entriesCount = 0
            ),
            yesterdayScore = null,
            weeklyAverage = 5f,
            calculatedAt = Clock.System.now()
        )
    }
}
