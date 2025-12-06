package com.lifo.humanoid.animation

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.FilamentAsset
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper.HumanoidBone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.floor

/**
 * Player for VRMA animations.
 * Handles playback, blending, and application of animation data to VRM models.
 *
 * Implements proper animation retargeting following Amica/three-vrm approach:
 * - VRMA animations contain rotations in the animation's rest pose space
 * - To apply to a different model, we need to transform: finalRot = parentWorldQuat * animQuat * boneWorldQuat^-1
 * - This converts from animation space to the target model's local bone space
 *
 * CRITICAL: After applying transforms, updateBoneMatrices() must be called on the
 * Animator to update the skinning matrices. Without this, transforms won't affect
 * the visible mesh!
 *
 * Reference: amica-master VRMAnimationLoaderPlugin.ts
 */
class VrmaAnimationPlayer(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {

    companion object {
        private const val TAG = "VrmaAnimationPlayer"

        /**
         * Mapping from common VRMA animation bone names to VRM humanoid bone names.
         * Different animation sources use different naming conventions.
         */
        private val ANIMATION_TO_VRM_BONE_MAP = mapOf(
            // Root
            "root" to "hips",

            // Torso/Spine (many animations use torso_1, torso_2, etc.)
            "torso_1" to "spine",
            "torso_2" to "chest",
            "torso_3" to "upperChest",
            "torso_4" to "upperChest",
            "torso_5" to "upperChest",
            "torso_6" to "upperChest",
            "torso_7" to "upperChest",

            // Neck
            "neck_1" to "neck",
            "neck_2" to "neck",

            // Head
            "head" to "head",

            // Left Arm
            "l_shoulder" to "leftShoulder",
            "l_up_arm" to "leftUpperArm",
            "l_low_arm" to "leftLowerArm",
            "l_hand" to "leftHand",

            // Right Arm
            "r_shoulder" to "rightShoulder",
            "r_up_arm" to "rightUpperArm",
            "r_low_arm" to "rightLowerArm",
            "r_hand" to "rightHand",

            // Left Leg
            "l_up_leg" to "leftUpperLeg",
            "l_low_leg" to "leftLowerLeg",
            "l_foot" to "leftFoot",
            "l_toes" to "leftToes",

            // Right Leg
            "r_up_leg" to "rightUpperLeg",
            "r_low_leg" to "rightLowerLeg",
            "r_foot" to "rightFoot",
            "r_toes" to "rightToes",

            // Standard VRM names (pass-through)
            "hips" to "hips",
            "spine" to "spine",
            "spine1" to "spine",
            "spine2" to "chest",
            "spine3" to "upperChest",
            "chest" to "chest",
            "upperchest" to "upperChest",
            "neck" to "neck",

            // Mixamo-style names
            "mixamorigHips" to "hips",
            "mixamorigSpine" to "spine",
            "mixamorigSpine1" to "chest",
            "mixamorigSpine2" to "upperChest",
            "mixamorigNeck" to "neck",
            "mixamorigHead" to "head",
            "mixamorigLeftShoulder" to "leftShoulder",
            "mixamorigLeftArm" to "leftUpperArm",
            "mixamorigLeftForeArm" to "leftLowerArm",
            "mixamorigLeftHand" to "leftHand",
            "mixamorigRightShoulder" to "rightShoulder",
            "mixamorigRightArm" to "rightUpperArm",
            "mixamorigRightForeArm" to "rightLowerArm",
            "mixamorigRightHand" to "rightHand",
            "mixamorigLeftUpLeg" to "leftUpperLeg",
            "mixamorigLeftLeg" to "leftLowerLeg",
            "mixamorigLeftFoot" to "leftFoot",
            "mixamorigLeftToeBase" to "leftToes",
            "mixamorigRightUpLeg" to "rightUpperLeg",
            "mixamorigRightLeg" to "rightLowerLeg",
            "mixamorigRightFoot" to "rightFoot",
            "mixamorigRightToeBase" to "rightToes"
        )
    }

    private val _currentAnimation = MutableStateFlow<VrmaAnimation?>(null)
    val currentAnimation: StateFlow<VrmaAnimation?> = _currentAnimation

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress

    private var playbackJob: Job? = null
    private var currentTimeSeconds: Float = 0f
    private var playbackSpeed: Float = 1.0f

    // ==================== Idle Animation System (following Amica pattern) ====================

    // The idle animation that plays in loop as default
    private var idleAnimation: VrmaAnimation? = null
    private var idleAnimationScope: CoroutineScope? = null

    // Flag to track if we're playing a one-shot animation
    private var isPlayingOneShot = false

    // Fade duration for transitions (following Amica)
    private var fadeInDuration: Float = 0.5f
    private var fadeOutDuration: Float = 1.0f

    // Node name to entity mapping for the current model
    private val nodeEntityMap = mutableMapOf<String, Int>()
    private val humanoidNodeMap = mutableMapOf<HumanoidBone, String>()

    // VRM humanoid bone name (from animation) to HumanoidBone enum mapping
    private val vrmBoneNameToEnum = mutableMapOf<String, HumanoidBone>()

    // Original transforms for blending/reset
    private val originalTransforms = mutableMapOf<Int, FloatArray>()

    // World matrices for proper transform application (following amica pattern)
    private val boneWorldMatrices = mutableMapOf<HumanoidBone, FloatArray>()
    private var hipsParentWorldMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }

    // Parent bone mapping for proper transform chain (following amica VRMHumanBoneParentMap)
    private val humanoidBoneParentMap = mapOf(
        HumanoidBone.HIPS to null,
        HumanoidBone.SPINE to HumanoidBone.HIPS,
        HumanoidBone.CHEST to HumanoidBone.SPINE,
        HumanoidBone.UPPER_CHEST to HumanoidBone.CHEST,
        HumanoidBone.NECK to HumanoidBone.UPPER_CHEST,
        HumanoidBone.HEAD to HumanoidBone.NECK,
        HumanoidBone.LEFT_SHOULDER to HumanoidBone.UPPER_CHEST,
        HumanoidBone.LEFT_UPPER_ARM to HumanoidBone.LEFT_SHOULDER,
        HumanoidBone.LEFT_LOWER_ARM to HumanoidBone.LEFT_UPPER_ARM,
        HumanoidBone.LEFT_HAND to HumanoidBone.LEFT_LOWER_ARM,
        HumanoidBone.RIGHT_SHOULDER to HumanoidBone.UPPER_CHEST,
        HumanoidBone.RIGHT_UPPER_ARM to HumanoidBone.RIGHT_SHOULDER,
        HumanoidBone.RIGHT_LOWER_ARM to HumanoidBone.RIGHT_UPPER_ARM,
        HumanoidBone.RIGHT_HAND to HumanoidBone.RIGHT_LOWER_ARM,
        HumanoidBone.LEFT_UPPER_LEG to HumanoidBone.HIPS,
        HumanoidBone.LEFT_LOWER_LEG to HumanoidBone.LEFT_UPPER_LEG,
        HumanoidBone.LEFT_FOOT to HumanoidBone.LEFT_LOWER_LEG,
        HumanoidBone.LEFT_TOES to HumanoidBone.LEFT_FOOT,
        HumanoidBone.RIGHT_UPPER_LEG to HumanoidBone.HIPS,
        HumanoidBone.RIGHT_LOWER_LEG to HumanoidBone.RIGHT_UPPER_LEG,
        HumanoidBone.RIGHT_FOOT to HumanoidBone.RIGHT_LOWER_LEG,
        HumanoidBone.RIGHT_TOES to HumanoidBone.RIGHT_FOOT
    )

    // Rest pose quaternions for proper rotation transformation (computed from world matrices)
    private val boneWorldQuaternions = mutableMapOf<HumanoidBone, FloatArray>()
    private val boneWorldQuaternionInverses = mutableMapOf<HumanoidBone, FloatArray>()
    private var hipsParentWorldQuaternion: FloatArray = floatArrayOf(0f, 0f, 0f, 1f) // Identity

    // Blend weight for smooth transitions
    private var blendWeight: Float = 1f
    private var blendDuration: Float = 0.3f // seconds

    // Track if properly initialized
    private var isInitialized = false

    // Track if destroyed - CRITICAL for preventing native crashes
    private var isDestroyed = false

    // Reference to Animator for updateBoneMatrices() - CRITICAL for skinning
    private var animator: Animator? = null

    /**
     * Initialize with a FilamentAsset to build node mapping
     */
    fun initialize(asset: FilamentAsset, nodeNames: List<String>) {
        if (isDestroyed) {
            Log.w(TAG, "Cannot initialize - player is destroyed")
            return
        }

        nodeEntityMap.clear()
        vrmBoneNameToEnum.clear()
        boneWorldMatrices.clear()
        boneWorldQuaternions.clear()
        boneWorldQuaternionInverses.clear()
        animNodeToHumanoidBone.clear()
        humanoidNodeMap.clear()

        // Store reference to Animator for bone matrix updates
        // This is CRITICAL - without updateBoneMatrices(), transforms don't affect skinned mesh!
        // Note: Animator is obtained from FilamentInstance (asset.getInstance()), not directly from FilamentAsset
        try {
            animator = asset.getInstance()?.animator
            Log.d(TAG, "Animator obtained from asset instance: ${animator != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Error obtaining animator: ${e.message}")
            return
        }

        // Build node name to entity mapping with safety checks
        nodeNames.forEachIndexed { index, name ->
            if (isDestroyed) return

            try {
                if (index < asset.entities.size) {
                    nodeEntityMap[name] = asset.entities[index]
                    Log.d(TAG, "Node mapping: '$name' -> entity ${asset.entities[index]}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping node '$name': ${e.message}")
            }
        }

        // Build VRM bone name to enum mapping for fast lookups
        HumanoidBone.entries.forEach { bone ->
            vrmBoneNameToEnum[bone.vrmName.lowercase()] = bone
            // Also add camelCase variant
            vrmBoneNameToEnum[bone.vrmName] = bone
        }

        // Calculate and store world matrices for all mapped bones
        calculateBoneWorldMatrices()

        // Store original transforms
        storeOriginalTransforms()

        isInitialized = true
        Log.d(TAG, "Initialized with ${nodeEntityMap.size} node mappings, ${boneMapper.getMappedBones().size} humanoid bones")
    }

    /**
     * Calculate world matrices and quaternions for all humanoid bones.
     * This is essential for correct VRMA animation application (following amica pattern).
     *
     * Following amica VRMAnimationLoaderPlugin.ts:
     * - Store world matrix for each bone
     * - Extract world quaternion for rotation transformation
     * - Compute inverse quaternions for the animation retargeting formula
     */
    private fun calculateBoneWorldMatrices() {
        if (isDestroyed) {
            Log.w(TAG, "Cannot calculate bone matrices - player is destroyed")
            return
        }

        try {
            val tm = engine.transformManager

            boneMapper.getBoneEntityMap().forEach { (bone, entity) ->
                if (isDestroyed) return

                try {
                    val instance = tm.getInstance(entity)
                    if (instance != 0) {
                        val worldMatrix = FloatArray(16)
                        tm.getWorldTransform(instance, worldMatrix)
                        boneWorldMatrices[bone] = worldMatrix

                        // Extract world quaternion from world matrix
                        val sx = kotlin.math.sqrt(worldMatrix[0] * worldMatrix[0] + worldMatrix[1] * worldMatrix[1] + worldMatrix[2] * worldMatrix[2])
                        val sy = kotlin.math.sqrt(worldMatrix[4] * worldMatrix[4] + worldMatrix[5] * worldMatrix[5] + worldMatrix[6] * worldMatrix[6])
                        val sz = kotlin.math.sqrt(worldMatrix[8] * worldMatrix[8] + worldMatrix[9] * worldMatrix[9] + worldMatrix[10] * worldMatrix[10])

                        val worldQuat = matrixToQuaternion(worldMatrix, sx, sy, sz)
                        boneWorldQuaternions[bone] = worldQuat
                        boneWorldQuaternionInverses[bone] = invertQuaternion(worldQuat)

                        // Store hips parent world matrix and quaternion
                        if (bone == HumanoidBone.HIPS) {
                            // For now, use identity as parent (root level)
                            hipsParentWorldMatrix = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
                            hipsParentWorldQuaternion = floatArrayOf(0f, 0f, 0f, 1f) // Identity
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating world matrix for bone $bone: ${e.message}")
                }
            }

            Log.d(TAG, "Calculated world matrices and quaternions for ${boneWorldMatrices.size} bones")
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculateBoneWorldMatrices: ${e.message}")
        }
    }

    /**
     * Invert a quaternion.
     * For a unit quaternion, the inverse is the conjugate: [-x, -y, -z, w]
     */
    private fun invertQuaternion(q: FloatArray): FloatArray {
        return floatArrayOf(-q[0], -q[1], -q[2], q[3])
    }

    // Animation node name -> HumanoidBone mapping (inverse of humanoidNodeMap for fast lookup)
    private val animNodeToHumanoidBone = mutableMapOf<String, HumanoidBone>()

    /**
     * Build humanoid bone to node name mapping from animation.
     * Maps animation node names to VRM humanoid bones.
     *
     * The VRMC_vrm_animation extension provides:
     * humanBones: { "hips": { node: 0 }, "spine": { node: 1 }, ... }
     *
     * After parsing in VrmaAnimationLoader, we get:
     * humanoidBoneMapping: { "hips": "root", "spine": "torso_1", ... }
     * where "root", "torso_1" are the animation's internal node names
     *
     * We build TWO mappings:
     * 1. humanoidNodeMap: HumanoidBone -> animation node name (for looking up what node name an animation uses for a bone)
     * 2. animNodeToHumanoidBone: animation node name -> HumanoidBone (for resolving track.nodeName to bone)
     */
    fun buildHumanoidMapping(animation: VrmaAnimation) {
        humanoidNodeMap.clear()
        animNodeToHumanoidBone.clear()

        // The animation.humanoidBoneMapping maps VRM bone name (e.g., "hips") -> animation node name
        animation.humanoidBoneMapping.forEach { (vrmBoneName, animationNodeName) ->
            val humanoidBone = vrmBoneNameToEnum[vrmBoneName.lowercase()]
                ?: vrmBoneNameToEnum[vrmBoneName]
                ?: HumanoidBone.entries.firstOrNull {
                    it.vrmName.equals(vrmBoneName, ignoreCase = true)
                }

            if (humanoidBone != null) {
                humanoidNodeMap[humanoidBone] = animationNodeName
                animNodeToHumanoidBone[animationNodeName] = humanoidBone
                Log.d(TAG, "Humanoid mapping: $vrmBoneName -> $humanoidBone (animation node: $animationNodeName)")
            } else {
                Log.w(TAG, "Unknown VRM bone name in animation: $vrmBoneName")
            }
        }

        Log.d(TAG, "Built humanoid mapping: ${humanoidNodeMap.size} bones from animation")
    }

    /**
     * Play an animation
     *
     * @param animation The VRMA animation to play
     * @param scope CoroutineScope for playback
     * @param loop Override the animation's loop setting
     * @param speed Playback speed multiplier (1.0 = normal)
     */
    fun play(
        animation: VrmaAnimation,
        scope: CoroutineScope,
        loop: Boolean = animation.isLooping,
        speed: Float = 1.0f
    ) {
        if (!isInitialized) {
            Log.e(TAG, "Cannot play animation - player not initialized!")
            return
        }

        stop()

        _currentAnimation.value = animation
        _isPlaying.value = true
        currentTimeSeconds = 0f
        playbackSpeed = speed
        blendWeight = 0f // Start blending in

        // Build humanoid mapping from animation to model bones
        buildHumanoidMapping(animation)

        // Set animation rest pose data for proper retargeting (following amica pattern)
        setAnimationRestPoseData(animation)

        // Reset debug frame counter at start of new animation
        rotationDebugFrameCount = 0

        Log.d(TAG, "=== Playing Animation ===")
        Log.d(TAG, "Name: ${animation.name}, Duration: ${animation.durationSeconds}s, Loop: $loop")
        Log.d(TAG, "Tracks: ${animation.tracks.size}, HumanoidBoneMapping: ${animation.humanoidBoneMapping.size}")
        Log.d(TAG, "HumanoidNodeMap after build: ${humanoidNodeMap.size} entries")

        // Log detailed mapping resolution
        var resolvedCount = 0
        var unresolvedCount = 0
        animation.tracks.take(5).forEach { track ->
            val entity = resolveEntity(track.nodeName)
            if (entity != null) {
                resolvedCount++
                Log.d(TAG, "Track '${track.nodeName}' (${track.path}) -> entity $entity")
            } else {
                unresolvedCount++
                Log.w(TAG, "Track '${track.nodeName}' (${track.path}) -> UNRESOLVED")
            }
        }
        if (animation.tracks.size > 5) {
            Log.d(TAG, "... and ${animation.tracks.size - 5} more tracks")
        }

        // Count total resolved
        val totalResolved = animation.tracks.count { resolveEntity(it.nodeName) != null }
        Log.d(TAG, "Resolution stats: $totalResolved/${animation.tracks.size} tracks resolved")
        Log.d(TAG, "=== Starting Playback ===")

        playbackJob = scope.launch {
            var lastFrameTime = System.nanoTime()
            val blendInDuration = blendDuration

            while (isActive && _isPlaying.value) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime

                // Update blend weight (smooth blend in)
                if (blendWeight < 1f) {
                    blendWeight = (blendWeight + deltaTime / blendInDuration).coerceAtMost(1f)
                }

                // Update animation time
                currentTimeSeconds += deltaTime * playbackSpeed

                // Handle looping or completion
                if (currentTimeSeconds >= animation.durationSeconds) {
                    if (loop) {
                        currentTimeSeconds %= animation.durationSeconds
                    } else {
                        // Play to end, then stop
                        currentTimeSeconds = animation.durationSeconds
                        applyAnimation(animation, currentTimeSeconds)
                        stop()
                        break
                    }
                }

                // Update progress
                _playbackProgress.value = currentTimeSeconds / animation.durationSeconds

                // Apply animation
                withContext(Dispatchers.Main) {
                    applyAnimation(animation, currentTimeSeconds)
                }

                // Target ~60fps
                delay(16)
            }
        }
    }

    /**
     * Stop the current animation with optional blend out.
     *
     * @param blendOut Whether to blend out (currently not implemented)
     * @param destroy If true, marks the player as destroyed to prevent further use
     */
    fun stop(blendOut: Boolean = true, destroy: Boolean = false) {
        if (blendOut && _isPlaying.value) {
            // Could implement blend out here
        }

        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _currentAnimation.value = null
        currentTimeSeconds = 0f
        _playbackProgress.value = 0f

        // Reset to original pose (if not destroying)
        if (!destroy && !isDestroyed) {
            resetToOriginalPose()
        }

        // Mark as destroyed if requested
        if (destroy) {
            Log.d(TAG, "VrmaAnimationPlayer destroyed")
            isDestroyed = true
            animator = null
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
        // Keep isPlaying true to indicate paused state
    }

    /**
     * Resume paused playback
     */
    fun resume(scope: CoroutineScope) {
        val animation = _currentAnimation.value ?: return
        if (!_isPlaying.value) return

        play(animation, scope, animation.isLooping, playbackSpeed)
    }

    /**
     * Set playback speed
     */
    fun setSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.1f, 3f)
    }

    /**
     * Seek to a specific time
     */
    fun seekTo(timeSeconds: Float) {
        val animation = _currentAnimation.value ?: return
        currentTimeSeconds = timeSeconds.coerceIn(0f, animation.durationSeconds)
        _playbackProgress.value = currentTimeSeconds / animation.durationSeconds
    }

    // ==================== Idle Animation System (following Amica model.ts pattern) ====================

    /**
     * Set and start the idle animation.
     * This animation will play in a continuous loop as the default state.
     * Following Amica's loadAnimation() pattern.
     *
     * @param animation The idle animation (typically idle_loop.vrma)
     * @param scope CoroutineScope for playback
     */
    fun setIdleAnimation(animation: VrmaAnimation, scope: CoroutineScope) {
        Log.d(TAG, "Setting idle animation: ${animation.name}")

        idleAnimation = animation
        idleAnimationScope = scope

        // Start playing idle immediately if not playing a one-shot
        if (!isPlayingOneShot) {
            startIdleLoop()
        }
    }

    /**
     * Start or restart the idle animation loop.
     * Internal method used after one-shot animations complete.
     */
    private fun startIdleLoop() {
        val idle = idleAnimation ?: return
        val scope = idleAnimationScope ?: return

        Log.d(TAG, "Starting idle loop: ${idle.name}")

        // Set animation rest pose data
        setAnimationRestPoseData(idle)

        // Play in loop
        play(
            animation = idle,
            scope = scope,
            loop = true,
            speed = 1.0f
        )
    }

    /**
     * Play a one-shot animation, then automatically return to idle.
     * Following Amica's playAnimation() pattern.
     *
     * @param animation The animation to play once
     * @param scope CoroutineScope for playback
     * @param fadeDuration Duration of crossfade transitions in seconds
     * @return The total duration of the animation including fade times
     */
    fun playOneShot(
        animation: VrmaAnimation,
        scope: CoroutineScope,
        fadeDuration: Float = 0.5f
    ): Float {
        if (idleAnimation == null) {
            Log.w(TAG, "No idle animation set, playing as regular animation")
            play(animation, scope, loop = false)
            return animation.durationSeconds
        }

        Log.d(TAG, "Playing one-shot animation: ${animation.name}, duration: ${animation.durationSeconds}s")

        isPlayingOneShot = true
        fadeOutDuration = fadeDuration
        fadeInDuration = fadeDuration

        // Set animation rest pose data for proper retargeting
        setAnimationRestPoseData(animation)

        // Stop current playback
        playbackJob?.cancel()

        _currentAnimation.value = animation
        _isPlaying.value = true
        currentTimeSeconds = 0f
        blendWeight = 0f // Start with blend weight 0 for fade-in

        playbackJob = scope.launch {
            var lastFrameTime = System.nanoTime()

            // Phase 1: Fade in
            while (isActive && blendWeight < 1f) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime

                blendWeight = (blendWeight + deltaTime / fadeInDuration).coerceAtMost(1f)
                currentTimeSeconds += deltaTime * playbackSpeed

                _playbackProgress.value = currentTimeSeconds / animation.durationSeconds

                withContext(Dispatchers.Main) {
                    applyAnimation(animation, currentTimeSeconds)
                }

                delay(16)
            }

            // Phase 2: Main playback
            while (isActive && currentTimeSeconds < animation.durationSeconds) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime

                currentTimeSeconds += deltaTime * playbackSpeed
                _playbackProgress.value = (currentTimeSeconds / animation.durationSeconds).coerceAtMost(1f)

                withContext(Dispatchers.Main) {
                    applyAnimation(animation, currentTimeSeconds.coerceAtMost(animation.durationSeconds))
                }

                delay(16)
            }

            // Phase 3: Return to idle with fade
            Log.d(TAG, "One-shot animation complete, returning to idle")
            isPlayingOneShot = false

            // Restart idle animation
            startIdleLoop()
        }

        // Return total duration (animation + fade in + fade out)
        return animation.durationSeconds + fadeInDuration + fadeOutDuration
    }

    /**
     * Check if idle animation is set and ready.
     */
    fun hasIdleAnimation(): Boolean = idleAnimation != null

    /**
     * Get the current idle animation (if set).
     */
    fun getIdleAnimation(): VrmaAnimation? = idleAnimation

    /**
     * Apply animation at a specific time.
     *
     * Following amica-master pattern:
     * - Rotations need to be transformed from animation space to local bone space
     * - Translations need to be scaled based on model proportions
     */
    private fun applyAnimation(animation: VrmaAnimation, time: Float) {
        if (isDestroyed) {
            return
        }

        try {
            val tm = engine.transformManager
            var appliedCount = 0
            var skippedCount = 0

            animation.tracks.forEach { track ->
                if (isDestroyed) return

                val entity = resolveEntity(track.nodeName)
                if (entity == null) {
                    skippedCount++
                    return@forEach
                }

                val instance = tm.getInstance(entity)
                if (instance == 0) {
                    skippedCount++
                    return@forEach
                }

                // Interpolate keyframes
                val interpolatedValues = interpolateTrack(track, time)
                if (interpolatedValues.isEmpty()) {
                    skippedCount++
                    return@forEach
                }

                // Get the humanoid bone for this track (if it is one)
                val humanoidBone = resolveHumanoidBone(track.nodeName)

                // Apply based on path type
                try {
                    when (track.path) {
                        AnimationPath.ROTATION -> {
                            applyRotationWithWorldTransform(tm, instance, entity, interpolatedValues, humanoidBone)
                            appliedCount++
                        }
                        AnimationPath.TRANSLATION -> {
                            // Only apply translation to hips (root motion)
                            if (humanoidBone == HumanoidBone.HIPS) {
                                applyTranslationWithScale(tm, instance, entity, interpolatedValues)
                                appliedCount++
                            }
                        }
                        AnimationPath.SCALE -> {
                            applyScale(tm, instance, entity, interpolatedValues)
                            appliedCount++
                        }
                        AnimationPath.WEIGHTS -> { /* Handle morph targets if needed */ }
                    }
                } catch (e: Exception) {
                    // Silently ignore transform errors during cleanup
                }
            }

            // CRITICAL: Update bone matrices for skinning!
            // Without this call, the transform changes don't affect the visible skinned mesh.
            // This is the key step that was missing - Filament separates transform updates
            // from skinning matrix updates for performance reasons.
            if (!isDestroyed) {
                try {
                    animator?.updateBoneMatrices()
                } catch (e: Exception) {
                    // Silently ignore updateBoneMatrices errors during cleanup
                }
            }

            // Increment debug frame counter after processing all tracks
            rotationDebugFrameCount++

            // Log stats periodically (every 60 frames = ~1 second)
            if ((time * 60).toInt() % 60 == 0) {
                Log.d(TAG, "Animation frame: applied=$appliedCount, skipped=$skippedCount tracks, time=${time}s, updateBoneMatrices called")
            }
        } catch (e: Exception) {
            // Silently ignore applyAnimation errors during cleanup
        }
    }

    /**
     * Resolve a node name from animation to its Filament entity.
     *
     * VRMA animations use their own node names in tracks that must be mapped to
     * the model's bone entities through the humanoidBoneMapping.
     *
     * Flow: animation node name -> animNodeToHumanoidBone -> HumanoidBone -> model entity
     */
    private fun resolveEntity(nodeName: String): Int? {
        // Strategy 1: Use animNodeToHumanoidBone mapping from VRMA extension (HIGHEST PRIORITY)
        // This directly maps animation node names to humanoid bones
        val boneFromVrmaMapping = animNodeToHumanoidBone[nodeName]
        if (boneFromVrmaMapping != null) {
            val entity = boneMapper.getBoneEntity(boneFromVrmaMapping)
            if (entity != null) {
                return entity
            }
        }

        // Strategy 2: Use ANIMATION_TO_VRM_BONE_MAP to translate common animation bone names to VRM names
        val mappedVrmBoneName = ANIMATION_TO_VRM_BONE_MAP[nodeName.lowercase()]
            ?: ANIMATION_TO_VRM_BONE_MAP[nodeName]

        if (mappedVrmBoneName != null) {
            val bone = vrmBoneNameToEnum[mappedVrmBoneName.lowercase()]
                ?: vrmBoneNameToEnum[mappedVrmBoneName]
                ?: HumanoidBone.entries.firstOrNull {
                    it.vrmName.equals(mappedVrmBoneName, ignoreCase = true)
                }

            if (bone != null) {
                val entity = boneMapper.getBoneEntity(bone)
                if (entity != null) {
                    return entity
                }
            }
        }

        // Strategy 3: Try direct VRM bone name lookup (e.g., nodeName = "hips", "spine", etc.)
        val directBone = vrmBoneNameToEnum[nodeName.lowercase()]
            ?: vrmBoneNameToEnum[nodeName]
            ?: HumanoidBone.entries.firstOrNull {
                it.vrmName.equals(nodeName, ignoreCase = true)
            }

        if (directBone != null) {
            val entity = boneMapper.getBoneEntity(directBone)
            if (entity != null) {
                return entity
            }
        }

        // Strategy 4: Try direct node name in the model's node mapping
        nodeEntityMap[nodeName]?.let { return it }

        // Strategy 5: Case-insensitive search in node names
        val lowerNodeName = nodeName.lowercase()
        nodeEntityMap.entries.firstOrNull { (name, _) ->
            name.lowercase() == lowerNodeName
        }?.let { return it.value }

        // Don't log every frame - only log once per unique unresolved node
        return null
    }

    /**
     * Resolve the HumanoidBone for an animation track node name.
     * Returns null if the node is not a humanoid bone.
     */
    private fun resolveHumanoidBone(nodeName: String): HumanoidBone? {
        // Strategy 1: Use animNodeToHumanoidBone mapping from VRMA extension (HIGHEST PRIORITY)
        animNodeToHumanoidBone[nodeName]?.let { return it }

        // Strategy 2: Use ANIMATION_TO_VRM_BONE_MAP
        val mappedVrmBoneName = ANIMATION_TO_VRM_BONE_MAP[nodeName.lowercase()]
            ?: ANIMATION_TO_VRM_BONE_MAP[nodeName]

        if (mappedVrmBoneName != null) {
            val bone = vrmBoneNameToEnum[mappedVrmBoneName.lowercase()]
                ?: vrmBoneNameToEnum[mappedVrmBoneName]
                ?: HumanoidBone.entries.firstOrNull {
                    it.vrmName.equals(mappedVrmBoneName, ignoreCase = true)
                }
            if (bone != null) return bone
        }

        // Strategy 3: Try direct VRM bone name
        return vrmBoneNameToEnum[nodeName.lowercase()]
            ?: vrmBoneNameToEnum[nodeName]
            ?: HumanoidBone.entries.firstOrNull {
                it.vrmName.equals(nodeName, ignoreCase = true)
            }
    }

    /**
     * Interpolate track keyframes at a specific time
     */
    private fun interpolateTrack(track: AnimationTrack, time: Float): FloatArray {
        val keyframes = track.keyframes
        if (keyframes.isEmpty()) return floatArrayOf()

        // Find surrounding keyframes
        var prevIndex = 0
        var nextIndex = 0

        for (i in keyframes.indices) {
            if (keyframes[i].time <= time) {
                prevIndex = i
            }
            if (keyframes[i].time >= time) {
                nextIndex = i
                break
            }
            nextIndex = i
        }

        val prevFrame = keyframes[prevIndex]
        val nextFrame = keyframes[nextIndex]

        // Calculate interpolation factor
        val t = if (prevFrame.time == nextFrame.time) {
            0f
        } else {
            ((time - prevFrame.time) / (nextFrame.time - prevFrame.time)).coerceIn(0f, 1f)
        }

        // Interpolate based on type
        return when (track.interpolation) {
            InterpolationType.STEP -> prevFrame.values.copyOf()
            InterpolationType.LINEAR -> {
                if (track.path == AnimationPath.ROTATION) {
                    slerpQuaternion(prevFrame.values, nextFrame.values, t)
                } else {
                    lerpArray(prevFrame.values, nextFrame.values, t)
                }
            }
            InterpolationType.CUBICSPLINE -> {
                // Simplified: treat as linear for now
                lerpArray(prevFrame.values, nextFrame.values, t)
            }
        }
    }

    /**
     * Linear interpolation for float arrays
     */
    private fun lerpArray(a: FloatArray, b: FloatArray, t: Float): FloatArray {
        val result = FloatArray(a.size)
        for (i in a.indices) {
            result[i] = a[i] + (b[i] - a[i]) * t
        }
        return result
    }

    /**
     * Spherical linear interpolation for quaternions
     */
    private fun slerpQuaternion(q1: FloatArray, q2: FloatArray, t: Float): FloatArray {
        if (q1.size != 4 || q2.size != 4) return q1

        // Calculate dot product
        var dot = q1[0] * q2[0] + q1[1] * q2[1] + q1[2] * q2[2] + q1[3] * q2[3]

        // If dot is negative, negate one quaternion to take shorter path
        val q2Adjusted = if (dot < 0) {
            dot = -dot
            floatArrayOf(-q2[0], -q2[1], -q2[2], -q2[3])
        } else {
            q2
        }

        // Use linear interpolation for very close quaternions
        if (dot > 0.9995f) {
            return normalizeQuaternion(lerpArray(q1, q2Adjusted, t))
        }

        // Spherical interpolation
        val theta0 = kotlin.math.acos(dot.coerceIn(-1f, 1f))
        val theta = theta0 * t
        val sinTheta = kotlin.math.sin(theta)
        val sinTheta0 = kotlin.math.sin(theta0)

        val s0 = kotlin.math.cos(theta) - dot * sinTheta / sinTheta0
        val s1 = sinTheta / sinTheta0

        return floatArrayOf(
            s0 * q1[0] + s1 * q2Adjusted[0],
            s0 * q1[1] + s1 * q2Adjusted[1],
            s0 * q1[2] + s1 * q2Adjusted[2],
            s0 * q1[3] + s1 * q2Adjusted[3]
        )
    }

    /**
     * Normalize a quaternion
     */
    private fun normalizeQuaternion(q: FloatArray): FloatArray {
        val length = kotlin.math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (length < 0.0001f) return q
        return floatArrayOf(q[0] / length, q[1] / length, q[2] / length, q[3] / length)
    }

    // Flag to enable detailed rotation debugging (enable for first few seconds then disable)
    private var rotationDebugFrameCount = 0
    private val MAX_DEBUG_FRAMES = 5

    // Animation rest pose world quaternions (extracted from animation's own skeleton)
    private val animationBoneWorldQuaternions = mutableMapOf<HumanoidBone, FloatArray>()
    private val animationBoneWorldQuaternionInverses = mutableMapOf<HumanoidBone, FloatArray>()
    private var animationHipsParentWorldQuaternion: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)

    // Animation rest hips position for translation scaling
    private var animationRestHipsPosition: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var modelRestHipsPosition: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var translationScale: Float = 1.0f

    /**
     * Store animation rest pose data from VrmaAnimation.
     * This is crucial for proper rotation retargeting.
     *
     * Following amica VRMAnimationLoaderPlugin.ts pattern.
     */
    fun setAnimationRestPoseData(animation: VrmaAnimation) {
        animationBoneWorldQuaternions.clear()
        animationBoneWorldQuaternionInverses.clear()

        // For VRMA animations, the rest pose is embedded in the animation file
        // We use the animation's humanoidBoneMapping and restHipsPosition
        animation.restHipsPosition?.let { pos ->
            animationRestHipsPosition = pos.copyOf()
        }

        // Calculate translation scale factor
        val hipsEntity = boneMapper.getBoneEntity(HumanoidBone.HIPS)
        if (hipsEntity != null) {
            val tm = engine.transformManager
            val instance = tm.getInstance(hipsEntity)
            if (instance != 0) {
                val worldTransform = FloatArray(16)
                tm.getWorldTransform(instance, worldTransform)
                modelRestHipsPosition = floatArrayOf(worldTransform[12], worldTransform[13], worldTransform[14])

                // Scale factor: model hips Y / animation hips Y
                if (animationRestHipsPosition[1] > 0.01f) {
                    translationScale = modelRestHipsPosition[1] / animationRestHipsPosition[1]
                    Log.d(TAG, "Translation scale: $translationScale (model hips Y: ${modelRestHipsPosition[1]}, anim hips Y: ${animationRestHipsPosition[1]})")
                }
            }
        }

        // For bones that have world quaternion data in the animation
        animation.boneWorldQuaternions?.forEach { (boneName, quatArray) ->
            if (quatArray.size != 4) return@forEach

            // Handle special "hipsParent" case (following amica pattern)
            if (boneName == "hipsParent") {
                val normalizedQuat = normalizeQuaternion(quatArray)
                animationHipsParentWorldQuaternion = normalizedQuat
                Log.d(TAG, "Set hipsParent world quaternion: [${normalizedQuat.joinToString { "%.4f".format(it) }}]")
                return@forEach
            }

            val bone = vrmBoneNameToEnum[boneName.lowercase()]
                ?: vrmBoneNameToEnum[boneName]
                ?: HumanoidBone.entries.firstOrNull { it.vrmName.equals(boneName, ignoreCase = true) }

            if (bone != null) {
                val normalizedQuat = normalizeQuaternion(quatArray)
                animationBoneWorldQuaternions[bone] = normalizedQuat
                animationBoneWorldQuaternionInverses[bone] = invertQuaternion(normalizedQuat)
                Log.d(TAG, "Set bone ${bone.vrmName} world quaternion: [${normalizedQuat.joinToString { "%.4f".format(it) }}]")
            }
        }

        Log.d(TAG, "Set animation rest pose data: ${animationBoneWorldQuaternions.size} bone quaternions, hipsParent set, scale=$translationScale")
    }

    /**
     * Apply rotation with proper animation retargeting.
     *
     * Following amica-master VRMAnimationLoaderPlugin.ts pattern:
     * The key insight is that VRMA animations store rotations in the animation's rest pose space.
     * To apply to a different model, we must transform:
     *
     * CRITICAL FORMULA (from amica):
     *   retargetedQuat = parentWorldQuat * animQuat * boneWorldQuat^-1
     *
     * Where:
     * - parentWorldQuat: World rotation of parent bone in animation's rest pose
     * - animQuat: The animation quaternion value (what we're applying)
     * - boneWorldQuat^-1: Inverse of bone's world rotation in animation's rest pose
     *
     * This transforms the rotation from the animation's bone space to the target model's bone space.
     */
    private fun applyRotationWithWorldTransform(
        tm: TransformManager,
        instance: Int,
        entity: Int,
        rotation: FloatArray,
        humanoidBone: HumanoidBone?
    ) {
        if (rotation.size != 4) return

        val original = originalTransforms[entity] ?: return

        // Extract original scale (from original T-pose transform)
        val sx = kotlin.math.sqrt(original[0] * original[0] + original[1] * original[1] + original[2] * original[2])
        val sy = kotlin.math.sqrt(original[4] * original[4] + original[5] * original[5] + original[6] * original[6])
        val sz = kotlin.math.sqrt(original[8] * original[8] + original[9] * original[9] + original[10] * original[10])

        // Normalize the animation quaternion
        val animQuat = normalizeQuaternion(rotation.copyOf())

        // =========================================================================
        // VRMA Animation Application Strategy:
        //
        // VRMA animations (VRM 1.0 format) store LOCAL rotations.
        // VRM 1.0 uses a specific coordinate system:
        // - Y-up
        // - Right-handed
        // - Character faces +Z direction
        //
        // Following amica's VRMAnimation.ts pattern for coordinate conversion:
        // When applying to a VRM model, check the meta version:
        // - VRM 1.0: Apply directly
        // - VRM 0.x: Negate X and Z components (rotation around Y is mirrored)
        //
        // Additionally, Filament uses a different coordinate convention than Three.js.
        // In Filament/glTF:
        // - Column-major matrices
        // - Right-handed Y-up
        //
        // CRITICAL: The animation quaternion represents the LOCAL rotation of the bone
        // in the animation's frame. We must consider the difference between:
        // - Animation rest pose (embedded in .vrma)
        // - Model rest pose (T-pose from VRM)
        //
        // For now, apply a coordinate system adjustment similar to amica's
        // VRM 0.x handling, as this appears to match the coordinate mismatch.
        // =========================================================================

        // Apply coordinate system adjustment for Filament
        // This mirrors the transformation in amica for VRM 0.x models:
        // x and z components are negated to account for coordinate system differences
        val retargetedQuat = floatArrayOf(
            -animQuat[0],  // Negate X
            animQuat[1],   // Keep Y
            -animQuat[2],  // Negate Z
            animQuat[3]    // Keep W
        )

        // Debug logging for first few frames - focus on arms to diagnose issues
        if (rotationDebugFrameCount < MAX_DEBUG_FRAMES && humanoidBone != null) {
            val isArmBone = humanoidBone.vrmName.contains("Arm", ignoreCase = true) ||
                            humanoidBone.vrmName.contains("Shoulder", ignoreCase = true)
            if (isArmBone) {
                val hasRetargeting = animationBoneWorldQuaternionInverses.containsKey(humanoidBone)
                Log.d(TAG, "ARM Rotation ${humanoidBone.vrmName}: input=[${rotation.joinToString { "%.3f".format(it) }}] -> retargeted=[${retargetedQuat.joinToString { "%.3f".format(it) }}] (retargeting=$hasRetargeting)")
            }
        }

        // Convert retargeted quaternion to rotation matrix
        val rotMatrix = quaternionToMatrix(retargetedQuat)

        // Build new transform: animation rotation with original scale and position
        val newTransform = FloatArray(16)

        // Apply rotation * scale (column-major order)
        newTransform[0] = rotMatrix[0] * sx
        newTransform[1] = rotMatrix[1] * sx
        newTransform[2] = rotMatrix[2] * sx
        newTransform[3] = 0f

        newTransform[4] = rotMatrix[4] * sy
        newTransform[5] = rotMatrix[5] * sy
        newTransform[6] = rotMatrix[6] * sy
        newTransform[7] = 0f

        newTransform[8] = rotMatrix[8] * sz
        newTransform[9] = rotMatrix[9] * sz
        newTransform[10] = rotMatrix[10] * sz
        newTransform[11] = 0f

        // Keep original position from rest pose
        newTransform[12] = original[12]
        newTransform[13] = original[13]
        newTransform[14] = original[14]
        newTransform[15] = 1f

        // Apply blend weight for smooth transitions
        val finalTransform = if (blendWeight < 1f) {
            // Blend using quaternion slerp for rotation part
            val originalQuat = extractQuaternionFromMatrix(original, sx, sy, sz)
            val blendedQuat = slerpQuaternion(originalQuat, retargetedQuat, blendWeight)
            val blendedRotMatrix = quaternionToMatrix(blendedQuat)

            FloatArray(16).apply {
                this[0] = blendedRotMatrix[0] * sx
                this[1] = blendedRotMatrix[1] * sx
                this[2] = blendedRotMatrix[2] * sx
                this[3] = 0f
                this[4] = blendedRotMatrix[4] * sy
                this[5] = blendedRotMatrix[5] * sy
                this[6] = blendedRotMatrix[6] * sy
                this[7] = 0f
                this[8] = blendedRotMatrix[8] * sz
                this[9] = blendedRotMatrix[9] * sz
                this[10] = blendedRotMatrix[10] * sz
                this[11] = 0f
                this[12] = original[12]
                this[13] = original[13]
                this[14] = original[14]
                this[15] = 1f
            }
        } else {
            newTransform
        }

        tm.setTransform(instance, finalTransform)
    }

    /**
     * Find the parent bone for a given humanoid bone in the animation hierarchy.
     * Walks up the parent chain to find a bone that exists in the animation.
     */
    private fun findParentBoneInAnimation(bone: HumanoidBone): HumanoidBone? {
        var parentBone = humanoidBoneParentMap[bone]
        while (parentBone != null && !animationBoneWorldQuaternions.containsKey(parentBone)) {
            parentBone = humanoidBoneParentMap[parentBone]
        }
        return parentBone
    }

    /**
     * Extract quaternion from transform matrix.
     */
    private fun extractQuaternionFromMatrix(m: FloatArray, sx: Float, sy: Float, sz: Float): FloatArray {
        return matrixToQuaternion(m, sx, sy, sz)
    }

    /**
     * Extract quaternion from a 4x4 transform matrix (column-major).
     * Scale components are passed in to properly normalize the rotation part.
     */
    private fun matrixToQuaternion(m: FloatArray, sx: Float, sy: Float, sz: Float): FloatArray {
        // Normalize the rotation part by dividing by scale
        val r00 = m[0] / sx
        val r10 = m[1] / sx
        val r20 = m[2] / sx
        val r01 = m[4] / sy
        val r11 = m[5] / sy
        val r21 = m[6] / sy
        val r02 = m[8] / sz
        val r12 = m[9] / sz
        val r22 = m[10] / sz

        // Convert rotation matrix to quaternion using Shepperd's method
        val trace = r00 + r11 + r22

        val x: Float
        val y: Float
        val z: Float
        val w: Float

        if (trace > 0) {
            val s = 0.5f / kotlin.math.sqrt(trace + 1.0f)
            w = 0.25f / s
            x = (r21 - r12) * s
            y = (r02 - r20) * s
            z = (r10 - r01) * s
        } else if (r00 > r11 && r00 > r22) {
            val s = 2.0f * kotlin.math.sqrt(1.0f + r00 - r11 - r22)
            w = (r21 - r12) / s
            x = 0.25f * s
            y = (r01 + r10) / s
            z = (r02 + r20) / s
        } else if (r11 > r22) {
            val s = 2.0f * kotlin.math.sqrt(1.0f + r11 - r00 - r22)
            w = (r02 - r20) / s
            x = (r01 + r10) / s
            y = 0.25f * s
            z = (r12 + r21) / s
        } else {
            val s = 2.0f * kotlin.math.sqrt(1.0f + r22 - r00 - r11)
            w = (r10 - r01) / s
            x = (r02 + r20) / s
            y = (r12 + r21) / s
            z = 0.25f * s
        }

        return normalizeQuaternion(floatArrayOf(x, y, z, w))
    }

    /**
     * Multiply two quaternions: result = q1 * q2
     * Quaternion format: [x, y, z, w]
     */
    private fun multiplyQuaternions(q1: FloatArray, q2: FloatArray): FloatArray {
        val x1 = q1[0]; val y1 = q1[1]; val z1 = q1[2]; val w1 = q1[3]
        val x2 = q2[0]; val y2 = q2[1]; val z2 = q2[2]; val w2 = q2[3]

        return floatArrayOf(
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,  // x
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,  // y
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,  // z
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2   // w
        )
    }

    /**
     * Apply translation with scale factor based on model proportions.
     *
     * Following amica-master pattern: translations should be scaled
     * based on the ratio between animation rest pose and model pose.
     */
    private fun applyTranslationWithScale(
        tm: TransformManager,
        instance: Int,
        entity: Int,
        translation: FloatArray
    ) {
        if (translation.size != 3) return

        val original = originalTransforms[entity] ?: return
        val transform = FloatArray(16)
        tm.getTransform(instance, transform)

        // Scale factor: for now use 1.0, but could be calculated from
        // model hips position / animation rest hips position
        val scaleFactor = 1.0f

        // Apply scaled translation with blend
        val targetX = translation[0] * scaleFactor
        val targetY = translation[1] * scaleFactor
        val targetZ = translation[2] * scaleFactor

        val blendedX = original[12] + (targetX - original[12]) * blendWeight
        val blendedY = original[13] + (targetY - original[13]) * blendWeight
        val blendedZ = original[14] + (targetZ - original[14]) * blendWeight

        transform[12] = blendedX
        transform[13] = blendedY
        transform[14] = blendedZ

        tm.setTransform(instance, transform)
    }

    /**
     * Apply rotation to a transform (legacy method kept for compatibility)
     */
    private fun applyRotation(tm: TransformManager, instance: Int, entity: Int, rotation: FloatArray) {
        applyRotationWithWorldTransform(tm, instance, entity, rotation, null)
    }

    /**
     * Apply translation to a transform (legacy method kept for compatibility)
     */
    private fun applyTranslation(tm: TransformManager, instance: Int, entity: Int, translation: FloatArray) {
        applyTranslationWithScale(tm, instance, entity, translation)
    }

    /**
     * Apply scale to a transform
     */
    private fun applyScale(tm: TransformManager, instance: Int, entity: Int, scale: FloatArray) {
        if (scale.size != 3) return

        val transform = FloatArray(16)
        tm.getTransform(instance, transform)

        // Simple scale application (would need more work for proper blending)
        transform[0] = scale[0]
        transform[5] = scale[1]
        transform[10] = scale[2]

        tm.setTransform(instance, transform)
    }

    /**
     * Convert quaternion to 4x4 rotation matrix in COLUMN-MAJOR order (for Filament/OpenGL).
     *
     * Quaternion format: [x, y, z, w]
     * Matrix layout: column-major, indices 0-3 = column 0, 4-7 = column 1, etc.
     */
    private fun quaternionToMatrix(q: FloatArray): FloatArray {
        val x = q[0]
        val y = q[1]
        val z = q[2]
        val w = q[3]

        val x2 = x + x
        val y2 = y + y
        val z2 = z + z
        val xx = x * x2
        val xy = x * y2
        val xz = x * z2
        val yy = y * y2
        val yz = y * z2
        val zz = z * z2
        val wx = w * x2
        val wy = w * y2
        val wz = w * z2

        // Column-major order for Filament/OpenGL
        // Column 0
        val m00 = 1f - (yy + zz)
        val m10 = xy + wz
        val m20 = xz - wy

        // Column 1
        val m01 = xy - wz
        val m11 = 1f - (xx + zz)
        val m21 = yz + wx

        // Column 2
        val m02 = xz + wy
        val m12 = yz - wx
        val m22 = 1f - (xx + yy)

        return floatArrayOf(
            // Column 0
            m00, m10, m20, 0f,
            // Column 1
            m01, m11, m21, 0f,
            // Column 2
            m02, m12, m22, 0f,
            // Column 3 (translation)
            0f, 0f, 0f, 1f
        )
    }

    /**
     * Store original transforms for all mapped nodes
     */
    private fun storeOriginalTransforms() {
        if (isDestroyed) {
            Log.w(TAG, "Cannot store original transforms - player is destroyed")
            return
        }

        try {
            val tm = engine.transformManager
            var nodeMapStored = 0
            var boneMapStored = 0

            nodeEntityMap.values.forEach { entity ->
                if (isDestroyed) return

                try {
                    val instance = tm.getInstance(entity)
                    if (instance != 0) {
                        val transform = FloatArray(16)
                        tm.getTransform(instance, transform)
                        originalTransforms[entity] = transform.copyOf()
                        nodeMapStored++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error storing transform for entity $entity: ${e.message}")
                }
            }

            boneMapper.getBoneEntityMap().values.forEach { entity ->
                if (isDestroyed) return

                if (!originalTransforms.containsKey(entity)) {
                    try {
                        val instance = tm.getInstance(entity)
                        if (instance != 0) {
                            val transform = FloatArray(16)
                            tm.getTransform(instance, transform)
                            originalTransforms[entity] = transform.copyOf()
                            boneMapStored++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error storing transform for bone entity $entity: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Stored original transforms: $nodeMapStored from nodeMap, $boneMapStored from boneMapper, total: ${originalTransforms.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in storeOriginalTransforms: ${e.message}")
        }
    }

    /**
     * Reset all nodes to original transforms
     */
    private fun resetToOriginalPose() {
        val tm = engine.transformManager

        originalTransforms.forEach { (entity, transform) ->
            val instance = tm.getInstance(entity)
            if (instance != 0) {
                tm.setTransform(instance, transform)
            }
        }

        // CRITICAL: Update bone matrices after reset too!
        animator?.updateBoneMatrices()
        Log.d(TAG, "Reset to original pose, updateBoneMatrices called")
    }
}
