package com.lifo.humanoid.domain.ar

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over ARCore session lifecycle and tracking.
 * Implementations wrap the platform-specific ARCore APIs while exposing
 * clean domain models consumable by the rendering and presentation layers.
 */
interface ArSessionManager {

    /** Whether AR is available on this device. */
    val isArAvailable: StateFlow<Boolean>

    /** Current AR tracking state. */
    val trackingState: StateFlow<ArTrackingState>

    /** Currently detected planes in the real world. */
    val detectedPlanes: StateFlow<List<ArPlane>>

    /** The active anchor where the avatar is placed, if any. */
    val currentAnchor: StateFlow<ArAnchor?>

    /** Result of per-frame center-screen hit-test (for focus reticle positioning). */
    val centerHitResult: StateFlow<ArHitResult?>

    /**
     * Initialize the AR session. Must be called from an Activity context.
     * @return true if session was created successfully.
     */
    fun initialize(activity: Activity): Boolean

    /** Resume AR tracking (call in onResume). */
    fun resume()

    /** Pause AR tracking (call in onPause). */
    fun pause()

    /** Destroy the AR session and release all resources. */
    fun destroy()

    /**
     * Process a single AR frame. Must be called once per render loop iteration.
     * @return The current frame data, or null if tracking is not available.
     */
    fun update(): ArFrame?

    /**
     * Perform a hit-test against detected surfaces at the given screen coordinates.
     * @return Hit result if a surface was found, null otherwise.
     */
    fun hitTest(x: Float, y: Float, viewWidth: Int, viewHeight: Int): ArHitResult?

    /**
     * Create an anchor at the given hit result location.
     * Replaces any existing anchor.
     * @return The created anchor, or null if placement failed.
     */
    fun placeAnchor(hitResult: ArHitResult): ArAnchor?

    /** Remove the current anchor. */
    fun removeAnchor()

    /**
     * Get the OpenGL texture ID for the camera feed (OES external texture).
     * This must be called AFTER [initialize] and used to create
     * the Filament external texture.
     */
    fun getCameraTextureId(): Int

    /** Get the current display rotation (Surface.ROTATION_0/90/180/270). */
    fun getDisplayRotation(): Int

    /**
     * Inform ARCore of the current display geometry.
     * Must be called whenever the surface size or display rotation changes.
     * ARCore needs this for correct plane detection, hit testing, and
     * transformCoordinates2d() computations.
     */
    fun setDisplayGeometry(width: Int, height: Int)

    /**
     * Get the shared EGL context for Filament Engine creation.
     * Passing this to Engine.create(sharedContext) allows Filament to share
     * GL objects (textures) with the ARCore EGL context.
     * @return EGLContext object, or null if not available.
     */
    fun getSharedContext(): Any? = null
}
