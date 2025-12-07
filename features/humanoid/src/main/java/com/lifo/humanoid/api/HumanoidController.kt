package com.lifo.humanoid.api

import com.lifo.humanoid.domain.model.Emotion
import com.lifo.util.speech.SpeechAnimationTarget
import com.lifo.util.speech.SpeechPlaybackEvent
import com.lifo.util.speech.SpeechEmotion
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
 *
 * Synchronized Speech Support:
 * - Implements SpeechAnimationTarget for ultra-synchronized audio-lipsync
 * - Receives real-time audio events to match lip movement to TTS output
 */
interface HumanoidController : SpeechAnimationTarget {

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
     * @deprecated Use synchronized speech via onPlaybackEvent() for accurate sync
     */
    @Deprecated("Use synchronized speech via onPlaybackEvent() for accurate sync")
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
     * This is the HumanoidController-specific property.
     */
    val isSpeaking: StateFlow<Boolean>

    /**
     * Whether a gesture animation is currently playing.
     */
    val isPlayingGesture: StateFlow<Boolean>

    // ==================== Synchronized Speech (from SpeechAnimationTarget) ====================

    /**
     * Whether lip-sync animation is currently active.
     * From SpeechAnimationTarget, maps to isSpeaking.
     */
    override val isAnimating: StateFlow<Boolean>

    /**
     * Current lip-sync progress (0.0-1.0).
     * Provided by LipSyncController.
     */
    override val progress: StateFlow<Float>

    /**
     * Handle a playback event for synchronized audio-lipsync.
     * This is the primary method for ultra-synchronized speech.
     *
     * @param event The playback event from the audio source
     */
    override fun onPlaybackEvent(event: SpeechPlaybackEvent)

    /**
     * Set the emotion for facial expression using SpeechEmotion.
     * Maps to the internal Emotion type.
     */
    override fun setEmotion(emotion: SpeechEmotion, intensity: Float)

    /**
     * Update lip-sync intensity based on real-time audio level.
     * Called frequently during playback for natural lip movement.
     *
     * @param level Audio amplitude (0.0-1.0)
     */
    override fun updateAudioIntensity(level: Float)
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
