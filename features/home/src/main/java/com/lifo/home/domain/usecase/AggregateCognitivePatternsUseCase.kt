package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.CognitivePatternsResult
import com.lifo.home.domain.model.CognitivePatternSummary
import com.lifo.home.domain.model.KnownCognitivePatterns
import com.lifo.home.domain.model.PatternSentiment
import com.lifo.home.domain.model.TimeRange
import com.lifo.home.domain.model.TrendDirection
import com.lifo.util.model.CognitivePattern
import com.lifo.util.model.DiaryInsight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Aggregate Cognitive Patterns Use Case
 *
 * Aggregates cognitive patterns from diary insights
 * to show CBT pattern trends and frequencies
 */
class AggregateCognitivePatternsUseCase @Inject constructor() {

    /**
     * Aggregate cognitive patterns from diary insights
     *
     * @param insights List of diary insights within the time range
     * @param timeRange The time range for aggregation
     * @param minOccurrences Minimum occurrences to include a pattern
     * @return CognitivePatternsResult with aggregated patterns
     */
    operator fun invoke(
        insights: List<DiaryInsight>,
        timeRange: TimeRange,
        minOccurrences: Int = 2
    ): CognitivePatternsResult {
        if (insights.isEmpty()) {
            return createEmptyResult()
        }

        val now = ZonedDateTime.now()
        val cutoffDate = LocalDate.now().minusDays(timeRange.days.toLong())

        // Filter insights by time range
        val filteredInsights = insights.filter { insight ->
            try {
                val insightDate = insight.generatedAt.toInstant()
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

        // Extract all patterns and count occurrences
        val patternOccurrences = mutableMapOf<String, MutableList<PatternOccurrence>>()

        filteredInsights.forEach { insight ->
            insight.cognitivePatterns.forEach { pattern ->
                val key = pattern.patternType.lowercase()
                patternOccurrences.getOrPut(key) { mutableListOf() }.add(
                    PatternOccurrence(
                        pattern = pattern,
                        date = insight.generatedAt.toInstant()
                            .atZone(ZoneId.systemDefault()),
                        examples = pattern.evidence
                    )
                )
            }
        }

        // Filter by minimum occurrences and create summaries
        val patternSummaries = patternOccurrences
            .filter { it.value.size >= minOccurrences }
            .map { (patternKey, occurrences) ->
                createPatternSummary(patternKey, occurrences, timeRange)
            }
            .sortedByDescending { it.occurrences }

        // Calculate percentages
        val totalPatterns = patternSummaries.sumOf { it.occurrences }
        val adaptiveCount = patternSummaries
            .filter { it.sentiment == PatternSentiment.ADAPTIVE }
            .sumOf { it.occurrences }
        val maladaptiveCount = patternSummaries
            .filter { it.sentiment == PatternSentiment.MALADAPTIVE }
            .sumOf { it.occurrences }

        return CognitivePatternsResult(
            patterns = patternSummaries,
            totalPatternCount = totalPatterns,
            adaptivePercentage = if (totalPatterns > 0) adaptiveCount.toFloat() / totalPatterns else 0f,
            maladaptivePercentage = if (totalPatterns > 0) maladaptiveCount.toFloat() / totalPatterns else 0f,
            calculatedAt = now
        )
    }

    private fun createPatternSummary(
        patternKey: String,
        occurrences: List<PatternOccurrence>,
        timeRange: TimeRange
    ): CognitivePatternSummary {
        val patternInfo = KnownCognitivePatterns.getPatternInfo(patternKey)

        // Calculate trend by comparing first half vs second half of time range
        val midpoint = LocalDate.now().minusDays(timeRange.days.toLong() / 2)
        val firstHalfCount = occurrences.count { it.date.toLocalDate().isBefore(midpoint) }
        val secondHalfCount = occurrences.count { !it.date.toLocalDate().isBefore(midpoint) }

        val trend = when {
            secondHalfCount > firstHalfCount * 1.2f -> TrendDirection.UP
            secondHalfCount < firstHalfCount * 0.8f -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }

        // Get unique examples
        val examples = occurrences
            .mapNotNull { it.examples.takeIf { e -> e.isNotBlank() } }
            .distinct()
            .take(3)

        return CognitivePatternSummary(
            patternName = patternInfo?.name ?: formatPatternName(patternKey),
            description = patternInfo?.description ?: "",
            occurrences = occurrences.size,
            trend = trend,
            sentiment = patternInfo?.sentiment ?: PatternSentiment.NEUTRAL,
            examples = examples
        )
    }

    private fun formatPatternName(key: String): String {
        return key.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
    }

    private fun createEmptyResult(): CognitivePatternsResult {
        return CognitivePatternsResult(
            patterns = emptyList(),
            totalPatternCount = 0,
            adaptivePercentage = 0f,
            maladaptivePercentage = 0f,
            calculatedAt = ZonedDateTime.now()
        )
    }

    private data class PatternOccurrence(
        val pattern: CognitivePattern,
        val date: ZonedDateTime,
        val examples: String
    )
}
