package com.lifo.home.domain.model

import androidx.compose.runtime.Immutable
import com.lifo.util.model.SentimentLabel
import kotlinx.datetime.Instant

/**
 * Insight Aggregation Models - For calculating and caching aggregated insights
 * Used by use cases to transform raw data into UI-ready formats
 */

// ==================== AGGREGATION RESULT MODELS ====================

/**
 * Result of mood distribution calculation
 */
@Immutable
data class MoodDistributionResult(
    val distribution: MoodDistribution,
    val dominantMood: DominantMood,
    val calculatedAt: Instant,
    val sampleSize: Int
)

/**
 * Result of cognitive pattern aggregation
 */
@Immutable
data class CognitivePatternsResult(
    val patterns: List<CognitivePatternSummary>,
    val totalPatternCount: Int,
    val adaptivePercentage: Float,      // Percentage of positive patterns
    val maladaptivePercentage: Float,   // Percentage of negative patterns
    val calculatedAt: Instant
)

/**
 * Result of topics frequency calculation
 */
@Immutable
data class TopicsFrequencyResult(
    val topics: List<TopicFrequency>,
    val emergingTopic: TopicTrend?,
    val totalTopicsCount: Int,
    val uniqueTopicsCount: Int,
    val calculatedAt: Instant
)

/**
 * Result of wellbeing trend calculation
 */
@Immutable
data class WellbeingTrendResult(
    val dataPoints: List<WellbeingDataPoint>,
    val latestScore: Float,
    val averageScore: Float,
    val trendVsAverage: Float,          // Difference from average
    val trendDirection: TrendDirection,
    val scoreLabel: WellbeingScoreLabel,
    val daysSinceLastSnapshot: Int,
    val calculatedAt: Instant
)

/**
 * Result of today's pulse calculation
 */
@Immutable
data class TodayPulseResult(
    val pulse: TodayPulse,
    val yesterdayScore: Float?,
    val weeklyAverage: Float,
    val calculatedAt: Instant
)

// ==================== AGGREGATION INPUT MODELS ====================

/**
 * Raw diary insight for aggregation processing
 */
@Immutable
data class RawDiaryInsight(
    val id: String,
    val diaryId: String,
    val dayKey: String,                  // "YYYY-MM-DD"
    val sentimentScore: Float,           // -1 to +1
    val sentimentMagnitude: Float,       // 0 to 10
    val sentimentLabel: SentimentLabel,
    val topics: List<String>,
    val cognitivePatterns: List<String>,
    val createdAt: Instant
)

/**
 * Raw wellbeing snapshot for trend calculation
 */
@Immutable
data class RawWellbeingSnapshot(
    val id: String,
    val overallScore: Float,             // 0 to 10
    val dimensions: Map<String, Float>,  // Individual dimension scores
    val createdAt: Instant
)

// ==================== AGGREGATION CONFIGURATION ====================

/**
 * Configuration for aggregation calculations
 */
@Immutable
data class AggregationConfig(
    val timeRange: TimeRange,
    val minDataPointsForTrend: Int = 3,
    val emergingTopicThreshold: Float = 0.3f,  // 30% increase to be "emerging"
    val minPatternOccurrences: Int = 2
)

// ==================== PATTERN MAPPING ====================

/**
 * Known cognitive patterns with their metadata
 * Based on CBT (Cognitive Behavioral Therapy) distortions
 */
object KnownCognitivePatterns {

