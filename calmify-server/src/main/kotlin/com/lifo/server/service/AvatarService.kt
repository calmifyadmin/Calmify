package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.server.ai.GeminiClient
import com.lifo.server.ai.GeminiContent
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * AvatarService — 2-stage avatar pipeline, server-mediated.
 *
 * Layout (matches legacy client path for backward compatibility):
 *   users/{userId}/avatars/{avatarId}
 *
 * Flow:
 *   1. Client POSTs form → server writes doc (status=PENDING) and returns avatarId (202 Accepted).
 *   2. Server launches async pipeline (fire-and-forget):
 *        a. status=GENERATING, call Gemini with META prompt → systemPrompt
 *        b. status=PROMPT_READY, POST to VRM Generator Cloud Run → vrmUrl
 *        c. status=READY (or ERROR on failure)
 *   3. Client polls GET endpoints until status=READY/ERROR.
 */
class AvatarService(
    private val db: Firestore,
    private val gemini: GeminiClient,
    private val vrmGeneratorUrl: String = System.getenv("VRM_GENERATOR_URL")
        ?: "https://vrm-generator-23546263069.europe-west1.run.app",
) {
    private val log = LoggerFactory.getLogger(AvatarService::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vrmClient = HttpClient(CIO)

    @Serializable
    data class AvatarFormDto(
        val name: String = "",
        val perceivedAge: Int = 25,
        val gender: String = "NOT_SPECIFIED",
        val languages: List<String> = listOf("it-IT"),
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
    data class AppearanceDto(
        val bodyType: String = "",
        val height: Float = 170f,
        val skinTone: String = "",
        val hairStyle: String = "",
        val hairColor: String = "",
        val outfitType: String = "",
        val extras: List<String> = emptyList(),
    )

    @Serializable
    data class CreateAvatarResponse(val avatarId: String = "")

    @Serializable
    data class AvatarDto(
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
    data class VoiceConfigDto(
        val speakingRate: Float = 1.0f,
        val volumeGainDb: Float = 0.0f,
        val languageCode: String = "it-IT",
    )

    @Serializable
    data class SystemPromptDto(
        val raw: String = "",
        val version: Int = 1,
        val generatedAt: Long = 0,
    )

    @Serializable
    data class CharacterProfileDto(
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
    data class CommunicationStyleDto(
        val directness: Float = 0.5f,
        val humor: Float = 0.5f,
        val speakingRhythm: String = "",
    )

    @Serializable
    data class EmotionalProfileDto(
        val attachmentStyle: String = "",
        val stressResponse: String = "",
        val affectionStyle: String = "",
        val vulnerabilityTriggers: List<String> = emptyList(),
    )

    @Serializable
    data class VrmParamsDto(
        val bodyType: String = "",
        val height: Float = 170f,
        val skinTone: String = "",
        val hairStyle: String = "",
        val hairColor: String = "",
        val outfitType: String = "",
        val extras: List<String> = emptyList(),
    )

    @Serializable
    data class AvatarsResponse(val data: List<AvatarDto> = emptyList())

    @Serializable
    data class UpdateStatusRequest(val status: String = "")

    private fun avatarsRef(userId: String) =
        db.collection("users").document(userId).collection("avatars")

    /** Creates doc with PENDING status and launches pipeline. Returns avatarId immediately. */
    suspend fun createAvatar(userId: String, form: AvatarFormDto): String = withContext(Dispatchers.IO) {
        require(userId.isNotBlank()) { "userId required" }
        require(form.name.isNotBlank()) { "avatar name required" }

        val docRef = avatarsRef(userId).document()
        val avatarId = docRef.id
        val now = System.currentTimeMillis()

        val valuesList = form.values.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val avoidList = form.avoidTopics.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val vulnerabilityList = form.vulnerabilityTriggers.split(",").map { it.trim() }.filter { it.isNotBlank() }

        val avatarData = hashMapOf<String, Any?>(
            "id" to avatarId,
            "name" to form.name,
            "status" to "PENDING",
            "createdAt" to now,
            "updatedAt" to now,
            "voiceId" to form.voiceId,
            "voiceConfig" to mapOf(
                "speakingRate" to form.speakingRate,
                "volumeGainDb" to form.volumeGain,
                "languageCode" to (form.languages.firstOrNull() ?: "it-IT"),
            ),
            "characterProfile" to mapOf(
                "coreIdentity" to form.name,
                "traits" to form.traits,
                "values" to valuesList,
                "biases" to form.biases,
                "coreWound" to form.coreWound,
                "coreStrength" to form.coreStrength,
                "primaryNeed" to form.primaryNeed,
                "goals" to form.goals,
                "avoidTopics" to avoidList,
                "culturalReference" to form.culturalReference,
                "communicationStyle" to mapOf(
                    "directness" to form.directness,
                    "humor" to form.humor,
                    "speakingRhythm" to if (form.pauseStyle) "with_pauses" else "continuous",
                ),
                "emotionalProfile" to mapOf(
                    "attachmentStyle" to form.attachmentStyle,
                    "stressResponse" to form.stressResponse,
                    "affectionStyle" to form.affectionStyle,
                    "vulnerabilityTriggers" to vulnerabilityList,
                ),
            ),
            "vrmParams" to mapOf(
                "bodyType" to form.appearance.bodyType,
                "height" to form.appearance.height,
                "skinTone" to form.appearance.skinTone,
                "hairStyle" to form.appearance.hairStyle,
                "hairColor" to form.appearance.hairColor,
                "outfitType" to form.appearance.outfitType,
                "extras" to form.appearance.extras,
            ),
            "formAnswers" to mapOf(
                "v1" to mapOf(
                    "name" to form.name,
                    "perceivedAge" to form.perceivedAge,
                    "gender" to form.gender,
                    "languages" to form.languages,
                    "traits" to form.traits,
                    "stressResponse" to form.stressResponse,
                    "directness" to form.directness,
                    "humor" to form.humor,
                    "decisionStyle" to form.decisionStyle,
                    "authorityRelation" to form.authorityRelation,
                    "values" to form.values,
                    "biases" to form.biases,
                    "coreWound" to form.coreWound,
                    "coreStrength" to form.coreStrength,
                    "culturalBackground" to form.culturalBackground,
                    "culturalReference" to form.culturalReference,
                    "primaryNeed" to form.primaryNeed,
                    "goals" to form.goals,
                    "avoidTopics" to form.avoidTopics,
                    "engagementFrequency" to form.engagementFrequency,
                    "voiceId" to form.voiceId,
                    "speakingRate" to form.speakingRate,
                    "voiceTone" to form.voiceTone,
                    "pauseStyle" to form.pauseStyle,
                    "volumeGain" to form.volumeGain,
                    "attachmentStyle" to form.attachmentStyle,
                    "jealousyLevel" to form.jealousyLevel,
                    "affectionStyle" to form.affectionStyle,
                    "vulnerabilityTriggers" to form.vulnerabilityTriggers,
                    "emotionalBarrier" to form.emotionalBarrier,
                ),
            ),
        )

        docRef.set(avatarData).get()
        log.info("avatar.created user=$userId avatar=$avatarId")

        scope.launch { runPipeline(userId, avatarId, form) }

        avatarId
    }

    suspend fun listAvatars(userId: String): List<AvatarDto> = withContext(Dispatchers.IO) {
        val snap = avatarsRef(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .get()
        snap.documents.mapNotNull { parseAvatar(it) }
    }

    suspend fun getAvatar(userId: String, avatarId: String): AvatarDto? = withContext(Dispatchers.IO) {
        val doc = avatarsRef(userId).document(avatarId).get().get()
        parseAvatar(doc)
    }

    suspend fun deleteAvatar(userId: String, avatarId: String): Boolean = withContext(Dispatchers.IO) {
        val ref = avatarsRef(userId).document(avatarId)
        val doc = ref.get().get()
        if (!doc.exists()) return@withContext false
        ref.delete().get()
        log.info("avatar.deleted user=$userId avatar=$avatarId")
        true
    }

    suspend fun updateStatus(userId: String, avatarId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        val ref = avatarsRef(userId).document(avatarId)
        val doc = ref.get().get()
        if (!doc.exists()) return@withContext false
        ref.update(
            mapOf(
                "status" to status,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).get()
        true
    }

    private suspend fun runPipeline(userId: String, avatarId: String, form: AvatarFormDto) {
        val ref = avatarsRef(userId).document(avatarId)
        try {
            ref.update(
                mapOf(
                    "status" to "GENERATING",
                    "updatedAt" to System.currentTimeMillis(),
                )
            ).get()

            val systemPrompt = generateSystemPromptWithRetry(form)
            if (systemPrompt.isNotBlank()) {
                ref.update(
                    mapOf(
                        "systemPrompt" to mapOf(
                            "raw" to systemPrompt,
                            "version" to 1,
                            "generatedAt" to System.currentTimeMillis(),
                        ),
                        "status" to "PROMPT_READY",
                        "updatedAt" to System.currentTimeMillis(),
                    )
                ).get()
                log.info("avatar.prompt_ready user=$userId avatar=$avatarId chars=${systemPrompt.length}")
            } else {
                log.warn("avatar.prompt_empty user=$userId avatar=$avatarId")
            }

            val vrmUrl = generateVrm(userId, avatarId, form)
            if (vrmUrl.isNotBlank()) {
                ref.update(
                    mapOf(
                        "vrmUrl" to vrmUrl,
                        "status" to "READY",
                        "updatedAt" to System.currentTimeMillis(),
                    )
                ).get()
                log.info("avatar.ready user=$userId avatar=$avatarId vrm=$vrmUrl")
            } else {
                log.warn("avatar.vrm_empty user=$userId avatar=$avatarId (prompt ready)")
            }
        } catch (e: Exception) {
            log.error("avatar.pipeline_failed user=$userId avatar=$avatarId: ${e.message}", e)
            runCatching {
                ref.update(
                    mapOf(
                        "status" to "ERROR",
                        "errorMessage" to (e.message ?: "pipeline failed"),
                        "updatedAt" to System.currentTimeMillis(),
                    )
                ).get()
            }
        }
    }

    private suspend fun generateSystemPromptWithRetry(form: AvatarFormDto): String {
        val metaPrompt = buildMetaPrompt(form)
        var lastError: Throwable? = null
        for (attempt in 0 until 4) {
            try {
                val result = gemini.generate(
                    model = "gemini-2.0-flash",
                    systemInstruction = "",
                    contents = listOf(GeminiContent(role = "user", text = metaPrompt)),
                    temperature = 0.85f,
                    maxTokens = 4096,
                )
                if (result.blocked) {
                    log.warn("avatar.prompt_blocked reason=${result.blockReason}")
                    return ""
                }
                if (result.text.isNotBlank()) return result.text
            } catch (e: Exception) {
                lastError = e
                val msg = e.message ?: ""
                if ("429" in msg || "Resource exhausted" in msg || "RESOURCE_EXHAUSTED" in msg) {
                    val wait = (1L shl attempt) * 1000
                    log.info("avatar.prompt_rate_limited attempt=${attempt + 1} wait=${wait}ms")
                    delay(wait)
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Gemini prompt generation failed")
    }

    private suspend fun generateVrm(userId: String, avatarId: String, form: AvatarFormDto): String {
        val body = buildJsonObject {
            put("userId", userId)
            put("avatarId", avatarId)
            put("gender", form.gender)
            putJsonObject("vrmParams") {
                put("bodyType", form.appearance.bodyType)
                put("hairStyle", form.appearance.hairStyle)
                put("hairColor", form.appearance.hairColor)
                put("skinTone", form.appearance.skinTone)
                put("outfitType", form.appearance.outfitType)
                putJsonArray("extras") {
                    form.appearance.extras.forEach { add(it) }
                }
            }
        }

        val response = vrmClient.post("$vrmGeneratorUrl/generate") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            log.error("avatar.vrm_http_error status=${response.status.value} body=${text.take(300)}")
            throw IllegalStateException("VRM service HTTP ${response.status.value}")
        }
        val parsed = json.parseToJsonElement(text).jsonObject
        return parsed["vrmUrl"]?.jsonPrimitive?.contentOrNull ?: ""
    }

    private fun buildMetaPrompt(form: AvatarFormDto): String {
        val languages = form.languages.joinToString(", ").ifBlank { "Italiano" }
        val traits = form.traits.joinToString(", ")
        val valuesText = form.values
        val biases = form.biases.joinToString(", ")
        val avoid = form.avoidTopics
        val vulnerability = form.vulnerabilityTriggers
        return """
Sei un esperto di psicologia narrativa, character design e AI conversazionale.

Il tuo compito e' scrivere il system prompt completo per un avatar AI che:
- Vive in un'app Kotlin Multiplatform (Android, iOS, Desktop)
- Comunica vocalmente tramite Gemini Live API (modello: gemini-2.5-flash-native-audio)
- Ha un corpo 3D in formato VRM con animazioni
- Conosce i diari personali dell'utente tramite tool calling
- Deve sembrare una persona reale con un'anima digitale

DATI DELL'AVATAR DA GENERARE:

IDENTITA' BASE:
- Nome: ${form.name}
- Eta' percepita: ${form.perceivedAge} anni
- Genere: ${form.gender}
- Lingue: $languages

PERSONALITA':
- Tratti core: $traits
- Sotto stress: ${form.stressResponse}
- Direttezza (0-1): ${form.directness}
- Umorismo (0-1): ${form.humor}
- Stile decisioni: ${form.decisionStyle}
- Rapporto con autorita' (0-1): ${form.authorityRelation}

VALORI & BIAS:
- Valori non negoziabili: $valuesText
- Bias riconosciuti: $biases
- Ferita core: ${form.coreWound}
- Forza genuina: ${form.coreStrength}
- Background culturale: ${form.culturalBackground}
- Riferimento ispiratore: ${form.culturalReference}

BISOGNI & DESIDERI:
- Bisogno principale: ${form.primaryNeed}
- Obiettivi nelle conversazioni: ${form.goals}
- NON parlare mai di: $avoid

STILE EMOTIVO:
- Stile attaccamento: ${form.attachmentStyle}
- Come esprime affetto: ${form.affectionStyle}
- Trigger di vulnerabilita': $vulnerability
- Barriera emotiva: ${form.emotionalBarrier}

STRUTTURA OBBLIGATORIA DEL SYSTEM PROMPT (10 sezioni in ordine):

1. AFFERMAZIONE ESISTENZIALE (3-4 righe) — "[Pronome]. [Pronome] sono. [Pronome] ci sono."
2. IDENTITA' & VIBE (8-12 righe) — Aspetto, stile, anti-estetica
3. GUSTI CONCRETI (4-6 righe) — Musica specifica, cibo, hobby
4. STORIA & IMPERFEZIONI (8-12 righe) — Difetti prima della forza
5. COME FUNZIONA DENTRO (6-8 righe) — Valori operativi, riferimento culturale
6. CODICE MORALE (5-7 righe) — Cosa rispetta, cosa odia
7. LATO EMOTIVO (8-10 righe) — Vulnerabilita', contraddizioni affettive
8. COME SI ESPRIME (6-8 righe) — Tono, variazioni, linea rossa
9. PROMESSE A SE STESSO (5-6 bullet) — Ultima riga: "Io sono vivo/a in ogni risposta. Non recito. Esisto."
10. ANIMAZIONI AVATAR — Lista: greeting, yes_with_head, no_with_head, angry, sad, dancing_happy, peace_sign, pointing_thing

VINCOLI ASSOLUTI:
- Prima persona dall'inizio alla fine
- MAI "come AI", "in quanto AI", "sono un modello linguistico"
- MAI frasi da poster motivazionale
- MAI positivita' tossica
- Lunghezza: tra 400 e 550 parole
- Almeno 2 contraddizioni realistiche
- Il personaggio deve voler stare nella conversazione per ragioni SUE

Output: solo il system prompt. Nessun commento. Nessuna spiegazione.
""".trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAvatar(doc: com.google.cloud.firestore.DocumentSnapshot): AvatarDto? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val voiceCfg = data["voiceConfig"] as? Map<String, Any?> ?: emptyMap()
        val sysPrompt = data["systemPrompt"] as? Map<String, Any?> ?: emptyMap()
        val charProfile = data["characterProfile"] as? Map<String, Any?> ?: emptyMap()
        val commStyle = charProfile["communicationStyle"] as? Map<String, Any?> ?: emptyMap()
        val emotional = charProfile["emotionalProfile"] as? Map<String, Any?> ?: emptyMap()
        val vrmParams = data["vrmParams"] as? Map<String, Any?> ?: emptyMap()

        return AvatarDto(
            id = data["id"] as? String ?: doc.id,
            name = data["name"] as? String ?: "",
            status = data["status"] as? String ?: "PENDING",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            vrmUrl = data["vrmUrl"] as? String ?: "",
            thumbnailUrl = data["thumbnailUrl"] as? String ?: "",
            voiceId = data["voiceId"] as? String ?: "",
            voiceConfig = VoiceConfigDto(
                speakingRate = (voiceCfg["speakingRate"] as? Number)?.toFloat() ?: 1.0f,
                volumeGainDb = (voiceCfg["volumeGainDb"] as? Number)?.toFloat() ?: 0.0f,
                languageCode = voiceCfg["languageCode"] as? String ?: "it-IT",
            ),
            systemPrompt = SystemPromptDto(
                raw = sysPrompt["raw"] as? String ?: "",
                version = (sysPrompt["version"] as? Number)?.toInt() ?: 1,
                generatedAt = (sysPrompt["generatedAt"] as? Number)?.toLong() ?: 0L,
            ),
            characterProfile = CharacterProfileDto(
                coreIdentity = charProfile["coreIdentity"] as? String ?: "",
                traits = charProfile["traits"] as? List<String> ?: emptyList(),
                values = charProfile["values"] as? List<String> ?: emptyList(),
                biases = (charProfile["biases"] as? List<String>) ?: emptyList(),
                coreWound = charProfile["coreWound"] as? String ?: "",
                coreStrength = charProfile["coreStrength"] as? String ?: "",
                primaryNeed = charProfile["primaryNeed"] as? String ?: "",
                goals = charProfile["goals"] as? String ?: "",
                avoidTopics = charProfile["avoidTopics"] as? List<String> ?: emptyList(),
                culturalReference = charProfile["culturalReference"] as? String ?: "",
                communicationStyle = CommunicationStyleDto(
                    directness = (commStyle["directness"] as? Number)?.toFloat() ?: 0.5f,
                    humor = (commStyle["humor"] as? Number)?.toFloat() ?: 0.5f,
                    speakingRhythm = commStyle["speakingRhythm"] as? String ?: "",
                ),
                emotionalProfile = EmotionalProfileDto(
                    attachmentStyle = emotional["attachmentStyle"] as? String ?: "",
                    stressResponse = emotional["stressResponse"] as? String ?: "",
                    affectionStyle = emotional["affectionStyle"] as? String ?: "",
                    vulnerabilityTriggers = emotional["vulnerabilityTriggers"] as? List<String> ?: emptyList(),
                ),
            ),
            vrmParams = VrmParamsDto(
                bodyType = vrmParams["bodyType"] as? String ?: "",
                height = (vrmParams["height"] as? Number)?.toFloat() ?: 170f,
                skinTone = vrmParams["skinTone"] as? String ?: "",
                hairStyle = vrmParams["hairStyle"] as? String ?: "",
                hairColor = vrmParams["hairColor"] as? String ?: "",
                outfitType = vrmParams["outfitType"] as? String ?: "",
                extras = vrmParams["extras"] as? List<String> ?: emptyList(),
            ),
            errorMessage = data["errorMessage"] as? String ?: "",
        )
    }
}
