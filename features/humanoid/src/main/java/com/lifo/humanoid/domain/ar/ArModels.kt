package com.lifo.humanoid.domain.ar

/**
 * AR tracking state — abstracts ARCore's TrackingState.
 */
enum class ArTrackingState {
    NOT_AVAILABLE,
    PAUSED,
    TRACKING
}

/**
 * Detected AR plane in the real world.
 */
data class ArPlane(
    val id: Long,
    val centerPose: FloatArray,   // 4x4 column-major transform
    val extentX: Float,
    val extentZ: Float,
    val type: PlaneType
) {
    enum class PlaneType {
        HORIZONTAL_UPWARD,
        HORIZONTAL_DOWNWARD,
        VERTICAL
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArPlane) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * AR anchor — a fixed point in the real world where the avatar is placed.
 */
data class ArAnchor(
    val id: Long,
    val pose: FloatArray,         // 4x4 column-major transform
    val trackingState: ArTrackingState
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArAnchor) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Result of a hit-test against detected AR surfaces.
 */
data class ArHitResult(
    val hitPose: FloatArray,      // 4x4 column-major transform
    val distance: Float,
    val planeId: Long?
)

/**
 * Single AR frame data extracted from ARCore.
 */
data class ArFrame(
    val timestamp: Long,
    val trackingState: ArTrackingState,
    val updatedPlanes: List<ArPlane>,
    val cameraPosition: FloatArray,    // xyz world position
    val viewMatrix: FloatArray,        // 4x4 column-major
    val projectionMatrix: FloatArray,  // 4x4 column-major
    val lightIntensity: Float = 1.0f,
    val lightDirection: FloatArray = floatArrayOf(0f, -1f, 0f),
    val lightColor: FloatArray = floatArrayOf(1f, 1f, 1f),
    val cameraUvs: FloatArray? = null  // Per-vertex UV coords for camera background (6 floats, V-flipped)
)

/**
 * AR placement state machine for the UI overlay.
 */
enum class ArPlacementState {
    INITIALIZING,
    SCANNING,
    READY_TO_PLACE,
    PLACED,
    TRACKING_LOST
}
