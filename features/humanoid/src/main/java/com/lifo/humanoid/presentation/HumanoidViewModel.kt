package com.lifo.humanoid.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.filament.gltfio.FilamentAsset
import com.lifo.humanoid.animation.BlinkController
import com.lifo.humanoid.animation.IdleRotationController
import com.lifo.humanoid.animation.VrmaAnimation
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.animation.VrmaAnimationPlayer
import com.lifo.humanoid.animation.VrmaAnimationPlayerFactory
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.data.vrm.VrmBlendShapePresets
import com.lifo.humanoid.data.vrm.VrmExtensions
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmLoader
import com.lifo.humanoid.domain.model.AvatarState
import com.lifo.humanoid.domain.model.Emotion
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.rendering.FilamentRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * ViewModel for the Humanoid avatar screen.
 * Manages avatar state, loading, animations, and blend shapes.
 *
 * Integrates:
 * - VRM model loading
 * - Blend shape control
 * - Blink animation
 * - Lip-sync animation
 * - VRMA animation playback
 */
@HiltViewModel
class HumanoidViewModel @Inject constructor(
    private val vrmLoader: VrmLoader,
    private val blendShapeController: VrmBlendShapeController,
    private val boneMapper: VrmHumanoidBoneMapper,
    private val blinkController: BlinkController,
    private val lipSyncController: LipSyncController,
    private val vrmaAnimationLoader: VrmaAnimationLoader,
    private val vrmaAnimationPlayerFactory: VrmaAnimationPlayerFactory
) : ViewModel() {

    companion object {
        private const val TAG = "HumanoidViewModel"
    }

    private val _uiState = MutableStateFlow(HumanoidUiState())
    val uiState: StateFlow<HumanoidUiState> = _uiState.asStateFlow()

    private val _avatarState = MutableStateFlow(AvatarState.Default)
    val avatarState: StateFlow<AvatarState> = _avatarState.asStateFlow()

    // VRM model data (ByteBuffer and extensions)
    private val _vrmModelData = MutableStateFlow<Pair<ByteBuffer, VrmExtensions>?>(null)
    val vrmModelData: StateFlow<Pair<ByteBuffer, VrmExtensions>?> = _vrmModelData.asStateFlow()

    // VRM extensions (blend shapes, etc.)
    private val _vrmExtensions = MutableStateFlow<VrmExtensions?>(null)
    val vrmExtensions: StateFlow<VrmExtensions?> = _vrmExtensions.asStateFlow()

    // Blend shape weights from controller
    val blendShapeWeights: StateFlow<Map<String, Float>> = blendShapeController.currentWeights

    // Available animations
    private val _availableAnimations = MutableStateFlow<List<VrmaAnimationLoader.AnimationAsset>>(emptyList())
    val availableAnimations: StateFlow<List<VrmaAnimationLoader.AnimationAsset>> = _availableAnimations.asStateFlow()

    // Loaded animations cache
    private val loadedAnimations = mutableMapOf<VrmaAnimationLoader.AnimationAsset, VrmaAnimation>()

    // Current animation being played
    private val _currentAnimation = MutableStateFlow<VrmaAnimation?>(null)
    val currentAnimation: StateFlow<VrmaAnimation?> = _currentAnimation.asStateFlow()

    // Animation states from controllers
    val isBlinking: StateFlow<Boolean> = blinkController.isBlinking
    val isSpeaking: StateFlow<Boolean> = lipSyncController.isSpeaking

    // ==================== Animation Player State ====================

    // Reference to FilamentRenderer for animation
    private var filamentRenderer: FilamentRenderer? = null

    // Animation player (initialized when model is loaded)
    private var vrmaAnimationPlayer: VrmaAnimationPlayer? = null

    // Flag to track if animation system is ready
    private val _isAnimationSystemReady = MutableStateFlow(false)
    val isAnimationSystemReady: StateFlow<Boolean> = _isAnimationSystemReady.asStateFlow()

    // Idle rotation controller for automatic idle animation cycling
    private var idleRotationController: IdleRotationController? = null

    // Idle rotation state
    private val _isIdleRotationActive = MutableStateFlow(false)
    val isIdleRotationActive: StateFlow<Boolean> = _isIdleRotationActive.asStateFlow()

    private val _currentIdleAnimation = MutableStateFlow<VrmaAnimationLoader.AnimationAsset?>(null)
    val currentIdleAnimation: StateFlow<VrmaAnimationLoader.AnimationAsset?> = _currentIdleAnimation.asStateFlow()

    init {
        // Load available animations
        _availableAnimations.value = vrmaAnimationLoader.getAvailableAnimations()

        // Auto-load default avatar on initialization
        loadDefaultAvatar()

        // Start blink controller
        blinkController.start(viewModelScope)

        // Observe avatar state changes and update blend shapes
        viewModelScope.launch {
            avatarState.collect { state ->
                updateBlendShapesForState(state)
            }
        }

        // Integrate blink controller with blend shapes
        viewModelScope.launch {
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

        Log.d(TAG, "HumanoidViewModel initialized with ${_availableAnimations.value.size} animations available")
    }

    /**
     * Load the default VRM avatar from assets
     */
    fun loadDefaultAvatar() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val modelData = vrmLoader.loadVrmFromAssets("models/default_avatar.vrm")

                if (modelData != null) {
                    _vrmModelData.value = modelData
                    _vrmExtensions.value = modelData.second

                    // Set available presets in blend shape controller
                    val presetNames = modelData.second.blendShapes.map { it.name }.toSet()
                    blendShapeController.setAvailablePresets(presetNames)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        avatarLoaded = true
                    )

                    Log.d(TAG, "Avatar loaded with ${presetNames.size} blend shape presets")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load default avatar"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading avatar", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading avatar: ${e.message}"
                )
            }
        }
    }

    /**
     * Set avatar emotion with improved preset matching
     */
    fun setEmotion(emotion: Emotion) {
        _avatarState.value = _avatarState.value.copy(emotion = emotion)
    }

    /**
     * Set avatar to speaking state
     */
    fun setSpeaking(isSpeaking: Boolean) {
        _avatarState.value = _avatarState.value.copy(isSpeaking = isSpeaking)
    }

    /**
     * Set avatar to listening state
     */
    fun setListening(isListening: Boolean) {
        _avatarState.value = if (isListening) {
            AvatarState.listening()
        } else {
            _avatarState.value.copy(isListening = false)
        }
    }

    /**
     * Enable/disable vision
     */
    fun setVisionEnabled(enabled: Boolean) {
        _avatarState.value = _avatarState.value.copy(visionEnabled = enabled)
    }

    // ==================== Model Loaded Callback ====================

    /**
     * Called when the VRM model is loaded by FilamentRenderer.
     * Initializes the animation player with the loaded asset.
     *
     * @param renderer The FilamentRenderer instance
     * @param asset The loaded FilamentAsset
     * @param nodeNames List of node names from the asset
     */
    fun onModelLoaded(
        renderer: FilamentRenderer,
        asset: FilamentAsset,
        nodeNames: List<String>
    ) {
        Log.d(TAG, "onModelLoaded called - initializing animation system")

        filamentRenderer = renderer

        // Get Engine from renderer
        val engine = renderer.getEngine()
        if (engine == null) {
            Log.e(TAG, "Engine not available from renderer")
            return
        }

        // Initialize bone mapper with the asset
        boneMapper.initialize(engine, asset, nodeNames)
        Log.d(TAG, "BoneMapper initialized with ${boneMapper.getBoneEntityMap().size} bones")

        // Initialize animation player
        vrmaAnimationPlayer = vrmaAnimationPlayerFactory.initializeWithAsset(engine, asset, nodeNames)
        Log.d(TAG, "VrmaAnimationPlayer initialized")

        _isAnimationSystemReady.value = true
        Log.d(TAG, "Animation system is ready")

        // Pre-load idle animation for immediate use
        viewModelScope.launch {
            preloadCommonAnimations()
        }
    }

    /**
     * Pre-load commonly used animations and set up idle rotation system.
     * Instead of a fixed idle animation, we use IdleRotationController
     * to automatically rotate between multiple idle animations.
     */
    private suspend fun preloadCommonAnimations() {
        // Pre-load ALL idle animations for smooth rotation
        val idleAnimations = VrmaAnimationLoader.getIdleAnimations()
        val animationsToPreload = idleAnimations + listOf(
            VrmaAnimationLoader.AnimationAsset.GREETING
        )

        Log.d(TAG, "Pre-loading ${animationsToPreload.size} animations (${idleAnimations.size} idle animations)")

        animationsToPreload.forEach { animationAsset ->
            if (!loadedAnimations.containsKey(animationAsset)) {
                vrmaAnimationLoader.loadAnimation(animationAsset)?.let { animation ->
                    loadedAnimations[animationAsset] = animation
                    Log.d(TAG, "Pre-loaded animation: ${animation.name}")
                }
            }
        }

        // Initialize and start idle rotation system automatically
        // This will rotate between idle animations every 10-40 seconds
        initializeIdleRotation()
        startIdleRotation()
        Log.d(TAG, "Idle rotation system started with ${idleAnimations.size} animations")
    }

    // ==================== Animation Methods ====================

    /**
     * Play a VRMA animation by asset type.
     * Following Amica pattern:
     * - If it's the idle animation, set it as the new idle
     * - Otherwise, play as one-shot and return to idle when done
     */
    fun playAnimation(animationAsset: VrmaAnimationLoader.AnimationAsset) {
        viewModelScope.launch {
            // Check if animation system is ready
            if (!_isAnimationSystemReady.value) {
                Log.w(TAG, "Animation system not ready yet")
                return@launch
            }

            val player = vrmaAnimationPlayer
            if (player == null) {
                Log.e(TAG, "Animation player not initialized")
                return@launch
            }

            // Check cache first, load if needed
            val animation = loadedAnimations.getOrPut(animationAsset) {
                vrmaAnimationLoader.loadAnimation(animationAsset) ?: run {
                    Log.e(TAG, "Failed to load animation: ${animationAsset.fileName}")
                    return@launch
                }
            }

            // Update UI state
            _currentAnimation.value = animation
            _uiState.value = _uiState.value.copy(
                isPlayingAnimation = true,
                currentAnimationName = animation.name
            )

            Log.d(TAG, "Playing animation: ${animation.name}, duration: ${animation.durationSeconds}s, looping: ${animation.isLooping}")

            // Following Amica pattern:
            // - idle animations play in loop as the default state
            // - Other animations play once then return to idle
            if (animationAsset.isIdle()) {
                // Set as the new idle animation
                player.setIdleAnimation(animation, viewModelScope)
            } else {
                // Pause idle rotation during non-idle animation
                pauseIdleRotation()

                // Play as one-shot, will automatically return to idle
                player.playOneShot(
                    animation = animation,
                    scope = viewModelScope,
                    fadeDuration = 0.5f,
                    onComplete = {
                        // Resume idle rotation when animation completes
                        resumeIdleRotation()
                    }
                )
            }
        }
    }

    /**
     * Stop current one-shot animation and return to idle.
     * Following Amica pattern: idle always plays, so "stop" means return to idle.
     */
    fun stopAnimation() {
        val player = vrmaAnimationPlayer
        if (player != null && player.hasIdleAnimation()) {
            // Return to idle animation
            player.getIdleAnimation()?.let { idleAnim ->
                player.setIdleAnimation(idleAnim, viewModelScope)
                _currentAnimation.value = idleAnim
                _uiState.value = _uiState.value.copy(
                    isPlayingAnimation = true,
                    currentAnimationName = idleAnim.name
                )
                Log.d(TAG, "Returned to idle animation")
            }
        } else {
            player?.stop()
            _currentAnimation.value = null
            _uiState.value = _uiState.value.copy(
                isPlayingAnimation = false,
                currentAnimationName = null
            )
            Log.d(TAG, "Animation stopped (no idle available)")
        }
    }

    // ==================== Idle Rotation Methods ====================

    /**
     * Initialize idle rotation controller (called when animation system is ready)
     */
    private fun initializeIdleRotation() {
        idleRotationController = IdleRotationController { animationAsset ->
            viewModelScope.launch {
                val animation = loadedAnimations.getOrPut(animationAsset) {
                    vrmaAnimationLoader.loadAnimation(animationAsset) ?: return@launch
                }
                vrmaAnimationPlayer?.let { player ->
                    Log.d(TAG, "Idle rotation: playing ${animation.name}")
                    player.setIdleAnimation(animation, viewModelScope)
                    _currentAnimation.value = animation
                    _currentIdleAnimation.value = animationAsset
                    _uiState.value = _uiState.value.copy(
                        isPlayingAnimation = true,
                        currentAnimationName = animation.name
                    )
                }
            }
        }

        // Observe idle rotation state
        viewModelScope.launch {
            idleRotationController?.isActive?.collect { isActive ->
                _isIdleRotationActive.value = isActive
            }
        }
        viewModelScope.launch {
            idleRotationController?.currentIdle?.collect { idle ->
                _currentIdleAnimation.value = idle
            }
        }
    }

    /**
     * Start automatic idle animation rotation
     */
    fun startIdleRotation() {
        if (idleRotationController == null) {
            initializeIdleRotation()
        }
        idleRotationController?.start(viewModelScope)
        Log.d(TAG, "Idle rotation started")
    }

    /**
     * Stop automatic idle animation rotation
     */
    fun stopIdleRotation() {
        idleRotationController?.stop()
        Log.d(TAG, "Idle rotation stopped")
    }

    /**
     * Toggle automatic idle animation rotation
     */
    fun toggleIdleRotation(): Boolean {
        val controller = idleRotationController
        return if (controller?.isActive?.value == true) {
            stopIdleRotation()
            false
        } else {
            startIdleRotation()
            true
        }
    }

    /**
     * Pause idle rotation (for gesture animations)
     */
    private fun pauseIdleRotation() {
        idleRotationController?.pause()
    }

    /**
     * Resume idle rotation after gesture animation
     */
    private fun resumeIdleRotation() {
        if (_isIdleRotationActive.value) {
            idleRotationController?.resume(viewModelScope)
        }
    }

    // ==================== Lip-Sync Methods ====================

    /**
     * Start lip-sync for text
     *
     * @param text The text to speak
     * @param durationMs Duration of the speech in milliseconds
     */
    fun speakText(text: String, durationMs: Long) {
        lipSyncController.speak(text, durationMs, viewModelScope)
    }

    /**
     * Stop lip-sync
     */
    fun stopSpeaking() {
        lipSyncController.stop()
    }

    // ==================== Blink Methods ====================

    /**
     * Trigger a manual blink
     */
    fun triggerBlink() {
        viewModelScope.launch {
            blinkController.triggerBlink()
        }
    }

    // ==================== Blend Shape Updates ====================

    /**
     * Update blend shapes based on current avatar state.
     * Uses VrmBlendShapePresets for better compatibility.
     */
    private fun updateBlendShapesForState(state: AvatarState) {
        val emotionName = state.emotion.getName().lowercase()
        val intensity = state.emotion.intensity

        // Use preset system for better matching
        val weights = VrmBlendShapePresets.getEmotionWeights(
            emotionName,
            intensity,
            blendShapeController.availablePresets
        )

        // Set as emotion category
        blendShapeController.setCategoryWeights(
            VrmBlendShapeController.CATEGORY_EMOTION,
            weights
        )
    }

    /**
     * Update blend shapes - call every frame
     */
    fun updateBlendShapes(deltaTime: Float) {
        blendShapeController.update(deltaTime)
    }

    /**
     * Reset avatar to default state
     */
    fun resetAvatar() {
        _avatarState.value = AvatarState.Default
        blendShapeController.reset()
        stopAnimation()
        stopSpeaking()
    }

    /**
     * Toggle debug mode
     */
    fun toggleDebugMode() {
        _uiState.value = _uiState.value.copy(debugMode = !_uiState.value.debugMode)
    }

    /**
     * Stop all animation controllers before Filament cleanup.
     * CRITICAL: Must be called BEFORE FilamentRenderer.cleanup() to prevent
     * accessing destroyed Filament assets.
     * See: https://github.com/google/filament/issues/7650
     */
    fun stopAllControllersBeforeCleanup() {
        Log.d(TAG, "stopAllControllersBeforeCleanup - stopping all animation controllers")
        blinkController.stop()
        lipSyncController.stop()
        idleRotationController?.stop()
        // Mark animation player as destroyed to prevent accessing Filament assets
        vrmaAnimationPlayer?.stop(blendOut = false, destroy = true)
        _isAnimationSystemReady.value = false
        Log.d(TAG, "All controllers stopped and marked as destroyed")
    }

    override fun onCleared() {
        super.onCleared()
        blinkController.stop()
        lipSyncController.stop()
        idleRotationController?.stop()
        vrmaAnimationPlayer?.stop(blendOut = false, destroy = true)
        vrmaAnimationPlayerFactory.clear()
        filamentRenderer = null
        _isAnimationSystemReady.value = false
        Log.d(TAG, "HumanoidViewModel cleared")
    }
}

/**
 * UI State for Humanoid screen
 */
data class HumanoidUiState(
    val isLoading: Boolean = false,
    val avatarLoaded: Boolean = false,
    val error: String? = null,
    val debugMode: Boolean = false,
    val isPlayingAnimation: Boolean = false,
    val currentAnimationName: String? = null
)
