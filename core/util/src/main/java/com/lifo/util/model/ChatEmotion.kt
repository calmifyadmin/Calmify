package com.lifo.util.model

/**
 * Represents different emotional states in chat with associated properties.
 * UI-specific properties (colors) are in ChatEmotionUiProvider in core/ui.
 */
enum class ChatEmotion(
    val displayName: String,
    val intensity: Float,
    val pulseSpeed: Float = 1.0f
) {
    CALM(displayName = "Calm", intensity = 0.7f, pulseSpeed = 0.8f),
    HAPPY(displayName = "Happy", intensity = 1.0f, pulseSpeed = 1.2f),
    SAD(displayName = "Sad", intensity = 0.5f, pulseSpeed = 0.6f),
    ANXIOUS(displayName = "Anxious", intensity = 0.9f, pulseSpeed = 1.5f),
    NEUTRAL(displayName = "Neutral", intensity = 0.6f, pulseSpeed = 1.0f)
}
