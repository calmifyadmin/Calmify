package com.lifo.util.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

/**
 * Bridge interface for synchronized speech between TTS audio and lip-sync animation.
 *
 * This interface enables ultra-synchronized playback where:
 * - Audio TTS and lip-sync animation start simultaneously
 * - Lip-sync duration adapts to real audio playback duration
 * - Both systems stop together when interrupted
 *
 * Architecture:
 * - Chat module implements SpeechAudioSource (provides audio events)
 * - Humanoid module implements SpeechAnimationTarget (consumes events for lip-sync)
 * - App module wires them together via DI
 *
 * This follows Dependency Inversion Principle to maintain module separation.
 */

/**
 * Playback state events for synchronized speech
 */
sealed class SpeechPlaybackEvent {
    /**
     * Playback is about to start. Prepare lip-sync with estimated duration.
     * @param text The text being spoken
     * @param estimatedDurationMs Estimated duration from TTS (may not be accurate)
     * @param messageId Unique identifier for this speech instance
     */
    data class Preparing(
        val text: String,
        val estimatedDurationMs: Long,
        val messageId: String
    ) : SpeechPlaybackEvent()

    /**
     * Audio playback has started. Begin lip-sync animation NOW.
     * @param messageId Unique identifier for this speech instance
     * @param timestamp System timestamp when playback started
     */
    data class Started(
        val messageId: String,
        val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : SpeechPlaybackEvent()

    /**
     * Audio is actively playing. Use for real-time sync adjustments.
     * @param messageId Unique identifier for this speech instance
     * @param progressMs Current playback position in milliseconds
     * @param totalDurationMs Total duration (updated as more data arrives in streaming)
     * @param audioLevel Current audio amplitude (0.0-1.0) for intensity matching
     */
    data class Playing(
        val messageId: String,
        val progressMs: Long,
        val totalDurationMs: Long,
        val audioLevel: Float = 0f,
        val visemeWeights: Map<String, Float> = emptyMap()
    ) : SpeechPlaybackEvent()

    /**
     * Audio buffer is draining (streaming complete, playing remaining buffer).
     * Lip-sync should prepare to end.
     * @param messageId Unique identifier for this speech instance
     * @param remainingMs Remaining playback time in milliseconds
     */
    data class Finishing(
        val messageId: String,
        val remainingMs: Long
    ) : SpeechPlaybackEvent()

    /**
     * Audio playback has ended. Stop lip-sync animation.
     * @param messageId Unique identifier for this speech instance
     * @param actualDurationMs The actual total duration of playback
     */
    data class Ended(
        val messageId: String,
        val actualDurationMs: Long
    ) : SpeechPlaybackEvent()

    /**
     * Audio was interrupted (barge-in, stop command). Stop lip-sync immediately.
     * @param messageId Unique identifier for this speech instance
     * @param reason Reason for interruption
     */
    data class Interrupted(
        val messageId: String,
        val reason: InterruptionReason
    ) : SpeechPlaybackEvent()

    /**
     * No active playback (idle state).
     */
    object Idle : SpeechPlaybackEvent()
}

/**
 * Reasons for speech interruption
 */
enum class InterruptionReason {
    USER_BARGE_IN,      // User started speaking
    MANUAL_STOP,        // Stop button pressed
    NEW_MESSAGE,        // New message started
    ERROR,              // Playback error
    SESSION_ENDED       // Session disconnected
}

/**
 * Emotion detected in the speech for expression synchronization
 */
enum class SpeechEmotion {
    NEUTRAL,
    HAPPY,
    SAD,
    EXCITED,
    THOUGHTFUL,
    EMPATHETIC,
    CURIOUS
}

/**
 * Complete speech request with all parameters for synchronized playback
 */
data class SpeechRequest(
    val text: String,
    val messageId: String,
    val emotion: SpeechEmotion = SpeechEmotion.NEUTRAL,
    val estimatedDurationMs: Long = 0L
)

/**
 * Source of speech audio events (implemented by Chat module)
 */
interface SpeechAudioSource {
    /**
     * Flow of playback events for synchronization
     */
    val playbackEvents: Flow<SpeechPlaybackEvent>

    /**
     * Current playback state
     */
    val isSpeaking: StateFlow<Boolean>

    /**
     * Real-time audio level (0.0-1.0) for intensity-based lip-sync
     */
    val audioLevel: StateFlow<Float>

    /**
     * Start speaking the given text
     */
    fun speak(request: SpeechRequest)

    /**
     * Stop current speech immediately
     */
    fun stop()
}

/**
 * Target for speech animation (implemented by Humanoid module)
 */
interface SpeechAnimationTarget {
    /**
     * Whether lip-sync animation is currently active
     */
    val isAnimating: StateFlow<Boolean>

    /**
     * Current lip-sync progress (0.0-1.0)
     */
    val progress: StateFlow<Float>

    /**
     * Handle a playback event for synchronization.
     * Called by the bridge controller when audio events occur.
     */
    fun onPlaybackEvent(event: SpeechPlaybackEvent)

    /**
     * Set the emotion for facial expression
     */
    fun setEmotion(emotion: SpeechEmotion, intensity: Float = 1.0f)

    /**
     * Update lip-sync intensity based on real-time audio level.
     * Called frequently during playback for natural lip movement.
     */
    fun updateAudioIntensity(level: Float)
}

/**
 * Controller that bridges audio source and animation target.
 * Manages the synchronization lifecycle.
 */
interface SynchronizedSpeechController {
    /**
     * Connect an audio source to this controller
     */
    fun attachAudioSource(source: SpeechAudioSource)

    /**
     * Connect an animation target to this controller
     */
    fun attachAnimationTarget(target: SpeechAnimationTarget)

    /**
     * Detach the current audio source
     */
    fun detachAudioSource()

    /**
     * Detach the current animation target
     */
    fun detachAnimationTarget()

    /**
     * Start synchronized speech with the given request.
     * Audio and lip-sync will start simultaneously.
     */
    fun speakSynchronized(request: SpeechRequest)

    /**
     * Stop both audio and animation immediately
     */
    fun stopSynchronized()

    /**
     * Whether synchronized speech is currently active
     */
    val isSpeaking: StateFlow<Boolean>

    /**
     * Release all resources
     */
    fun release()
}
