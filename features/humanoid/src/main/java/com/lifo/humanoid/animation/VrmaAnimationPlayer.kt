package com.lifo.humanoid.animation

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
 * Player for VRMA animations with proper animation blending.
 *
 * CRITICAL FIX: Implements dual-animation blending following Amica/three-vrm approach.
 * Instead of blending from T-pose to new animation, we now:
 * 1. Keep the previous animation playing during transition
 * 2. Crossfade weights between previous and new animation
 * 3. Sample both animations at their respective times and blend the results
 */
class VrmaAnimationPlayer(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {

    companion object {
        private val ANIMATION_TO_VRM_BONE_MAP = mapOf(
            "root" to "hips",
            "torso_1" to "spine",
            "torso_2" to "chest",
            "torso_3" to "upperChest",
            "torso_4" to "upperChest",
            "torso_5" to "upperChest",
            "torso_6" to "upperChest",
            "torso_7" to "upperChest",
            "neck_1" to "neck",
            "neck_2" to "neck",
            "head" to "head",
            "l_shoulder" to "leftShoulder",
            "l_up_arm" to "leftUpperArm",
            "l_low_arm" to "leftLowerArm",
            "l_hand" to "leftHand",
            "r_shoulder" to "rightShoulder",
            "r_up_arm" to "rightUpperArm",
            "r_low_arm" to "rightLowerArm",
            "r_hand" to "rightHand",
            "l_up_leg" to "leftUpperLeg",
            "l_low_leg" to "leftLowerLeg",
            "l_foot" to "leftFoot",
            "l_toes" to "leftToes",
            "r_up_leg" to "rightUpperLeg",
            "r_low_leg" to "rightLowerLeg",
            "r_foot" to "rightFoot",
            "r_toes" to "rightToes",
            "hips" to "hips",
            "spine" to "spine",
            "spine1" to "spine",
            "spine2" to "chest",
            "spine3" to "upperChest",
            "chest" to "chest",
            "upperchest" to "upperChest",
            "neck" to "neck",
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

    // ==================== Animation Action System (following Amica) ====================

    /**
     * Represents an active animation with its own weight, time, and state.
     * This mirrors THREE.AnimationAction from Amica.
     */
    private data class AnimationAction(
        val animation: VrmaAnimation,
        var time: Float = 0f,
        var weight: Float = 1f,
        var targetWeight: Float = 1f,
        var fadeDuration: Float = 0f,
        var fadeStartTime: Float = 0f,
        var fadeStartWeight: Float = 0f,
        var isPlaying: Boolean = true,
        var loop: Boolean = false,
        var clampWhenFinished: Boolean = true,
        val nodeMapping: MutableMap<String, HumanoidBone> = mutableMapOf()
    ) {
        val isActive: Boolean
            get() = isPlaying && weight > 0.0001f

        val isFading: Boolean
            get() = fadeDuration > 0f && weight != targetWeight
    }

    private val _currentAnimation = MutableStateFlow<VrmaAnimation?>(null)
    val currentAnimation: StateFlow<VrmaAnimation?> = _currentAnimation

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress

    private var playbackJob: Job? = null
    private var playbackSpeed: Float = 1.0f

    // ==================== Dual-Animation Blending System ====================

    /**
     * The current primary animation action.
     * Following Amica's _currentAction pattern.
     */
    private var currentAction: AnimationAction? = null

    /**
     * The previous animation action (fading out during transition).
     * This is the KEY to smooth transitions - we keep it playing while fading out.
     */
    private var previousAction: AnimationAction? = null

    /**
     * All active actions (for complex multi-animation blending if needed).
     */
    private val activeActions = mutableListOf<AnimationAction>()

    // Fade durations (following Amica defaults)
    private var defaultFadeInDuration: Float = 0.5f   // Amica uses 0.5s fadeIn
    private var defaultFadeOutDuration: Float = 1.0f  // Amica uses 1.0s fadeOut

    // Idle animation system
    private var idleAnimation: VrmaAnimation? = null
    private var idleAnimationScope: CoroutineScope? = null
    private var idleAction: AnimationAction? = null
    private var isPlayingOneShot = false
    private var onOneShotComplete: (() -> Unit)? = null

    // Node/bone mappings
    private val nodeEntityMap = mutableMapOf<String, Int>()
    private val vrmBoneNameToEnum = mutableMapOf<String, HumanoidBone>()

    // Original transforms for reset
    private val originalTransforms = mutableMapOf<Int, FloatArray>()

    // World matrices for proper transform application
    private val boneWorldMatrices = mutableMapOf<HumanoidBone, FloatArray>()
    private val boneWorldQuaternions = mutableMapOf<HumanoidBone, FloatArray>()
    private val boneWorldQuaternionInverses = mutableMapOf<HumanoidBone, FloatArray>()

    // Animation rest pose data
    private val animationBoneWorldQuaternions = mutableMapOf<HumanoidBone, FloatArray>()
    private val animationBoneWorldQuaternionInverses = mutableMapOf<HumanoidBone, FloatArray>()
    private var translationScale: Float = 1.0f

    // State tracking
    private var isInitialized = false
    @Volatile
    private var isDestroyed = false
    private var animator: Animator? = null

    // Debug
    private var rotationDebugFrameCount = 0
    private val MAX_DEBUG_FRAMES = 5

    /**
     * Initialize with a FilamentAsset to build node mapping
     */
    fun initialize(asset: FilamentAsset, nodeNames: List<String>) {
        if (isDestroyed) {
            println("[VrmaAnimationPlayer] WARNING: Cannot initialize - player is destroyed")
            return
        }

        nodeEntityMap.clear()
        vrmBoneNameToEnum.clear()
        boneWorldMatrices.clear()
        activeActions.clear()
        currentAction = null
        previousAction = null

        try {
            animator = asset.getInstance()?.animator
            println("[VrmaAnimationPlayer] Animator obtained from asset instance: ${animator != null}")
        } catch (e: Exception) {
            println("[VrmaAnimationPlayer] ERROR: Error obtaining animator: ${e.message}")
            return
        }

        nodeNames.forEachIndexed { index, name ->
            if (isDestroyed) return
            try {
                if (index < asset.entities.size) {
                    nodeEntityMap[name] = asset.entities[index]
                }
            } catch (e: Exception) {
                println("[VrmaAnimationPlayer] ERROR: Error mapping node '$name': ${e.message}")
            }
        }

        HumanoidBone.entries.forEach { bone ->
            vrmBoneNameToEnum[bone.vrmName.lowercase()] = bone
            vrmBoneNameToEnum[bone.vrmName] = bone
        }

        calculateBoneWorldMatrices()
        storeOriginalTransforms()

        isInitialized = true
        println("[VrmaAnimationPlayer] Initialized with ${nodeEntityMap.size} node mappings")
    }

    private fun calculateBoneWorldMatrices() {
        if (isDestroyed) return

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

                        val sx = kotlin.math.sqrt(worldMatrix[0] * worldMatrix[0] + worldMatrix[1] * worldMatrix[1] + worldMatrix[2] * worldMatrix[2])
                        val sy = kotlin.math.sqrt(worldMatrix[4] * worldMatrix[4] + worldMatrix[5] * worldMatrix[5] + worldMatrix[6] * worldMatrix[6])
                        val sz = kotlin.math.sqrt(worldMatrix[8] * worldMatrix[8] + worldMatrix[9] * worldMatrix[9] + worldMatrix[10] * worldMatrix[10])

                        val worldQuat = matrixToQuaternion(worldMatrix, sx, sy, sz)
                        boneWorldQuaternions[bone] = worldQuat
                        boneWorldQuaternionInverses[bone] = invertQuaternion(worldQuat)
                    }
                } catch (e: Exception) {
                    println("[VrmaAnimationPlayer] ERROR: Error calculating world matrix for bone $bone: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("[VrmaAnimationPlayer] ERROR: Error in calculateBoneWorldMatrices: ${e.message}")
        }
    }

    private fun invertQuaternion(q: FloatArray): FloatArray {
        return floatArrayOf(-q[0], -q[1], -q[2], q[3])
    }

    /**
     * Build humanoid bone mapping for an animation action.
     */
    private fun buildHumanoidMappingForAction(action: AnimationAction) {
        action.nodeMapping.clear()

        action.animation.humanoidBoneMapping.forEach { (vrmBoneName, animationNodeName) ->
            val humanoidBone = vrmBoneNameToEnum[vrmBoneName.lowercase()]
                ?: vrmBoneNameToEnum[vrmBoneName]
                ?: HumanoidBone.entries.firstOrNull {
                    it.vrmName.equals(vrmBoneName, ignoreCase = true)
                }

            if (humanoidBone != null) {
                action.nodeMapping[animationNodeName] = humanoidBone
            }
        }
    }

    // ==================== Core Blending Methods (Following Amica) ====================

    /**
     * Fade to a new animation action.
     * This is the KEY method - implements Amica's fadeToAction() pattern.
     *
     * @param destAction The new action to transition to
     * @param fadeOutDuration Duration for the previous action to fade out
     * @param fadeInDuration Duration for the new action to fade in
     */
    private fun fadeToAction(
        destAction: AnimationAction,
        fadeOutDuration: Float = defaultFadeOutDuration,
        fadeInDuration: Float = defaultFadeInDuration
    ) {
        val prevAction = currentAction

        // Store previous action for blending (DON'T stop it!)
        if (prevAction != null && prevAction !== destAction) {
            previousAction = prevAction
            // Start fading out the previous action
            prevAction.fadeStartWeight = prevAction.weight
            prevAction.targetWeight = 0f
            prevAction.fadeDuration = fadeOutDuration
            prevAction.fadeStartTime = 0f  // Will be updated in the playback loop

            println("[VrmaAnimationPlayer] Fading out previous action: ${prevAction.animation.name}, weight=${prevAction.weight} -> 0")
        }

        // Setup the new action
        currentAction = destAction
        destAction.time = 0f  // reset() equivalent
        destAction.isPlaying = true
        destAction.fadeStartWeight = 0f
        destAction.targetWeight = 1f
        destAction.fadeDuration = fadeInDuration
        destAction.fadeStartTime = 0f
        destAction.weight = 0f  // Start at 0, will fade in

        // Add to active actions if not already there
        if (!activeActions.contains(destAction)) {
            activeActions.add(destAction)
        }

        println("[VrmaAnimationPlayer] Fading in new action: ${destAction.animation.name}, weight=0 -> 1")
    }

    /**
     * Update fade weights for all active actions.
     * Called every frame during playback.
     */
    private fun updateFadeWeights(deltaTime: Float) {
        val actionsToRemove = mutableListOf<AnimationAction>()

        activeActions.forEach { action ->
            if (action.fadeDuration > 0f && action.weight != action.targetWeight) {
                // Update fade progress
                action.fadeStartTime += deltaTime
                val fadeProgress = (action.fadeStartTime / action.fadeDuration).coerceIn(0f, 1f)

                // Interpolate weight
                action.weight = lerp(action.fadeStartWeight, action.targetWeight, fadeProgress)

                // Check if fade complete
                if (fadeProgress >= 1f) {
                    action.weight = action.targetWeight
                    action.fadeDuration = 0f

                    // If faded out completely, mark for removal
                    if (action.targetWeight <= 0f) {
                        action.isPlaying = false
                        actionsToRemove.add(action)
                        println("[VrmaAnimationPlayer] Action ${action.animation.name} faded out completely, removing")
                    }
                }
            }
        }

        // Remove fully faded-out actions
        actionsToRemove.forEach { action ->
            activeActions.remove(action)
            if (previousAction === action) {
                previousAction = null
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    /**
     * Play an animation with proper blending transition.
     */
    fun play(
        animation: VrmaAnimation,
        scope: CoroutineScope,
        loop: Boolean = animation.isLooping,
        speed: Float = 1.0f
    ) {
        if (isDestroyed) {
            println("[VrmaAnimationPlayer] WARNING: Cannot play animation - player is destroyed")
            return
        }
        if (!isInitialized) {
            println("[VrmaAnimationPlayer] ERROR: Cannot play animation - player not initialized!")
            return
        }

        // Cancel previous playback job but DON'T reset poses!
        playbackJob?.cancel()

        _currentAnimation.value = animation
        _isPlaying.value = true
        playbackSpeed = speed
        rotationDebugFrameCount = 0

        // Create new action for this animation
        val newAction = AnimationAction(
            animation = animation,
            loop = loop,
            clampWhenFinished = !loop
        )

        // Build humanoid mapping for this action
        buildHumanoidMappingForAction(newAction)

        // Set animation rest pose data
        setAnimationRestPoseData(animation)

        println("[VrmaAnimationPlayer] === Playing Animation with Blending ===")
        println("[VrmaAnimationPlayer] Name: ${animation.name}, Duration: ${animation.durationSeconds}s, Loop: $loop")
        println("[VrmaAnimationPlayer] Previous action: ${currentAction?.animation?.name ?: "none"}")

        // Transition to new action with crossfade
        fadeToAction(newAction)

        playbackJob = scope.launch {
            var lastFrameTime = System.nanoTime()

            while (isActive && _isPlaying.value && !isDestroyed) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime

                // Update fade weights for all active actions
                updateFadeWeights(deltaTime)

                // Update time for all active actions
                activeActions.forEach { action ->
                    if (action.isPlaying) {
                        action.time += deltaTime * playbackSpeed

                        // Handle looping/completion
                        if (action.time >= action.animation.durationSeconds) {
                            if (action.loop) {
                                action.time %= action.animation.durationSeconds
                            } else {
                                action.time = action.animation.durationSeconds
                                if (action.clampWhenFinished) {
                                    // Keep at last frame, don't stop yet
                                } else {
                                    action.isPlaying = false
                                }

                                // Fire "finished" event for non-looping actions
                                if (action === currentAction && !action.loop) {
                                    onAnimationFinished(action)
                                }
                            }
                        }
                    }
                }

                // Update progress based on current action
                currentAction?.let { action ->
                    _playbackProgress.value = action.time / action.animation.durationSeconds
                }

                // Apply blended animation from all active actions
                // Check isDestroyed before dispatching to Main to avoid accessing destroyed engine
                if (!isDestroyed) {
                    withContext(Dispatchers.Main) {
                        applyBlendedAnimation()
                    }
                }

                delay(16) // ~60fps
            }
        }
    }

    /**
     * Called when a non-looping animation finishes.
     */
    private fun onAnimationFinished(action: AnimationAction) {
        println("[VrmaAnimationPlayer] Animation finished: ${action.animation.name}")

        // If we have an idle animation, transition back to it
        if (idleAction != null && action !== idleAction) {
            println("[VrmaAnimationPlayer] Returning to idle animation")

            // Invoke the onComplete callback before transitioning
            if (isPlayingOneShot) {
                onOneShotComplete?.invoke()
                onOneShotComplete = null
            }

            isPlayingOneShot = false
            fadeToAction(idleAction!!)
        }
    }

    /**
     * Apply blended animation from all active actions.
     * This is where the magic happens - we sample ALL active animations
     * and blend their outputs based on weights.
     */
    private fun applyBlendedAnimation() {
        if (isDestroyed) return
        if (activeActions.isEmpty()) return

        try {
            // Double-check after potential scheduling delay — engine may have been
            // destroyed between the coroutine resuming and reaching this point.
            // Also check animator — nulled in stop(destroy=true) BEFORE job cancel.
            if (isDestroyed || animator == null) return
            val tm = engine.transformManager

            // Collect all bone rotations and translations from all active actions, weighted
            val blendedRotations = mutableMapOf<Int, BlendedRotation>()
            val blendedTranslations = mutableMapOf<Int, BlendedTranslation>()

            // Take a snapshot of active actions to avoid ConcurrentModificationException
            val actionsSnapshot = activeActions.toList()

            actionsSnapshot.forEach { action ->
                if (isDestroyed) return  // Check before each action's native calls
                if (!action.isActive) return@forEach

                action.animation.tracks.forEach { track ->
                    if (isDestroyed) return  // Check inside inner loop
                    val entity = resolveEntity(track.nodeName, action)
                    if (entity == null) return@forEach

                    if (isDestroyed) return  // Check before native TransformManager call
                    val instance = tm.getInstance(entity)
                    if (instance == 0) return@forEach

                    val interpolatedValues = interpolateTrack(track, action.time)
                    if (interpolatedValues.isEmpty()) return@forEach

                    when (track.path) {
                        AnimationPath.ROTATION -> {
                            val humanoidBone = resolveHumanoidBone(track.nodeName, action)

                            // Get or create blended rotation entry
                            val blended = blendedRotations.getOrPut(entity) {
                                BlendedRotation(entity, instance, humanoidBone)
                            }

                            // Add this action's contribution
                            blended.addContribution(interpolatedValues, action.weight)
                        }
                        AnimationPath.TRANSLATION -> {
                            // Get or create blended translation entry
                            val blended = blendedTranslations.getOrPut(entity) {
                                BlendedTranslation(entity, instance)
                            }

                            // Add this action's contribution
                            blended.addContribution(interpolatedValues, action.weight)
                        }
                        else -> { /* Ignore scale for now */ }
                    }
                }
            }

            // Final check before applying transforms to native engine
            if (isDestroyed) return

            // Apply all blended transforms (rotation + translation)
            val allEntities = (blendedRotations.keys + blendedTranslations.keys).toSet()
            allEntities.forEach { entity ->
                if (isDestroyed) return  // Check before each native setTransform
                val rotation = blendedRotations[entity]
                val translation = blendedTranslations[entity]
                applyBlendedTransform(tm, entity, rotation, translation)
            }

            // Update bone matrices for skinning
            if (!isDestroyed) {
                animator?.updateBoneMatrices()
            }

            rotationDebugFrameCount++

        } catch (e: Exception) {
            if (!isDestroyed) {
                println("[VrmaAnimationPlayer] ERROR: Error in applyBlendedAnimation: ${e.message}")
            }
        }
    }

    /**
     * Helper class to accumulate weighted rotation contributions.
     */
    private inner class BlendedRotation(
        val entity: Int,
        val instance: Int,
        val humanoidBone: HumanoidBone?
    ) {
        private val contributions = mutableListOf<Pair<FloatArray, Float>>() // (quaternion, weight)
        private var totalWeight = 0f

        fun addContribution(quaternion: FloatArray, weight: Float) {
            if (weight > 0.0001f) {
                contributions.add(quaternion to weight)
                totalWeight += weight
            }
        }

        fun getBlendedQuaternion(): FloatArray? {
            if (contributions.isEmpty()) return null
            if (contributions.size == 1) return contributions[0].first

            // Normalize weights and blend quaternions
            var result = contributions[0].first.copyOf()
            var accumulatedWeight = contributions[0].second / totalWeight

            for (i in 1 until contributions.size) {
                val (quat, weight) = contributions[i]
                val normalizedWeight = weight / totalWeight
                val t = normalizedWeight / (accumulatedWeight + normalizedWeight)
                result = slerpQuaternion(result, quat, t)
                accumulatedWeight += normalizedWeight
            }

            return result
        }
    }

    /**
     * Helper class to accumulate weighted translation contributions.
     */
    private inner class BlendedTranslation(
        val entity: Int,
        val instance: Int
    ) {
        private val contributions = mutableListOf<Pair<FloatArray, Float>>() // (translation xyz, weight)
        private var totalWeight = 0f

        fun addContribution(translation: FloatArray, weight: Float) {
            if (weight > 0.0001f && translation.size >= 3) {
                contributions.add(translation to weight)
                totalWeight += weight
            }
        }

        fun getBlendedTranslation(): FloatArray? {
            if (contributions.isEmpty()) return null
            if (contributions.size == 1) return contributions[0].first

            // Linear interpolation for translations
            val result = FloatArray(3) { 0f }
            contributions.forEach { (trans, weight) ->
                val normalizedWeight = weight / totalWeight
                result[0] += trans[0] * normalizedWeight
                result[1] += trans[1] * normalizedWeight
                result[2] += trans[2] * normalizedWeight
            }

            return result
        }
    }

    /**
     * Apply blended rotation and translation to a transform.
     * Translations from animation are ADDED to the original position,
     * allowing the avatar to move freely in world space.
     */
    private fun applyBlendedTransform(
        tm: TransformManager,
        entity: Int,
        rotation: BlendedRotation?,
        translation: BlendedTranslation?
    ) {
        if (isDestroyed) return
        val original = originalTransforms[entity] ?: return
        val instance = try { tm.getInstance(entity) } catch (e: Exception) { return }
        if (instance == 0) return

        val sx = kotlin.math.sqrt(original[0] * original[0] + original[1] * original[1] + original[2] * original[2])
        val sy = kotlin.math.sqrt(original[4] * original[4] + original[5] * original[5] + original[6] * original[6])
        val sz = kotlin.math.sqrt(original[8] * original[8] + original[9] * original[9] + original[10] * original[10])

        val newTransform = FloatArray(16)

        // Apply rotation if available
        if (rotation != null) {
            val rotQuat = rotation.getBlendedQuaternion()
            if (rotQuat != null) {
                val animQuat = normalizeQuaternion(rotQuat.copyOf())

                // Apply coordinate system adjustment
                val retargetedQuat = floatArrayOf(
                    -animQuat[0],
                    animQuat[1],
                    -animQuat[2],
                    animQuat[3]
                )

                val rotMatrix = quaternionToMatrix(retargetedQuat)

                newTransform[0] = rotMatrix[0] * sx
                newTransform[1] = rotMatrix[1] * sx
                newTransform[2] = rotMatrix[2] * sx
                newTransform[4] = rotMatrix[4] * sy
                newTransform[5] = rotMatrix[5] * sy
                newTransform[6] = rotMatrix[6] * sy
                newTransform[8] = rotMatrix[8] * sz
                newTransform[9] = rotMatrix[9] * sz
                newTransform[10] = rotMatrix[10] * sz
            } else {
                // Keep original rotation
                newTransform[0] = original[0]
                newTransform[1] = original[1]
                newTransform[2] = original[2]
                newTransform[4] = original[4]
                newTransform[5] = original[5]
                newTransform[6] = original[6]
                newTransform[8] = original[8]
                newTransform[9] = original[9]
                newTransform[10] = original[10]
            }
        } else {
            // Keep original rotation
            newTransform[0] = original[0]
            newTransform[1] = original[1]
            newTransform[2] = original[2]
            newTransform[4] = original[4]
            newTransform[5] = original[5]
            newTransform[6] = original[6]
            newTransform[8] = original[8]
            newTransform[9] = original[9]
            newTransform[10] = original[10]
        }

        newTransform[3] = 0f
        newTransform[7] = 0f
        newTransform[11] = 0f
        newTransform[15] = 1f

        // Apply translation: ADD animation translation to original position
        // This allows the avatar to move freely in world space
        val animTranslation = translation?.getBlendedTranslation()
        if (animTranslation != null) {
            // Apply coordinate system adjustment for translation too
            // X is negated, Y stays, Z is negated (same as rotation)
            newTransform[12] = original[12] + (-animTranslation[0])
            newTransform[13] = original[13] + animTranslation[1]
            newTransform[14] = original[14] + (-animTranslation[2])
        } else {
            // Keep original translation
            newTransform[12] = original[12]
            newTransform[13] = original[13]
            newTransform[14] = original[14]
        }

        if (!isDestroyed) {
            try {
                tm.setTransform(instance, newTransform)
            } catch (e: Exception) {
                // Entity may have been destroyed during transition
            }
        }
    }

    /**
     * Stop the current animation.
     */
    fun stop(blendOut: Boolean = true, destroy: Boolean = false) {
        // CRITICAL: Set isDestroyed FIRST so any in-flight applyBlendedAnimation() sees it
        // before we destroy the engine/assets. This prevents SIGSEGV from native Filament calls.
        if (destroy) {
            println("[VrmaAnimationPlayer] VrmaAnimationPlayer destroying — setting flag + nulling animator before cancel")
            isDestroyed = true
            // Null animator BEFORE cancel: even if a queued withContext(Main) block
            // executes after cancel, animator?.updateBoneMatrices() becomes a no-op.
            animator = null
        }

        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _currentAnimation.value = null
        _playbackProgress.value = 0f

        // Clear all actions
        activeActions.clear()
        currentAction = null
        previousAction = null

        if (!destroy && !isDestroyed) {
            resetToOriginalPose()
        }
    }

    fun pause() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun resume(scope: CoroutineScope) {
        if (isDestroyed) return
        val animation = _currentAnimation.value ?: return
        if (!_isPlaying.value) return
        play(animation, scope, currentAction?.loop ?: false, playbackSpeed)
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.1f, 3f)
    }

    fun seekTo(timeSeconds: Float) {
        currentAction?.let { action ->
            action.time = timeSeconds.coerceIn(0f, action.animation.durationSeconds)
            _playbackProgress.value = action.time / action.animation.durationSeconds
        }
    }

    // ==================== Idle Animation System ====================

    fun setIdleAnimation(animation: VrmaAnimation, scope: CoroutineScope) {
        if (isDestroyed) {
            println("[VrmaAnimationPlayer] WARNING: Cannot set idle animation - player is destroyed")
            return
        }
        println("[VrmaAnimationPlayer] Setting idle animation: ${animation.name}")

        idleAnimation = animation
        idleAnimationScope = scope

        // Create the idle action
        idleAction = AnimationAction(
            animation = animation,
            loop = true,
            clampWhenFinished = false
        )
        buildHumanoidMappingForAction(idleAction!!)

        if (!isPlayingOneShot) {
            startIdleLoop()
        }
    }

    private fun startIdleLoop() {
        if (isDestroyed) return
        val idle = idleAction ?: return
        val scope = idleAnimationScope ?: return

        println("[VrmaAnimationPlayer] Starting idle loop: ${idle.animation.name}")

        setAnimationRestPoseData(idle.animation)

        // Use fadeToAction for smooth transition
        fadeToAction(idle)

        // Start playback if not already running
        if (playbackJob == null || !_isPlaying.value) {
            _isPlaying.value = true
            _currentAnimation.value = idle.animation

            playbackJob = scope.launch {
                var lastFrameTime = System.nanoTime()

                while (isActive && _isPlaying.value && !isDestroyed) {
                    val currentTime = System.nanoTime()
                    val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = currentTime

                    updateFadeWeights(deltaTime)

                    activeActions.forEach { action ->
                        if (action.isPlaying) {
                            action.time += deltaTime * playbackSpeed
                            if (action.time >= action.animation.durationSeconds) {
                                if (action.loop) {
                                    action.time %= action.animation.durationSeconds
                                } else {
                                    action.time = action.animation.durationSeconds
                                    if (action === currentAction && !action.loop) {
                                        onAnimationFinished(action)
                                    }
                                }
                            }
                        }
                    }

                    currentAction?.let { action ->
                        _playbackProgress.value = action.time / action.animation.durationSeconds
                    }

                    // Check isDestroyed before dispatching to Main to avoid accessing destroyed engine
                    if (!isDestroyed) {
                        withContext(Dispatchers.Main) {
                            applyBlendedAnimation()
                        }
                    }

                    delay(16)
                }
            }
        }
    }

    fun playOneShot(
        animation: VrmaAnimation,
        scope: CoroutineScope,
        fadeDuration: Float = 0.5f,
        onComplete: (() -> Unit)? = null
    ): Float {
        if (isDestroyed) {
            println("[VrmaAnimationPlayer] WARNING: Cannot playOneShot - player is destroyed")
            return 0f
        }
        if (idleAnimation == null) {
            println("[VrmaAnimationPlayer] WARNING: No idle animation set, playing as regular animation")
            play(animation, scope, loop = false)
            onComplete?.invoke()
            return animation.durationSeconds
        }

        println("[VrmaAnimationPlayer] Playing one-shot animation: ${animation.name}")

        isPlayingOneShot = true
        onOneShotComplete = onComplete
        defaultFadeInDuration = fadeDuration
        defaultFadeOutDuration = fadeDuration

        setAnimationRestPoseData(animation)

        val newAction = AnimationAction(
            animation = animation,
            loop = false,
            clampWhenFinished = true
        )
        buildHumanoidMappingForAction(newAction)

        // Crossfade from current (idle) to new action
        fadeToAction(newAction, fadeOutDuration = 1.0f, fadeInDuration = 0.5f)

        _currentAnimation.value = animation
        _isPlaying.value = true

        return animation.durationSeconds + defaultFadeInDuration + defaultFadeOutDuration
    }

    fun hasIdleAnimation(): Boolean = idleAnimation != null
    fun getIdleAnimation(): VrmaAnimation? = idleAnimation

    // ==================== Animation Rest Pose Data ====================

    fun setAnimationRestPoseData(animation: VrmaAnimation) {
        animationBoneWorldQuaternions.clear()
        animationBoneWorldQuaternionInverses.clear()

        animation.boneWorldQuaternions?.forEach { (boneName, quatArray) ->
            if (quatArray.size != 4) return@forEach

            val bone = vrmBoneNameToEnum[boneName.lowercase()]
                ?: vrmBoneNameToEnum[boneName]
                ?: HumanoidBone.entries.firstOrNull { it.vrmName.equals(boneName, ignoreCase = true) }

            if (bone != null) {
                val normalizedQuat = normalizeQuaternion(quatArray)
                animationBoneWorldQuaternions[bone] = normalizedQuat
                animationBoneWorldQuaternionInverses[bone] = invertQuaternion(normalizedQuat)
            }
        }
    }

    // ==================== Entity Resolution ====================

    private fun resolveEntity(nodeName: String, action: AnimationAction): Int? {
        // Use action's own node mapping first
        val boneFromMapping = action.nodeMapping[nodeName]
        if (boneFromMapping != null) {
            val entity = boneMapper.getBoneEntity(boneFromMapping)
            if (entity != null) return entity
        }

        // Fallback strategies...
        val mappedVrmBoneName = ANIMATION_TO_VRM_BONE_MAP[nodeName.lowercase()]
            ?: ANIMATION_TO_VRM_BONE_MAP[nodeName]

        if (mappedVrmBoneName != null) {
            val bone = vrmBoneNameToEnum[mappedVrmBoneName.lowercase()]
                ?: vrmBoneNameToEnum[mappedVrmBoneName]
            if (bone != null) {
                val entity = boneMapper.getBoneEntity(bone)
                if (entity != null) return entity
            }
        }

        val directBone = vrmBoneNameToEnum[nodeName.lowercase()]
            ?: vrmBoneNameToEnum[nodeName]
        if (directBone != null) {
            val entity = boneMapper.getBoneEntity(directBone)
            if (entity != null) return entity
        }

        nodeEntityMap[nodeName]?.let { return it }

        return null
    }

    private fun resolveHumanoidBone(nodeName: String, action: AnimationAction): HumanoidBone? {
        action.nodeMapping[nodeName]?.let { return it }

        val mappedVrmBoneName = ANIMATION_TO_VRM_BONE_MAP[nodeName.lowercase()]
            ?: ANIMATION_TO_VRM_BONE_MAP[nodeName]

        if (mappedVrmBoneName != null) {
            val bone = vrmBoneNameToEnum[mappedVrmBoneName.lowercase()]
                ?: vrmBoneNameToEnum[mappedVrmBoneName]
            if (bone != null) return bone
        }

        return vrmBoneNameToEnum[nodeName.lowercase()]
            ?: vrmBoneNameToEnum[nodeName]
    }

    // ==================== Interpolation ====================

    private fun interpolateTrack(track: AnimationTrack, time: Float): FloatArray {
        val keyframes = track.keyframes
        if (keyframes.isEmpty()) return floatArrayOf()

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

        val t = if (prevFrame.time == nextFrame.time) {
            0f
        } else {
            ((time - prevFrame.time) / (nextFrame.time - prevFrame.time)).coerceIn(0f, 1f)
        }

        return when (track.interpolation) {
            InterpolationType.STEP -> prevFrame.values.copyOf()
            InterpolationType.LINEAR -> {
                if (track.path == AnimationPath.ROTATION) {
                    slerpQuaternion(prevFrame.values, nextFrame.values, t)
                } else {
                    lerpArray(prevFrame.values, nextFrame.values, t)
                }
            }
            InterpolationType.CUBICSPLINE -> lerpArray(prevFrame.values, nextFrame.values, t)
        }
    }

    private fun lerpArray(a: FloatArray, b: FloatArray, t: Float): FloatArray {
        val result = FloatArray(a.size)
        for (i in a.indices) {
            result[i] = a[i] + (b[i] - a[i]) * t
        }
        return result
    }

    private fun slerpQuaternion(q1: FloatArray, q2: FloatArray, t: Float): FloatArray {
        if (q1.size != 4 || q2.size != 4) return q1

        var dot = q1[0] * q2[0] + q1[1] * q2[1] + q1[2] * q2[2] + q1[3] * q2[3]

        val q2Adjusted = if (dot < 0) {
            dot = -dot
            floatArrayOf(-q2[0], -q2[1], -q2[2], -q2[3])
        } else {
            q2
        }

        if (dot > 0.9995f) {
            return normalizeQuaternion(lerpArray(q1, q2Adjusted, t))
        }

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

    private fun normalizeQuaternion(q: FloatArray): FloatArray {
        val length = kotlin.math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (length < 0.0001f) return q
        return floatArrayOf(q[0] / length, q[1] / length, q[2] / length, q[3] / length)
    }

    // ==================== Matrix/Quaternion Utilities ====================

    private fun matrixToQuaternion(m: FloatArray, sx: Float, sy: Float, sz: Float): FloatArray {
        val r00 = m[0] / sx; val r10 = m[1] / sx; val r20 = m[2] / sx
        val r01 = m[4] / sy; val r11 = m[5] / sy; val r21 = m[6] / sy
        val r02 = m[8] / sz; val r12 = m[9] / sz; val r22 = m[10] / sz

        val trace = r00 + r11 + r22
        val x: Float; val y: Float; val z: Float; val w: Float

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

    private fun quaternionToMatrix(q: FloatArray): FloatArray {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]

        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2
        val yy = y * y2; val yz = y * z2; val zz = z * z2
        val wx = w * x2; val wy = w * y2; val wz = w * z2

        return floatArrayOf(
            1f - (yy + zz), xy + wz, xz - wy, 0f,
            xy - wz, 1f - (xx + zz), yz + wx, 0f,
            xz + wy, yz - wx, 1f - (xx + yy), 0f,
            0f, 0f, 0f, 1f
        )
    }

    // ==================== Transform Storage ====================

    private fun storeOriginalTransforms() {
        if (isDestroyed) return

        try {
            val tm = engine.transformManager

            nodeEntityMap.values.forEach { entity ->
                if (isDestroyed) return
                try {
                    val instance = tm.getInstance(entity)
                    if (instance != 0) {
                        val transform = FloatArray(16)
                        tm.getTransform(instance, transform)
                        originalTransforms[entity] = transform.copyOf()
                    }
                } catch (e: Exception) {}
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
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
    }

    private fun resetToOriginalPose() {
        if (isDestroyed) return

        try {
            val tm = engine.transformManager

            originalTransforms.forEach { (entity, transform) ->
                if (isDestroyed) return
                try {
                    val instance = tm.getInstance(entity)
                    if (instance != 0) {
                        tm.setTransform(instance, transform)
                    }
                } catch (e: Exception) {
                    // Entity may have been destroyed — silently skip
                }
            }

            if (!isDestroyed) {
                animator?.updateBoneMatrices()
            }
        } catch (e: Exception) {
            println("[VrmaAnimationPlayer] ERROR: Error in resetToOriginalPose: ${e.message}")
        }
    }
}