    private val patterns = mapOf(
        // Maladaptive patterns (negative)
        "catastrophizing" to PatternInfo(
            name = "Pensiero catastrofico",
            description = "Tendenza a immaginare il peggio",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "overgeneralization" to PatternInfo(
            name = "Generalizzazione",
            description = "Estendere singoli eventi a tutto",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "black_white_thinking" to PatternInfo(
            name = "Pensiero dicotomico",
            description = "Vedere le cose in bianco o nero",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "mind_reading" to PatternInfo(
            name = "Lettura del pensiero",
            description = "Presumere di sapere cosa pensano gli altri",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "fortune_telling" to PatternInfo(
            name = "Previsione negativa",
            description = "Prevedere un futuro negativo senza prove",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "emotional_reasoning" to PatternInfo(
            name = "Ragionamento emotivo",
            description = "Confondere i sentimenti con i fatti",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "should_statements" to PatternInfo(
            name = "Doverizzazioni",
            description = "Uso eccessivo di 'dovrei/devo'",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "labeling" to PatternInfo(
            name = "Etichettatura",
            description = "Attribuire etichette negative a se' o altri",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "personalization" to PatternInfo(
            name = "Personalizzazione",
            description = "Assumersi colpe non proprie",
            sentiment = PatternSentiment.MALADAPTIVE
        ),
        "minimizing" to PatternInfo(
            name = "Minimizzazione",
            description = "Sminuire i propri successi",
            sentiment = PatternSentiment.MALADAPTIVE
        ),

        // Adaptive patterns (positive)
        "positive_thinking" to PatternInfo(
            name = "Pensiero positivo",
            description = "Riconoscimento dei progressi",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "gratitude" to PatternInfo(
            name = "Gratitudine",
            description = "Apprezzamento per cio' che si ha",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "growth_mindset" to PatternInfo(
            name = "Mentalita' di crescita",
            description = "Vedere le sfide come opportunita'",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "self_compassion" to PatternInfo(
            name = "Auto-compassione",
            description = "Essere gentili con se' stessi",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "problem_solving" to PatternInfo(
            name = "Problem solving",
            description = "Approccio costruttivo ai problemi",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "acceptance" to PatternInfo(
            name = "Accettazione",
            description = "Accettare cio' che non si puo' cambiare",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "mindfulness" to PatternInfo(
            name = "Consapevolezza",
            description = "Attenzione al momento presente",
            sentiment = PatternSentiment.ADAPTIVE
        ),
        "realistic_optimism" to PatternInfo(
            name = "Ottimismo realistico",
            description = "Speranza basata su fatti concreti",
            sentiment = PatternSentiment.ADAPTIVE
        ),

        // Neutral patterns
        "reflection" to PatternInfo(
            name = "Riflessione",
            description = "Analisi di situazioni ed emozioni",
            sentiment = PatternSentiment.NEUTRAL
        ),
        "self_awareness" to PatternInfo(
            name = "Autoconsapevolezza",
            description = "Riconoscimento dei propri stati",
            sentiment = PatternSentiment.NEUTRAL
        )
    )

    fun getPatternInfo(patternKey: String): PatternInfo? {
        return patterns[patternKey.lowercase()]
    }

    fun getAllPatterns(): Map<String, PatternInfo> = patterns

    data class PatternInfo(
        val name: String,
        val description: String,
        val sentiment: PatternSentiment
    )
}

// ==================== TOPIC CATEGORIES ====================

/**
 * Common topic categories for grouping
 */
object TopicCategories {

    val categories = mapOf(
        "work" to TopicCategory("Lavoro", listOf("lavoro", "ufficio", "carriera", "colleghi", "capo", "progetto", "meeting")),
        "family" to TopicCategory("Famiglia", listOf("famiglia", "genitori", "figli", "fratelli", "parenti")),
        "health" to TopicCategory("Salute", listOf("salute", "malattia", "medico", "terapia", "dolore", "stanchezza")),
        "relationships" to TopicCategory("Relazioni", listOf("relazione", "amicizia", "partner", "amore", "conflitto")),
        "creativity" to TopicCategory("Creativita'", listOf("creativita'", "arte", "musica", "scrittura", "hobby")),
        "sports" to TopicCategory("Sport", listOf("sport", "palestra", "corsa", "allenamento", "fitness")),
        "sleep" to TopicCategory("Sonno", listOf("sonno", "insonnia", "riposo", "stanchezza", "sogni")),
        "finances" to TopicCategory("Finanze", listOf("soldi", "finanze", "spese", "risparmi", "investimenti")),
        "growth" to TopicCategory("Crescita", listOf("crescita", "obiettivi", "miglioramento", "successo", "apprendimento")),
        "stress" to TopicCategory("Stress", listOf("stress", "ansia", "pressione", "preoccupazione", "tensione"))
    )

    fun getCategoryForTopic(topic: String): String? {
        val lowerTopic = topic.lowercase()
        return categories.entries.find { (_, category) ->
            category.keywords.any { lowerTopic.contains(it) }
        }?.key
    }

    data class TopicCategory(
        val displayName: String,
        val keywords: List<String>
    )
}
