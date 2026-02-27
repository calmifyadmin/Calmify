package com.lifo.humanoid.animation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * VRM LookAt controller — ported from Amica's VRMLookAtSmoother.
 *
 * Features (matching Amica):
 *  1. Exponential smoothing (frame-rate independent)
 *  2. Smoothed saccades (random micro eye movements for realism)
 *  3. 0.6 scaling factor (eyes don't 100% lock-on — more natural)
 *  4. Range clamping — eyes never rotate beyond physical limits
 *  5. Behind-threshold — eyes return to center when camera goes behind avatar
 *  6. Range-mapped blend shape output (lookleft/lookright/lookup/lookdown)
 *
 * @see <a href="https://github.com/amica-project/amica">Amica VRMLookAtSmoother</a>
 */
class LookAtController {

    // ==================== Configuration ====================

    /** Enable/disable the controller. */
    var enabled = true

    /** Approximate head position in world space (set after model loads). */
    var headPosition = floatArrayOf(0f, 0.75f, 0f)

    /** Max horizontal eye rotation (degrees). VRM typical: 10-20 for bones. */
    var horizontalMaxAngle = 20f

    /** Max vertical eye rotation (degrees). VRM typical: 10-15 for bones. */
    var verticalMaxAngle = 15f

    /**
     * Raw input angle beyond which the camera is considered "behind" the avatar.
     * Eyes smoothly return to center when |rawYaw| exceeds this.
     */
    var behindThreshold = 80f

    /**
     * Smoothing factor — higher = faster tracking, lower = more lag.
     * Amica uses ~4.0. We use 3.0 for a softer, more organic feel.
     * Formula: k = 1 - exp(-smoothFactor * deltaTime)
     */
    var smoothFactor = 3.0f

    /**
     * User gaze scaling factor.
     * Amica uses 0.6 — eyes don't fully lock on, which is more natural.
     */
    var userGazeScale = 0.6f

    /** Enable/disable saccadic micro-movements. */
    var enableSaccades = true

    // ==================== Saccade Parameters ====================
    // Ported from Amica with smoother transitions:
    // Original: SACCADE_MIN_INTERVAL = 0.5, SACCADE_PROC = 0.05, SACCADE_RADIUS = 5.0

    /** Minimum interval between saccades (seconds). */
    private val saccadeMinInterval = 0.5f

    /** Probability of saccade occurring each eligible frame. */
    private val saccadeProc = 0.05f

    /** Saccade radius in degrees (reduced from Amica's 5.0 for subtler effect). */
    private val saccadeRadius = 2.5f

    /** Smoothing factor for saccade transitions (instant jumps → smooth glides). */
    private val saccadeSmoothFactor = 10.0f

    // ==================== Internal State ====================

    /** Smoothed yaw (degrees, before gaze scale). */
    private var yawDamped = 0f

    /** Smoothed pitch (degrees, before gaze scale). */
    private var pitchDamped = 0f

    /** Last computed yaw (degrees, after smoothing + saccades + scale). For bone rotation. */
    var lastYawDeg = 0f
        private set

    /** Last computed pitch (degrees, after smoothing + saccades + scale). For bone rotation. */
    var lastPitchDeg = 0f
        private set

    /** Current saccade yaw offset (smoothed toward target). */
    private var saccadeYaw = 0f

    /** Current saccade pitch offset (smoothed toward target). */
    private var saccadePitch = 0f

    /** Target saccade yaw (set on trigger, smoothed toward over time). */
    private var saccadeTargetYaw = 0f

    /** Target saccade pitch (set on trigger, smoothed toward over time). */
    private var saccadeTargetPitch = 0f

    /** Timer for saccade interval. */
    private var saccadeTimer = 0f

    /** Reusable output map to avoid allocations. */
    private val outputWeights = mutableMapOf<String, Float>()

    /**
     * Update the LookAt system. Call once per frame.
     *
     * @param cameraEye Current camera eye position in world space [x, y, z].
     * @param deltaSeconds Time since last frame in seconds (typically ~0.016 at 60fps).
     * @return Map of blend shape name -> weight (0..1), or empty if disabled.
     */
    fun update(cameraEye: FloatArray, deltaSeconds: Float): Map<String, Float> {
        if (!enabled) return emptyMap()

        // ── 1. Compute raw yaw/pitch from head to camera ──
        val dx = cameraEye[0] - headPosition[0]
        val dy = cameraEye[1] - headPosition[1]
        val dz = cameraEye[2] - headPosition[2]

        val horizontalDist = sqrt(dx * dx + dz * dz)

        // Yaw: atan2(dx, -dz) because avatar faces -Z in glTF
        // Positive yaw -> camera is to avatar's RIGHT
        val rawYaw = Math.toDegrees(atan2(dx.toDouble(), (-dz).toDouble())).toFloat()

        // Pitch: atan2(dy, horizontalDist)
        // Positive pitch -> camera is ABOVE
        val rawPitch = Math.toDegrees(atan2(dy.toDouble(), horizontalDist.toDouble())).toFloat()

        // ── 2. Clamp + behind threshold ──
        // When camera goes far to the side or behind, eyes return to center
        val isBehind = abs(rawYaw) > behindThreshold
        val targetYaw = if (isBehind) 0f
                         else rawYaw.coerceIn(-horizontalMaxAngle, horizontalMaxAngle)
        val targetPitch = if (isBehind) 0f
                          else rawPitch.coerceIn(-verticalMaxAngle, verticalMaxAngle)

        // ── 3. Exponential smoothing (Amica: VRMLookAtSmoother) ──
        // k = 1 - exp(-smoothFactor * delta) -> frame-rate independent damping
        val k = 1.0f - exp(-smoothFactor * deltaSeconds)
        yawDamped += (targetYaw - yawDamped) * k
        pitchDamped += (targetPitch - pitchDamped) * k

        // ── 4. Apply user gaze scale (Amica: 0.6) ──
        var yawFrame = userGazeScale * yawDamped
        var pitchFrame = userGazeScale * pitchDamped

        // ── 5. Saccades with smooth transitions ──
        if (enableSaccades) {
            saccadeTimer += deltaSeconds

            // Trigger new saccade target
            if (saccadeTimer > saccadeMinInterval && Math.random() < saccadeProc) {
                saccadeTargetYaw = (2f * Math.random().toFloat() - 1f) * saccadeRadius
                saccadeTargetPitch = (2f * Math.random().toFloat() - 1f) * saccadeRadius
                saccadeTimer = 0f
            }

            // Smooth interpolation toward saccade target (no instant jumps)
            val sk = 1.0f - exp(-saccadeSmoothFactor * deltaSeconds)
            saccadeYaw += (saccadeTargetYaw - saccadeYaw) * sk
            saccadePitch += (saccadeTargetPitch - saccadePitch) * sk

            if (!isBehind) {
                yawFrame += saccadeYaw
                pitchFrame += saccadePitch
            }
        }

        // ── 6. Store final angles for bone rotation ──
        // Final clamp to max output range (gaze-scaled)
        val maxYawOut = horizontalMaxAngle * userGazeScale
        val maxPitchOut = verticalMaxAngle * userGazeScale
        lastYawDeg = yawFrame.coerceIn(-maxYawOut, maxYawOut)
        lastPitchDeg = pitchFrame.coerceIn(-maxPitchOut, maxPitchOut)

        // ── 7. Map to blend shape weights with range saturation ──
        outputWeights.clear()

        // Horizontal
        val normalizedYaw = (lastYawDeg / maxYawOut).coerceIn(-1f, 1f)
        if (normalizedYaw > 0.01f) {
            outputWeights["lookright"] = normalizedYaw
            outputWeights["lookleft"] = 0f
        } else if (normalizedYaw < -0.01f) {
            outputWeights["lookleft"] = -normalizedYaw
            outputWeights["lookright"] = 0f
        } else {
            outputWeights["lookleft"] = 0f
            outputWeights["lookright"] = 0f
        }

        // Vertical
        val normalizedPitch = (lastPitchDeg / maxPitchOut).coerceIn(-1f, 1f)
        if (normalizedPitch > 0.01f) {
            outputWeights["lookup"] = normalizedPitch
            outputWeights["lookdown"] = 0f
        } else if (normalizedPitch < -0.01f) {
            outputWeights["lookdown"] = -normalizedPitch
            outputWeights["lookup"] = 0f
        } else {
            outputWeights["lookup"] = 0f
            outputWeights["lookdown"] = 0f
        }

        return outputWeights
    }
}
