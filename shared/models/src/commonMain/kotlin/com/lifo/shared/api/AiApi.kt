package com.lifo.shared.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// --- AI Chat Request/Response (for BACKEND_AI_SERVER) ---

@Serializable
data class AiChatRequest(
    @ProtoNumber(1) val sessionId: String = "",
    @ProtoNumber(2) val message: String = "",
    @ProtoNumber(3) val context: AiContextProto = AiContextProto(),
)

@Serializable
data class AiContextProto(
    @ProtoNumber(1) val recentMood: String = "",
    @ProtoNumber(2) val recentTopics: List<String> = emptyList(),
    @ProtoNumber(3) val userName: String = "",
    @ProtoNumber(4) val aiTone: String = "FRIENDLY",
    @ProtoNumber(5) val topicsToAvoid: List<String> = emptyList(),
)

@Serializable
data class AiChatResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val message: String = "",
    @ProtoNumber(3) val sessionId: String = "",
    @ProtoNumber(4) val tokensUsed: Int = 0,
    @ProtoNumber(5) val cached: Boolean = false,
    @ProtoNumber(6) val error: ApiError = ApiError(),
)

@Serializable
data class AiInsightRequest(
    @ProtoNumber(1) val diaryId: String = "",
    @ProtoNumber(2) val text: String = "",
    @ProtoNumber(3) val mood: String = "",
    @ProtoNumber(4) val triggers: List<String> = emptyList(),
)

@Serializable
data class AiUsageResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val dailyTokensUsed: Int = 0,
    @ProtoNumber(3) val dailyTokensLimit: Int = 0,
    @ProtoNumber(4) val monthlyTokensUsed: Int = 0,
    @ProtoNumber(5) val monthlyTokensLimit: Int = 0,
    @ProtoNumber(6) val isPremium: Boolean = false,
    @ProtoNumber(7) val error: ApiError = ApiError(),
)

// --- Text Analysis ---

@Serializable
data class AiAnalyzeRequest(
    @ProtoNumber(1) val text: String = "",
    @ProtoNumber(2) val analysisType: String = "full", // "sentiment", "topics", "full"
)

@Serializable
data class AiAnalyzeResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val sentiment: String = "NEUTRAL",
    @ProtoNumber(3) val magnitude: Float = 0f,
    @ProtoNumber(4) val topics: List<String> = emptyList(),
    @ProtoNumber(5) val cached: Boolean = false,
    @ProtoNumber(6) val error: ApiError = ApiError(),
)

// --- Insight Response (structured AI output) ---

@Serializable
data class AiInsightResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val sentimentLabel: String = "NEUTRAL",
    @ProtoNumber(3) val sentimentMagnitude: Float = 0f,
    @ProtoNumber(4) val cognitivePatterns: List<AiCognitivePatternProto> = emptyList(),
    @ProtoNumber(5) val topics: List<String> = emptyList(),
    @ProtoNumber(6) val summary: String = "",
    @ProtoNumber(7) val suggestions: List<String> = emptyList(),
    @ProtoNumber(8) val cached: Boolean = false,
    @ProtoNumber(9) val tokensUsed: Int = 0,
    @ProtoNumber(10) val error: ApiError = ApiError(),
)

@Serializable
data class AiCognitivePatternProto(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val confidence: Float = 0f,
    @ProtoNumber(3) val excerpt: String = "",
)
