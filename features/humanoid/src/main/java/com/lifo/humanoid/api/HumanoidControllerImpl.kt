package com.lifo.humanoid.api

import android.util.Log
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.domain.model.Emotion
import com.lifo.humanoid.presentation.HumanoidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Implementation of HumanoidController that bridges to HumanoidViewModel.
 *
 * This adapter allows external modules to control the Humanoid avatar
 * via the clean HumanoidController interface without direct ViewModel coupling.
 */
class HumanoidControllerImpl(
    private val viewModel: HumanoidViewModel
) : HumanoidController {

    companion object {
        private const val TAG = "HumanoidControllerImpl"
    }

    // Coroutine scope for StateFlow conversions
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun setEmotion(emotion: Emotion, intensity: Float) {
        Log.d(TAG, "Setting emotion: ${emotion.getName()} with intensity $intensity")
        viewModel.setEmotion(emotion)
    }

    override fun speakText(text: String, durationMs: Long) {
        Log.d(TAG, "Speaking text: '${text.take(50)}...' for ${durationMs}ms")
        viewModel.speakText(text, durationMs)
    }

    override fun stopSpeaking() {
        Log.d(TAG, "Stopping speech")
        viewModel.stopSpeaking()
    }

    override fun playGesture(gesture: GestureType, loop: Boolean) {
        Log.d(TAG, "Playing gesture: $gesture (loop=$loop)")

        val animationAsset = when (gesture) {
            GestureType.GREETING -> VrmaAnimationLoader.AnimationAsset.GREETING
            GestureType.DANCE -> VrmaAnimationLoader.AnimationAsset.DANCE
            GestureType.VICTORY -> VrmaAnimationLoader.AnimationAsset.PEACE_SIGN
            GestureType.THINKING -> VrmaAnimationLoader.AnimationAsset.MODEL_POSE
            GestureType.PEACE_SIGN -> VrmaAnimationLoader.AnimationAsset.PEACE_SIGN
            GestureType.SHOOT -> VrmaAnimationLoader.AnimationAsset.SHOOT
            GestureType.SQUAT -> VrmaAnimationLoader.AnimationAsset.SQUAT
            GestureType.SPIN -> VrmaAnimationLoader.AnimationAsset.SPIN
            GestureType.SHOW_FULL_BODY -> VrmaAnimationLoader.AnimationAsset.SHOW_FULL_BODY

            // TODO: Implement NOD and SHAKE_HEAD animations
            GestureType.NOD, GestureType.SHAKE_HEAD -> {
                Log.w(TAG, "Gesture $gesture not yet implemented")
                return
            }
        }

        viewModel.playAnimation(animationAsset)
    }

    override fun stopGesture() {
        Log.d(TAG, "Stopping gesture, returning to idle")
        viewModel.stopAnimation()
    }

    override fun resetToNeutral() {
        Log.d(TAG, "Resetting avatar to neutral state")
        viewModel.resetAvatar()
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
        viewModel.isSpeaking

    override val isPlayingGesture: StateFlow<Boolean> =
        viewModel.uiState
            .map { it.isPlayingAnimation }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = false
            )
}
