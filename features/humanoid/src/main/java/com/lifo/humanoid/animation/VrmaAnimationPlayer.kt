package com.lifo.humanoid.animation

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
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
 */
class VrmaAnimationPlayer(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {

    companion object {
        private const val TAG = "VrmaAnimationPlayer"
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

    // Node name to entity mapping for the current model
    private val nodeEntityMap = mutableMapOf<String, Int>()
    private val humanoidNodeMap = mutableMapOf<HumanoidBone, String>()

    // Original transforms for blending/reset
    private val originalTransforms = mutableMapOf<Int, FloatArray>()

    // Blend weight for smooth transitions
    private var blendWeight: Float = 1f
    private var blendDuration: Float = 0.3f // seconds

    /**
     * Initialize with a FilamentAsset to build node mapping
     */
    fun initialize(asset: FilamentAsset, nodeNames: List<String>) {
        nodeEntityMap.clear()

        // Build node name to entity mapping
        nodeNames.forEachIndexed { index, name ->
            if (index < asset.entities.size) {
                nodeEntityMap[name] = asset.entities[index]
                Log.d(TAG, "Node mapping: '$name' -> entity ${asset.entities[index]}")
            }
        }

        // Store original transforms
        storeOriginalTransforms()

        Log.d(TAG, "Initialized with ${nodeEntityMap.size} node mappings")
    }

    /**
     * Build humanoid bone to node name mapping from animation
     */
    fun buildHumanoidMapping(animation: VrmaAnimation) {
        humanoidNodeMap.clear()

        animation.humanoidBoneMapping.forEach { (boneName, nodeName) ->
            val humanoidBone = HumanoidBone.entries.firstOrNull {
                it.vrmName.equals(boneName, ignoreCase = true)
            }
            if (humanoidBone != null) {
                humanoidNodeMap[humanoidBone] = nodeName
            }
        }

        Log.d(TAG, "Built humanoid mapping: ${humanoidNodeMap.size} bones")
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
        stop()

        _currentAnimation.value = animation
        _isPlaying.value = true
        currentTimeSeconds = 0f
        playbackSpeed = speed
        blendWeight = 0f // Start blending in

        // Build humanoid mapping
        buildHumanoidMapping(animation)

        Log.d(TAG, "Playing animation: ${animation.name}, duration: ${animation.durationSeconds}s, loop: $loop")

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
     * Stop the current animation with optional blend out
     */
    fun stop(blendOut: Boolean = true) {
        if (blendOut && _isPlaying.value) {
            // Could implement blend out here
        }

        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _currentAnimation.value = null
        currentTimeSeconds = 0f
        _playbackProgress.value = 0f

        // Reset to original pose
        resetToOriginalPose()
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

    /**
     * Apply animation at a specific time
     */
    private fun applyAnimation(animation: VrmaAnimation, time: Float) {
        val tm = engine.transformManager

        animation.tracks.forEach { track ->
            val entity = resolveEntity(track.nodeName) ?: return@forEach
            val instance = tm.getInstance(entity)
            if (instance == 0) return@forEach

            // Interpolate keyframes
            val interpolatedValues = interpolateTrack(track, time)
            if (interpolatedValues.isEmpty()) return@forEach

            // Apply based on path type
            when (track.path) {
                AnimationPath.ROTATION -> applyRotation(tm, instance, entity, interpolatedValues)
                AnimationPath.TRANSLATION -> applyTranslation(tm, instance, entity, interpolatedValues)
                AnimationPath.SCALE -> applyScale(tm, instance, entity, interpolatedValues)
                AnimationPath.WEIGHTS -> { /* Handle morph targets if needed */ }
            }
        }
    }

    /**
     * Resolve a node name to its Filament entity
     */
    private fun resolveEntity(nodeName: String): Int? {
        // Try direct node name mapping
        nodeEntityMap[nodeName]?.let { return it }

        // Try humanoid bone mapping
        val humanoidBone = HumanoidBone.entries.firstOrNull { bone ->
            humanoidNodeMap[bone] == nodeName
        }
        if (humanoidBone != null) {
            return boneMapper.getBoneEntity(humanoidBone)
        }

        // Try VRM bone name directly
        val directBone = HumanoidBone.entries.firstOrNull {
            it.vrmName.equals(nodeName, ignoreCase = true)
        }
        if (directBone != null) {
            return boneMapper.getBoneEntity(directBone)
        }

        return null
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

    /**
     * Apply rotation to a transform
     */
    private fun applyRotation(tm: TransformManager, instance: Int, entity: Int, rotation: FloatArray) {
        if (rotation.size != 4) return

        val original = originalTransforms[entity] ?: return
        val transform = original.copyOf()

        // Convert quaternion to rotation matrix
        val rotMatrix = quaternionToMatrix(rotation)

        // Extract position from original
        val px = transform[12]
        val py = transform[13]
        val pz = transform[14]

        // Apply rotation (keeping original scale)
        val sx = kotlin.math.sqrt(original[0] * original[0] + original[1] * original[1] + original[2] * original[2])
        val sy = kotlin.math.sqrt(original[4] * original[4] + original[5] * original[5] + original[6] * original[6])
        val sz = kotlin.math.sqrt(original[8] * original[8] + original[9] * original[9] + original[10] * original[10])

        // Combine rotation and scale
        transform[0] = rotMatrix[0] * sx
        transform[1] = rotMatrix[1] * sx
        transform[2] = rotMatrix[2] * sx
        transform[4] = rotMatrix[4] * sy
        transform[5] = rotMatrix[5] * sy
        transform[6] = rotMatrix[6] * sy
        transform[8] = rotMatrix[8] * sz
        transform[9] = rotMatrix[9] * sz
        transform[10] = rotMatrix[10] * sz

        // Restore position
        transform[12] = px
        transform[13] = py
        transform[14] = pz

        // Blend with original if needed
        if (blendWeight < 1f) {
            for (i in 0..15) {
                transform[i] = original[i] + (transform[i] - original[i]) * blendWeight
            }
        }

        tm.setTransform(instance, transform)
    }

    /**
     * Apply translation to a transform
     */
    private fun applyTranslation(tm: TransformManager, instance: Int, entity: Int, translation: FloatArray) {
        if (translation.size != 3) return

        val original = originalTransforms[entity] ?: return
        val transform = FloatArray(16)
        tm.getTransform(instance, transform)

        // Apply translation with blend
        val blendedX = original[12] + (translation[0] - original[12]) * blendWeight
        val blendedY = original[13] + (translation[1] - original[13]) * blendWeight
        val blendedZ = original[14] + (translation[2] - original[14]) * blendWeight

        transform[12] = blendedX
        transform[13] = blendedY
        transform[14] = blendedZ

        tm.setTransform(instance, transform)
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
     * Convert quaternion to 4x4 rotation matrix
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

        return floatArrayOf(
            1f - (yy + zz), xy + wz, xz - wy, 0f,
            xy - wz, 1f - (xx + zz), yz + wx, 0f,
            xz + wy, yz - wx, 1f - (xx + yy), 0f,
            0f, 0f, 0f, 1f
        )
    }

    /**
     * Store original transforms for all mapped nodes
     */
    private fun storeOriginalTransforms() {
        val tm = engine.transformManager

        nodeEntityMap.values.forEach { entity ->
            val instance = tm.getInstance(entity)
            if (instance != 0) {
                val transform = FloatArray(16)
                tm.getTransform(instance, transform)
                originalTransforms[entity] = transform.copyOf()
            }
        }

        boneMapper.getBoneEntityMap().values.forEach { entity ->
            if (!originalTransforms.containsKey(entity)) {
                val instance = tm.getInstance(entity)
                if (instance != 0) {
                    val transform = FloatArray(16)
                    tm.getTransform(instance, transform)
                    originalTransforms[entity] = transform.copyOf()
                }
            }
        }

        Log.d(TAG, "Stored ${originalTransforms.size} original transforms")
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
    }
}
