package com.lifo.util.model

import androidx.compose.ui.graphics.Color

/**
 * Represents different emotional states in chat with associated visual properties.
 * Used for aura effects, particle systems, and visual feedback.
 */
enum class ChatEmotion(
    val displayName: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val intensity: Float,
    val pulseSpeed: Float = 1.0f
) {
    CALM(
        displayName = "Calm", 
        primaryColor = Color(0xFF4A90E2), 
        secondaryColor = Color(0xFF7B68EE), 
        tertiaryColor = Color(0xFF9C88FF),
        intensity = 0.7f,
        pulseSpeed = 0.8f
    ),
    
    HAPPY(
        displayName = "Happy", 
        primaryColor = Color(0xFFFFD700), 
        secondaryColor = Color(0xFFFF8C00), 
        tertiaryColor = Color(0xFFFFB347),
        intensity = 1.0f,
        pulseSpeed = 1.2f
    ),
    
    SAD(
        displayName = "Sad", 
        primaryColor = Color(0xFF708090), 
        secondaryColor = Color(0xFFB0C4DE), 
        tertiaryColor = Color(0xFF87CEEB),
        intensity = 0.5f,
        pulseSpeed = 0.6f
    ),
    
    ANXIOUS(
        displayName = "Anxious", 
        primaryColor = Color(0xFFFF6B6B), 
        secondaryColor = Color(0xFFFF8E53), 
        tertiaryColor = Color(0xFFFF7F7F),
        intensity = 0.9f,
        pulseSpeed = 1.5f
    ),
    
    NEUTRAL(
        displayName = "Neutral", 
        primaryColor = Color(0xFFF8F9FA), 
        secondaryColor = Color(0xFFE3F2FD), 
        tertiaryColor = Color(0xFFBBDEFB),
        intensity = 0.6f,
        pulseSpeed = 1.0f
    );
    
    /**
     * Get gradient colors for aura effects
     */
    fun getGradientColors(): List<Color> = listOf(primaryColor, secondaryColor, tertiaryColor)
    
    /**
     * Get color with applied alpha for layering effects
     */
    fun getColorWithAlpha(alpha: Float): Color = primaryColor.copy(alpha = alpha * intensity)
    
    /**
     * Get particle color variations for dynamic effects
     */
    fun getParticleColors(): List<Color> = listOf(
        primaryColor.copy(alpha = 0.8f),
        secondaryColor.copy(alpha = 0.6f),
        tertiaryColor.copy(alpha = 0.4f)
    )
}

/**
 * Utility object for emotion color palettes
 */
object EmotionColors {
    val CALM = listOf(Color(0xFF4A90E2), Color(0xFF7B68EE), Color(0xFF9C88FF))
    val HAPPY = listOf(Color(0xFFFFD700), Color(0xFFFF8C00), Color(0xFFFFB347))
    val SAD = listOf(Color(0xFF708090), Color(0xFFB0C4DE), Color(0xFF87CEEB))
    val ANXIOUS = listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53), Color(0xFFFF7F7F))
    val NEUTRAL = listOf(Color(0xFFF8F9FA), Color(0xFFE3F2FD), Color(0xFFBBDEFB))
    
    /**
     * Get emotion colors by name (for legacy compatibility)
     */
    fun getEmotionColors(emotionName: String): List<Color> {
        return when (emotionName.uppercase()) {
            "CALM" -> CALM
            "HAPPY" -> HAPPY
            "SAD" -> SAD
            "ANXIOUS" -> ANXIOUS
            else -> NEUTRAL
        }
    }
}