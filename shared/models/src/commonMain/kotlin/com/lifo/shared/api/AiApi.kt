package com.lifo.shared.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// --- AI Chat Request/Response (for BACKEND_AI_SERVER) ---

@Serializable
data class AiChatRequest(
    @ProtoNumber(1) val sessionId: String = "",
    @ProtoNumber(2) val message: String = "",
    @ProtoNumber(3) val context: AiContextProto? = null,
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
    @ProtoNumber(1) val message: String = "",
    @ProtoNumber(2) val sessionId: String = "",
    @ProtoNumber(3) val tokensUsed: Int = 0,
    @ProtoNumber(4) val cached: Boolean = false,
    @ProtoNumber(5) val error: ApiError? = null,
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
    @ProtoNumber(1) val dailyTokensUsed: Int = 0,
    @ProtoNumber(2) val dailyTokensLimit: Int = 0,
    @ProtoNumber(3) val monthlyTokensUsed: Int = 0,
    @ProtoNumber(4) val monthlyTokensLimit: Int = 0,
    @ProtoNumber(5) val isPremium: Boolean = false,
    @ProtoNumber(6) val error: ApiError? = null,
)
