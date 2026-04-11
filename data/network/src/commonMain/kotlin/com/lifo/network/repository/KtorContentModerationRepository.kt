package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ContentModerationRepository
import com.lifo.util.repository.ContentModerationRepository.*
import kotlinx.serialization.Serializable

/**
 * Content moderation via Ktor Server (Gemini-powered).
 * Replaces Cloud Functions — all AI runs server-side.
 */
class KtorContentModerationRepository(
    private val api: KtorApiClient,
) : ContentModerationRepository {

    override suspend fun checkToxicity(text: String): RequestState<ModerationResult> {
        val result = api.post<ModerationResponseDto>(
            "/api/v1/moderation/toxicity",
            TextRequest(text),
        )
        return when (result) {
            is RequestState.Success -> RequestState.Success(
                ModerationResult(
                    isToxic = result.data.isToxic,
                    toxicityScore = result.data.toxicityScore,
                    categories = result.data.categories,
                    suggestion = when (result.data.suggestion) {
                        "BLOCK" -> ModerationAction.BLOCK
                        "WARN" -> ModerationAction.WARN
                        else -> ModerationAction.ALLOW
                    },
                )
            )
            is RequestState.Error -> {
                // Moderation failure should not block posting — default to ALLOW
                RequestState.Success(ModerationResult())
            }
            else -> RequestState.Success(ModerationResult())
        }
    }

    override suspend fun analyzeSentiment(text: String): RequestState<SentimentResult> {
        val result = api.post<SentimentResponseDto>(
            "/api/v1/moderation/sentiment",
            TextRequest(text),
        )
        return when (result) {
            is RequestState.Success -> RequestState.Success(
                SentimentResult(
                    sentiment = when (result.data.sentiment) {
                        "POSITIVE" -> Sentiment.POSITIVE
                        "NEGATIVE" -> Sentiment.NEGATIVE
                        "MIXED" -> Sentiment.MIXED
                        else -> Sentiment.NEUTRAL
                    },
                    score = result.data.score,
                    magnitude = result.data.magnitude,
                )
            )
            is RequestState.Error -> RequestState.Success(SentimentResult())
            else -> RequestState.Success(SentimentResult())
        }
    }

    override suspend fun classifyMood(text: String): RequestState<String> {
        val result = api.post<MoodResponseDto>(
            "/api/v1/moderation/mood",
            TextRequest(text),
        )
        return when (result) {
            is RequestState.Success -> RequestState.Success(result.data.mood)
            is RequestState.Error -> RequestState.Success("Neutral")
            else -> RequestState.Success("Neutral")
        }
    }

    override suspend fun checkDuplicate(text: String, authorId: String): RequestState<DuplicateResult> {
        val result = api.post<DuplicateResponseDto>(
            "/api/v1/moderation/duplicate",
            TextRequest(text),
        )
        return when (result) {
            is RequestState.Success -> RequestState.Success(
                DuplicateResult(
                    isDuplicate = result.data.isDuplicate,
                    similarThreadId = result.data.similarThreadId.takeIf { it.isNotEmpty() },
                    similarityScore = result.data.similarityScore,
                )
            )
            is RequestState.Error -> RequestState.Success(DuplicateResult())
            else -> RequestState.Success(DuplicateResult())
        }
    }

    override suspend fun generateEmbedding(text: String): RequestState<FloatArray> {
        val result = api.post<EmbeddingResponseDto>(
            "/api/v1/moderation/embedding",
            TextRequest(text),
        )
        return when (result) {
            is RequestState.Success -> RequestState.Success(result.data.embedding.toFloatArray())
            is RequestState.Error -> RequestState.Error(result.error)
            else -> RequestState.Error(Exception("Unexpected state"))
        }
    }
}

@Serializable
private data class TextRequest(val text: String = "")

@Serializable
private data class ModerationResponseDto(
    val isToxic: Boolean = false,
    val toxicityScore: Float = 0f,
    val categories: List<String> = emptyList(),
    val suggestion: String = "ALLOW",
)

@Serializable
private data class SentimentResponseDto(
    val sentiment: String = "NEUTRAL",
    val score: Float = 0f,
    val magnitude: Float = 0f,
)

@Serializable
private data class MoodResponseDto(
    val mood: String = "Neutral",
)

@Serializable
private data class DuplicateResponseDto(
    val isDuplicate: Boolean = false,
    val similarThreadId: String = "",
    val similarityScore: Float = 0f,
)

@Serializable
private data class EmbeddingResponseDto(
    val embedding: List<Float> = emptyList(),
)
