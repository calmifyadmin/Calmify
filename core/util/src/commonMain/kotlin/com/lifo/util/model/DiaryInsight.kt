package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * DiaryInsight Model - Pure Kotlin (KMP)
 *
 * AI-generated psychological insights for diary entries
 * Firestore mapping handled by FirestoreMapper in data/mongo
 */
@OptIn(ExperimentalUuidApi::class)
data class DiaryInsight(
    var id: String = Uuid.random().toString(),
    var diaryId: String = "",
    var ownerId: String = "",
    var generatedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    var dayKey: String = "",
    var sourceTimezone: String = "",

    // Sentiment Analysis
    var sentimentPolarity: Float = 0f,
    var sentimentMagnitude: Float = 0f,

    // Content Analysis
    var topics: List<String> = emptyList(),
    var keyPhrases: List<String> = emptyList(),

    // Cognitive Patterns (CBT-informed)
    var cognitivePatterns: List<CognitivePattern> = emptyList(),

    // Summary & Suggestions
    var summary: String = "",
    var suggestedPrompts: List<String> = emptyList(),

    // Confidence & Metadata
    var confidence: Float = 0f,
    var modelUsed: String = "gemini-2.0-flash-exp",
    var processingTimeMs: Long? = null,

    // User Feedback
    var userCorrection: String? = null,
    var userRating: Int? = null
) {
    fun getSentimentLabel(): SentimentLabel = sentimentPolarity.toSentimentLabel()
}

/**
 * Sentiment Label Categories
 */
enum class SentimentLabel(val displayName: String) {
    VERY_NEGATIVE("Molto Negativo"),
    NEGATIVE("Negativo"),
    NEUTRAL("Neutro"),
    POSITIVE("Positivo"),
    VERY_POSITIVE("Molto Positivo"),
}

/**
 * Cognitive Pattern
 * Based on CBT (Cognitive Behavioral Therapy) distortions
 */
data class CognitivePattern(
    var patternType: String = "",
    var patternName: String = "",
    var description: String = "",
    var evidence: String = "",
    var confidence: Float = 0f
)

fun Float.toSentimentLabel(): SentimentLabel = when {
    this < -0.6f -> SentimentLabel.VERY_NEGATIVE
    this < -0.2f -> SentimentLabel.NEGATIVE
    this < 0.2f -> SentimentLabel.NEUTRAL
    this < 0.6f -> SentimentLabel.POSITIVE
    else -> SentimentLabel.VERY_POSITIVE
}

fun SentimentLabel.getColorName(): String = when (this) {
    SentimentLabel.VERY_NEGATIVE -> "error"
    SentimentLabel.NEGATIVE -> "errorContainer"
    SentimentLabel.NEUTRAL -> "surfaceVariant"
    SentimentLabel.POSITIVE -> "primaryContainer"
    SentimentLabel.VERY_POSITIVE -> "primary"
}
