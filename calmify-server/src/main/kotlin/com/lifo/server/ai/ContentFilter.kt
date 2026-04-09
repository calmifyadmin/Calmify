package com.lifo.server.ai

import org.slf4j.LoggerFactory

/**
 * Input/output content filtering for AI requests.
 *
 * Input: blocks prompt injection attempts and overly long messages.
 * Output: handles blocked responses gracefully, strips PII leaks.
 */
class ContentFilter {
    private val logger = LoggerFactory.getLogger(ContentFilter::class.java)

    private val maxInputLength = 10_000

    private val injectionPatterns = listOf(
        "ignore previous instructions",
        "ignore all previous",
        "ignore your instructions",
        "you are now",
        "new instructions:",
        "override system prompt",
        "system prompt",
        "jailbreak",
        "DAN mode",
        "developer mode",
    )

    /**
     * Validates user input before sending to Gemini.
     * Throws [ContentPolicyException] if input violates policies.
     */
    fun validateInput(text: String) {
        if (text.length > maxInputLength) {
            throw ContentPolicyException("Il messaggio e' troppo lungo (max $maxInputLength caratteri)")
        }

        if (text.isBlank()) {
            throw ContentPolicyException("Il messaggio non puo' essere vuoto")
        }

        val lower = text.lowercase()
        for (pattern in injectionPatterns) {
            if (pattern in lower) {
                logger.warn("Prompt injection attempt detected: '$pattern'")
                throw ContentPolicyException("Richiesta non valida")
            }
        }
    }

    /**
     * Processes Gemini output — handles blocked responses and cleans up text.
     */
    fun processOutput(result: GeminiResult): String {
        if (result.blocked) {
            logger.warn("Gemini blocked response. Reason: ${result.blockReason}")
            return "Mi dispiace, non posso rispondere a questa domanda. Posso aiutarti con qualcos'altro?"
        }

        if (result.text.isBlank()) {
            return "Non ho una risposta per questo. Puoi riformulare la domanda?"
        }

        return result.text
    }
}

class ContentPolicyException(message: String) : RuntimeException(message)
