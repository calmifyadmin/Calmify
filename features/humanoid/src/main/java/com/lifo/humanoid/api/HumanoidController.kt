package com.lifo.humanoid.api

import com.lifo.humanoid.domain.model.Emotion
import kotlinx.coroutines.flow.StateFlow

/**
 * Public API for controlling the Humanoid avatar from external modules.
 *
 * This interface allows other modules (like features:chat) to control
 * the avatar's emotions, lip-sync, and animations without depending
 * on the internal implementation details of the Humanoid module.
 *
 * Architecture pattern: Dependency Inversion Principle
 * - Chat module can depend on this interface
 * - Humanoid module implements this interface
 * - App module wires them together via DI
 */
interface HumanoidController {

    /**
     * Set the avatar's emotional expression.
     * The emotion will smoothly transition using blend shape interpolation.
     *
     * @param emotion The target emotion
     * @param intensity Intensity of the emotion (0.0 to 1.0)
     */
    fun setEmotion(emotion: Emotion, intensity: Float = 1.0f)

    /**
     * Start lip-sync animation for text-to-speech.
     * Converts text to phonemes and animates the avatar's mouth accordingly.
     *
     * @param text The text being spoken
     * @param durationMs Duration of the speech in milliseconds
     */
    fun speakText(text: String, durationMs: Long)

    /**
     * Stop current lip-sync animation and return mouth to neutral.
     */
    fun stopSpeaking()

    /**
     * Play a gesture animation (e.g., greeting, nodding, thinking).
     *
     * @param gesture The type of gesture to perform
     * @param loop Whether to loop the animation
     */
    fun playGesture(gesture: GestureType, loop: Boolean = false)

    /**
     * Stop current gesture animation and return to idle state.
     */
    fun stopGesture()

    /**
     * Reset the avatar to default neutral state.
     * Clears all emotions, stops animations, returns to idle.
     */
    fun resetToNeutral()

    /**
     * Trigger a manual blink (in addition to automatic blinking).
     */
    suspend fun triggerBlink()

    // ==================== State Observation ====================

    /**
     * Current emotion of the avatar.
     */
    val currentEmotion: StateFlow<Emotion>

    /**
     * Whether the avatar is currently speaking (lip-sync active).
     */
    val isSpeaking: StateFlow<Boolean>

    /**
     * Whether a gesture animation is currently playing.
     */
    val isPlayingGesture: StateFlow<Boolean>
}

/**
 * Available gesture animations that can be triggered.
 * Corresponds to VRMA animations loaded in the Humanoid module.
 */
enum class GestureType {
    /**
     * Greeting gesture (wave, bow, etc.)
     */
    GREETING,

    /**
     * Dance animation
     */
    DANCE,

    /**
     * Victory pose
     */
    VICTORY,

    /**
     * Thinking pose (hand on chin, etc.)
     */
    THINKING,

    /**
     * Nod (affirmative)
     */
    NOD,

    /**
     * Shake head (negative)
     */
    SHAKE_HEAD,

    /**
     * Peace sign
     */
    PEACE_SIGN,

    /**
     * Shooting gesture
     */
    SHOOT,

    /**
     * Squat animation
     */
    SQUAT,

    /**
     * Spin animation
     */
    SPIN,

    /**
     * Show full body
     */
    SHOW_FULL_BODY
}
