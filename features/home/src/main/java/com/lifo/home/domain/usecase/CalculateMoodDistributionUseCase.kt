package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.DominantMood
import com.lifo.home.domain.model.MoodDistribution
import com.lifo.home.domain.model.TimeRange
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.SentimentLabel
import java.time.LocalDate
import java.time.ZoneId
/**
 * Calculate Mood Distribution Use Case
 *
 * Aggregates diary insights to calculate mood distribution
 * for the donut chart visualization
 */
class CalculateMoodDistributionUseCase {

    /**
     * Calculate mood distribution from diary insights
     *
     * @param insights List of diary insights within the time range
     * @param timeRange The time range for aggregation
     * @return MoodDistribution with percentage breakdowns
     */
    operator fun invoke(
        insights: List<DiaryInsight>,
        timeRange: TimeRange
    ): MoodDistribution {
        if (insights.isEmpty()) {
            return createEmptyDistribution(timeRange)
        }

        // Filter insights by time range
        val cutoffDate = LocalDate.now().minusDays(timeRange.days.toLong())
        val filteredInsights = insights.filter { insight ->
            try {
                val insightDate = java.time.Instant.ofEpochMilli(insight.generatedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                insightDate.isAfter(cutoffDate) || insightDate.isEqual(cutoffDate)
            } catch (_: Exception) {
                false
            }
        }

        if (filteredInsights.isEmpty()) {
            return createEmptyDistribution(timeRange)
        }

        // Count sentiments
        val sentimentCounts = mutableMapOf<SentimentLabel, Int>()
        SentimentLabel.entries.forEach { sentimentCounts[it] = 0 }

        filteredInsights.forEach { insight ->
            val sentiment = insight.getSentimentLabel()
            sentimentCounts[sentiment] = sentimentCounts.getOrDefault(sentiment, 0) + 1
        }

        val total = filteredInsights.size.toFloat()

        // Calculate percentages
        val positiveCount = (sentimentCounts[SentimentLabel.POSITIVE] ?: 0) +
                (sentimentCounts[SentimentLabel.VERY_POSITIVE] ?: 0)
        val negativeCount = (sentimentCounts[SentimentLabel.NEGATIVE] ?: 0) +
                (sentimentCounts[SentimentLabel.VERY_NEGATIVE] ?: 0)
        val neutralCount = sentimentCounts[SentimentLabel.NEUTRAL] ?: 0

        // Create detailed breakdown
        val detailedBreakdown = sentimentCounts.mapValues { (_, count) ->
            count / total
        }

        return MoodDistribution(
            positive = positiveCount / total,
            neutral = neutralCount / total,
            negative = negativeCount / total,
            detailedBreakdown = detailedBreakdown,
            totalEntries = filteredInsights.size,
            timeRange = timeRange
        )
    }

    /**
     * Extract dominant mood from distribution
     * Note: Uses shape-based indicators instead of emojis (M3 Expressive)
     */
    fun getDominantMood(distribution: MoodDistribution): DominantMood {
        val maxEntry = distribution.detailedBreakdown.maxByOrNull { it.value }
            ?: return DominantMood(
                sentiment = SentimentLabel.NEUTRAL,
                label = SentimentLabel.NEUTRAL.displayName,
                percentage = 0f
            )

        return DominantMood(
            sentiment = maxEntry.key,
            label = maxEntry.key.displayName,
            percentage = maxEntry.value
        )
    }

    private fun createEmptyDistribution(timeRange: TimeRange): MoodDistribution {
        return MoodDistribution(
            positive = 0f,
            neutral = 0f,
            negative = 0f,
            detailedBreakdown = emptyMap(),
            totalEntries = 0,
            timeRange = timeRange
        )
    }
}
