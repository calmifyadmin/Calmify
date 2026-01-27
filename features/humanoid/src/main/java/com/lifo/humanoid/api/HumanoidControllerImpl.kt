package com.lifo.humanoid.api

import android.util.Log
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.domain.model.Emotion
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.presentation.HumanoidViewModel
import com.lifo.util.speech.SpeechEmotion
import com.lifo.util.speech.SpeechPlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Implementation of HumanoidController that bridges to HumanoidViewModel.
 *
 * This adapter allows external modules to control the Humanoid avatar
 * via the clean HumanoidController interface without direct ViewModel coupling.
 *
 * Implements synchronized speech for ultra-accurate audio-lipsync:
 * - Receives audio playback events
 * - Synchronizes lip-sync animation with actual audio timing
 * - Modulates lip movement intensity based on audio amplitude
 */
class HumanoidControllerImpl(
    private val viewModel: HumanoidViewModel,
    private val lipSyncController: LipSyncController
) : HumanoidController {

    companion object {
        private const val TAG = "HumanoidControllerImpl"
    }

    // Coroutine scope for StateFlow conversions and async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Progress state exposed from LipSyncController
    private val _progress = MutableStateFlow(0f)

    @Deprecated("Use synchronized speech via onPlaybackEvent() for accurate sync")
    override fun setEmotion(emotion: Emotion, intensity: Float) {
        Log.d(TAG, "Setting emotion: ${emotion.getName()} with intensity $intensity")
        viewModel.setEmotion(emotion)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use synchronized speech via onPlaybackEvent() for accurate sync")
    override fun speakText(text: String, durationMs: Long) {
        Log.d(TAG, "Speaking text: '${text.take(50)}...' for ${durationMs}ms")
        viewModel.speakText(text, durationMs)
    }

    override fun stopSpeaking() {
        Log.d(TAG, "Stopping speech")
        viewModel.stopSpeaking()
        lipSyncController.stopSynchronized()
    }

    override fun playGesture(gesture: GestureType, loop: Boolean) {
        Log.d(TAG, "Playing gesture: $gesture (loop=$loop)")

        val animationAsset = when (gesture) {
            // Greetings
            GestureType.GREETING -> VrmaAnimationLoader.AnimationAsset.GREETING
            GestureType.HELLO -> VrmaAnimationLoader.AnimationAsset.HELLO

            // Agreement/Disagreement
            GestureType.YES -> VrmaAnimationLoader.AnimationAsset.YES_WITH_HEAD
            GestureType.NO -> VrmaAnimationLoader.AnimationAsset.NO_WITH_HEAD
            GestureType.I_AGREE -> VrmaAnimationLoader.AnimationAsset.I_AGREE
            GestureType.I_DONT_THINK_SO -> VrmaAnimationLoader.AnimationAsset.I_DONT_THINK_SO
            GestureType.I_DONT_KNOW -> VrmaAnimationLoader.AnimationAsset.I_DONT_KNOW

            // Emotions
            GestureType.ANGRY -> VrmaAnimationLoader.AnimationAsset.ANGRY
            GestureType.SAD -> VrmaAnimationLoader.AnimationAsset.SAD
            GestureType.HAPPY -> VrmaAnimationLoader.AnimationAsset.DANCING_HAPPY
            GestureType.YOU_ARE_CRAZY -> VrmaAnimationLoader.AnimationAsset.YOU_ARE_CRAZY

            // Actions
            GestureType.DANCE -> VrmaAnimationLoader.AnimationAsset.DANCE
            GestureType.PEACE_SIGN -> VrmaAnimationLoader.AnimationAsset.PEACE_SIGN
            GestureType.SHOOT -> VrmaAnimationLoader.AnimationAsset.SHOOT
            GestureType.POINTING -> VrmaAnimationLoader.AnimationAsset.POINTING_THING
            GestureType.SHOW_FULL_BODY -> VrmaAnimationLoader.AnimationAsset.SHOW_FULL_BODY
        }

        viewModel.playAnimation(animationAsset)
    }

    override fun playAnimationByName(animationName: String): Boolean {
        Log.d(TAG, "🎭 Playing animation by name: $animationName")

        val gesture = GestureType.fromAnimationName(animationName)
        return if (gesture != null) {
            playGesture(gesture, loop = false)
            Log.d(TAG, "🎭 Animation started: $gesture")
            true
        } else {
            Log.w(TAG, "⚠️ Unknown animation name: $animationName")
            false
        }
    }

    override fun stopGesture() {
        Log.d(TAG, "Stopping gesture, returning to idle")
        viewModel.stopAnimation()
    }

    override fun resetToNeutral() {
        Log.d(TAG, "Resetting avatar to neutral state")
        viewModel.resetAvatar()
        lipSyncController.stopSynchronized()
    }

    override suspend fun triggerBlink() {
        viewModel.triggerBlink()
    }

    // ==================== State Observation ====================

    override val currentEmotion: StateFlow<Emotion> =
        viewModel.avatarState
            .map { it.emotion }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = Emotion.Neutral
            )

    override val isSpeaking: StateFlow<Boolean> =
        lipSyncController.isSpeaking

    override val isAnimating: StateFlow<Boolean> =
        lipSyncController.isSpeaking

    override val isPlayingGesture: StateFlow<Boolean> =
        viewModel.uiState
            .map { it.isPlayingAnimation }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )

    // ==================== Synchronized Speech (SpeechAnimationTarget) ====================

    override val progress: StateFlow<Float> =
        lipSyncController.progress

    override fun onPlaybackEvent(event: SpeechPlaybackEvent) {
        Log.d(TAG, "🎬 Received playback event: ${event::class.simpleName}")

        when (event) {
            is SpeechPlaybackEvent.Preparing -> {
                // Prepare lip-sync with text and estimated duration
                Log.d(TAG, "📝 Preparing lip-sync: '${event.text.take(30)}...' (${event.estimatedDurationMs}ms)")
                lipSyncController.prepareSynchronized(
                    text = event.text,
                    estimatedDurationMs = event.estimatedDurationMs,
                    messageId = event.messageId,
                    scope = scope
                )
            }

            is SpeechPlaybackEvent.Started -> {
                // Audio started - begin lip-sync NOW!
                Log.d(TAG, "▶️ Audio started - starting synchronized lip-sync")
                lipSyncController.startSynchronized(
                    actualDurationMs = 0, // Will use estimated duration
                    scope = scope
                )
            }

            is SpeechPlaybackEvent.Playing -> {
                // Update progress and intensity
                lipSyncController.updateSyncProgress(
                    progressMs = event.progressMs,
                    totalDurationMs = event.totalDurationMs
                )
                lipSyncController.updateAudioIntensity(event.audioLevel)
            }

            is SpeechPlaybackEvent.Finishing -> {
                // Prepare to end (audio buffer draining)
                Log.d(TAG, "⏳ Audio finishing, remaining: ${event.remainingMs}ms")
                // Let lip-sync continue naturally
            }

            is SpeechPlaybackEvent.Ended -> {
                // Audio ended - stop lip-sync
                Log.d(TAG, "✅ Audio ended (${event.actualDurationMs}ms) - stopping lip-sync")
                lipSyncController.stopSynchronized()
            }

            is SpeechPlaybackEvent.Interrupted -> {
                // Interrupted - stop immediately
                Log.d(TAG, "⚠️ Audio interrupted (${event.reason}) - stopping lip-sync immediately")
                lipSyncController.stopSynchronized()
            }

            is SpeechPlaybackEvent.Idle -> {
                // Idle state - ensure lip-sync is stopped
                if (lipSyncController.isActive()) {
                    lipSyncController.stopSynchronized()
                }
            }
        }
    }

    override fun setEmotion(emotion: SpeechEmotion, intensity: Float) {
        // Map SpeechEmotion to internal Emotion type
        val internalEmotion: Emotion = when (emotion) {
            SpeechEmotion.NEUTRAL -> Emotion.Neutral
            SpeechEmotion.HAPPY -> Emotion.Happy(intensity)
            SpeechEmotion.SAD -> Emotion.Sad(intensity)
            SpeechEmotion.EXCITED -> Emotion.Surprised(intensity)
            SpeechEmotion.THOUGHTFUL -> Emotion.Thinking(intensity)
            SpeechEmotion.EMPATHETIC -> Emotion.Calm(intensity)
            SpeechEmotion.CURIOUS -> Emotion.Confused(intensity) // Closest approximation
        }

        Log.d(TAG, "🎭 Setting emotion from speech: $emotion -> ${internalEmotion.getName()}")
        viewModel.setEmotion(internalEmotion)
    }

    override fun updateAudioIntensity(level: Float) {
        // Update lip-sync intensity based on audio amplitude
        lipSyncController.updateAudioIntensity(level)
    }
}
