package com.lifo.mongo.repository

import com.lifo.util.model.RequestState
import com.lifo.util.repository.ContentModerationRepository
import com.lifo.util.repository.ContentModerationRepository.DuplicateResult
import com.lifo.util.repository.ContentModerationRepository.ModerationAction
import com.lifo.util.repository.ContentModerationRepository.ModerationResult
import com.lifo.util.repository.ContentModerationRepository.Sentiment
import com.lifo.util.repository.ContentModerationRepository.SentimentResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * KMP implementation of ContentModerationRepository using Ktor HTTP client.
 *
 * Calls Cloud Functions via HTTPS POST instead of Firebase Functions SDK.
 * This allows the same code to work on Android, iOS, Desktop, and Web.
 *
 * @param httpClient Ktor HttpClient (engine injected per-platform via Koin)
 * @param functionsBaseUrl Base URL for Cloud Functions (e.g. "https://europe-west1-calmify-xxxxx.cloudfunctions.net")
 */
class KtorContentModerationRepository(
    private val httpClient: HttpClient,
    private val functionsBaseUrl: String
) : ContentModerationRepository {

    override suspend fun checkToxicity(text: String): RequestState<ModerationResult> {
        return try {
            val response = callFunction("checkToxicity", mapOf("text" to text))
            val data = response.jsonObject

            RequestState.Success(
                ModerationResult(
                    isToxic = data["isToxic"]?.jsonPrimitive?.boolean ?: false,
                    toxicityScore = data["toxicityScore"]?.jsonPrimitive?.float ?: 0f,
                    categories = data["categories"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList(),
                    suggestion = when (data["suggestion"]?.jsonPrimitive?.content) {
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
            val response = callFunction("analyzeSentiment", mapOf("text" to text))
            val data = response.jsonObject

            RequestState.Success(
                SentimentResult(
                    sentiment = when (data["sentiment"]?.jsonPrimitive?.content) {
                        "POSITIVE" -> Sentiment.POSITIVE
                        "NEGATIVE" -> Sentiment.NEGATIVE
                        "MIXED" -> Sentiment.MIXED
                        else -> Sentiment.NEUTRAL
                    },
                    score = data["score"]?.jsonPrimitive?.float ?: 0f,
                    magnitude = data["magnitude"]?.jsonPrimitive?.float ?: 0f
                )
            )
        } catch (e: Exception) {
            RequestState.Success(SentimentResult())
        }
    }

    override suspend fun classifyMood(text: String): RequestState<String> {
        return try {
            val response = callFunction("classifyMood", mapOf("text" to text))
            val mood = response.jsonObject["mood"]?.jsonPrimitive?.content ?: "Neutral"
            RequestState.Success(mood)
        } catch (e: Exception) {
            RequestState.Success("Neutral")
        }
    }

    override suspend fun checkDuplicate(text: String, authorId: String): RequestState<DuplicateResult> {
        return try {
            val response = callFunction(
                "checkDuplicate",
                mapOf("text" to text, "authorId" to authorId)
            )
            val data = response.jsonObject

            RequestState.Success(
                DuplicateResult(
                    isDuplicate = data["isDuplicate"]?.jsonPrimitive?.boolean ?: false,
                    similarThreadId = data["similarThreadId"]?.jsonPrimitive?.content,
                    similarityScore = data["similarityScore"]?.jsonPrimitive?.float ?: 0f
                )
            )
        } catch (e: Exception) {
            RequestState.Success(DuplicateResult())
        }
    }

    override suspend fun generateEmbedding(text: String): RequestState<FloatArray> {
        return try {
            val response = callFunction("generateEmbedding", mapOf("text" to text))
            val embeddingArray = response.jsonObject["embedding"]?.jsonArray
                ?.map { it.jsonPrimitive.float } ?: emptyList()
            RequestState.Success(embeddingArray.toFloatArray())
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    /**
     * Call a Cloud Function via HTTPS POST.
     *
     * Firebase callable functions expect:
     *   POST https://<region>-<project>.cloudfunctions.net/<functionName>
     *   Content-Type: application/json
     *   Body: { "data": { ... } }
     *
     * And return:
     *   { "result": { ... } }
     */
    private suspend fun callFunction(
        functionName: String,
        data: Map<String, String>
    ): JsonObject {
        @Serializable
        data class CallableRequest(val data: Map<String, String>)

        val response: JsonObject = httpClient.post("$functionsBaseUrl/$functionName") {
            contentType(ContentType.Application.Json)
            setBody(CallableRequest(data))
        }.body()

        return response["result"]?.jsonObject ?: response
    }
}
