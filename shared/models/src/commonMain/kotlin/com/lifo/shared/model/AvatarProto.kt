package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AvatarProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val status: String = "PENDING",
    @ProtoNumber(4) val createdAt: Long = 0L,
    @ProtoNumber(5) val vrmUrl: String = "",
    @ProtoNumber(6) val thumbnailUrl: String = "",
    @ProtoNumber(7) val voiceId: String = "",
    @ProtoNumber(8) val voiceConfig: VoiceConfigProto = VoiceConfigProto(),
    @ProtoNumber(9) val systemPrompt: AvatarSystemPromptProto = AvatarSystemPromptProto(),
    @ProtoNumber(10) val characterProfile: CharacterProfileProto = CharacterProfileProto(),
    @ProtoNumber(11) val vrmParams: VrmParamsProto = VrmParamsProto(),
)

@Serializable
data class VoiceConfigProto(
    @ProtoNumber(1) val speakingRate: Float = 1.0f,
    @ProtoNumber(2) val volumeGainDb: Float = 0.0f,
    @ProtoNumber(3) val languageCode: String = "it-IT",
)

@Serializable
data class AvatarSystemPromptProto(
    @ProtoNumber(1) val raw: String = "",
    @ProtoNumber(2) val version: Int = 1,
    @ProtoNumber(3) val generatedAt: Long = 0L,
)

@Serializable
data class CharacterProfileProto(
    @ProtoNumber(1) val coreIdentity: String = "",
    @ProtoNumber(2) val traits: List<String> = emptyList(),
    @ProtoNumber(3) val values: List<String> = emptyList(),
    @ProtoNumber(4) val biases: List<String> = emptyList(),
    @ProtoNumber(5) val coreWound: String = "",
    @ProtoNumber(6) val coreStrength: String = "",
    @ProtoNumber(7) val primaryNeed: String = "",
    @ProtoNumber(8) val goals: String = "",
    @ProtoNumber(9) val avoidTopics: List<String> = emptyList(),
    @ProtoNumber(10) val culturalReference: String = "",
    @ProtoNumber(11) val communicationStyle: CommunicationStyleProto = CommunicationStyleProto(),
    @ProtoNumber(12) val emotionalProfile: EmotionalProfileProto = EmotionalProfileProto(),
)

@Serializable
data class CommunicationStyleProto(
    @ProtoNumber(1) val directness: Float = 0.5f,
    @ProtoNumber(2) val humor: Float = 0.5f,
    @ProtoNumber(3) val speakingRhythm: String = "",
)

@Serializable
data class EmotionalProfileProto(
    @ProtoNumber(1) val attachmentStyle: String = "",
    @ProtoNumber(2) val stressResponse: String = "",
    @ProtoNumber(3) val affectionStyle: String = "",
    @ProtoNumber(4) val vulnerabilityTriggers: List<String> = emptyList(),
)

@Serializable
data class VrmParamsProto(
    @ProtoNumber(1) val bodyType: String = "",
    @ProtoNumber(2) val height: Float = 170f,
    @ProtoNumber(3) val skinTone: String = "",
    @ProtoNumber(4) val hairStyle: String = "",
    @ProtoNumber(5) val hairColor: String = "",
    @ProtoNumber(6) val outfitType: String = "",
    @ProtoNumber(7) val extras: List<String> = emptyList(),
)
