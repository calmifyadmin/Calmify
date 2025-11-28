package com.lifo.humanoid.animation

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper.HumanoidBone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies a natural idle pose to convert VRM T-Pose to a relaxed stance.
 * Based on approaches from Animaze/VRoid for natural avatar positioning.
 *
 * T-Pose is the default VRM pose with arms extended horizontally.
 * This controller rotates arms down and applies subtle adjustments for a natural look.
 */
class IdlePoseController(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {

    companion object {
        private const val TAG = "IdlePoseController"

        // Convert degrees to radians
        private fun deg(degrees: Float) = degrees * (PI.toFloat() / 180f)

        // Arm rotation angles for natural pose
        val UPPER_ARM_ROTATION_Z = deg(70f)    // Rotate arms down 70 degrees
        val LOWER_ARM_ROTATION_Z = deg(10f)    // Slight bend at elbow
        val HAND_ROTATION_X = deg(-5f)         // Slight wrist rotation
        val HAND_ROTATION_Z = deg(5f)          // Slight hand angle

        // Subtle body adjustments
        val SPINE_ROTATION_X = deg(-2f)        // Slight forward lean
        val HEAD_ROTATION_X = deg(3f)          // Slight head tilt up
    }

    // Track if idle pose has been applied
    private var isApplied = false

    /**
     * Apply the natural idle pose (arms down, relaxed stance)
     */
    fun applyIdlePose() {
        if (!boneMapper.hasArmBones()) {
            Log.w(TAG, "Missing arm bones for idle pose")
            return
        }

        val tm = engine.transformManager
        Log.d(TAG, "Applying idle pose to avatar")

        // Rotate left arm down
        boneMapper.getBoneEntity(HumanoidBone.LEFT_UPPER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, UPPER_ARM_ROTATION_Z)
        }

        boneMapper.getBoneEntity(HumanoidBone.LEFT_LOWER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, LOWER_ARM_ROTATION_Z)
        }

        boneMapper.getBoneEntity(HumanoidBone.LEFT_HAND)?.let { entity ->
            rotateAroundX(tm, entity, HAND_ROTATION_X)
            rotateAroundZ(tm, entity, HAND_ROTATION_Z)
        }

        // Rotate right arm down (negative Z for right side)
        boneMapper.getBoneEntity(HumanoidBone.RIGHT_UPPER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, -UPPER_ARM_ROTATION_Z)
        }

        boneMapper.getBoneEntity(HumanoidBone.RIGHT_LOWER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, -LOWER_ARM_ROTATION_Z)
        }

        boneMapper.getBoneEntity(HumanoidBone.RIGHT_HAND)?.let { entity ->
            rotateAroundX(tm, entity, HAND_ROTATION_X)
            rotateAroundZ(tm, entity, -HAND_ROTATION_Z)
        }

        // Apply subtle body adjustments
        applyBodyAdjustments(tm)

        isApplied = true
        Log.d(TAG, "Idle pose applied successfully")
    }

    /**
     * Apply subtle body adjustments for more natural stance
     */
    private fun applyBodyAdjustments(tm: TransformManager) {
        // Slight spine adjustment
        boneMapper.getBoneEntity(HumanoidBone.SPINE)?.let { entity ->
            rotateAroundX(tm, entity, SPINE_ROTATION_X)
        }

        // Subtle head tilt
        boneMapper.getBoneEntity(HumanoidBone.HEAD)?.let { entity ->
            rotateAroundX(tm, entity, HEAD_ROTATION_X)
        }
    }

    /**
     * Reset to T-Pose (original VRM pose)
     */
    fun resetToTPose() {
        if (!isApplied) return

        val tm = engine.transformManager

        // Reset all modified bones to stored original transforms
        listOf(
            HumanoidBone.LEFT_UPPER_ARM,
            HumanoidBone.LEFT_LOWER_ARM,
            HumanoidBone.LEFT_HAND,
            HumanoidBone.RIGHT_UPPER_ARM,
            HumanoidBone.RIGHT_LOWER_ARM,
            HumanoidBone.RIGHT_HAND,
            HumanoidBone.SPINE,
            HumanoidBone.HEAD
        ).forEach { bone ->
            boneMapper.getBoneEntity(bone)?.let { entity ->
                val original = boneMapper.getOriginalTransform(entity)
                if (original != null) {
                    val instance = tm.getInstance(entity)
                    if (instance != 0) {
                        tm.setTransform(instance, original)
                    }
                }
            }
        }

        isApplied = false
        Log.d(TAG, "Reset to T-Pose")
    }

    /**
     * Check if idle pose is currently applied
     */
    fun isIdlePoseApplied(): Boolean = isApplied

    /**
     * Rotate an entity around the Z axis
     */
    private fun rotateAroundZ(tm: TransformManager, entity: Int, angle: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val transform = FloatArray(16)
        tm.getTransform(instance, transform)

        val cosAngle = cos(angle)
        val sinAngle = sin(angle)

        // Z-axis rotation matrix
        val rotZ = floatArrayOf(
            cosAngle, sinAngle, 0f, 0f,
            -sinAngle, cosAngle, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        val result = multiplyMatrix4(transform, rotZ)
        tm.setTransform(instance, result)
    }

    /**
     * Rotate an entity around the X axis
     */
    private fun rotateAroundX(tm: TransformManager, entity: Int, angle: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val transform = FloatArray(16)
        tm.getTransform(instance, transform)

        val cosAngle = cos(angle)
        val sinAngle = sin(angle)

        // X-axis rotation matrix
        val rotX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, cosAngle, sinAngle, 0f,
            0f, -sinAngle, cosAngle, 0f,
            0f, 0f, 0f, 1f
        )

        val result = multiplyMatrix4(transform, rotX)
        tm.setTransform(instance, result)
    }

    /**
     * Rotate an entity around the Y axis
     */
    private fun rotateAroundY(tm: TransformManager, entity: Int, angle: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val transform = FloatArray(16)
        tm.getTransform(instance, transform)

        val cosAngle = cos(angle)
        val sinAngle = sin(angle)

        // Y-axis rotation matrix
        val rotY = floatArrayOf(
            cosAngle, 0f, -sinAngle, 0f,
            0f, 1f, 0f, 0f,
            sinAngle, 0f, cosAngle, 0f,
            0f, 0f, 0f, 1f
        )

        val result = multiplyMatrix4(transform, rotY)
        tm.setTransform(instance, result)
    }

    /**
     * Multiply two 4x4 matrices (column-major order as used by Filament)
     */
    private fun multiplyMatrix4(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row + k * 4] * b[k + col * 4]
                }
                result[row + col * 4] = sum
            }
        }
        return result
    }
}
