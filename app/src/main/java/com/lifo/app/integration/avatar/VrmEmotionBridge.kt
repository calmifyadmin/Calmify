package com.lifo.app.integration.avatar

import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.humanoid.api.HumanoidController
import com.lifo.humanoid.domain.model.Emotion

/**
 * VRM Emotion Bridge - Unified Integration
 *
 * Centralized emotion mapping for both Chat TTS and Gemini Live API.
 * Maps various emotion sources to VRM blend shapes with appropriate intensities.
 *
 * Supported inputs:
 * - GeminiNativeVoiceSystem.Emotion (Chat TTS)
 * - AIEmotion (Gemini Live API)
 * - Text-based emotion detection (both systems)
 */
class VrmEmotionBridge(
    private val humanoidController: HumanoidController
) {
    /**
     * Apply emotion from Chat TTS system.
     *
     * Maps GeminiNativeVoiceSystem emotions to VRM expressions
     * with appropriate intensity levels.
     */
    fun applyChatEmotion(chatEmotion: GeminiNativeVoiceSystem.Emotion) {
        val vrmEmotion = when (chatEmotion) {
            GeminiNativeVoiceSystem.Emotion.NEUTRAL -> Emotion.Neutral
            GeminiNativeVoiceSystem.Emotion.HAPPY -> Emotion.Happy(1.0f)
            GeminiNativeVoiceSystem.Emotion.SAD -> Emotion.Sad(1.0f)
            GeminiNativeVoiceSystem.Emotion.EXCITED -> Emotion.Excited(1.0f)
            GeminiNativeVoiceSystem.Emotion.THOUGHTFUL -> Emotion.Thinking(1.0f)
            GeminiNativeVoiceSystem.Emotion.EMPATHETIC -> Emotion.Calm(0.8f)
            GeminiNativeVoiceSystem.Emotion.CURIOUS -> Emotion.Surprised(0.5f)
        }

        humanoidController.setEmotion(vrmEmotion)
    }

    /**
     * Apply emotion from Gemini Live API.
     *
     * Maps AIEmotion states to VRM expressions.
     * Speaking state uses Neutral to let lip-sync handle the mouth.
     */
    fun applyLiveEmotion(aiEmotion: AIEmotion) {
        val vrmEmotion = when (aiEmotion) {
            AIEmotion.Neutral -> Emotion.Neutral
            AIEmotion.Happy -> Emotion.Happy(1.0f)
            AIEmotion.Thinking -> Emotion.Thinking(0.8f)
            AIEmotion.Speaking -> Emotion.Neutral // Lip-sync handles mouth during speaking
        }

        humanoidController.setEmotion(vrmEmotion)
    }

    /**
     * Detect and apply emotion from AI transcript text.
     *
     * Analyzes AI response text to determine appropriate emotional expression.
     * Works for both Chat TTS and Live API transcripts.
     *
     * @param text The AI transcript text to analyze
     * @return The detected Emotion with appropriate intensity
     */
    fun detectAndApplyEmotionFromText(text: String) {
        val detectedEmotion = detectEmotionFromText(text)
        humanoidController.setEmotion(detectedEmotion)
    }

    /**
     * Detect emotion from text without applying it.
     * Useful for preview or conditional application.
     */
    fun detectEmotionFromText(text: String): Emotion {
        val lowerText = text.lowercase()

        return when {
            // Happy/Positive emotions
            lowerText.containsAny(
                "felice", "contento", "contenta", "bene", "ottimo", "ottima",
                "fantastico", "fantastica", "meraviglioso", "meravigliosa",
                "perfetto", "perfetta", "bellissimo", "bellissima",
                "happy", "great", "wonderful", "amazing", "excellent"
            ) -> Emotion.Happy(0.8f)

            // Sad/Empathetic emotions
            lowerText.containsAny(
                "triste", "dispiaciuto", "dispiaciuta", "mi dispiace",
                "purtroppo", "sfortunatamente", "difficile",
                "sorry", "sad", "unfortunately", "difficult"
            ) -> Emotion.Sad(0.7f)

            // Excited/Curious emotions
            lowerText.containsAny(
                "interessante", "curioso", "curiosa", "wow",
                "incredibile", "fantastico", "eccitante",
                "interesting", "curious", "exciting", "amazing"
            ) -> Emotion.Excited(0.6f)

            // Surprised emotions
            lowerText.containsAny(
                "davvero", "serio", "seriamente", "non ci credo",
                "incredibile", "assurdo", "pazzesco",
                "really", "seriously", "unbelievable", "no way"
            ) -> Emotion.Surprised(0.5f)

            // Thinking/Processing emotions
            lowerText.containsAny(
                "penso", "credo", "forse", "probabilmente",
                "vediamo", "consideriamo", "riflettiamo",
                "think", "maybe", "perhaps", "consider", "let me"
            ) -> Emotion.Thinking(0.6f)

            // Calm/Understanding emotions
            lowerText.containsAny(
                "capisco", "comprendo", "certo", "naturalmente",
                "certamente", "ovviamente", "chiaro",
                "understand", "of course", "certainly", "clearly"
            ) -> Emotion.Calm(0.6f)

            // Worried/Concerned emotions
            lowerText.containsAny(
                "preoccupato", "preoccupata", "ansioso", "ansiosa",
                "attenzione", "attento", "attenta", "stai attento",
                "worried", "anxious", "careful", "be careful"
            ) -> Emotion.Worried(0.5f)

            // Default to neutral
            else -> Emotion.Neutral
        }
    }

    /**
     * Extension function to check if string contains any of the keywords.
     */
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
