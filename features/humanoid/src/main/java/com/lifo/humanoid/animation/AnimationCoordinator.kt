package com.lifo.humanoid.animation

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
    private val vrmaAnimationPlayer: VrmaAnimationPlayer,
    private val vrmaAnimationLoader: VrmaAnimationLoader
) {

    // State tracking
    private var isInitialized = false
    private var isRunning = false
    private var animationScope: CoroutineScope? = null

    // Idle rotation controller for automatic VRMA idle animation cycling
    private var idleRotationController: IdleRotationController? = null

    // Current animation state
    data class AnimationState(
        val isBlinking: Boolean = false,
        val isSpeaking: Boolean = false,
        val isPlayingAnimation: Boolean = false,
        val idleActive: Boolean = false,
        val idleRotationActive: Boolean = false,
        val currentAnimationName: String? = null,
        val currentIdleAnimation: String? = null
    )

    /**
     * Initialize and start all animation systems
     *
     * @param scope CoroutineScope for animation loops
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("[AnimationCoordinator] AnimationCoordinator already running")
            return
        }

        isRunning = true
        isInitialized = true
        animationScope = scope
        println("[AnimationCoordinator] Starting AnimationCoordinator")

        // Start idle animation (lowest priority, always running in background)
        idleAnimationController.start(scope)

        // Start blink controller (always active, overlays everything)
        blinkController.start(scope)

        // Initialize idle rotation controller for automatic idle VRMA cycling
        initializeIdleRotation(scope)

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

        // Monitor VRMA animation state for gesture/action animations
        scope.launch {
            vrmaAnimationPlayer.isPlaying.collect { isPlaying ->
                val currentAnim = vrmaAnimationPlayer.currentAnimation.value
                val isIdleAnim = currentAnim?.name?.contains("idle", ignoreCase = true) == true

                if (isPlaying && !isIdleAnim) {
                    // Reduce idle during non-idle gesture animations
                    idleAnimationController.setIntensity(0.2f)
                    // Pause idle rotation during non-idle animations
                    idleRotationController?.pause()
                } else {
                    idleAnimationController.setIntensity(1.0f)
                    // Resume idle rotation when gesture animation ends
                    if (!isPlaying) {
                        idleRotationController?.resume(scope)
                    }
                }
            }
        }

        println("[AnimationCoordinator] All animation systems started")
    }

    /**
     * Initialize idle rotation controller
     */
    private fun initializeIdleRotation(scope: CoroutineScope) {
        idleRotationController = IdleRotationController { animationAsset ->
            scope.launch {
                val animation = vrmaAnimationLoader.loadAnimation(animationAsset)
                if (animation != null) {
                    println("[AnimationCoordinator] Playing idle rotation animation: ${animation.name}")
                    vrmaAnimationPlayer.play(animation, scope, loop = true)
                } else {
                    println("[AnimationCoordinator] WARNING: Failed to load idle animation: ${animationAsset.fileName}")
                }
            }
        }
    }

    /**
     * Start idle rotation (automatic cycling of idle animations)
     */
    fun startIdleRotation() {
        animationScope?.let { scope ->
            idleRotationController?.start(scope)
            println("[AnimationCoordinator] Idle rotation started")
        } ?: println("[AnimationCoordinator] WARNING: Cannot start idle rotation - no animation scope")
    }

    /**
     * Stop idle rotation
     */
    fun stopIdleRotation() {
        idleRotationController?.stop()
        println("[AnimationCoordinator] Idle rotation stopped")
    }

    /**
     * Toggle idle rotation
     */
    fun toggleIdleRotation(): Boolean {
        val controller = idleRotationController ?: return false
        return if (controller.isActive.value) {
            stopIdleRotation()
            false
        } else {
            startIdleRotation()
            true
        }
    }

    /**
     * Stop all animation systems
     */
    fun stop() {
        println("[AnimationCoordinator] Stopping AnimationCoordinator")

        isRunning = false
        animationScope = null

        blinkController.stop()
        lipSyncController.stop()
        idleAnimationController.stop()
        idleRotationController?.stop()
        vrmaAnimationPlayer.stop()

        // Clear all blend shape categories
        blendShapeController.reset()

        println("[AnimationCoordinator] All animation systems stopped")
    }

    /**
     * Pause all animations (useful when app goes to background)
     */
    fun pause() {
        blinkController.pause()
        vrmaAnimationPlayer.pause()
        idleRotationController?.pause()
        // Idle and lip-sync can continue as they don't have explicit pause
    }

    /**
     * Resume animations after pause
     */
    fun resume(scope: CoroutineScope) {
        animationScope = scope
        blinkController.resume(scope)
        vrmaAnimationPlayer.resume(scope)
        idleRotationController?.resume(scope)
    }

    /**
     * Play a VRMA animation
     */
    fun playAnimation(
        animation: VrmaAnimation,
        scope: CoroutineScope,
        loop: Boolean = animation.isLooping
    ) {
        println("[AnimationCoordinator] Playing animation: ${animation.name}")
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
            idleRotationActive = idleRotationController?.isActive?.value ?: false,
            currentAnimationName = vrmaAnimationPlayer.currentAnimation.value?.name,
            currentIdleAnimation = idleRotationController?.currentIdle?.value?.displayName
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

    // Idle rotation state (will be initialized in start())
    val isIdleRotationActive: StateFlow<Boolean>
        get() = idleRotationController?.isActive ?: kotlinx.coroutines.flow.MutableStateFlow(false)

    val currentIdleAnimation: StateFlow<VrmaAnimationLoader.AnimationAsset?>
        get() = idleRotationController?.currentIdle ?: kotlinx.coroutines.flow.MutableStateFlow(null)
}
