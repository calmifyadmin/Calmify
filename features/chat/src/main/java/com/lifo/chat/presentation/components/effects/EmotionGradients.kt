package com.lifo.chat.presentation.components.effects

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lifo.chat.domain.model.AIEmotion
import kotlin.math.max

/**
 * Emotion-based gradient system for liquid globe visualization
 * Maps AI emotional states to beautiful gradient color palettes
 */
object EmotionGradients {
    
    // Emotion color palettes
    private val emotionPalettes = mapOf(
        AIEmotion.Neutral to listOf(
            Color(0xFF6366F1), // Indigo
            Color(0xFF3B82F6), // Blue
            Color(0xFF06B6D4)  // Cyan
        ),
        AIEmotion.Happy to listOf(
            Color(0xFFFBBF24), // Yellow
            Color(0xFFF59E0B), // Amber
            Color(0xFFEF4444)  // Orange-Red
        ),
        AIEmotion.Thinking to listOf(
            Color(0xFF8B5CF6), // Violet
            Color(0xFFA855F7), // Purple
            Color(0xFFEC4899)  // Pink
        ),
        AIEmotion.Speaking to listOf(
            Color(0xFF10B981), // Emerald
            Color(0xFF06B6D4), // Cyan
            Color(0xFF3B82F6)  // Blue
        )
    )
    
    /**
     * Get radial gradient brush for the given emotion
     */
    fun getEmotionGradient(
        emotion: AIEmotion,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        radius: Float = 1.0f
    ): Brush {
        val colors = emotionPalettes[emotion] ?: emotionPalettes[AIEmotion.Neutral]!!
        return Brush.radialGradient(
            colors = colors,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            radius = radius
        )
    }
    
    /**
     * Get linear gradient brush for the given emotion
     */
    fun getEmotionLinearGradient(
        emotion: AIEmotion,
        startX: Float = 0.0f,
        startY: Float = 0.0f,
        endX: Float = 1.0f,
        endY: Float = 1.0f
    ): Brush {
        val colors = emotionPalettes[emotion] ?: emotionPalettes[AIEmotion.Neutral]!!
        return Brush.linearGradient(
            colors = colors,
            start = androidx.compose.ui.geometry.Offset(startX, startY),
            end = androidx.compose.ui.geometry.Offset(endX, endY)
        )
    }
    
    /**
     * Get primary color for the emotion
     */
    fun getPrimaryColor(emotion: AIEmotion): Color {
        return emotionPalettes[emotion]?.first() ?: emotionPalettes[AIEmotion.Neutral]!!.first()
    }
    
    /**
     * Get secondary color for the emotion
     */
    fun getSecondaryColor(emotion: AIEmotion): Color {
        val palette = emotionPalettes[emotion] ?: emotionPalettes[AIEmotion.Neutral]!!
        return palette.getOrNull(1) ?: palette.first()
    }
    
    /**
     * Get accent color for the emotion
     */
    fun getAccentColor(emotion: AIEmotion): Color {
        val palette = emotionPalettes[emotion] ?: emotionPalettes[AIEmotion.Neutral]!!
        return palette.lastOrNull() ?: palette.first()
    }
    
    /**
     * Get glow color for outer effects
     */
    fun getGlowColor(emotion: AIEmotion, alpha: Float = 0.3f): Color {
        return getPrimaryColor(emotion).copy(alpha = max(0f, alpha))
    }
    
    /**
     * Get animated gradient that pulses between colors
     */
    fun getAnimatedGradient(
        emotion: AIEmotion,
        animationProgress: Float, // 0.0 to 1.0
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        radius: Float = 1.0f
    ): Brush {
        val palette = emotionPalettes[emotion] ?: emotionPalettes[AIEmotion.Neutral]!!
        
        // Interpolate between colors based on animation progress
        val colorCount = palette.size
        val scaledProgress = animationProgress * (colorCount - 1)
        val colorIndex = scaledProgress.toInt()
        val colorProgress = scaledProgress - colorIndex
        
        val fromColor = palette.getOrNull(colorIndex) ?: palette.first()
        val toColor = palette.getOrNull(colorIndex + 1) ?: palette.last()
        
        val interpolatedColor = Color(
            red = fromColor.red + (toColor.red - fromColor.red) * colorProgress,
            green = fromColor.green + (toColor.green - fromColor.green) * colorProgress,
            blue = fromColor.blue + (toColor.blue - fromColor.blue) * colorProgress,
            alpha = fromColor.alpha + (toColor.alpha - fromColor.alpha) * colorProgress
        )
        
        return Brush.radialGradient(
            colors = listOf(interpolatedColor, fromColor, toColor),
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            radius = radius
        )
    }
}