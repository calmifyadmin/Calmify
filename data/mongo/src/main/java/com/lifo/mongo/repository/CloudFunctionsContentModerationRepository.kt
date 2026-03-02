package com.lifo.mongo.repository

import com.google.firebase.functions.FirebaseFunctions
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ContentModerationRepository
import com.lifo.util.repository.ContentModerationRepository.DuplicateResult
import com.lifo.util.repository.ContentModerationRepository.ModerationAction
import com.lifo.util.repository.ContentModerationRepository.ModerationResult
import com.lifo.util.repository.ContentModerationRepository.Sentiment
import com.lifo.util.repository.ContentModerationRepository.SentimentResult
import kotlinx.coroutines.tasks.await

/**
 * Cloud Functions (Genkit) implementation of ContentModerationRepository.
 *
 * Calls Firebase Cloud Functions that run Genkit AI pipelines:
 * - checkToxicity → genkit/toxicity-check
 * - analyzeSentiment → genkit/sentiment-analysis
 * - classifyMood → genkit/mood-classification
 * - checkDuplicate → genkit/duplicate-detection
 * - generateEmbedding → genkit/generate-embedding
 *
 * Requires Cloud Functions deployment (Wave 8E backend).
 */
class CloudFunctionsContentModerationRepository(
    private val functions: FirebaseFunctions
) : ContentModerationRepository {

    override suspend fun checkToxicity(text: String): RequestState<ModerationResult> {
        return try {
            val result = functions.getHttpsCallable("checkToxicity")
                .call(hashMapOf("text" to text))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any> ?: emptyMap()

            RequestState.Success(
                ModerationResult(
                    isToxic = data["isToxic"] as? Boolean ?: false,
                    toxicityScore = (data["toxicityScore"] as? Number)?.toFloat() ?: 0f,
                    categories = (data["categories"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    suggestion = when (data["suggestion"] as? String) {
                        "BLOCK" -> ModerationAction.BLOCK
                        "WARN" -> ModerationAction.WARN
                        else -> ModerationAction.ALLOW
                    }
                )
            )
        } catch (e: Exception) {
            // Moderation failure should not block posting — default to ALLOW
            RequestState.Success(ModerationResult())
        }
    }

    override suspend fun analyzeSentiment(text: String): RequestState<SentimentResult> {
        return try {
            val result = functions.getHttpsCallable("analyzeSentiment")
                .call(hashMapOf("text" to text))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any> ?: emptyMap()

            RequestState.Success(
                SentimentResult(
                    sentiment = when (data["sentiment"] as? String) {
                        "POSITIVE" -> Sentiment.POSITIVE
                        "NEGATIVE" -> Sentiment.NEGATIVE
                        "MIXED" -> Sentiment.MIXED
                        else -> Sentiment.NEUTRAL
                    },
                    score = (data["score"] as? Number)?.toFloat() ?: 0f,
                    magnitude = (data["magnitude"] as? Number)?.toFloat() ?: 0f
                )
            )
        } catch (e: Exception) {
            RequestState.Success(SentimentResult())
        }
    }

    override suspend fun classifyMood(text: String): RequestState<String> {
        return try {
            val result = functions.getHttpsCallable("classifyMood")
                .call(hashMapOf("text" to text))
                .await()

            val mood = (result.getData() as? Map<*, *>)?.get("mood") as? String ?: "Neutral"
            RequestState.Success(mood)
        } catch (e: Exception) {
            RequestState.Success("Neutral")
        }
    }

    override suspend fun checkDuplicate(text: String, authorId: String): RequestState<DuplicateResult> {
        return try {
            val result = functions.getHttpsCallable("checkDuplicate")
                .call(hashMapOf("text" to text, "authorId" to authorId))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any> ?: emptyMap()

            RequestState.Success(
                DuplicateResult(
                    isDuplicate = data["isDuplicate"] as? Boolean ?: false,
                    similarThreadId = data["similarThreadId"] as? String,
                    similarityScore = (data["similarityScore"] as? Number)?.toFloat() ?: 0f
                )
            )
        } catch (e: Exception) {
            RequestState.Success(DuplicateResult())
        }
    }

    override suspend fun generateEmbedding(text: String): RequestState<FloatArray> {
        return try {
            val result = functions.getHttpsCallable("generateEmbedding")
                .call(hashMapOf("text" to text))
                .await()

            @Suppress("UNCHECKED_CAST")
            val embeddingList = (result.getData() as? Map<String, Any>)?.get("embedding") as? List<Number>
                ?: emptyList()

            RequestState.Success(FloatArray(embeddingList.size) { embeddingList[it].toFloat() })
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}
