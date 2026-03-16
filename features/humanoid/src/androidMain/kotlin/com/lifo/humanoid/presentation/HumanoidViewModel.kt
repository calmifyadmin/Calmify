package com.lifo.humanoid.presentation

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
// ArFilamentRenderer replaced by SceneView -- see onSceneViewArModelLoaded()
import com.lifo.humanoid.rendering.FilamentRenderer
import com.lifo.util.auth.AuthProvider
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.AvatarRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

// ==================== MVI Contract ====================

/**
 * MVI Contract for the Humanoid avatar screen.
 *
 * Defines all user intents, the consolidated UI state, and one-shot effects.
 */
object HumanoidContract {

    sealed interface Intent : MviContract.Intent {
        // Avatar loading
        data object LoadDefaultAvatar : Intent

        // Avatar state changes
        data class SetEmotion(val emotion: Emotion) : Intent
        data class SetSpeaking(val isSpeaking: Boolean) : Intent
        data class SetListening(val isListening: Boolean) : Intent
        data class SetVisionEnabled(val enabled: Boolean) : Intent

        // Model loaded callbacks (from Filament/SceneView)
        data class OnModelLoaded(
            val renderer: FilamentRenderer,
            val asset: FilamentAsset,
            val nodeNames: List<String>
        ) : Intent

        data class OnSceneViewArModelLoaded(
            val engine: com.google.android.filament.Engine,
            val asset: FilamentAsset,
            val nodeNames: List<String>
        ) : Intent

        // Animation control
        data class PlayAnimation(val animationAsset: VrmaAnimationLoader.AnimationAsset) : Intent
        data object StopAnimation : Intent

        // Idle rotation
        data object StartIdleRotation : Intent
        data object StopIdleRotation : Intent
        data object ToggleIdleRotation : Intent

        // Lip-sync
        data class SpeakText(val text: String, val durationMs: Long) : Intent
        data object StopSpeaking : Intent

        // Blink
        data object TriggerBlink : Intent

        // Blend shapes
        data class UpdateBlendShapes(val deltaTime: Float) : Intent

        // Reset
        data object ResetAvatar : Intent

        // Debug
        data object ToggleDebugMode : Intent

        // Lifecycle
        data object StopAllControllersBeforeCleanup : Intent
    }

    data class State(
        val isLoading: Boolean = false,
        val avatarLoaded: Boolean = false,
        val error: String? = null,
        val debugMode: Boolean = false,
        val isPlayingAnimation: Boolean = false,
        val currentAnimationName: String? = null,
        // Avatar domain state
        val avatarState: AvatarState = AvatarState.Default,
        // VRM model data
        val vrmModelData: Pair<ByteBuffer, VrmExtensions>? = null,
        val vrmExtensions: VrmExtensions? = null,
        // Animations
        val availableAnimations: List<VrmaAnimationLoader.AnimationAsset> = emptyList(),
        val currentAnimation: VrmaAnimation? = null,
        // Animation system
        val isAnimationSystemReady: Boolean = false,
        val isIdleRotationActive: Boolean = false,
        val currentIdleAnimation: VrmaAnimationLoader.AnimationAsset? = null
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class AvatarLoadError(val message: String) : Effect
        data class AnimationError(val message: String) : Effect
        data class IdleRotationToggled(val isActive: Boolean) : Effect
    }
}

// ==================== Backward-Compat Type Alias ====================

/**
 * Backward-compatible type alias.
 * Callers that reference `HumanoidUiState` will keep compiling.
 */
typealias HumanoidUiState = HumanoidContract.State

// ==================== ViewModel ====================

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
 *
 * Migrated to MVI pattern (MviViewModel).
 * All former public functions are kept as thin backward-compatible wrappers
 * that delegate to [onIntent].
 */
