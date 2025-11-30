package com.lifo.app.integration

import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.humanoid.api.HumanoidController
import com.lifo.humanoid.domain.model.Emotion

/**
 * Emotion Bridge - Amica Integration
 *
 * Maps Chat emotions to VRM blend shapes following the Amica emotion system.
 *
 * This adapter translates Gemini Native Voice System emotions into the
 * appropriate VRM facial expressions with the correct intensity levels.
 */
class EmotionBridge(
    private val humanoidController: HumanoidController
) {
    /**
     * Applies a chat emotion to the VRM avatar.
     *
     * Maps GeminiNativeVoiceSystem emotions to Amica-compatible VRM emotions
     * with appropriate intensity levels:
     * - Surprised: 0.5 intensity (subtle reaction)
     * - All others: 1.0 intensity (full expression)
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
}
