package com.lifo.server.ai

import com.google.cloud.firestore.Firestore
import io.github.reactivecircus.cache4k.Cache
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/**
 * Centralized prompt management. Prompts are stored in Firestore (config/prompts/{id})
 * and cached locally for 5 minutes. Falls back to hardcoded defaults if Firestore is
 * unavailable — so the server works even without a network connection.
 *
 * This enables:
 * - Prompt updates without redeploying the server
 * - A/B testing on prompt variants
 * - Version tracking for prompt changes
 */
class PromptRegistry(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(PromptRegistry::class.java)

    private val cache = Cache.Builder<String, PromptTemplate>()
        .expireAfterWrite(5.minutes)
        .build()

    suspend fun getPrompt(id: String): PromptTemplate {
        cache.get(id)?.let { return it }

        // Try Firestore
        try {
            val doc = db.collection("config").document("prompts")
                .collection("templates").document(id).get().get()
            if (doc.exists()) {
                val template = PromptTemplate(
                    id = id,
                    version = doc.getLong("version")?.toInt() ?: 1,
                    systemInstruction = doc.getString("systemInstruction") ?: "",
                    userTemplate = doc.getString("userTemplate") ?: "{{message}}",
                    model = doc.getString("model") ?: "gemini-2.0-flash",
                    temperature = doc.getDouble("temperature")?.toFloat() ?: 0.8f,
                    maxTokens = doc.getLong("maxTokens")?.toInt() ?: 4096,
                )
                cache.put(id, template)
                logger.info("Loaded prompt '$id' v${template.version} from Firestore")
                return template
            }
        } catch (e: Exception) {
            logger.warn("Failed to load prompt '$id' from Firestore: ${e.message}")
        }

        // Fallback to defaults
        val default = DEFAULTS[id] ?: throw IllegalArgumentException("Unknown prompt: $id")
        cache.put(id, default)
        return default
    }

    fun invalidateCache() {
        cache.invalidateAll()
    }

    companion object {
        val DEFAULTS = mapOf(
            "chat_v3" to PromptTemplate(
                id = "chat_v3",
                version = 3,
                systemInstruction = """
                    Sei Calmify, un compagno di benessere emotivo.
                    Rispondi in italiano. Sii empatico, mai giudicante.
                    Usa tecniche CBT quando appropriato.
                    Se l'utente esprime pensieri autolesionistici,
                    suggerisci risorse professionali (Telefono Amico: 02 2327 2327).
                    Non dare diagnosi mediche. Sei un supporto, non un terapeuta.
                """.trimIndent(),
                userTemplate = "{{message}}",
                model = "gemini-2.0-flash",
                temperature = 0.8f,
                maxTokens = 4096,
            ),
            "insight_analysis_v2" to PromptTemplate(
                id = "insight_analysis_v2",
                version = 2,
                systemInstruction = """
                    Analizza il seguente diary entry. Rispondi SOLO in JSON valido.
                    Schema JSON richiesto:
                    {
                      "sentimentLabel": "VERY_NEGATIVE|NEGATIVE|NEUTRAL|POSITIVE|VERY_POSITIVE",
                      "sentimentMagnitude": 0.0-1.0,
                      "cognitivePatterns": [{"name": "string", "confidence": 0.0-1.0, "excerpt": "string"}],
                      "topics": ["string"],
                      "summary": "string (1-2 frasi in italiano)",
                      "suggestions": ["string"]
                    }
                    Cognitive patterns CBT: catastrophizing, black_white_thinking, mind_reading,
                    fortune_telling, emotional_reasoning, overgeneralization, personalization,
                    should_statements, labeling, magnification.
                """.trimIndent(),
                userTemplate = "Mood: {{mood}}/10\nTriggers: {{triggers}}\nTesto: {{text}}",
                model = "gemini-2.0-flash",
                temperature = 0.3f,
                maxTokens = 2048,
            ),
            "text_analysis_v1" to PromptTemplate(
                id = "text_analysis_v1",
                version = 1,
                systemInstruction = """
                    Analizza il testo per sentiment e argomenti.
                    Rispondi SOLO in JSON: {"sentiment": "string", "magnitude": 0.0-1.0, "topics": ["string"]}.
                """.trimIndent(),
                userTemplate = "{{text}}",
                model = "gemini-2.0-flash",
                temperature = 0.2f,
                maxTokens = 1024,
            ),
        )
    }
}

data class PromptTemplate(
    val id: String,
    val version: Int,
    val systemInstruction: String,
    val userTemplate: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
) {
    fun renderUser(vars: Map<String, String>): String {
        var result = userTemplate
        for ((key, value) in vars) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}