class HumanoidViewModel constructor(
    private val vrmLoader: VrmLoader,
    private val blendShapeController: VrmBlendShapeController,
    private val boneMapper: VrmHumanoidBoneMapper,
    private val blinkController: BlinkController,
    private val lipSyncController: LipSyncController,
    private val vrmaAnimationLoader: VrmaAnimationLoader,
    private val vrmaAnimationPlayerFactory: VrmaAnimationPlayerFactory,
    private val avatarRepository: AvatarRepository? = null,
    private val authProvider: AuthProvider? = null
) : MviViewModel<HumanoidContract.Intent, HumanoidContract.State, HumanoidContract.Effect>(
    initialState = HumanoidContract.State()
) {

    // ==================== Backward-Compatible Aliases ====================

    /**
     * Backward-compatible alias: `uiState` -> MVI `state`.
     * Callers collecting `viewModel.uiState` keep working unchanged.
     */
    val uiState: StateFlow<HumanoidContract.State> get() = state

    /**
     * Backward-compatible: expose avatarState as a derived StateFlow.
     */
    val avatarState: StateFlow<AvatarState> =
        state.map { it.avatarState }
            .stateIn(scope, SharingStarted.Eagerly, AvatarState.Default)

    /**
     * Backward-compatible: expose vrmModelData as a derived StateFlow.
     */
    val vrmModelData: StateFlow<Pair<ByteBuffer, VrmExtensions>?> =
        state.map { it.vrmModelData }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Backward-compatible: expose vrmExtensions as a derived StateFlow.
     */
    val vrmExtensions: StateFlow<VrmExtensions?> =
        state.map { it.vrmExtensions }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Blend shape weights from controller (unchanged - not owned by MVI state).
     */
    val blendShapeWeights: StateFlow<Map<String, Float>> = blendShapeController.currentWeights

    /**
     * Backward-compatible: expose availableAnimations as a derived StateFlow.
     */
    val availableAnimations: StateFlow<List<VrmaAnimationLoader.AnimationAsset>> =
        state.map { it.availableAnimations }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Backward-compatible: expose currentAnimation as a derived StateFlow.
     */
    val currentAnimation: StateFlow<VrmaAnimation?> =
        state.map { it.currentAnimation }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Animation states from controllers (unchanged - external StateFlows).
     */
    val isBlinking: StateFlow<Boolean> = blinkController.isBlinking
    val isSpeaking: StateFlow<Boolean> = lipSyncController.isSpeaking

    /**
     * Backward-compatible: expose isAnimationSystemReady as a derived StateFlow.
     */
    val isAnimationSystemReady: StateFlow<Boolean> =
        state.map { it.isAnimationSystemReady }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Backward-compatible: expose isIdleRotationActive as a derived StateFlow.
     */
    val isIdleRotationActive: StateFlow<Boolean> =
        state.map { it.isIdleRotationActive }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Backward-compatible: expose currentIdleAnimation as a derived StateFlow.
     */
    val currentIdleAnimation: StateFlow<VrmaAnimationLoader.AnimationAsset?> =
        state.map { it.currentIdleAnimation }
            .stateIn(scope, SharingStarted.Eagerly, null)

    // ==================== Internal Mutable State (not in MVI State) ====================

    // Loaded animations cache
    private val loadedAnimations = mutableMapOf<VrmaAnimationLoader.AnimationAsset, VrmaAnimation>()

    // Reference to FilamentRenderer for animation
    private var filamentRenderer: FilamentRenderer? = null

    // Animation player (initialized when model is loaded)
    private var vrmaAnimationPlayer: VrmaAnimationPlayer? = null

    // Idle rotation controller for automatic idle animation cycling
    private var idleRotationController: IdleRotationController? = null

    // ==================== Init ====================

    init {
        // Load available animations
        updateState { copy(availableAnimations = vrmaAnimationLoader.getAvailableAnimations()) }

        // Auto-load default avatar (LiveChat and other callers depend on this)
        onIntent(HumanoidContract.Intent.LoadDefaultAvatar)

        // Start blink controller
        blinkController.start(scope)

        // Observe avatar state changes and update blend shapes
        scope.launch {
            state.map { it.avatarState }.distinctUntilChanged().collect { avatarState ->
                updateBlendShapesForState(avatarState)
            }
        }

        // Integrate blink controller with blend shapes
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

        println("[HumanoidViewModel] HumanoidViewModel initialized with ${currentState.availableAnimations.size} animations available")
    }

    // ==================== MVI handleIntent ====================

    override fun handleIntent(intent: HumanoidContract.Intent) {
        when (intent) {
            is HumanoidContract.Intent.LoadDefaultAvatar -> handleLoadDefaultAvatar()
            is HumanoidContract.Intent.SetEmotion -> handleSetEmotion(intent.emotion)
            is HumanoidContract.Intent.SetSpeaking -> handleSetSpeaking(intent.isSpeaking)
            is HumanoidContract.Intent.SetListening -> handleSetListening(intent.isListening)
            is HumanoidContract.Intent.SetVisionEnabled -> handleSetVisionEnabled(intent.enabled)
            is HumanoidContract.Intent.OnModelLoaded -> handleOnModelLoaded(intent.renderer, intent.asset, intent.nodeNames)
            is HumanoidContract.Intent.OnSceneViewArModelLoaded -> handleOnSceneViewArModelLoaded(intent.engine, intent.asset, intent.nodeNames)
            is HumanoidContract.Intent.PlayAnimation -> handlePlayAnimation(intent.animationAsset)
            is HumanoidContract.Intent.StopAnimation -> handleStopAnimation()
            is HumanoidContract.Intent.StartIdleRotation -> handleStartIdleRotation()
            is HumanoidContract.Intent.StopIdleRotation -> handleStopIdleRotation()
            is HumanoidContract.Intent.ToggleIdleRotation -> handleToggleIdleRotation()
            is HumanoidContract.Intent.SpeakText -> handleSpeakText(intent.text, intent.durationMs)
            is HumanoidContract.Intent.StopSpeaking -> handleStopSpeaking()
            is HumanoidContract.Intent.TriggerBlink -> handleTriggerBlink()
            is HumanoidContract.Intent.UpdateBlendShapes -> handleUpdateBlendShapes(intent.deltaTime)
            is HumanoidContract.Intent.ResetAvatar -> handleResetAvatar()
            is HumanoidContract.Intent.ToggleDebugMode -> handleToggleDebugMode()
            is HumanoidContract.Intent.StopAllControllersBeforeCleanup -> handleStopAllControllersBeforeCleanup()
        }
    }

    // ==================== Intent Handlers ====================

    private fun handleLoadDefaultAvatar() {
        scope.launch {
            updateState { copy(isLoading = true, error = null) }

            try {
                val modelData = vrmLoader.loadVrmFromAssets("models/default_avatar.vrm")

                if (modelData != null) {
                    // Set available presets in blend shape controller
                    val presetNames = modelData.second.blendShapes.map { it.name }.toSet()
                    blendShapeController.setAvailablePresets(presetNames)

                    updateState {
                        copy(
                            isLoading = false,
                            avatarLoaded = true,
                            vrmModelData = modelData,
                            vrmExtensions = modelData.second
                        )
                    }

                    println("[HumanoidViewModel] Avatar loaded with ${presetNames.size} blend shape presets")
                } else {
                    updateState {
                        copy(
                            isLoading = false,
                            error = "Failed to load default avatar"
                        )
                    }
                    sendEffect(HumanoidContract.Effect.AvatarLoadError("Failed to load default avatar"))
                }
            } catch (e: Exception) {
                println("[HumanoidViewModel] ERROR: Error loading avatar: ${e.message}")
                updateState {
                    copy(
                        isLoading = false,
                        error = "Error loading avatar: ${e.message}"
                    )
                }
                sendEffect(HumanoidContract.Effect.AvatarLoadError("Error loading avatar: ${e.message}"))
            }
        }
    }

    /**
     * Load an avatar by ID from Firestore, then download and display its VRM.
     */
    fun loadAvatarById(avatarId: String) {
        val repo = avatarRepository ?: run {
            println("[HumanoidViewModel] AvatarRepository not available, falling back to default")
            return
        }
        val userId = authProvider?.currentUserId ?: run {
            println("[HumanoidViewModel] No authenticated user, falling back to default")
            return
        }

        scope.launch {
            updateState { copy(isLoading = true, error = null, avatarLoaded = false) }

            try {
                val avatar = repo.getAvatar(userId, avatarId)
                if (avatar == null || avatar.vrmUrl.isBlank()) {
                    println("[HumanoidViewModel] Avatar not found or no VRM URL, loading default")
                    handleLoadDefaultAvatar()
                    return@launch
                }

                println("[HumanoidViewModel] Loading VRM from URL: ${avatar.vrmUrl}")
                val modelData = vrmLoader.loadVrmFromUrl(avatar.vrmUrl)

                if (modelData != null) {
                    val presetNames = modelData.second.blendShapes.map { it.name }.toSet()
                    blendShapeController.setAvailablePresets(presetNames)

                    updateState {
                        copy(
                            isLoading = false,
                            avatarLoaded = true,
                            vrmModelData = modelData,
                            vrmExtensions = modelData.second
                        )
                    }
                    println("[HumanoidViewModel] Avatar '$avatarId' loaded with ${presetNames.size} blend shape presets")
                } else {
                    updateState { copy(isLoading = false, error = "Failed to load VRM from URL") }
                    sendEffect(HumanoidContract.Effect.AvatarLoadError("Failed to load VRM"))
                }
            } catch (e: Exception) {
                println("[HumanoidViewModel] ERROR loading avatar by ID: ${e.message}")
                updateState { copy(isLoading = false, error = "Error: ${e.message}") }
                sendEffect(HumanoidContract.Effect.AvatarLoadError("Error: ${e.message}"))
            }
        }
    }

    private fun handleSetEmotion(emotion: Emotion) {
        updateState { copy(avatarState = avatarState.copy(emotion = emotion)) }
    }

    private fun handleSetSpeaking(isSpeaking: Boolean) {
        updateState { copy(avatarState = avatarState.copy(isSpeaking = isSpeaking)) }
    }

    private fun handleSetListening(isListening: Boolean) {
        if (isListening) {
            updateState { copy(avatarState = AvatarState.listening()) }
        } else {
            updateState { copy(avatarState = avatarState.copy(isListening = false)) }
        }
    }

    private fun handleSetVisionEnabled(enabled: Boolean) {
        updateState { copy(avatarState = avatarState.copy(visionEnabled = enabled)) }
    }

    private fun handleOnModelLoaded(
        renderer: FilamentRenderer,
        asset: FilamentAsset,
        nodeNames: List<String>
    ) {
        println("[HumanoidViewModel] onModelLoaded called - initializing animation system")

        filamentRenderer = renderer

        // Get Engine from renderer
        val engine = renderer.getEngine()
        if (engine == null) {
            println("[HumanoidViewModel] ERROR: Engine not available from renderer")
            return
        }

        // Initialize bone mapper with the asset
        boneMapper.initialize(engine, asset, nodeNames)
        println("[HumanoidViewModel] BoneMapper initialized with ${boneMapper.getBoneEntityMap().size} bones")

        // Pass eye bone entities to renderer for LookAt (backup path)
        val leftEyeEntity = boneMapper.getBoneEntity(VrmHumanoidBoneMapper.HumanoidBone.LEFT_EYE)
        val rightEyeEntity = boneMapper.getBoneEntity(VrmHumanoidBoneMapper.HumanoidBone.RIGHT_EYE)
        if (leftEyeEntity != null || rightEyeEntity != null) {
            renderer.setEyeBoneEntities(leftEyeEntity ?: 0, rightEyeEntity ?: 0)
            println("[HumanoidViewModel] Eye bones from boneMapper: L=$leftEyeEntity R=$rightEyeEntity")
        }

        // Initialize animation player
        vrmaAnimationPlayer = vrmaAnimationPlayerFactory.initializeWithAsset(engine, asset, nodeNames)
        println("[HumanoidViewModel] VrmaAnimationPlayer initialized")

        updateState { copy(isAnimationSystemReady = true) }
        println("[HumanoidViewModel] Animation system is ready")

        // Pre-load idle animation for immediate use
        scope.launch {
            preloadCommonAnimations()
        }
    }

    private fun handleOnSceneViewArModelLoaded(
        engine: com.google.android.filament.Engine,
        asset: FilamentAsset,
        nodeNames: List<String>
    ) {
        println("[HumanoidViewModel] onSceneViewArModelLoaded called - initializing AR animation system")

        boneMapper.initialize(engine, asset, nodeNames)
        println("[HumanoidViewModel] BoneMapper initialized (AR/SceneView) with ${boneMapper.getBoneEntityMap().size} bones")

        vrmaAnimationPlayer = vrmaAnimationPlayerFactory.initializeWithAsset(engine, asset, nodeNames)
        updateState { copy(isAnimationSystemReady = true) }

        scope.launch {
            preloadCommonAnimations()
        }
    }

    private fun handlePlayAnimation(animationAsset: VrmaAnimationLoader.AnimationAsset) {
        scope.launch {
            // Check if animation system is ready
            if (!currentState.isAnimationSystemReady) {
                println("[HumanoidViewModel] WARNING: Animation system not ready yet")
                return@launch
            }

            val player = vrmaAnimationPlayer
            if (player == null) {
                println("[HumanoidViewModel] ERROR: Animation player not initialized")
                sendEffect(HumanoidContract.Effect.AnimationError("Animation player not initialized"))
                return@launch
            }

            // Check cache first, load if needed
            val animation = loadedAnimations.getOrPut(animationAsset) {
                vrmaAnimationLoader.loadAnimation(animationAsset) ?: run {
                    println("[HumanoidViewModel] ERROR: Failed to load animation: ${animationAsset.fileName}")
                    sendEffect(HumanoidContract.Effect.AnimationError("Failed to load animation: ${animationAsset.fileName}"))
                    return@launch
                }
            }

            // Update state
            updateState {
                copy(
                    currentAnimation = animation,
                    isPlayingAnimation = true,
                    currentAnimationName = animation.name
                )
            }

            println("[HumanoidViewModel] Playing animation: ${animation.name}, duration: ${animation.durationSeconds}s, looping: ${animation.isLooping}")

            // Following Amica pattern:
            // - idle animations play in loop as the default state
            // - Other animations play once then return to idle
            if (animationAsset.isIdle()) {
                // Set as the new idle animation
                player.setIdleAnimation(animation, scope)
            } else {
                // Pause idle rotation during non-idle animation
                pauseIdleRotation()

                // Play as one-shot, will automatically return to idle
                player.playOneShot(
                    animation = animation,
                    scope = scope,
                    fadeDuration = 0.5f,
                    onComplete = {
                        // Resume idle rotation when animation completes
                        resumeIdleRotation()
                    }
                )
            }
        }
    }

    private fun handleStopAnimation() {
        val player = vrmaAnimationPlayer
        if (player != null && player.hasIdleAnimation()) {
            // Return to idle animation
            player.getIdleAnimation()?.let { idleAnim ->
                player.setIdleAnimation(idleAnim, scope)
                updateState {
                    copy(
                        currentAnimation = idleAnim,
                        isPlayingAnimation = true,
                        currentAnimationName = idleAnim.name
                    )
                }
                println("[HumanoidViewModel] Returned to idle animation")
            }
        } else {
            player?.stop()
            updateState {
                copy(
                    currentAnimation = null,
                    isPlayingAnimation = false,
                    currentAnimationName = null
                )
            }
            println("[HumanoidViewModel] Animation stopped (no idle available)")
        }
    }

    private fun handleStartIdleRotation() {
        if (idleRotationController == null) {
            initializeIdleRotation()
        }
        idleRotationController?.start(scope)
        println("[HumanoidViewModel] Idle rotation started")
    }

    private fun handleStopIdleRotation() {
        idleRotationController?.stop()
        println("[HumanoidViewModel] Idle rotation stopped")
    }

    private fun handleToggleIdleRotation() {
        val controller = idleRotationController
        val isActive = if (controller?.isActive?.value == true) {
            handleStopIdleRotation()
            false
        } else {
            handleStartIdleRotation()
            true
        }
        sendEffect(HumanoidContract.Effect.IdleRotationToggled(isActive))
    }

    private fun handleSpeakText(text: String, durationMs: Long) {
        lipSyncController.speak(text, durationMs, scope)
    }

    private fun handleStopSpeaking() {
        lipSyncController.stop()
    }

    private fun handleTriggerBlink() {
        scope.launch {
            blinkController.triggerBlink()
        }
    }

    private fun handleUpdateBlendShapes(deltaTime: Float) {
        blendShapeController.update(deltaTime)
    }

    private fun handleResetAvatar() {
        updateState { copy(avatarState = AvatarState.Default) }
        blendShapeController.reset()
        handleStopAnimation()
        handleStopSpeaking()
    }

    private fun handleToggleDebugMode() {
        updateState { copy(debugMode = !debugMode) }
    }

    private fun handleStopAllControllersBeforeCleanup() {
        println("[HumanoidViewModel] stopAllControllersBeforeCleanup - stopping all animation controllers")
        blinkController.stop()
        lipSyncController.stop()
        idleRotationController?.stop()
        // Mark animation player as destroyed to prevent accessing Filament assets
        vrmaAnimationPlayer?.stop(blendOut = false, destroy = true)
        // Null out the reference so any late scope.launch callbacks
        // (e.g., from IdleRotationController's onPlayAnimation) see null and skip.
        vrmaAnimationPlayer = null
        updateState { copy(isAnimationSystemReady = false) }
        println("[HumanoidViewModel] All controllers stopped and marked as destroyed")
    }

    // ==================== Private Helpers ====================

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

        println("[HumanoidViewModel] Pre-loading ${animationsToPreload.size} animations (${idleAnimations.size} idle animations)")

        animationsToPreload.forEach { animationAsset ->
            if (!loadedAnimations.containsKey(animationAsset)) {
                vrmaAnimationLoader.loadAnimation(animationAsset)?.let { animation ->
                    loadedAnimations[animationAsset] = animation
                    println("[HumanoidViewModel] Pre-loaded animation: ${animation.name}")
                }
            }
        }

        // Initialize and start idle rotation system automatically
        // This will rotate between idle animations every 10-40 seconds
        initializeIdleRotation()
        handleStartIdleRotation()
        println("[HumanoidViewModel] Idle rotation system started with ${idleAnimations.size} animations")
    }

    /**
     * Initialize idle rotation controller (called when animation system is ready)
     */
    private fun initializeIdleRotation() {
        idleRotationController = IdleRotationController { animationAsset ->
            scope.launch {
                val animation = loadedAnimations.getOrPut(animationAsset) {
                    vrmaAnimationLoader.loadAnimation(animationAsset) ?: return@launch
                }
                vrmaAnimationPlayer?.let { player ->
                    println("[HumanoidViewModel] Idle rotation: playing ${animation.name}")
                    player.setIdleAnimation(animation, scope)
                    updateState {
                        copy(
                            currentAnimation = animation,
                            currentIdleAnimation = animationAsset,
                            isPlayingAnimation = true,
                            currentAnimationName = animation.name
                        )
                    }
                }
            }
        }

        // Observe idle rotation state
        scope.launch {
            idleRotationController?.isActive?.collect { isActive ->
                updateState { copy(isIdleRotationActive = isActive) }
            }
        }
        scope.launch {
            idleRotationController?.currentIdle?.collect { idle ->
                updateState { copy(currentIdleAnimation = idle) }
            }
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
        if (currentState.isIdleRotationActive) {
            idleRotationController?.resume(scope)
        }
    }

    /**
     * Update blend shapes based on current avatar state.
     * Uses VrmBlendShapePresets for better compatibility.
     */
    private fun updateBlendShapesForState(avatarState: AvatarState) {
        val emotionName = avatarState.emotion.getName().lowercase()
        val intensity = avatarState.emotion.intensity

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

    // ==================== Backward-Compatible Public Wrappers ====================
    // These thin wrappers delegate to onIntent() so existing callers keep working.

    /** Load the default VRM avatar from assets */
    fun loadDefaultAvatar() = onIntent(HumanoidContract.Intent.LoadDefaultAvatar)

    /** Set avatar emotion with improved preset matching */
    fun setEmotion(emotion: Emotion) = onIntent(HumanoidContract.Intent.SetEmotion(emotion))

    /** Set avatar to speaking state */
    fun setSpeaking(isSpeaking: Boolean) = onIntent(HumanoidContract.Intent.SetSpeaking(isSpeaking))

    /** Set avatar to listening state */
    fun setListening(isListening: Boolean) = onIntent(HumanoidContract.Intent.SetListening(isListening))

    /** Enable/disable vision */
    fun setVisionEnabled(enabled: Boolean) = onIntent(HumanoidContract.Intent.SetVisionEnabled(enabled))

    /**
     * Called when the VRM model is loaded by FilamentRenderer.
     * Initializes the animation player with the loaded asset.
     */
    fun onModelLoaded(
        renderer: FilamentRenderer,
        asset: FilamentAsset,
        nodeNames: List<String>
    ) = onIntent(HumanoidContract.Intent.OnModelLoaded(renderer, asset, nodeNames))

    /**
     * Called when the VRM model is loaded in AR mode (SceneView).
     * Takes Engine directly (SceneView provides its own Engine).
     */
    fun onSceneViewArModelLoaded(
        engine: com.google.android.filament.Engine,
        asset: FilamentAsset,
        nodeNames: List<String>
    ) = onIntent(HumanoidContract.Intent.OnSceneViewArModelLoaded(engine, asset, nodeNames))

    /** Play a VRMA animation by asset type */
    fun playAnimation(animationAsset: VrmaAnimationLoader.AnimationAsset) =
        onIntent(HumanoidContract.Intent.PlayAnimation(animationAsset))

    /** Stop current one-shot animation and return to idle */
    fun stopAnimation() = onIntent(HumanoidContract.Intent.StopAnimation)

    /** Start automatic idle animation rotation */
    fun startIdleRotation() = onIntent(HumanoidContract.Intent.StartIdleRotation)

    /** Stop automatic idle animation rotation */
    fun stopIdleRotation() = onIntent(HumanoidContract.Intent.StopIdleRotation)

    /** Toggle automatic idle animation rotation */
    fun toggleIdleRotation(): Boolean {
        onIntent(HumanoidContract.Intent.ToggleIdleRotation)
        // Return the new state (after toggle)
        return currentState.isIdleRotationActive
    }

    /** Start lip-sync for text */
    fun speakText(text: String, durationMs: Long) =
        onIntent(HumanoidContract.Intent.SpeakText(text, durationMs))

    /** Stop lip-sync */
    fun stopSpeaking() = onIntent(HumanoidContract.Intent.StopSpeaking)

    /** Trigger a manual blink */
    fun triggerBlink() = onIntent(HumanoidContract.Intent.TriggerBlink)

    /** Update blend shapes - call every frame */
    fun updateBlendShapes(deltaTime: Float) =
        onIntent(HumanoidContract.Intent.UpdateBlendShapes(deltaTime))

    /** Reset avatar to default state */
    fun resetAvatar() = onIntent(HumanoidContract.Intent.ResetAvatar)

    /** Toggle debug mode */
    fun toggleDebugMode() = onIntent(HumanoidContract.Intent.ToggleDebugMode)

    /**
     * Stop all animation controllers before Filament cleanup.
     * CRITICAL: Must be called BEFORE FilamentRenderer.cleanup() to prevent
     * accessing destroyed Filament assets.
     */
    fun stopAllControllersBeforeCleanup() =
        onIntent(HumanoidContract.Intent.StopAllControllersBeforeCleanup)

    // ==================== Lifecycle ====================

    override fun onCleared() {
        super.onCleared()
        blinkController.stop()
        lipSyncController.stop()
        idleRotationController?.stop()
        vrmaAnimationPlayer?.stop(blendOut = false, destroy = true)
        vrmaAnimationPlayerFactory.clear()
        filamentRenderer = null
        // State is already dead at this point; no need to updateState
        println("[HumanoidViewModel] HumanoidViewModel cleared")
    }
}
