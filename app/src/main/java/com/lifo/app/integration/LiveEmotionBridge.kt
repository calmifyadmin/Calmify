package com.lifo.app.integration

import com.lifo.chat.domain.model.AIEmotion
import com.lifo.humanoid.api.HumanoidController
import com.lifo.humanoid.domain.model.Emotion

/**
 * Live Emotion Bridge - Gemini Live API Integration
 *
 * Maps Gemini Live AI emotions to VRM blend shapes for the avatar.
 * This bridge handles real-time emotion synchronization during live
 * voice conversations with the AI.
 *
 * Key features:
 * - Real-time AIEmotion -> Emotion mapping
 * - Text-based emotion detection for enhanced expressions
 * - Smooth transitions between emotional states
 */
class LiveEmotionBridge(
    private val humanoidController: HumanoidController
) {
    /**
     * Applies a Gemini Live AI emotion to the VRM avatar.
     *
     * Maps AIEmotion (from Gemini Live) to VRM Emotion with appropriate
     * intensity levels for natural expressions:
     * - Speaking: Neutral (lip-sync handles mouth)
     * - Thinking: Full intensity thinking expression
     * - Happy: Full intensity happy expression
     * - Neutral: Reset to neutral state
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
     * Detects emotion from AI transcript text.
     *
     * Analyzes the AI's response text to determine the appropriate
     * emotional expression. This enhances the avatar's reactions
     * beyond simple turn-based states.
     *
     * Keywords analyzed:
     * - Happy/Positive: felice, contento, bene, ottimo, fantastico, etc.
     * - Sad/Empathetic: triste, dispiaciuto, mi dispiace, capisco, etc.
     * - Excited/Interested: interessante, curioso, wow, incredibile, etc.
     * - Calm/Understanding: capisco, comprendo, certo, naturalmente, etc.
     * - Surprised: davvero, serio, non ci credo, etc.
     *
     * @param text The AI transcript text to analyze
     * @return The detected Emotion with appropriate intensity
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
     * Applies emotion detected from text to the avatar.
     *
     * Combines text analysis with current AI state for
     * the most appropriate emotional expression.
     *
     * @param text The AI transcript text
     */
    fun applyEmotionFromText(text: String) {
        val detectedEmotion = detectEmotionFromText(text)
        humanoidController.setEmotion(detectedEmotion)
    }

    /**
     * Extension function to check if string contains any of the keywords.
     */
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
