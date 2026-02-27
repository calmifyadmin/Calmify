package com.lifo.humanoid.data.ar

import android.app.Activity
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.FloatBuffer
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.lifo.humanoid.domain.ar.ArAnchor
import com.lifo.humanoid.domain.ar.ArFrame
import com.lifo.humanoid.domain.ar.ArHitResult
import com.lifo.humanoid.domain.ar.ArPlane
import com.lifo.humanoid.domain.ar.ArSessionManager
import com.lifo.humanoid.domain.ar.ArTrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArCoreSessionManager @Inject constructor(
    private val context: Context
) : ArSessionManager {

    companion object {
        private const val TAG = "ArCoreSessionManager"
    }

    private var session: Session? = null
    private var activity: Activity? = null
    private var cameraTextureId: Int = -1
    private var currentArCoreAnchor: com.google.ar.core.Anchor? = null
    private var anchorIdCounter = 0L

    // Display geometry for ARCore — needed for plane detection and hit testing.
    // setDisplayGeometry() is called only when rotation/size actually changes,
    // NOT every frame. This matches SceneView's approach (called in onLayout).
    @Volatile private var displayWidth = 0
    @Volatile private var displayHeight = 0
    private var lastSetRotation = -1
    private var lastSetWidth = 0
    private var lastSetHeight = 0
    private var planeLogCount = 0
    @Volatile private var lastFrame: Frame? = null

    // Center hit-test debounce: avoid flickering between null/non-null.
    // Keep the last valid result for N frames after losing it.
    private var centerHitLostFrames = 0
    private var lastValidCenterHit: ArHitResult? = null
    private val CENTER_HIT_DEBOUNCE_FRAMES = 20 // ~333ms at 60fps — longer debounce prevents flicker

    // Camera UV buffers for transformCoordinates2d() — SceneView pattern.
    // Input: 3 UV pairs in VIEW_NORMALIZED matching the oversized triangle.
    // Output: transformed to TEXTURE_NORMALIZED, then V-flipped for OpenGL.
    private val inputUvs = FloatBuffer.wrap(ArCameraStreamRenderer.INPUT_UVS.copyOf())
    private var outputUvs = FloatBuffer.allocate(6)
    private var lastCameraUvs: FloatArray? = null

    // Dedicated EGL context for ARCore GL operations.
    // ARCore requires a valid GL context on the calling thread for
    // session.update() and camera texture creation.
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    private val _isArAvailable = MutableStateFlow(false)
    override val isArAvailable: StateFlow<Boolean> = _isArAvailable.asStateFlow()

    private val _trackingState = MutableStateFlow(ArTrackingState.NOT_AVAILABLE)
    override val trackingState: StateFlow<ArTrackingState> = _trackingState.asStateFlow()

    private val _detectedPlanes = MutableStateFlow<List<ArPlane>>(emptyList())
    override val detectedPlanes: StateFlow<List<ArPlane>> = _detectedPlanes.asStateFlow()

    private val _currentAnchor = MutableStateFlow<ArAnchor?>(null)
    override val currentAnchor: StateFlow<ArAnchor?> = _currentAnchor.asStateFlow()

    private val _centerHitResult = MutableStateFlow<ArHitResult?>(null)
    override val centerHitResult: StateFlow<ArHitResult?> = _centerHitResult.asStateFlow()

    init {
        // Create dedicated EGL context early — needed by both Filament (shared context)
        // and ARCore (GL operations). Must exist before Engine.create(sharedContext).
        createEglContext()
        checkAvailability()
    }

    private fun checkAvailability() {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        _isArAvailable.value = when {
            availability.isSupported -> true
            availability.isTransient -> {
                // Re-check later — ARCore may still be installing
                false
            }
            else -> false
        }
        Log.d(TAG, "ARCore availability: $availability, isSupported=${_isArAvailable.value}")
    }

    override fun initialize(activity: Activity): Boolean {
        this.activity = activity

        if (session != null) {
            Log.d(TAG, "Session already initialized")
            return true
        }

        try {
            // Request ARCore installation if needed
            when (ArCoreApk.getInstance().requestInstall(activity, true)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    Log.d(TAG, "ARCore installation requested")
                    return false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    Log.d(TAG, "ARCore is installed")
                }
            }

            val newSession = Session(activity)
            newSession.configure(newSession.config.apply {
                // HORIZONTAL only — avatar placement on floors/tables.
                // Skipping VERTICAL halves feature-tracking work → faster first plane.
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                // AMBIENT_INTENSITY is ~5x lighter than ENVIRONMENTAL_HDR
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
            })

            // EGL context already created in init{} — just make it current
            makeEglContextCurrent()

            // Create OES texture for camera feed (requires GL context)
            cameraTextureId = createCameraTexture()
            newSession.setCameraTextureName(cameraTextureId)
            Log.d(TAG, "Camera texture created with EGL context, textureId=$cameraTextureId")

            session = newSession
            _isArAvailable.value = true
            Log.d(TAG, "AR session initialized successfully, textureId=$cameraTextureId")
            return true

        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "ARCore APK too old", e)
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "SDK too old for ARCore", e)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR session", e)
        }

        _isArAvailable.value = false
        return false
    }

    override fun resume() {
        try {
            session?.resume()
            Log.d(TAG, "AR session resumed")
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available on resume", e)
            _trackingState.value = ArTrackingState.NOT_AVAILABLE
        }
    }

    override fun pause() {
        session?.pause()
        Log.d(TAG, "AR session paused")
    }

    override fun destroy() {
        currentArCoreAnchor?.detach()
        currentArCoreAnchor = null
        _currentAnchor.value = null

        session?.close()
        session = null
        lastFrame = null
        activity = null

        // Delete GL texture while EGL context is still current
        if (cameraTextureId > 0) {
            makeEglContextCurrent()
            val textures = intArrayOf(cameraTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
            cameraTextureId = -1
        }

        // Clean up dedicated EGL context
        destroyEglContext()

        _trackingState.value = ArTrackingState.NOT_AVAILABLE
        _detectedPlanes.value = emptyList()
        _centerHitResult.value = null
        Log.d(TAG, "AR session destroyed")
    }

    override fun update(): ArFrame? {
        val sess = session ?: return null

        return try {
            // ARCore requires a valid GL context on the calling thread
            makeEglContextCurrent()

            // Tell ARCore the current display geometry — essential for plane detection,
            // hit testing, and transformCoordinates2d().
            // ONLY call when rotation/size actually changes (same as SceneView's onLayout approach).
            // Calling every frame caused black camera on Samsung ANGLE/Vulkan.
            if (displayWidth > 0 && displayHeight > 0) {
                val rotation = getDisplayRotation()
                if (rotation != lastSetRotation || displayWidth != lastSetWidth || displayHeight != lastSetHeight) {
                    sess.setDisplayGeometry(rotation, displayWidth, displayHeight)
                    lastSetRotation = rotation
                    lastSetWidth = displayWidth
                    lastSetHeight = displayHeight
                    Log.d(TAG, "setDisplayGeometry: rotation=$rotation ${displayWidth}x${displayHeight}")
                }
            }

            val frame = sess.update()
            lastFrame = frame
            val camera = frame.camera

            // Update tracking state
            val arTrackingState = mapTrackingState(camera.trackingState)
            _trackingState.value = arTrackingState

            // HEAVY DIAGNOSTIC: log tracking state for first 120 frames (~2 sec)
            if (planeLogCount < 120 || (planeLogCount % 300 == 0)) {
                val rot = getDisplayRotation()
                Log.d(TAG, "Frame #$planeLogCount: tracking=${camera.trackingState} displayGeom=${displayWidth}x${displayHeight} rotation=$rot")
            }

            // Compute camera UVs for the background triangle.
            // Uses Frame.transformCoordinates2d() (same as SceneView's ARCameraStream).
            // This must happen every frame (or when display geometry changes).
            val cameraUvs = computeCameraUvs(frame)

            if (camera.trackingState != TrackingState.TRACKING) {
                if (planeLogCount < 120) {
                    Log.d(TAG, "  Camera NOT TRACKING (${camera.trackingState}) — returning empty planes")
                }
                return ArFrame(
                    timestamp = frame.timestamp,
                    trackingState = arTrackingState,
                    updatedPlanes = emptyList(),
                    cameraPosition = floatArrayOf(0f, 0f, 0f),
                    viewMatrix = FloatArray(16),
                    projectionMatrix = FloatArray(16),
                    cameraUvs = cameraUvs
                )
            }

            // Extract camera matrices
            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            // Camera position from display-oriented pose
            val cameraPose = camera.displayOrientedPose
            val cameraPosition = floatArrayOf(
                cameraPose.tx(), cameraPose.ty(), cameraPose.tz()
            )

            // Update detected planes
            val allPlanes = sess.getAllTrackables(Plane::class.java)
            val trackingPlanes = allPlanes.filter { it.trackingState == TrackingState.TRACKING }
            val planes = trackingPlanes.mapNotNull { plane -> mapPlane(plane) }
            _detectedPlanes.value = planes

            // Diagnostic: log plane detection progress (first 30 frames, then every 60th)
            if (planeLogCount < 30 || (planeLogCount % 60 == 0 && allPlanes.isNotEmpty())) {
                Log.d(TAG, "Planes: total=${allPlanes.size} tracking=${trackingPlanes.size} mapped=${planes.size} displayGeom=${displayWidth}x${displayHeight}")
            }
            planeLogCount++

            // Center-screen hit-test for focus reticle positioning.
            // Runs every frame so the reticle tracks the surface in real-time.
            // Debounced: keeps last valid result for a few frames to prevent flickering.
            if (displayWidth > 0 && displayHeight > 0) {
                val centerHit = performCenterHitTest(frame)
                if (centerHit != null) {
                    lastValidCenterHit = centerHit
                    centerHitLostFrames = 0
                    _centerHitResult.value = centerHit
                } else {
                    centerHitLostFrames++
                    if (centerHitLostFrames > CENTER_HIT_DEBOUNCE_FRAMES) {
                        lastValidCenterHit = null
                        _centerHitResult.value = null
                    }
                    // else: keep the last valid result to avoid flicker
                }
            }

            // Update anchor pose EVERY frame (not just on tracking state change).
            // ARCore continuously refines anchor poses as it builds a better
            // understanding of the environment. Without per-frame updates, the
            // avatar drifts from its anchor and appears at the wrong height.
            currentArCoreAnchor?.let { anchor ->
                val anchorState = mapTrackingState(anchor.trackingState)
                val current = _currentAnchor.value
                if (current != null) {
                    val pose = FloatArray(16)
                    anchor.pose.toMatrix(pose, 0)
                    _currentAnchor.value = current.copy(
                        pose = pose,
                        trackingState = anchorState
                    )
                }
            }

            // Extract light estimation
            var lightIntensity = 1.0f
            val lightDirection = floatArrayOf(0f, -1f, 0f)
            val lightColor = floatArrayOf(1f, 1f, 1f)
            frame.lightEstimate?.let { estimate ->
                if (estimate.state == com.google.ar.core.LightEstimate.State.VALID) {
                    lightIntensity = estimate.pixelIntensity
                }
            }

            ArFrame(
                timestamp = frame.timestamp,
                trackingState = arTrackingState,
                updatedPlanes = planes,
                cameraPosition = cameraPosition,
                viewMatrix = viewMatrix,
                projectionMatrix = projectionMatrix,
                lightIntensity = lightIntensity,
                lightDirection = lightDirection,
                lightColor = lightColor,
                cameraUvs = cameraUvs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating AR frame", e)
            null
        }
    }

    override fun hitTest(x: Float, y: Float, viewWidth: Int, viewHeight: Int): ArHitResult? {
        // Use the last frame from update() — do NOT call sess.update() again,
        // because that would consume a new camera image and the tap coordinates
        // correspond to the currently displayed frame, not a new one.
        val frame = lastFrame
        if (frame == null) {
            Log.w(TAG, "hitTest: lastFrame is null — render loop may not have started yet")
            return null
        }

        return try {
            val hits = frame.hitTest(x, y)
            Log.d(TAG, "hitTest(${x.toInt()},${y.toInt()}): ${hits.size} hit(s)")

            // Find the first hit on a tracking plane
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane &&
                    trackable.isPoseInPolygon(hit.hitPose) &&
                    trackable.trackingState == TrackingState.TRACKING
                ) {
                    val pose = FloatArray(16)
                    hit.hitPose.toMatrix(pose, 0)
                    Log.d(TAG, "hitTest: plane found at distance=${hit.distance} pos=(${pose[12]}, ${pose[13]}, ${pose[14]})")
                    return ArHitResult(
                        hitPose = pose,
                        distance = hit.distance,
                        planeId = trackable.hashCode().toLong()
                    )
                }
            }
            Log.d(TAG, "hitTest: no trackable plane at (${x.toInt()},${y.toInt()}) — ${hits.size} hits but none on tracked plane polygon")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Hit test failed", e)
            null
        }
    }

    /**
     * Perform a hit-test from the center of the screen.
     * Used every frame for the focus reticle positioning.
     * Lighter than the full hitTest — no logging on miss.
     */
    private fun performCenterHitTest(frame: Frame): ArHitResult? {
        val cx = displayWidth / 2f
        val cy = displayHeight / 2f

        return try {
            val hits = frame.hitTest(cx, cy)
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane &&
                    trackable.isPoseInPolygon(hit.hitPose) &&
                    trackable.trackingState == TrackingState.TRACKING
                ) {
                    val pose = FloatArray(16)
                    hit.hitPose.toMatrix(pose, 0)
                    return ArHitResult(
                        hitPose = pose,
                        distance = hit.distance,
                        planeId = trackable.hashCode().toLong()
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun placeAnchor(hitResult: ArHitResult): ArAnchor? {
        val sess = session ?: return null

        return try {
            // Remove previous anchor
            currentArCoreAnchor?.detach()

            // Create anchor from the hit result pose (no need to call sess.update again)
            val pose = com.google.ar.core.Pose.makeTranslation(
                hitResult.hitPose[12], hitResult.hitPose[13], hitResult.hitPose[14]
            )
            val anchor = sess.createAnchor(pose)
            currentArCoreAnchor = anchor

            val anchorPose = FloatArray(16)
            anchor.pose.toMatrix(anchorPose, 0)

            val arAnchor = ArAnchor(
                id = ++anchorIdCounter,
                pose = anchorPose,
                trackingState = mapTrackingState(anchor.trackingState)
            )
            _currentAnchor.value = arAnchor
            Log.d(TAG, "Anchor placed at (${anchorPose[12]}, ${anchorPose[13]}, ${anchorPose[14]})")
            arAnchor
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place anchor", e)
            null
        }
    }

    override fun removeAnchor() {
        currentArCoreAnchor?.detach()
        currentArCoreAnchor = null
        _currentAnchor.value = null
        Log.d(TAG, "Anchor removed")
    }

    override fun getCameraTextureId(): Int = cameraTextureId

    override fun getSharedContext(): Any? = eglContext

    override fun getDisplayRotation(): Int {
        // CRITICAL: Must use Activity context, NOT application context.
        // On Android 12+, application context's defaultDisplay is deprecated and
        // may return wrong rotation values, causing ARCore to misinterpret camera
        // orientation and fail plane detection.
        val act = activity
        return if (act != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            act.display?.rotation ?: Surface.ROTATION_0
        } else if (act != null) {
            @Suppress("DEPRECATION")
            act.windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
        } else {
            // Fallback to application context (unreliable on newer Android)
            @Suppress("DEPRECATION")
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    override fun setDisplayGeometry(width: Int, height: Int) {
        if (displayWidth != width || displayHeight != height) {
            Log.d(TAG, "setDisplayGeometry: ${width}x${height} (was ${displayWidth}x${displayHeight})")
        }
        displayWidth = width
        displayHeight = height
    }

    // --- Private helpers ---

    private fun createEglContext() {
        // GLES 3.0 EGL context — matches Filament's requirements and the
        // zirman/arcore-filament-example-app reference implementation.
        // This context is passed to Engine.create(sharedContext) so Filament's
        // internal GL context shares textures with ours.
        val eglOpenGlEs3Bit = 0x40  // EGL_OPENGL_ES3_BIT_KHR

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed")
            return
        }

        if (!EGL14.eglInitialize(display, null, 0, null, 0)) {
            Log.e(TAG, "eglInitialize failed")
            return
        }

        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                display,
                intArrayOf(EGL14.EGL_RENDERABLE_TYPE, eglOpenGlEs3Bit, EGL14.EGL_NONE),
                0, configs, 0, 1, numConfigs, 0
            )
        ) {
            Log.e(TAG, "eglChooseConfig failed")
            return
        }
        val config = configs[0] ?: run {
            Log.e(TAG, "No EGL config found for GLES 3")
            return
        }

        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0
        )
        if (context == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed")
            return
        }

        val surface = EGL14.eglCreatePbufferSurface(
            display, config,
            intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        if (surface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreatePbufferSurface failed")
            EGL14.eglDestroyContext(display, context)
            return
        }

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            Log.e(TAG, "eglMakeCurrent failed during init")
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            return
        }

        eglDisplay = display
        eglContext = context
        eglSurface = surface
        Log.d(TAG, "EGL GLES3 context created successfully for ARCore + Filament shared context")
    }

    private fun makeEglContextCurrent() {
        val display = eglDisplay ?: return
        val surface = eglSurface ?: return
        val context = eglContext ?: return
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            Log.e(TAG, "eglMakeCurrent failed: error=0x${Integer.toHexString(EGL14.eglGetError())}")
        }
    }

    private fun destroyEglContext() {
        val display = eglDisplay
        if (display != null) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            eglSurface?.let { EGL14.eglDestroySurface(display, it) }
            eglContext?.let { EGL14.eglDestroyContext(display, it) }
            EGL14.eglTerminate(display)
            Log.d(TAG, "EGL context destroyed")
        }
        eglDisplay = null
        eglContext = null
        eglSurface = null
    }

    private fun createCameraTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        return textures[0]
    }

    /**
     * Compute per-vertex UV coordinates for the camera background triangle.
     *
     * Uses Frame.transformCoordinates2d() to map VIEW_NORMALIZED UVs to
     * TEXTURE_NORMALIZED, then flips V for OpenGL convention.
     * This is the same approach as SceneView's ARCameraStream.kt.
     *
     * @return 6 floats (3 pairs of u,v) ready to upload to vertex buffer,
     *         or the last computed UVs if transform fails.
     */
    private fun computeCameraUvs(frame: Frame): FloatArray? {
        if (displayWidth <= 0 || displayHeight <= 0) {
            return lastCameraUvs  // setDisplayGeometry not called yet
        }

        return try {
            inputUvs.rewind()
            outputUvs.rewind()

            frame.transformCoordinates2d(
                Coordinates2d.VIEW_NORMALIZED,
                inputUvs,
                Coordinates2d.TEXTURE_NORMALIZED,
                outputUvs
            )

            // Read output and flip V for OpenGL (origin bottom-left vs ARCore top-left)
            val result = FloatArray(6)
            outputUvs.rewind()
            outputUvs.get(result)
            for (i in 1 until 6 step 2) {
                result[i] = 1.0f - result[i]
            }

            lastCameraUvs = result

            if (planeLogCount < 3) {
                Log.d(TAG, "Camera UVs: (${result[0]},${result[1]}) (${result[2]},${result[3]}) (${result[4]},${result[5]})")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "transformCoordinates2d failed", e)
            lastCameraUvs
        }
    }

    private fun mapTrackingState(state: TrackingState): ArTrackingState = when (state) {
        TrackingState.TRACKING -> ArTrackingState.TRACKING
        TrackingState.PAUSED -> ArTrackingState.PAUSED
        TrackingState.STOPPED -> ArTrackingState.NOT_AVAILABLE
    }

    private fun mapPlane(plane: Plane): ArPlane? {
        if (plane.subsumedBy != null) return null // Skip merged planes

        val centerPose = FloatArray(16)
        plane.centerPose.toMatrix(centerPose, 0)

        return ArPlane(
            id = plane.hashCode().toLong(),
            centerPose = centerPose,
            extentX = plane.extentX,
            extentZ = plane.extentZ,
            type = when (plane.type) {
                Plane.Type.HORIZONTAL_UPWARD_FACING -> ArPlane.PlaneType.HORIZONTAL_UPWARD
                Plane.Type.HORIZONTAL_DOWNWARD_FACING -> ArPlane.PlaneType.HORIZONTAL_DOWNWARD
                Plane.Type.VERTICAL -> ArPlane.PlaneType.VERTICAL
            }
        )
    }
}
