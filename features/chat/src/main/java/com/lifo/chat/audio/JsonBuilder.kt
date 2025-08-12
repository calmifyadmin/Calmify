package com.lifo.chat.audio

/**
 * Helper class to build JSON strings without dependency on org.json
 * Avoids compatibility issues with Android's limited JSONObject implementation
 */
object JsonBuilder {

    fun buildGeminiAudioRequest(
        text: String,
        voice: String,
        temperature: Double
    ): String {
        // Escape text for JSON - comprehensive escaping
        val escapedText = text
            .replace("\\", "\\\\")     // Backslash must be first
            .replace("\"", "\\\"")      // Quote
            .replace("\n", "\\n")       // Newline
            .replace("\r", "\\r")       // Carriage return
            .replace("\t", "\\t")       // Tab
            .replace("\b", "\\b")       // Backspace
            .replace("\u000C", "\\f")   // Form feed
            .replace("/", "\\/")        // Forward slash (optional but safe)

        return """
        {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {
                            "text": "$escapedText"
                        }
                    ]
                }
            ],
            "generationConfig": {
                "responseModalities": ["audio"],
                "temperature": $temperature,
                "speech_config": {
                    "voice_config": {
                        "prebuilt_voice_config": {
                            "voice_name": "$voice"
                        }
                    }
                }
            }
        }
        """.trimIndent()
    }

    fun parseAudioResponse(json: String): AudioResponseData? {
        return try {
            // Check if this is a streaming response with data: prefix
            val actualJson = if (json.startsWith("data: ")) {
                json.substring(6)
            } else {
                json
            }

            // Simple JSON parsing for the specific response structure
            val candidatesStart = actualJson.indexOf("\"candidates\"")
            if (candidatesStart == -1) return null

            // Look for inline data in the response
            val inlineDataStart = actualJson.indexOf("\"inlineData\"", candidatesStart)
            if (inlineDataStart != -1) {
                // Found inline data - extract it
                val dataStart = actualJson.indexOf("\"", inlineDataStart + 13) + 1
                val dataEnd = findClosingQuote(actualJson, dataStart)

                if (dataStart > 0 && dataEnd > dataStart) {
                    val audioData = actualJson.substring(dataStart, dataEnd)

                    // Check finish reason
                    val finishReason = extractFinishReason(actualJson)

                    return AudioResponseData(audioData, finishReason)
                }
            }

            // If no inline data, still check for finish reason
            val finishReason = extractFinishReason(actualJson)
            if (finishReason != null) {
                return AudioResponseData("", finishReason)
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun findClosingQuote(json: String, startIndex: Int): Int {
        var i = startIndex
        while (i < json.length) {
            if (json[i] == '"' && (i == 0 || json[i - 1] != '\\')) {
                return i
            }
            i++
        }
        return -1
    }

    private fun extractFinishReason(json: String): String? {
        val finishReasonStart = json.indexOf("\"finishReason\"")
        return if (finishReasonStart != -1) {
            val reasonStart = json.indexOf("\"", finishReasonStart + 14) + 1
            val reasonEnd = findClosingQuote(json, reasonStart)
            if (reasonStart > 0 && reasonEnd > reasonStart) {
                json.substring(reasonStart, reasonEnd)
            } else null
        } else null
    }

    data class AudioResponseData(
        val audioData: String,
        val finishReason: String?
    )
}