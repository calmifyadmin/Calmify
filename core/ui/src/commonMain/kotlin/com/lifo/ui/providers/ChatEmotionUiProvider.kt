package com.lifo.ui.providers

import androidx.compose.ui.graphics.Color
import com.lifo.util.model.ChatEmotion

object ChatEmotionUiProvider {

    data class EmotionColorSet(
        val primary: Color,
        val secondary: Color,
        val tertiary: Color
    ) {
        fun toGradientColors(): List<Color> = listOf(primary, secondary, tertiary)

        fun toParticleColors(): List<Color> = listOf(
            primary.copy(alpha = 0.8f),
            secondary.copy(alpha = 0.6f),
            tertiary.copy(alpha = 0.4f)
        )
    }

    fun getColors(emotion: ChatEmotion): EmotionColorSet = when (emotion) {
        ChatEmotion.CALM -> EmotionColorSet(
            primary = Color(0xFF4A90E2),
            secondary = Color(0xFF7B68EE),
            tertiary = Color(0xFF9C88FF)
        )
        ChatEmotion.HAPPY -> EmotionColorSet(
            primary = Color(0xFFFFD700),
            secondary = Color(0xFFFF8C00),
            tertiary = Color(0xFFFFB347)
        )
        ChatEmotion.SAD -> EmotionColorSet(
            primary = Color(0xFF708090),
            secondary = Color(0xFFB0C4DE),
            tertiary = Color(0xFF87CEEB)
        )
        ChatEmotion.ANXIOUS -> EmotionColorSet(
            primary = Color(0xFFFF6B6B),
            secondary = Color(0xFFFF8E53),
            tertiary = Color(0xFFFF7F7F)
        )
        ChatEmotion.NEUTRAL -> EmotionColorSet(
            primary = Color(0xFFF8F9FA),
            secondary = Color(0xFFE3F2FD),
            tertiary = Color(0xFFBBDEFB)
        )
    }

    fun getColorWithAlpha(emotion: ChatEmotion, alpha: Float): Color =
        getColors(emotion).primary.copy(alpha = alpha * emotion.intensity)

    fun getEmotionColors(emotionName: String): List<Color> =
        getColors(
            ChatEmotion.entries.find { it.name == emotionName.uppercase() }
                ?: ChatEmotion.NEUTRAL
        ).toGradientColors()
}
