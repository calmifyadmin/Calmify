package com.lifo.util.repository

import com.lifo.util.model.RequestState

/**
 * ContentModerationRepository Interface
 *
 * AI-powered content moderation via Genkit on Cloud Functions.
 * Provides toxicity detection, duplicate checking, sentiment analysis,
 * mood classification, and embedding generation for semantic search.
 */
interface ContentModerationRepository {
    suspend fun checkToxicity(text: String): RequestState<ModerationResult>
    suspend fun analyzeSentiment(text: String): RequestState<SentimentResult>
    suspend fun classifyMood(text: String): RequestState<String>
    suspend fun checkDuplicate(text: String, authorId: String): RequestState<DuplicateResult>
    suspend fun generateEmbedding(text: String): RequestState<FloatArray>

    data class ModerationResult(
        val isToxic: Boolean = false,
        val toxicityScore: Float = 0f,
        val categories: List<String> = emptyList(),
        val suggestion: ModerationAction = ModerationAction.ALLOW
    )

    enum class ModerationAction { ALLOW, WARN, BLOCK }

    data class SentimentResult(
        val sentiment: Sentiment = Sentiment.NEUTRAL,
        val score: Float = 0f,
        val magnitude: Float = 0f
    )

    enum class Sentiment { POSITIVE, NEGATIVE, NEUTRAL, MIXED }

    data class DuplicateResult(
        val isDuplicate: Boolean = false,
        val similarThreadId: String? = null,
        val similarityScore: Float = 0f
    )
}
