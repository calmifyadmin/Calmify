package com.lifo.util.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * DiaryInsight Model - Firestore Compatible
 *
 * AI-generated psychological insights for diary entries
 * Week 5 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 2.1
 *
 * Firestore collection: diary_insights
 */
data class DiaryInsight(
    @DocumentId
    var id: String = UUID.randomUUID().toString(),
    var diaryId: String = "",                  // Reference to diary entry
    var ownerId: String = "",                   // User ID
    @ServerTimestamp
    var generatedAt: Date = Date.from(Instant.now()), // When insight was generated (technical timestamp)
    var dayKey: String = "",                    // Business date YYYY-MM-DD from diary (for chart grouping)
    var sourceTimezone: String = "",            // User's timezone when diary was created (for debugging/migrations)

    // Sentiment Analysis
    var sentimentPolarity: Float = 0f,          // -1.0 (negative) to +1.0 (positive)
    var sentimentMagnitude: Float = 0f,         // 0.0 (neutral) to 10+ (very intense)

    // Content Analysis
    var topics: List<String> = emptyList(),     // ["work", "family", "health"]
    var keyPhrases: List<String> = emptyList(), // Key phrases from diary

    // Cognitive Patterns (CBT-informed)
    var cognitivePatterns: List<CognitivePattern> = emptyList(),

    // Summary & Suggestions
    var summary: String = "",                   // 1-2 sentence AI summary
    var suggestedPrompts: List<String> = emptyList(),

    // Confidence & Metadata
    var confidence: Float = 0f,                 // 0.0-1.0 overall confidence
    var modelUsed: String = "gemini-2.0-flash-exp",
    var processingTimeMs: Long? = null,

    // User Feedback
    var userCorrection: String? = null,         // User correction text
    var userRating: Int? = null                 // 1-5 stars (optional)
) {
    // No-arg constructor required by Firestore
    constructor() : this(
        id = UUID.randomUUID().toString(),
        diaryId = "",
        ownerId = "",
        generatedAt = Date.from(Instant.now()),
        dayKey = "",
        sourceTimezone = "",
        sentimentPolarity = 0f,
        sentimentMagnitude = 0f,
        topics = emptyList(),
        keyPhrases = emptyList(),
        cognitivePatterns = emptyList(),
        summary = "",
        suggestedPrompts = emptyList(),
        confidence = 0f,
        modelUsed = "gemini-2.0-flash-exp",
        processingTimeMs = null,
        userCorrection = null,
        userRating = null
    )

    /**
     * Get sentiment label based on polarity
     */
    fun getSentimentLabel(): SentimentLabel = sentimentPolarity.toSentimentLabel()
}

/**
 * Sentiment Label Categories
 */
enum class SentimentLabel(val displayName: String, val emoji: String) {
    VERY_NEGATIVE("Molto Negativo", "😢"),
    NEGATIVE("Negativo", "😟"),
    NEUTRAL("Neutro", "😐"),
    POSITIVE("Positivo", "😊"),
    VERY_POSITIVE("Molto Positivo", "😄")
}

/**
 * Cognitive Pattern
 * Based on CBT (Cognitive Behavioral Therapy) distortions
 */
data class CognitivePattern(
    var patternType: String = "",               // e.g., "catastrophizing", "black-and-white"
    var patternName: String = "",               // User-friendly name
    var description: String = "",               // What this pattern means
    var evidence: String = "",                  // Quote from diary showing this pattern
    var confidence: Float = 0f                  // 0.0-1.0 confidence score
) {
    constructor() : this("", "", "", "", 0f)
}

/**
 * Extension functions for sentiment classification
 */
fun Float.toSentimentLabel(): SentimentLabel = when {
    this < -0.6f -> SentimentLabel.VERY_NEGATIVE
    this < -0.2f -> SentimentLabel.NEGATIVE
    this < 0.2f -> SentimentLabel.NEUTRAL
    this < 0.6f -> SentimentLabel.POSITIVE
    else -> SentimentLabel.VERY_POSITIVE
}

/**
 * Get Material3 color for sentiment
 */
fun SentimentLabel.getColorName(): String = when (this) {
    SentimentLabel.VERY_NEGATIVE -> "error"
    SentimentLabel.NEGATIVE -> "errorContainer"
    SentimentLabel.NEUTRAL -> "surfaceVariant"
    SentimentLabel.POSITIVE -> "primaryContainer"
    SentimentLabel.VERY_POSITIVE -> "primary"
}
