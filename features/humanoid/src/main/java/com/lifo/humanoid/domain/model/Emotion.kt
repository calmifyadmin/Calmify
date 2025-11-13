package com.lifo.humanoid.domain.model

/**
 * Represents the emotional state of the humanoid avatar.
 * Each emotion can have an intensity value for smooth blending.
 */
sealed class Emotion {
    abstract val intensity: Float

    data object Neutral : Emotion() {
        override val intensity: Float = 1.0f
    }

    data class Happy(override val intensity: Float = 1.0f) : Emotion()
    data class Sad(override val intensity: Float = 1.0f) : Emotion()
    data class Angry(override val intensity: Float = 1.0f) : Emotion()
    data class Surprised(override val intensity: Float = 1.0f) : Emotion()
    data class Thinking(override val intensity: Float = 1.0f) : Emotion()
    data class Excited(override val intensity: Float = 1.0f) : Emotion()
    data class Calm(override val intensity: Float = 1.0f) : Emotion()
    data class Confused(override val intensity: Float = 1.0f) : Emotion()
    data class Worried(override val intensity: Float = 1.0f) : Emotion()
    data class Disappointed(override val intensity: Float = 1.0f) : Emotion()

    /**
     * Multi-emotion blend for complex expressions
     * Example: slightly happy while thinking
     */
    data class Blend(
        val emotions: Map<Emotion, Float>,
        override val intensity: Float = 1.0f
    ) : Emotion()

    /**
     * Get the name of this emotion for logging/debugging
     */
    fun getName(): String = when (this) {
        is Neutral -> "Neutral"
        is Happy -> "Happy"
        is Sad -> "Sad"
        is Angry -> "Angry"
        is Surprised -> "Surprised"
        is Thinking -> "Thinking"
        is Excited -> "Excited"
        is Calm -> "Calm"
        is Confused -> "Confused"
        is Worried -> "Worried"
        is Disappointed -> "Disappointed"
        is Blend -> "Blend(${emotions.keys.joinToString { it.getName() }})"
    }
}
