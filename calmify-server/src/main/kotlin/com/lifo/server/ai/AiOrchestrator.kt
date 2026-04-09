package com.lifo.server.ai

import com.lifo.shared.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Central orchestrator for all AI operations.
 *
 * Pipeline per request:
 * 1. Content filter (input validation)
 * 2. Quota check (daily/monthly token limits)
 * 3. Cache check (semantic hash → cached response)
 * 4. Model selection (Flash vs Pro based on task + tier)
 * 5. Prompt assembly (template + user message)
 * 6. Gemini API call
 * 7. Output processing (blocked response handling)
 * 8. Token tracking
 * 9. Cache store
 */
class AiOrchestrator(
    private val geminiClient: GeminiClient,
    private val promptRegistry: PromptRegistry,
    private val modelRouter: ModelRouter,
    private val responseCache: ResponseCache,
    private val tokenTracker: TokenTracker,
    private val contentFilter: ContentFilter,
) {
    private val logger = LoggerFactory.getLogger(AiOrchestrator::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─── Chat (non-streaming) ───────────────────────────────────────────

    suspend fun chat(userId: String, request: AiChatRequest): AiChatResponse {
        // 1. Validate input
        contentFilter.validateInput(request.message)

        // 2. Check quota
        tokenTracker.checkQuota(userId)

        // 3. Cache check
        val cacheKey = responseCache.getChatKey(userId, request.message)
        responseCache.getChat(cacheKey)?.let { cached ->
            logger.debug("Cache hit for chat: $cacheKey")
            return AiChatResponse(
                message = cached.text,
                sessionId = request.sessionId,
                tokensUsed = 0,
                cached = true,
            )
        }

        // 4. Select model
        val userTier = tokenTracker.getUserTier(userId)
        val model = modelRouter.selectModel(ModelRouter.TaskType.CHAT, userTier)

        // 5. Get prompt template
        val prompt = promptRegistry.getPrompt("chat_v3")

        // 6. Build contents (context from AiContextProto + user message)
        val contents = buildChatContents(request)

        // 7. Call Gemini
        val result = geminiClient.generate(
            model = model,
            systemInstruction = prompt.systemInstruction + buildContextSuffix(request.context),
            contents = contents,
            temperature = prompt.temperature,
            maxTokens = prompt.maxTokens,
        )

        // 8. Process output
        val responseText = contentFilter.processOutput(result)

        // 9. Track tokens
        if (result.tokensUsed > 0) {
            tokenTracker.record(userId, result.tokensUsed)
        }

        // 10. Cache
        responseCache.putChat(cacheKey, responseText, result.tokensUsed)

        return AiChatResponse(
            message = responseText,
            sessionId = request.sessionId,
            tokensUsed = result.tokensUsed,
            cached = false,
        )
    }

    // ─── Chat (streaming) ───────────────────────────────────────────────

    fun chatStream(userId: String, request: AiChatRequest): Flow<String> = flow {
        contentFilter.validateInput(request.message)
        tokenTracker.checkQuota(userId)

        val model = modelRouter.selectModel(ModelRouter.TaskType.CHAT_STREAM)
        val prompt = promptRegistry.getPrompt("chat_v3")
        val contents = buildChatContents(request)

        geminiClient.generateStream(
            model = model,
            systemInstruction = prompt.systemInstruction + buildContextSuffix(request.context),
            contents = contents,
            temperature = prompt.temperature,
            maxTokens = prompt.maxTokens,
        ).collect { chunk ->
            emit(chunk)
        }
    }

    // ─── Diary Insight ──────────────────────────────────────────────────

    suspend fun generateInsight(userId: String, request: AiInsightRequest): AiInsightResponse {
        // Cache: same diary = same insight (TTL 24h)
        val cacheKey = responseCache.getInsightKey(request.diaryId)
        responseCache.getInsight(cacheKey)?.let { cached ->
            logger.debug("Cache hit for insight: $cacheKey")
            return parseInsightJson(cached.text).copy(cached = true, tokensUsed = 0)
        }

        tokenTracker.checkQuota(userId)

        val prompt = promptRegistry.getPrompt("insight_analysis_v2")
        val model = modelRouter.selectModel(ModelRouter.TaskType.INSIGHT)

        val userMessage = prompt.renderUser(mapOf(
            "mood" to request.mood,
            "triggers" to request.triggers.joinToString(", "),
            "text" to request.text,
        ))

        // Use JSON mode for structured output
        val jsonText = geminiClient.generateJson(
            model = model,
            systemInstruction = prompt.systemInstruction,
            contents = listOf(GeminiContent(role = "user", text = userMessage)),
            temperature = prompt.temperature,
            maxTokens = prompt.maxTokens,
        )

        // Parse and cache
        val result = geminiClient.generate(
            model = model,
            systemInstruction = prompt.systemInstruction,
            contents = listOf(GeminiContent(role = "user", text = userMessage)),
            temperature = prompt.temperature,
            maxTokens = prompt.maxTokens,
        )

        if (result.tokensUsed > 0) tokenTracker.record(userId, result.tokensUsed)
        responseCache.putInsight(cacheKey, jsonText, result.tokensUsed)

        return parseInsightJson(jsonText).copy(tokensUsed = result.tokensUsed)
    }

    // ─── Text Analysis ──────────────────────────────────────────────────

    suspend fun analyzeText(userId: String, request: AiAnalyzeRequest): AiAnalyzeResponse {
        contentFilter.validateInput(request.text)
        tokenTracker.checkQuota(userId)

        val prompt = promptRegistry.getPrompt("text_analysis_v1")
        val model = modelRouter.selectModel(ModelRouter.TaskType.TEXT_ANALYSIS)

        val userMessage = prompt.renderUser(mapOf("text" to request.text))

        val jsonText = geminiClient.generateJson(
            model = model,
            systemInstruction = prompt.systemInstruction,
            contents = listOf(GeminiContent(role = "user", text = userMessage)),
            temperature = prompt.temperature,
            maxTokens = prompt.maxTokens,
        )

        val result = geminiClient.generate(
            model = model,
            systemInstruction = prompt.systemInstruction,
            contents = listOf(GeminiContent(role = "user", text = userMessage)),
            temperature = prompt.temperature,
            maxTokens = prompt.maxTokens,
        )

        if (result.tokensUsed > 0) tokenTracker.record(userId, result.tokensUsed)

        return parseAnalyzeJson(jsonText)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun buildChatContents(request: AiChatRequest): List<GeminiContent> {
        return listOf(GeminiContent(role = "user", text = request.message))
    }

    private fun buildContextSuffix(context: AiContextProto?): String {
        if (context == null) return ""
        val parts = mutableListOf<String>()
        if (context.userName.isNotEmpty()) parts.add("L'utente si chiama ${context.userName}.")
        if (context.recentMood.isNotEmpty()) parts.add("Umore recente: ${context.recentMood}.")
        if (context.recentTopics.isNotEmpty()) parts.add("Argomenti recenti: ${context.recentTopics.joinToString(", ")}.")
        if (context.topicsToAvoid.isNotEmpty()) parts.add("Evita questi argomenti: ${context.topicsToAvoid.joinToString(", ")}.")
        if (context.aiTone.isNotEmpty()) parts.add("Tono richiesto: ${context.aiTone}.")
        return if (parts.isEmpty()) "" else "\n\nContesto utente:\n${parts.joinToString("\n")}"
    }

    private fun parseInsightJson(jsonText: String): AiInsightResponse {
        return try {
            val obj = json.parseToJsonElement(jsonText).jsonObject
            AiInsightResponse(
                sentimentLabel = obj["sentimentLabel"]?.jsonPrimitive?.contentOrNull ?: "NEUTRAL",
                sentimentMagnitude = obj["sentimentMagnitude"]?.jsonPrimitive?.floatOrNull ?: 0f,
                cognitivePatterns = obj["cognitivePatterns"]?.jsonArray?.map { p ->
                    val pObj = p.jsonObject
                    AiCognitivePatternProto(
                        name = pObj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                        confidence = pObj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f,
                        excerpt = pObj["excerpt"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList(),
                topics = obj["topics"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: "",
                suggestions = obj["suggestions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse insight JSON: ${e.message}")
            AiInsightResponse(summary = jsonText) // Fallback: raw text as summary
        }
    }

    private fun parseAnalyzeJson(jsonText: String): AiAnalyzeResponse {
        return try {
            val obj = json.parseToJsonElement(jsonText).jsonObject
            AiAnalyzeResponse(
                sentiment = obj["sentiment"]?.jsonPrimitive?.contentOrNull ?: "NEUTRAL",
                magnitude = obj["magnitude"]?.jsonPrimitive?.floatOrNull ?: 0f,
                topics = obj["topics"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse analyze JSON: ${e.message}")
            AiAnalyzeResponse()
        }
    }
}
