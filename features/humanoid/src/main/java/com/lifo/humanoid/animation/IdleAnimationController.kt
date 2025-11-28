package com.lifo.humanoid.animation

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper.HumanoidBone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/**
 * Controller for procedural idle animations.
 * Simulates breathing, micro-movements, and natural body sway.
 *
 * Since VRM models don't include embedded idle animations,
 * this controller creates procedural animations by modifying bone transforms.
 */
class IdleAnimationController(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {

    companion object {
        private const val TAG = "IdleAnimationController"
        private const val TWO_PI = 2f * PI.toFloat()
    }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private var isRunning = false
    private var animationJob: Job? = null
    private var elapsedTime = 0f

    // Configuration
    private var breathConfig = BreathConfig()
    private var microMovementConfig = MicroMovementConfig()
    private var intensityMultiplier = 1.0f

    /**
     * Breathing animation configuration
     */
    data class BreathConfig(
        val cycleSeconds: Float = 4f,           // Complete breathing cycle duration
        val chestScaleAmount: Float = 0.015f,   // How much the chest expands (subtle)
        val shoulderRiseAmount: Float = 0.003f, // How much shoulders rise
        val spineMovement: Float = 0.002f       // Subtle spine movement
    )

    /**
     * Micro-movement configuration for natural idle
     */
    data class MicroMovementConfig(
        val headSwayAmplitude: Float = 0.015f,  // Radians - very subtle
        val headSwayFrequency: Float = 0.25f,   // Hz - slow
        val bodySwayAmplitude: Float = 0.006f,  // Radians - barely noticeable
        val bodySwayFrequency: Float = 0.12f,   // Hz - very slow
        val weightShiftAmount: Float = 0.004f,  // Subtle weight shifting
        val weightShiftFrequency: Float = 0.08f // Hz - very slow
    )

    /**
     * Start idle animations
     *
     * @param scope CoroutineScope for the animation loop
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) {
            Log.d(TAG, "IdleAnimationController already running")
            return
        }

        if (!boneMapper.hasEssentialBones()) {
            Log.w(TAG, "Missing essential bones for idle animation")
            return
        }

        isRunning = true
        _isActive.value = true
        elapsedTime = 0f

        Log.d(TAG, "Starting IdleAnimationController")

        // Store original transforms
        storeOriginalTransforms()

        animationJob = scope.launch(Dispatchers.Default) {
            val targetDeltaTime = 1f / 60f // Target 60fps
            var lastFrameTime = System.nanoTime()

            while (isActive && isRunning) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
                lastFrameTime = currentTime

                // Update animation
                withContext(Dispatchers.Main) {
                    update(deltaTime.coerceAtMost(0.1f)) // Cap delta to prevent jumps
                }

                // Maintain ~60fps
                val frameTime = (System.nanoTime() - currentTime) / 1_000_000f
                val sleepTime = (targetDeltaTime * 1000f - frameTime).toLong()
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }
    }

    /**
     * Stop idle animations and reset to original pose
     */
    fun stop() {
        Log.d(TAG, "Stopping IdleAnimationController")
        isRunning = false
        _isActive.value = false
        animationJob?.cancel()
        animationJob = null
        resetToDefaultPose()
    }

    /**
     * Set animation intensity (0.0 = none, 1.0 = full)
     */
    fun setIntensity(intensity: Float) {
        intensityMultiplier = intensity.coerceIn(0f, 2f)
    }

    /**
     * Update configuration
     */
    fun setBreathConfig(config: BreathConfig) {
        breathConfig = config
    }

    fun setMicroMovementConfig(config: MicroMovementConfig) {
        microMovementConfig = config
    }

    /**
     * Update one frame of animation
     */
    private fun update(deltaTime: Float) {
        if (!isRunning) return

        elapsedTime += deltaTime
        val tm = engine.transformManager

        // Apply all idle animations
        applyBreathing(tm)
        applyHeadSway(tm)
        applyBodySway(tm)
        applyWeightShift(tm)
    }

    /**
     * Apply breathing animation via chest/spine scaling
     */
    private fun applyBreathing(tm: TransformManager) {
        val chestEntity = boneMapper.getBoneEntity(HumanoidBone.CHEST) ?: return

        // Calculate breathing phase (smooth sine wave)
        val breathPhase = (elapsedTime / breathConfig.cycleSeconds) * TWO_PI
        val breathFactor = (sin(breathPhase) + 1f) / 2f * intensityMultiplier

        // Scale the chest subtly
        val scale = 1f + breathFactor * breathConfig.chestScaleAmount

        // Apply scale to chest
        applyScaleToEntity(tm, chestEntity, scale, 1f, scale)

        // Also apply to upper chest if available (reduced effect)
        boneMapper.getBoneEntity(HumanoidBone.UPPER_CHEST)?.let { upperChestEntity ->
            val upperScale = 1f + breathFactor * breathConfig.chestScaleAmount * 0.5f
            applyScaleToEntity(tm, upperChestEntity, upperScale, 1f, upperScale)
        }

        // Subtle spine movement
        boneMapper.getBoneEntity(HumanoidBone.SPINE)?.let { spineEntity ->
            val spineRotation = sin(breathPhase) * breathConfig.spineMovement * intensityMultiplier
            applyRotationDelta(tm, spineEntity, spineRotation, 0f, 0f)
        }
    }

    /**
     * Apply subtle head sway for natural idle
     */
    private fun applyHeadSway(tm: TransformManager) {
        val headEntity = boneMapper.getBoneEntity(HumanoidBone.HEAD) ?: return

        // Multi-frequency movement for more natural feel
        val primarySway = sin(elapsedTime * microMovementConfig.headSwayFrequency * TWO_PI)
        val secondarySway = cos(elapsedTime * microMovementConfig.headSwayFrequency * 1.3f * TWO_PI) * 0.5f

        val swayX = primarySway * microMovementConfig.headSwayAmplitude * intensityMultiplier
        val swayZ = secondarySway * microMovementConfig.headSwayAmplitude * 0.5f * intensityMultiplier

        applyRotationDelta(tm, headEntity, swayX, 0f, swayZ)

        // Also apply reduced movement to neck
        boneMapper.getBoneEntity(HumanoidBone.NECK)?.let { neckEntity ->
            applyRotationDelta(tm, neckEntity, swayX * 0.3f, 0f, swayZ * 0.3f)
        }
    }

    /**
     * Apply subtle body sway to spine
     */
    private fun applyBodySway(tm: TransformManager) {
        val spineEntity = boneMapper.getBoneEntity(HumanoidBone.SPINE) ?: return

        val swayZ = sin(elapsedTime * microMovementConfig.bodySwayFrequency * TWO_PI) *
                microMovementConfig.bodySwayAmplitude * intensityMultiplier

        applyRotationDelta(tm, spineEntity, 0f, 0f, swayZ)
    }

    /**
     * Apply weight shifting to hips for natural stance
     */
    private fun applyWeightShift(tm: TransformManager) {
        val hipsEntity = boneMapper.getBoneEntity(HumanoidBone.HIPS) ?: return

        val shiftZ = sin(elapsedTime * microMovementConfig.weightShiftFrequency * TWO_PI) *
                microMovementConfig.weightShiftAmount * intensityMultiplier

        applyRotationDelta(tm, hipsEntity, 0f, 0f, shiftZ)
    }

    /**
     * Apply scale to an entity, preserving original transform
     */
    private fun applyScaleToEntity(tm: TransformManager, entity: Int, scaleX: Float, scaleY: Float, scaleZ: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val original = boneMapper.getOriginalTransform(entity) ?: return
        val transform = original.copyOf()

        // Apply scale to the rotation/scale part of the matrix
        // Scale is on the diagonal for a standard transform matrix
        transform[0] *= scaleX
        transform[5] *= scaleY
        transform[10] *= scaleZ

        tm.setTransform(instance, transform)
    }

    /**
     * Apply rotation delta to an entity
     */
    private fun applyRotationDelta(
        tm: TransformManager,
        entity: Int,
        deltaX: Float,
        deltaY: Float,
        deltaZ: Float
    ) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val original = boneMapper.getOriginalTransform(entity) ?: return

        // Build rotation matrix from deltas
        val rotation = buildRotationMatrix(deltaX, deltaY, deltaZ)

        // Multiply original transform by rotation
        val result = multiplyMatrices(original, rotation)
        tm.setTransform(instance, result)
    }

    /**
     * Build a rotation matrix from Euler angles (in radians)
     * Order: ZYX (standard glTF/Filament convention)
     */
    private fun buildRotationMatrix(rotX: Float, rotY: Float, rotZ: Float): FloatArray {
        val cosX = cos(rotX)
        val sinX = sin(rotX)
        val cosY = cos(rotY)
        val sinY = sin(rotY)
        val cosZ = cos(rotZ)
        val sinZ = sin(rotZ)

        return floatArrayOf(
            cosY * cosZ, cosY * sinZ, -sinY, 0f,
            sinX * sinY * cosZ - cosX * sinZ, sinX * sinY * sinZ + cosX * cosZ, sinX * cosY, 0f,
            cosX * sinY * cosZ + sinX * sinZ, cosX * sinY * sinZ - sinX * cosZ, cosX * cosY, 0f,
            0f, 0f, 0f, 1f
        )
    }

    /**
     * Multiply two 4x4 matrices
     */
    private fun multiplyMatrices(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (row in 0..3) {
            for (col in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row + k * 4] * b[k + col * 4]
                }
                result[row + col * 4] = sum
            }
        }
        return result
    }

    /**
     * Store original transforms for all mapped bones
     */
    private fun storeOriginalTransforms() {
        val tm = engine.transformManager

        boneMapper.getBoneEntityMap().values.forEach { entity ->
            val instance = tm.getInstance(entity)
            if (instance != 0) {
                val transform = FloatArray(16)
                tm.getTransform(instance, transform)
                boneMapper.storeOriginalTransform(entity, transform)
            }
        }
        Log.d(TAG, "Stored original transforms for ${boneMapper.getBoneEntityMap().size} bones")
    }

    /**
     * Reset all bones to their original transforms
     */
    private fun resetToDefaultPose() {
        val tm = engine.transformManager

        boneMapper.getBoneEntityMap().values.forEach { entity ->
            val original = boneMapper.getOriginalTransform(entity)
            if (original != null) {
                val instance = tm.getInstance(entity)
                if (instance != 0) {
                    tm.setTransform(instance, original)
                }
            }
        }
        Log.d(TAG, "Reset to default pose")
    }
}
