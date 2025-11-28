package com.lifo.humanoid.animation

import android.util.Log
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.lipsync.LipSyncController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordinates all animation systems and manages priorities.
 *
 * Animation Priority (highest to lowest):
 * 1. Lip-Sync (highest priority during speech)
 * 2. Blink (always overlays)
 * 3. VRMA Animations (gesture animations)
 * 4. Emotions (facial expressions)
 * 5. Idle (breathing, micro-movements - lowest priority)
 *
 * This coordinator ensures smooth transitions and prevents conflicts
 * between different animation systems.
 */
class AnimationCoordinator(
    private val blendShapeController: VrmBlendShapeController,
    private val blinkController: BlinkController,
    private val lipSyncController: LipSyncController,
    private val idleAnimationController: IdleAnimationController,
    private val vrmaAnimationPlayer: VrmaAnimationPlayer
) {

    companion object {
        private const val TAG = "AnimationCoordinator"
    }

    // State tracking
    private var isInitialized = false
    private var isRunning = false

    // Current animation state
    data class AnimationState(
        val isBlinking: Boolean = false,
        val isSpeaking: Boolean = false,
        val isPlayingAnimation: Boolean = false,
        val idleActive: Boolean = false,
        val currentAnimationName: String? = null
    )

    /**
     * Initialize and start all animation systems
     *
     * @param scope CoroutineScope for animation loops
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) {
            Log.d(TAG, "AnimationCoordinator already running")
            return
        }

        isRunning = true
        isInitialized = true
        Log.d(TAG, "Starting AnimationCoordinator")

        // Start idle animation (lowest priority, always running in background)
        idleAnimationController.start(scope)

        // Start blink controller (always active, overlays everything)
        blinkController.start(scope)

        // Set up blink integration with blend shapes
        scope.launch {
            blinkController.blinkWeight.collect { weight ->
                if (weight > 0.001f) {
                    blendShapeController.setCategoryWeights(
                        VrmBlendShapeController.CATEGORY_BLINK,
                        mapOf("blink" to weight)
                    )
                } else {
                    blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_BLINK)
                }
            }
        }

        // Monitor lip-sync state to adjust idle intensity
        scope.launch {
            lipSyncController.isSpeaking.collect { isSpeaking ->
                // Reduce idle intensity during speech for cleaner lip movements
                if (isSpeaking) {
                    idleAnimationController.setIntensity(0.3f)
                } else {
                    idleAnimationController.setIntensity(1.0f)
                }
            }
        }

        // Monitor VRMA animation state
        scope.launch {
            vrmaAnimationPlayer.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    // Reduce/pause idle during gesture animations
                    idleAnimationController.setIntensity(0.2f)
                } else {
                    idleAnimationController.setIntensity(1.0f)
                }
            }
        }

        Log.d(TAG, "All animation systems started")
    }

    /**
     * Stop all animation systems
     */
    fun stop() {
        Log.d(TAG, "Stopping AnimationCoordinator")

        isRunning = false

        blinkController.stop()
        lipSyncController.stop()
        idleAnimationController.stop()
        vrmaAnimationPlayer.stop()

        // Clear all blend shape categories
        blendShapeController.reset()

        Log.d(TAG, "All animation systems stopped")
    }

    /**
     * Pause all animations (useful when app goes to background)
     */
    fun pause() {
        blinkController.pause()
        vrmaAnimationPlayer.pause()
        // Idle and lip-sync can continue as they don't have explicit pause
    }

    /**
     * Resume animations after pause
     */
    fun resume(scope: CoroutineScope) {
        blinkController.resume(scope)
        vrmaAnimationPlayer.resume(scope)
    }

    /**
     * Play a VRMA animation
     */
    fun playAnimation(
        animation: VrmaAnimation,
        scope: CoroutineScope,
        loop: Boolean = animation.isLooping
    ) {
        Log.d(TAG, "Playing animation: ${animation.name}")
        vrmaAnimationPlayer.play(animation, scope, loop)
    }

    /**
     * Stop current VRMA animation
     */
    fun stopAnimation() {
        vrmaAnimationPlayer.stop()
    }

    /**
     * Start lip-sync for text
     */
    fun speak(text: String, durationMs: Long, scope: CoroutineScope) {
        lipSyncController.speak(text, durationMs, scope)
    }

    /**
     * Stop lip-sync
     */
    fun stopSpeaking() {
        lipSyncController.stop()
    }

    /**
     * Trigger a manual blink
     */
    suspend fun triggerBlink() {
        blinkController.triggerBlink()
    }

    /**
     * Set emotion blend shapes
     */
    fun setEmotionWeights(weights: Map<String, Float>) {
        blendShapeController.setCategoryWeights(
            VrmBlendShapeController.CATEGORY_EMOTION,
            weights
        )
    }

    /**
     * Clear emotion blend shapes
     */
    fun clearEmotion() {
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_EMOTION)
    }

    /**
     * Set idle animation intensity
     */
    fun setIdleIntensity(intensity: Float) {
        idleAnimationController.setIntensity(intensity)
    }

    /**
     * Get current animation state
     */
    fun getCurrentState(): AnimationState {
        return AnimationState(
            isBlinking = blinkController.isBlinking.value,
            isSpeaking = lipSyncController.isSpeaking.value,
            isPlayingAnimation = vrmaAnimationPlayer.isPlaying.value,
            idleActive = idleAnimationController.isActive.value,
            currentAnimationName = vrmaAnimationPlayer.currentAnimation.value?.name
        )
    }

    /**
     * Check if coordinator is running
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Check if initialized
     */
    fun isInitialized(): Boolean = isInitialized

    // Expose state flows for UI observation
    val isBlinking: StateFlow<Boolean> = blinkController.isBlinking
    val isSpeaking: StateFlow<Boolean> = lipSyncController.isSpeaking
    val isPlayingAnimation: StateFlow<Boolean> = vrmaAnimationPlayer.isPlaying
    val currentAnimation: StateFlow<VrmaAnimation?> = vrmaAnimationPlayer.currentAnimation
    val animationProgress: StateFlow<Float> = vrmaAnimationPlayer.playbackProgress
    val lipSyncProgress: StateFlow<Float> = lipSyncController.progress
}
