package com.lifo.server.ai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Server-side Gemini API client using Ktor HTTP client.
 * API key stays on the server — never exposed to clients.
 */
class GeminiClient(
    private val apiKey: String,
) {
    private val logger = LoggerFactory.getLogger(GeminiClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient(CIO)

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Generate content (non-streaming).
     * Returns the full response text + token count.
     */
    suspend fun generate(
        model: String,
        systemInstruction: String,
        contents: List<GeminiContent>,
        temperature: Float = 0.8f,
        maxTokens: Int = 4096,
    ): GeminiResult {
        val requestBody = buildJsonObject {
            // System instruction
            if (systemInstruction.isNotEmpty()) {
                putJsonObject("system_instruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemInstruction) }
                    }
                }
            }

            // Contents (conversation history + current message)
            putJsonArray("contents") {
                for (content in contents) {
                    addJsonObject {
                        put("role", content.role)
                        putJsonArray("parts") {
                            addJsonObject { put("text", content.text) }
                        }
                    }
                }
            }

            // Generation config
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
            }

            // Safety settings — allow therapeutic content
            putJsonArray("safetySettings") {
                for (cat in listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")) {
                    addJsonObject {
                        put("category", cat)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    }
                }
            }
        }

        val response = httpClient.post("$baseUrl/models/$model:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val body = json.parseToJsonElement(response.bodyAsText())
        return parseResponse(body)
    }

    /**
     * Streaming generation — emits text chunks as they arrive.
     */
    fun generateStream(
        model: String,
        systemInstruction: String,
        contents: List<GeminiContent>,
        temperature: Float = 0.8f,
        maxTokens: Int = 4096,
    ): Flow<String> = flow {
        val requestBody = buildJsonObject {
            if (systemInstruction.isNotEmpty()) {
                putJsonObject("system_instruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemInstruction) }
                    }
                }
            }
            putJsonArray("contents") {
                for (content in contents) {
                    addJsonObject {
                        put("role", content.role)
                        putJsonArray("parts") {
                            addJsonObject { put("text", content.text) }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
            }

            // Safety settings — same as non-streaming generate
            putJsonArray("safetySettings") {
                for (cat in listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")) {
                    addJsonObject {
                        put("category", cat)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    }
                }
            }
        }

        val response = httpClient.preparePost("$baseUrl/models/$model:streamGenerateContent") {
            header("x-goog-api-key", apiKey)
            parameter("alt", "sse")
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }.execute { httpResponse ->
            val channel = httpResponse.bodyAsChannel()
            val buffer = StringBuilder()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isNotEmpty() && data != "[DONE]") {
                        try {
                            val chunk = json.parseToJsonElement(data)
                            val text = chunk.jsonObject["candidates"]
                                ?.jsonArray?.firstOrNull()
                                ?.jsonObject?.get("content")
                                ?.jsonObject?.get("parts")
                                ?.jsonArray?.firstOrNull()
                                ?.jsonObject?.get("text")
                                ?.jsonPrimitive?.contentOrNull
                            if (!text.isNullOrEmpty()) {
                                emit(text)
                            }
                        } catch (e: Exception) {
                            logger.debug("Skipping unparseable SSE chunk: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate content with JSON mode — for structured output (insights, analysis).
     * Returns full GeminiResult (text + token count) to avoid needing a second call.
     */
    suspend fun generateJson(
        model: String,
        systemInstruction: String,
        contents: List<GeminiContent>,
        temperature: Float = 0.3f,
        maxTokens: Int = 2048,
    ): GeminiResult {
        val requestBody = buildJsonObject {
            if (systemInstruction.isNotEmpty()) {
                putJsonObject("system_instruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", systemInstruction) }
                    }
                }
            }
            putJsonArray("contents") {
                for (content in contents) {
                    addJsonObject {
                        put("role", content.role)
                        putJsonArray("parts") {
                            addJsonObject { put("text", content.text) }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
                put("responseMimeType", "application/json")
            }

            // Safety settings — same as non-JSON generate
            putJsonArray("safetySettings") {
                for (cat in listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")) {
                    addJsonObject {
                        put("category", cat)
                        put("threshold", "BLOCK_ONLY_HIGH")
                    }
                }
            }
        }

        val response = httpClient.post("$baseUrl/models/$model:generateContent") {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val body = json.parseToJsonElement(response.bodyAsText())
        return parseResponse(body)
    }

    private fun parseResponse(body: JsonElement): GeminiResult {
        val candidates = body.jsonObject["candidates"]?.jsonArray
        if (candidates.isNullOrEmpty()) {
            val blockReason = body.jsonObject["promptFeedback"]
                ?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            logger.warn("Gemini returned no candidates. Block reason: $blockReason")
            return GeminiResult(
                text = "",
                tokensUsed = 0,
                blocked = blockReason != null,
                blockReason = blockReason,
            )
        }

        val text = candidates.first()
            .jsonObject["content"]
            ?.jsonObject?.get("parts")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.contentOrNull ?: ""

        val usageMetadata = body.jsonObject["usageMetadata"]?.jsonObject
        val totalTokens = usageMetadata?.get("totalTokenCount")?.jsonPrimitive?.intOrNull ?: 0

        return GeminiResult(text = text, tokensUsed = totalTokens)
    }

    fun close() {
        httpClient.close()
    }
}

data class GeminiContent(
    val role: String, // "user" or "model"
    val text: String,
)

data class GeminiResult(
    val text: String,
    val tokensUsed: Int,
    val blocked: Boolean = false,
    val blockReason: String? = null,
)
