package com.lifo.humanoid.domain.model

/**
 * Represents the complete state of the humanoid avatar.
 * This is the main state object that drives all avatar rendering and animation.
 */
data class AvatarState(
    val emotion: Emotion = Emotion.Neutral,
    val currentViseme: Viseme = Viseme.SILENCE,
    val isSpeaking: Boolean = false,
    val isListening: Boolean = false,
    val visionEnabled: Boolean = false,
    val blendShapeWeights: Map<String, Float> = emptyMap(),
    val gazeDirection: GazeDirection = GazeDirection.Center,
    val idleAnimation: IdleAnimation = IdleAnimation.Breathing
) {
    /**
     * Represents where the avatar is looking
     */
    enum class GazeDirection {
        Center,
        Left,
        Right,
        Up,
        Down,
        AtCamera // Direct eye contact with user
    }

    /**
     * Idle animations to make the avatar feel alive
     */
    enum class IdleAnimation {
        Breathing,
        Blinking,
        HeadTilt,
        None
    }

    companion object {
        /**
         * Default resting state for the avatar
         */
        val Default = AvatarState()

        /**
         * Speaking state with happy emotion
         */
        fun speaking(emotion: Emotion = Emotion.Happy()) = AvatarState(
            emotion = emotion,
            isSpeaking = true,
            isListening = false
        )

        /**
         * Listening state with attentive expression
         */
        fun listening() = AvatarState(
            emotion = Emotion.Calm(),
            isSpeaking = false,
            isListening = true,
            gazeDirection = GazeDirection.AtCamera
        )

        /**
         * Thinking state
         */
        fun thinking() = AvatarState(
            emotion = Emotion.Thinking(),
            isSpeaking = false,
            isListening = false,
            gazeDirection = GazeDirection.Up
        )
    }
}
