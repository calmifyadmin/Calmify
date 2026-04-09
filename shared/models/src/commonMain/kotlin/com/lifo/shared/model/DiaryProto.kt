package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DiaryProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val mood: String = "Neutral",
    @ProtoNumber(4) val title: String = "",
    @ProtoNumber(5) val description: String = "",
    @ProtoNumber(6) val images: List<String> = emptyList(),
    @ProtoNumber(7) val dateMillis: Long = 0L,
    @ProtoNumber(8) val dayKey: String = "",
    @ProtoNumber(9) val timezone: String = "",
    @ProtoNumber(10) val emotionIntensity: Int = 5,
    @ProtoNumber(11) val stressLevel: Int = 5,
    @ProtoNumber(12) val energyLevel: Int = 5,
    @ProtoNumber(13) val calmAnxietyLevel: Int = 5,
    @ProtoNumber(14) val primaryTrigger: String = "NONE",
    @ProtoNumber(15) val dominantBodySensation: String = "NONE",
)

@Serializable
data class DiaryInsightProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val diaryId: String = "",
    @ProtoNumber(3) val ownerId: String = "",
    @ProtoNumber(4) val generatedAtMillis: Long = 0L,
    @ProtoNumber(5) val dayKey: String = "",
    @ProtoNumber(6) val sourceTimezone: String = "",
    @ProtoNumber(7) val sentimentPolarity: Float = 0f,
    @ProtoNumber(8) val sentimentMagnitude: Float = 0f,
    @ProtoNumber(9) val topics: List<String> = emptyList(),
    @ProtoNumber(10) val keyPhrases: List<String> = emptyList(),
    @ProtoNumber(11) val cognitivePatterns: List<CognitivePatternProto> = emptyList(),
    @ProtoNumber(12) val summary: String = "",
    @ProtoNumber(13) val suggestedPrompts: List<String> = emptyList(),
    @ProtoNumber(14) val confidence: Float = 0f,
    @ProtoNumber(15) val modelUsed: String = "gemini-2.0-flash-exp",
    @ProtoNumber(16) val processingTimeMs: Long = 0L,
    @ProtoNumber(17) val userCorrection: String = "",
    @ProtoNumber(18) val userRating: Int = 0,
)

@Serializable
data class CognitivePatternProto(
    @ProtoNumber(1) val patternType: String = "",
    @ProtoNumber(2) val patternName: String = "",
    @ProtoNumber(3) val description: String = "",
    @ProtoNumber(4) val evidence: String = "",
    @ProtoNumber(5) val confidence: Float = 0f,
)
