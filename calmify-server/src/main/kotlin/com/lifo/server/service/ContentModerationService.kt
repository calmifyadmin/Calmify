package com.lifo.server.service

import com.lifo.server.ai.GeminiClient
import com.lifo.server.ai.GeminiContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class ContentModerationService(private val geminiClient: GeminiClient) {
    private val logger = LoggerFactory.getLogger(ContentModerationService::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class ModerationResponse(
        val isToxic: Boolean = false,
        val toxicityScore: Float = 0f,
        val categories: List<String> = emptyList(),
        val suggestion: String = "ALLOW",
    )

    @Serializable
    data class SentimentResponse(
        val sentiment: String = "NEUTRAL",
        val score: Float = 0f,
        val magnitude: Float = 0f,
    )

    @Serializable
    data class DuplicateResponse(
        val isDuplicate: Boolean = false,
        val similarThreadId: String = "",
        val similarityScore: Float = 0f,
    )

    suspend fun checkToxicity(text: String): ModerationResponse = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """You are a content moderation system. Analyze text for toxicity. Respond in JSON only:
{"isToxic": bool, "toxicityScore": 0.0-1.0, "categories": ["insult","threat","profanity"], "suggestion": "ALLOW"|"WARN"|"BLOCK"}"""
            val result = geminiClient.generate(
                model = "gemini-2.0-flash",
                systemInstruction = systemPrompt,
                contents = listOf(GeminiContent(role = "user", text = text)),
                temperature = 0.1f,
            )
            val cleaned = result.text.trim().removePrefix("```json").removeSuffix("```").trim()
            json.decodeFromString<ModerationResponse>(cleaned)
        } catch (e: Exception) {
            logger.warn("Toxicity check failed for text, defaulting to ALLOW: ${e.message}")
            ModerationResponse()
        }
    }

    suspend fun analyzeSentiment(text: String): SentimentResponse = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """You are a sentiment analysis system. Analyze text sentiment. Respond in JSON only:
{"sentiment": "POSITIVE"|"NEGATIVE"|"NEUTRAL"|"MIXED", "score": -1.0 to 1.0, "magnitude": 0.0 to 1.0}"""
            val result = geminiClient.generate(
                model = "gemini-2.0-flash",
                systemInstruction = systemPrompt,
                contents = listOf(GeminiContent(role = "user", text = text)),
                temperature = 0.1f,
            )
            val cleaned = result.text.trim().removePrefix("```json").removeSuffix("```").trim()
            json.decodeFromString<SentimentResponse>(cleaned)
        } catch (e: Exception) {
            logger.warn("Sentiment analysis failed, defaulting to NEUTRAL: ${e.message}")
            SentimentResponse()
        }
    }

    suspend fun classifyMood(text: String): String = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = "You are a mood classifier. Reply with ONLY one word from: Happy, Angry, Bored, Calm, Depressed, Disappointed, Humorous, Lonely, Mysterious, Romantic, Shameful, Awful, Surprised, Suspicious, Tense, Neutral"
            val result = geminiClient.generate(
                model = "gemini-2.0-flash",
                systemInstruction = systemPrompt,
                contents = listOf(GeminiContent(role = "user", text = text)),
                temperature = 0.1f,
            )
            result.text.trim()
        } catch (e: Exception) {
            logger.warn("Mood classification failed, defaulting to Neutral: ${e.message}")
            "Neutral"
        }
    }

    suspend fun checkDuplicate(text: String, authorId: String): DuplicateResponse {
        // MVP: no embedding-based duplicate check. Always return not duplicate.
        // Future: integrate Vertex AI Embeddings + cosine similarity.
        return DuplicateResponse()
    }

    suspend fun generateEmbedding(text: String): FloatArray {
        // MVP: return empty array. Future: Vertex AI text-embedding-004.
        return floatArrayOf()
    }
}
