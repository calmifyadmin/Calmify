package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.TimeRange
import com.lifo.home.domain.model.TopicFrequency
import com.lifo.home.domain.model.TopicsFrequencyResult
import com.lifo.home.domain.model.TopicTrend
import com.lifo.home.domain.model.TrendDirection
import com.lifo.util.model.DiaryInsight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
/**
 * Calculate Topics Frequency Use Case
 *
 * Aggregates topics from diary insights to show
 * word cloud and emerging topics
 */
class CalculateTopicsFrequencyUseCase {

    companion object {
        private const val EMERGING_THRESHOLD = 0.3f  // 30% increase to be "emerging"
        private const val MIN_FREQUENCY = 2          // Minimum times to show topic
        private const val MAX_TOPICS = 15            // Maximum topics to return
    }

    /**
     * Calculate topics frequency from diary insights
     *
     * @param insights List of diary insights within the time range
     * @param timeRange The time range for aggregation
     * @return TopicsFrequencyResult with topic frequencies and emerging topic
     */
    operator fun invoke(
        insights: List<DiaryInsight>,
        timeRange: TimeRange
    ): TopicsFrequencyResult {
        if (insights.isEmpty()) {
            return createEmptyResult()
        }

        val now = ZonedDateTime.now()
        val cutoffDate = LocalDate.now().minusDays(timeRange.days.toLong())

        // Filter insights by time range
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
            return createEmptyResult()
        }

        // Split into two halves for trend comparison
        val midpoint = LocalDate.now().minusDays(timeRange.days.toLong() / 2)

        val firstHalfTopics = mutableMapOf<String, TopicData>()
        val secondHalfTopics = mutableMapOf<String, TopicData>()

        filteredInsights.forEach { insight ->
            val insightDate = java.time.Instant.ofEpochMilli(insight.generatedAtMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val isFirstHalf = insightDate.isBefore(midpoint)

            insight.topics.forEach { topic ->
                val normalizedTopic = topic.lowercase().trim()
                if (normalizedTopic.isNotBlank()) {
                    val targetMap = if (isFirstHalf) firstHalfTopics else secondHalfTopics
                    val existing = targetMap[normalizedTopic] ?: TopicData(0, 0f, 0)
                    targetMap[normalizedTopic] = TopicData(
                        count = existing.count + 1,
                        sentimentSum = existing.sentimentSum + insight.sentimentPolarity,
                        entryCount = existing.entryCount + 1
                    )
                }
            }
        }

        // Combine and calculate frequencies
        val allTopics = mutableMapOf<String, TopicFrequency>()
        val allTopicKeys = (firstHalfTopics.keys + secondHalfTopics.keys).distinct()

        allTopicKeys.forEach { topic ->
            val firstHalf = firstHalfTopics[topic]
            val secondHalf = secondHalfTopics[topic]

            val totalCount = (firstHalf?.count ?: 0) + (secondHalf?.count ?: 0)
            val totalSentiment = (firstHalf?.sentimentSum ?: 0f) + (secondHalf?.sentimentSum ?: 0f)
            val totalEntries = (firstHalf?.entryCount ?: 0) + (secondHalf?.entryCount ?: 0)

            if (totalCount >= MIN_FREQUENCY) {
                val firstHalfCount = firstHalf?.count ?: 0
                val secondHalfCount = secondHalf?.count ?: 0

                // Calculate change percentage
                val changePercent = if (firstHalfCount > 0) {
                    ((secondHalfCount - firstHalfCount).toFloat() / firstHalfCount)
                } else if (secondHalfCount > 0) {
                    1f  // New topic in second half
                } else {
                    0f
                }

                allTopics[topic] = TopicFrequency(
                    topic = formatTopicName(topic),
                    frequency = totalCount,
                    sentimentAverage = if (totalEntries > 0) totalSentiment / totalEntries else 0f,
                    isEmerging = changePercent >= EMERGING_THRESHOLD,
                    changePercent = changePercent
                )
            }
        }

        // Sort by frequency and take top topics
        val sortedTopics = allTopics.values
            .sortedByDescending { it.frequency }
            .take(MAX_TOPICS)

        // Find emerging topic (highest positive change)
        val emergingTopic = sortedTopics
            .filter { it.isEmerging }
            .maxByOrNull { it.changePercent }
            ?.let { topic ->
                TopicTrend(
                    topic = topic.topic,
                    changePercent = topic.changePercent,
                    direction = TrendDirection.UP
                )
            }

        return TopicsFrequencyResult(
            topics = sortedTopics,
            emergingTopic = emergingTopic,
            totalTopicsCount = sortedTopics.sumOf { it.frequency },
            uniqueTopicsCount = sortedTopics.size,
            calculatedAt = now
        )
    }

    private fun formatTopicName(topic: String): String {
        return topic.replaceFirstChar { it.titlecase() }
    }

    private fun createEmptyResult(): TopicsFrequencyResult {
        return TopicsFrequencyResult(
            topics = emptyList(),
            emergingTopic = null,
            totalTopicsCount = 0,
            uniqueTopicsCount = 0,
            calculatedAt = ZonedDateTime.now()
        )
    }

    private data class TopicData(
        val count: Int,
        val sentimentSum: Float,
        val entryCount: Int
    )
}
