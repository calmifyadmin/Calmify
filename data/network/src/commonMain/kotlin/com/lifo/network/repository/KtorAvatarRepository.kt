package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.Avatar
import com.lifo.util.model.AvatarCreationForm
import com.lifo.util.model.AvatarStatus
import com.lifo.util.model.AvatarSystemPrompt
import com.lifo.util.model.CharacterProfile
import com.lifo.util.model.CommunicationStyle
import com.lifo.util.model.EmotionalProfile
import com.lifo.util.model.RequestState
import com.lifo.util.model.VoiceConfig
import com.lifo.util.model.VrmParams
import com.lifo.util.repository.AvatarRepository
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * KMP REST implementation of AvatarRepository.
 *
 * Server-mediated 2-stage pipeline:
 *   POST /api/v1/avatars → 202 Accepted { avatarId } — triggers async pipeline on server
 *   GET  /api/v1/avatars/{id} → polled until status ∈ {READY, ERROR}
 *
 * observeAvatar / observeUserAvatars use adaptive polling:
 *   - fast (2s) while status ∈ {PENDING, GENERATING, PROMPT_READY}
 *   - slow (30s) once READY/ERROR — stays subscribed for manual refresh
 */
class KtorAvatarRepository(
    private val api: KtorApiClient,
) : AvatarRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Serializable
    private data class AvatarFormDto(
        val name: String = "",
        val perceivedAge: Int = 25,
        val gender: String = "NOT_SPECIFIED",
        val languages: List<String> = emptyList(),
        val traits: List<String> = emptyList(),
        val stressResponse: String = "RATIONALIZE",
        val directness: Float = 0.5f,
        val humor: Float = 0.5f,
        val decisionStyle: String = "INSTINCT",
        val authorityRelation: Float = 0.5f,
        val values: String = "",
        val biases: List<String> = emptyList(),
        val coreWound: String = "",
        val coreStrength: String = "",
        val culturalBackground: String = "",
        val culturalReference: String = "",
        val primaryNeed: String = "BE_HEARD",
        val goals: String = "",
        val avoidTopics: String = "",
        val engagementFrequency: String = "DAILY",
        val voiceId: String = "",
        val speakingRate: Float = 1.0f,
        val voiceTone: String = "NEUTRAL",
        val pauseStyle: Boolean = false,
        val volumeGain: Float = 0.0f,
        val attachmentStyle: String = "SECURE",
        val jealousyLevel: Float = 0.0f,
        val affectionStyle: String = "",
        val vulnerabilityTriggers: String = "",
        val emotionalBarrier: String = "",
        val appearance: AppearanceDto = AppearanceDto(),
    )

    @Serializable
    private data class AppearanceDto(
        val bodyType: String = "",
        val height: Float = 170f,
        val skinTone: String = "",
        val hairStyle: String = "",
        val hairColor: String = "",
        val outfitType: String = "",
        val extras: List<String> = emptyList(),
    )

    @Serializable
    private data class CreateAvatarResponse(val avatarId: String = "")

    @Serializable
    private data class VoiceConfigDto(
        val speakingRate: Float = 1.0f,
        val volumeGainDb: Float = 0.0f,
        val languageCode: String = "it-IT",
    )

    @Serializable
    private data class SystemPromptDto(
        val raw: String = "",
        val version: Int = 1,
        val generatedAt: Long = 0,
    )

    @Serializable
    private data class CommunicationStyleDto(
        val directness: Float = 0.5f,
        val humor: Float = 0.5f,
        val speakingRhythm: String = "",
    )

    @Serializable
    private data class EmotionalProfileDto(
        val attachmentStyle: String = "",
        val stressResponse: String = "",
        val affectionStyle: String = "",
        val vulnerabilityTriggers: List<String> = emptyList(),
    )

    @Serializable
    private data class CharacterProfileDto(
        val coreIdentity: String = "",
        val traits: List<String> = emptyList(),
        val values: List<String> = emptyList(),
        val biases: List<String> = emptyList(),
        val coreWound: String = "",
        val coreStrength: String = "",
        val primaryNeed: String = "",
        val goals: String = "",
        val avoidTopics: List<String> = emptyList(),
        val culturalReference: String = "",
        val communicationStyle: CommunicationStyleDto = CommunicationStyleDto(),
        val emotionalProfile: EmotionalProfileDto = EmotionalProfileDto(),
    )

    @Serializable
    private data class VrmParamsDto(
        val bodyType: String = "",
        val height: Float = 170f,
        val skinTone: String = "",
        val hairStyle: String = "",
        val hairColor: String = "",
        val outfitType: String = "",
        val extras: List<String> = emptyList(),
    )

    @Serializable
    private data class AvatarDto(
        val id: String = "",
        val name: String = "",
        val status: String = "PENDING",
        val createdAt: Long = 0,
        val updatedAt: Long = 0,
        val vrmUrl: String = "",
        val thumbnailUrl: String = "",
        val voiceId: String = "",
        val voiceConfig: VoiceConfigDto = VoiceConfigDto(),
        val systemPrompt: SystemPromptDto = SystemPromptDto(),
        val characterProfile: CharacterProfileDto = CharacterProfileDto(),
        val vrmParams: VrmParamsDto = VrmParamsDto(),
        val errorMessage: String = "",
    )

    @Serializable
    private data class AvatarsResponse(val data: List<AvatarDto> = emptyList())

    @Serializable
    private data class UpdateStatusRequest(val status: String = "")

    override suspend fun createAvatar(userId: String, form: AvatarCreationForm): String {
        val token = api.getIdToken() ?: error("not authenticated")
        val dto = form.toDto()
        val response = api.client.post("/api/v1/avatars") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.Accepted) {
            error("avatar create failed: HTTP ${response.status.value} ${response.bodyAsText().take(200)}")
        }
        val text = response.bodyAsText()
        val parsed = json.decodeFromString(CreateAvatarResponse.serializer(), text)
        return parsed.avatarId
    }

    override fun observeAvatar(userId: String, avatarId: String): Flow<Avatar?> = flow {
        var lastEmitted: Avatar? = null
        while (true) {
            val avatar = runCatching { getAvatar(userId, avatarId) }.getOrNull()
            if (avatar != lastEmitted) {
                emit(avatar)
                lastEmitted = avatar
            }
            val delayMs = if (avatar?.status == AvatarStatus.READY || avatar?.status == AvatarStatus.ERROR) {
                30_000L
            } else 2_000L
            delay(delayMs)
        }
    }

    override fun observeUserAvatars(userId: String): Flow<List<Avatar>> = flow {
        var lastEmitted: List<Avatar> = emptyList()
        var hasTransient = false
        while (true) {
            val list = runCatching { getUserAvatars(userId) }.getOrDefault(emptyList())
            if (list != lastEmitted) {
                emit(list)
                lastEmitted = list
            }
            hasTransient = list.any {
                it.status == AvatarStatus.PENDING ||
                    it.status == AvatarStatus.GENERATING ||
                    it.status == AvatarStatus.PROMPT_READY
            }
            delay(if (hasTransient) 2_000L else 30_000L)
        }
    }

    override suspend fun getAvatar(userId: String, avatarId: String): Avatar? {
        val token = api.getIdToken() ?: return null
        val response = api.client.get("/api/v1/avatars/$avatarId") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) return null
        val dto = json.decodeFromString(AvatarDto.serializer(), response.bodyAsText())
        return dto.toDomain()
    }

    override suspend fun getUserAvatars(userId: String): List<Avatar> {
        val token = api.getIdToken() ?: return emptyList()
        val response = api.client.get("/api/v1/avatars") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) return emptyList()
        val parsed = json.decodeFromString(AvatarsResponse.serializer(), response.bodyAsText())
        return parsed.data.map { it.toDomain() }
    }

    override suspend fun deleteAvatar(userId: String, avatarId: String) {
        val token = api.getIdToken() ?: return
        api.client.delete("/api/v1/avatars/$avatarId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    override suspend fun updateAvatarStatus(userId: String, avatarId: String, status: AvatarStatus) {
        val token = api.getIdToken() ?: return
        api.client.patch("/api/v1/avatars/$avatarId/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateStatusRequest(status = status.name))
        }
    }

    private fun AvatarCreationForm.toDto(): AvatarFormDto = AvatarFormDto(
        name = name,
        perceivedAge = perceivedAge,
        gender = gender.name,
        languages = languages,
        traits = traits,
        stressResponse = stressResponse.name,
        directness = directness,
        humor = humor,
        decisionStyle = decisionStyle.name,
        authorityRelation = authorityRelation,
        values = values,
        biases = biases,
        coreWound = coreWound,
        coreStrength = coreStrength,
        culturalBackground = culturalBackground,
        culturalReference = culturalReference,
        primaryNeed = primaryNeed.name,
        goals = goals,
        avoidTopics = avoidTopics,
        engagementFrequency = engagementFrequency.name,
        voiceId = voiceId,
        speakingRate = speakingRate,
        voiceTone = voiceTone.name,
        pauseStyle = pauseStyle,
        volumeGain = volumeGain,
        attachmentStyle = attachmentStyle.name,
        jealousyLevel = jealousyLevel,
        affectionStyle = affectionStyle,
        vulnerabilityTriggers = vulnerabilityTriggers,
        emotionalBarrier = emotionalBarrier,
        appearance = AppearanceDto(
            bodyType = appearance.bodyType,
            height = appearance.height,
            skinTone = appearance.skinTone,
            hairStyle = appearance.hairStyle,
            hairColor = appearance.hairColor,
            outfitType = appearance.outfitType,
            extras = appearance.extras,
        ),
    )

    private fun AvatarDto.toDomain(): Avatar = Avatar(
        id = id,
        name = name,
        status = runCatching { AvatarStatus.valueOf(status) }.getOrDefault(AvatarStatus.PENDING),
        createdAt = createdAt,
        vrmUrl = vrmUrl,
        thumbnailUrl = thumbnailUrl,
        voiceId = voiceId,
        voiceConfig = VoiceConfig(
            speakingRate = voiceConfig.speakingRate,
            volumeGainDb = voiceConfig.volumeGainDb,
            languageCode = voiceConfig.languageCode,
        ),
        systemPrompt = AvatarSystemPrompt(
            raw = systemPrompt.raw,
            version = systemPrompt.version,
            generatedAt = systemPrompt.generatedAt,
        ),
        characterProfile = CharacterProfile(
            coreIdentity = characterProfile.coreIdentity,
            traits = characterProfile.traits,
            values = characterProfile.values,
            biases = characterProfile.biases,
            coreWound = characterProfile.coreWound,
            coreStrength = characterProfile.coreStrength,
            primaryNeed = characterProfile.primaryNeed,
            goals = characterProfile.goals,
            avoidTopics = characterProfile.avoidTopics,
            culturalReference = characterProfile.culturalReference,
            communicationStyle = CommunicationStyle(
                directness = characterProfile.communicationStyle.directness,
                humor = characterProfile.communicationStyle.humor,
                speakingRhythm = characterProfile.communicationStyle.speakingRhythm,
            ),
            emotionalProfile = EmotionalProfile(
                attachmentStyle = characterProfile.emotionalProfile.attachmentStyle,
                stressResponse = characterProfile.emotionalProfile.stressResponse,
                affectionStyle = characterProfile.emotionalProfile.affectionStyle,
                vulnerabilityTriggers = characterProfile.emotionalProfile.vulnerabilityTriggers,
            ),
        ),
        vrmParams = VrmParams(
            bodyType = vrmParams.bodyType,
            height = vrmParams.height,
            skinTone = vrmParams.skinTone,
            hairStyle = vrmParams.hairStyle,
            hairColor = vrmParams.hairColor,
            outfitType = vrmParams.outfitType,
            extras = vrmParams.extras,
        ),
    )
}
