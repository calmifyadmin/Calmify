package com.lifo.humanoid.rendering

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.lifo.humanoid.data.ar.ArCameraStreamRenderer
import com.lifo.humanoid.data.ar.ArFocusReticle
import com.lifo.humanoid.data.ar.ArPlaneRenderer
import com.lifo.humanoid.domain.ar.ArAnchor
import com.lifo.humanoid.domain.ar.ArFrame
import com.lifo.humanoid.domain.ar.ArPlane
import com.lifo.humanoid.domain.ar.ArHitResult
import com.lifo.humanoid.domain.ar.ArSessionManager
import com.lifo.humanoid.domain.ar.ArTrackingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * AR-enabled Filament renderer that displays the VRM avatar anchored in
 * real-world space using ARCore for tracking and camera feed.
 *
 * Uses SurfaceView (required by ARCore GL context) with camera passthrough
 * as the background, ARCore-driven camera matrices, and anchor-based avatar positioning.
 *
 * Parallel to [FilamentRenderer] (non-AR), sharing the same model loading,
 * blend shape, and LookAt logic.
 */
class ArFilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val arSessionManager: ArSessionManager
) {
    companion object {
        private const val TAG = "ArFilamentRenderer"
        private const val RESIZE_DEBOUNCE_MS = 100L
        private const val MIN_DIMENSION_CHANGE = 10
        private const val FRAME_WAIT_TIMEOUT_MS = 50L
    }

    // Core Filament components
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0

    // Asset loading
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var materialProvider: UbershaderProvider? = null

    // Surface management
    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var swapChain: SwapChain? = null

    // Camera background renderer
    private var cameraStreamRenderer: ArCameraStreamRenderer? = null

    // AR plane visualization + focus reticle
    private var planeRenderer: ArPlaneRenderer? = null
    private var focusReticle: ArFocusReticle? = null
    private var planeVisualizationHidden = false

    // Current loaded avatar
    @Volatile
    private var currentAsset: FilamentAsset? = null

    // VRM blend shape mapping: blend shape name -> (entity, morph target index)
    private val blendShapeMapping = mutableMapOf<String, MutableList<Pair<Int, Int>>>()
    private var pendingBlendShapes: Pair<FilamentAsset, List<com.lifo.humanoid.data.vrm.VrmBlendShape>>? = null
    private val blendShapesInitialized = AtomicBoolean(false)

    // Lifecycle flags
    @Volatile
    private var isInitialized = false
    private val isDestroyed = AtomicBoolean(false)

    // LookAt — avatar eyes follow camera (reuses Amica-style controller)
    private val lookAtController = com.lifo.humanoid.animation.LookAtController()
    private val cameraEyeBuffer = FloatArray(3)
    private var lastFrameTimeNanos = 0L

    // Eye bone entities for bone-based lookAt
    private var leftEyeEntity: Int = 0
    private var rightEyeEntity: Int = 0
    private var hasLookAtBlendShapes = false
    private var lookAtDiagLogged = false
    private var originalLeftEyeTransform: FloatArray? = null
    private var originalRightEyeTransform: FloatArray? = null
    private val boneResultBuffer = FloatArray(16)

    // Avatar positioning
    private var anchorTransform: FloatArray? = null
    private var modelCenterScaleTransform: FloatArray? = null

    // Lighting entity
    private var sunEntity: Int = 0

    // Resize management
    private val isResizing = AtomicBoolean(false)
    private val pendingResize = AtomicBoolean(false)
    private val frameInProgress = AtomicBoolean(false)
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var pendingResizeRunnable: Runnable? = null
    private val currentWidth = AtomicInteger(0)
    private val currentHeight = AtomicInteger(0)

    private val _rendererState = MutableStateFlow(FilamentRenderer.RendererState.IDLE)
    val rendererState: StateFlow<FilamentRenderer.RendererState> = _rendererState.asStateFlow()

    // Model loaded callback
    interface OnModelLoadedListener {
        fun onModelLoaded(asset: FilamentAsset, nodeNames: List<String>)
    }

    @Volatile
    private var modelLoadedListener: OnModelLoadedListener? = null

    fun setOnModelLoadedListener(listener: OnModelLoadedListener?) {
        if (!isDestroyed.get()) modelLoadedListener = listener
    }

    private val frameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Diagnostic: log first few render calls and canRender state
    @Volatile
    private var renderDiagCount = 0
    private var firstRenderLogged = false

    // ==================== Public Accessors ====================

    fun isSafeToUse(): Boolean = isInitialized && !isDestroyed.get()

    fun getEngine(): Engine? {
        if (!isSafeToUse()) return null
        return engine
    }

    fun getCurrentAsset(): FilamentAsset? {
        if (!isSafeToUse()) return null
        return currentAsset
    }

    fun getTransformManager(): TransformManager? {
        if (!isSafeToUse()) return null
        return engine?.transformManager
    }

    fun getAnimator(): com.google.android.filament.gltfio.Animator? {
        if (!isSafeToUse()) return null
        return try {
            currentAsset?.getInstance()?.animator
        } catch (e: Exception) { null }
    }

    fun updateBoneMatrices() {
        if (isDestroyed.get() || !isInitialized) return
        try {
            currentAsset?.getInstance()?.animator?.updateBoneMatrices()
        } catch (_: Exception) { }
    }

    fun canRender(): Boolean {
        val result = isInitialized &&
                !isDestroyed.get() &&
                !isResizing.get() &&
                !pendingResize.get() &&
                swapChain != null &&
                _rendererState.value == FilamentRenderer.RendererState.RENDERING

        // Diagnostic: log first few canRender=false and first canRender=true
        if (renderDiagCount < 5) {
            renderDiagCount++
            if (!result) {
                Log.d(TAG, "canRender=false: init=$isInitialized destroyed=${isDestroyed.get()} " +
                    "resizing=${isResizing.get()} pendingResize=${pendingResize.get()} " +
                    "swapChain=${swapChain != null} state=${_rendererState.value}")
            } else {
                Log.d(TAG, "canRender=TRUE! Rendering pipeline active.")
            }
        }

        return result
    }

    fun pauseRendering() {
        if (isDestroyed.get()) return
        if (_rendererState.value == FilamentRenderer.RendererState.RENDERING) {
            _rendererState.value = FilamentRenderer.RendererState.PAUSED
        }
    }

    fun resumeRendering() {
        if (isDestroyed.get()) return
        if (_rendererState.value == FilamentRenderer.RendererState.PAUSED ||
            _rendererState.value == FilamentRenderer.RendererState.IDLE) {
            _rendererState.value = FilamentRenderer.RendererState.RENDERING
        }
    }

    // ==================== Initialization ====================

    fun initialize() {
        if (isInitialized || isDestroyed.get()) return

        try {
            // Ensure Filament native libs loaded
            FilamentNativeLoader.ensureLoaded()

            // Create Engine with shared EGL context from ArCoreSessionManager.
            // This allows Filament's internal GL context to share textures (OES camera
            // texture) with our ARCore EGL context — matching the pattern from
            // zirman/arcore-filament-example-app reference implementation.
            val sharedContext = arSessionManager.getSharedContext()
            val newEngine = if (sharedContext != null) {
                Log.d(TAG, "Creating Filament Engine with shared EGL context")
                Engine.create(sharedContext)
            } else {
                Log.w(TAG, "No shared EGL context — creating standalone Engine")
                Engine.create()
            }
            engine = newEngine
            renderer = newEngine.createRenderer()
            scene = newEngine.createScene()

            view = newEngine.createView()
            view?.scene = scene

            cameraEntity = EntityManager.get().create()
            camera = newEngine.createCamera(cameraEntity)
            view?.camera = camera

            materialProvider = UbershaderProvider(newEngine)
            assetLoader = AssetLoader(newEngine, materialProvider!!, EntityManager.get())
            resourceLoader = ResourceLoader(newEngine)

            displayHelper = DisplayHelper(context)

            // AR mode: SurfaceView, opaque (camera is background)
            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                isOpaque = true  // Not transparent — camera feed is the background

                renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        if (isDestroyed.get()) return
                        waitForFrameCompletion()
                        val eng = engine ?: return
                        swapChain?.let { eng.destroySwapChain(it) }
                        swapChain = eng.createSwapChain(surface)
                        displayHelper?.attach(renderer!!, surfaceView.display)
                        pendingResize.set(false)

                        // CRITICAL FIX: Set viewport immediately when surface is ready.
                        // Without this, the Filament View has a 0×0 viewport and renders nothing.
                        // Previous bug: setting currentWidth/Height here caused onResized →
                        // handleResizeWithDebounce to filter out the initial resize, so
                        // performResize (which sets the viewport) was NEVER called.
                        val w = surfaceView.width
                        val h = surfaceView.height
                        if (w > 0 && h > 0) {
                            view?.viewport = Viewport(0, 0, w, h)
                            arSessionManager.setDisplayGeometry(w, h)
                            currentWidth.set(w)
                            currentHeight.set(h)
                            isResizing.set(false)
                            pendingResize.set(false)
                            if (_rendererState.value != FilamentRenderer.RendererState.DESTROYED) {
                                _rendererState.value = FilamentRenderer.RendererState.RENDERING
                            }
                            Log.d(TAG, "Surface ready: ${w}x${h} — viewport set, state=RENDERING")
                        }
                    }

                    override fun onDetachedFromSurface() {
                        if (isDestroyed.get()) return
                        waitForFrameCompletion()
                        val eng = engine
                        if (eng != null && !isDestroyed.get()) {
                            swapChain?.let { eng.destroySwapChain(it); swapChain = null }
                        } else {
                            swapChain = null
                        }
                        displayHelper?.detach()
                    }

                    override fun onResized(width: Int, height: Int) {
                        if (!isDestroyed.get()) handleResizeWithDebounce(width, height)
                    }
                }

                attachTo(surfaceView)
            }

            // Setup lighting (slightly different for AR — will be updated by ARCore)
            setupLighting()
            configureArView()

            // Camera stream renderer is lazy-initialized in render() because
            // ArCoreSessionManager.initialize(activity) hasn't been called yet at this
            // point, so getCameraTextureId() returns -1. The texture is created when
            // the AR session is initialized (from CalmifyApp's LaunchedEffect).

            isInitialized = true
            Log.d(TAG, "ArFilamentRenderer initialized with SurfaceView")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR Filament renderer", e)
            cleanup()
            throw RuntimeException("Failed to initialize AR Filament renderer", e)
        }
    }

    private fun setupLighting() {
        val eng = engine ?: return
        val scn = scene ?: return

        sunEntity = EntityManager.get().create()

        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 0.95f)
            .intensity(100000.0f)  // Slightly lower for AR (ARCore light estimation supplements)
            .direction(0.3f, -1.0f, -0.5f)
            .castShadows(true)
            .build(eng, sunEntity)

        scn.addEntity(sunEntity)

        scn.indirectLight = IndirectLight.Builder()
            .intensity(50000.0f)
            .build(eng)
    }

    private fun configureArView() {
        val v = view ?: return
        val eng = engine ?: return

        // Post-processing: lighter for AR (performance)
        v.isPostProcessingEnabled = true
        v.antiAliasing = View.AntiAliasing.FXAA
        v.ambientOcclusion = View.AmbientOcclusion.NONE  // Disable SSAO for AR perf

        v.bloomOptions = v.bloomOptions.apply {
            enabled = false  // No bloom in AR (looks unnatural)
        }

        v.colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .exposure(0.5f)
            .contrast(1.0f)
            .saturation(1.0f)
            .build(eng)

        // AR mode: opaque rendering (camera quad is behind scene)
        v.blendMode = View.BlendMode.OPAQUE
        scene?.skybox = null
    }

    // ==================== AR Camera + Rendering ====================

    /**
     * Main AR render loop. Called once per frame.
     * Integrates ARCore tracking with Filament rendering.
     */
    fun render(frameTimeNanos: Long): Boolean {
        if (!isInitialized || isDestroyed.get()) return false
        if (isResizing.get() || pendingResize.get()) return false

        val currentSwapChain = swapChain ?: return false
        val rend = renderer ?: return false
        val v = view ?: return false
        val cam = camera ?: return false

        if (!frameInProgress.compareAndSet(false, true)) return false

        try {
            if (isDestroyed.get()) return false

            // Diagnostic: log first successful render entry + periodic avatar status
            if (!firstRenderLogged) {
                val vp = v.viewport
                Log.d(TAG, "=== FIRST AR RENDER ===")
                Log.d(TAG, "  swapChain=$currentSwapChain")
                Log.d(TAG, "  viewport=${vp.left},${vp.bottom} ${vp.width}x${vp.height}")
                Log.d(TAG, "  state=${_rendererState.value}")
                Log.d(TAG, "  cameraStreamRenderer=${cameraStreamRenderer != null}")
                Log.d(TAG, "  textureId=${arSessionManager.getCameraTextureId()}")
                Log.d(TAG, "  currentAsset=${currentAsset != null}")
                Log.d(TAG, "  anchorTransform=${anchorTransform != null}")
                Log.d(TAG, "  modelCenterScale=${modelCenterScaleTransform != null}")
                firstRenderLogged = true
            }
            // Log avatar state every 5 seconds (300 frames @ 60fps)
            if (renderDiagCount > 0 && renderDiagCount % 300 == 0) {
                val asset = currentAsset
                val hasAnchor = anchorTransform != null
                val hasModel = modelCenterScaleTransform != null
                val entityCount = asset?.entities?.size ?: 0
                Log.d(TAG, "AR status: asset=${asset != null} entities=$entityCount anchor=$hasAnchor modelTransform=$hasModel planeVizHidden=$planeVisualizationHidden")
                if (hasAnchor && anchorTransform != null) {
                    val a = anchorTransform!!
                    Log.d(TAG, "  anchor pos=(${a[12]}, ${a[13]}, ${a[14]})")
                }
                if (asset != null && hasModel) {
                    val tm = engine?.transformManager
                    val rootInst = tm?.getInstance(asset.root) ?: 0
                    if (rootInst != 0) {
                        val xform = FloatArray(16)
                        tm?.getTransform(rootInst, xform)
                        Log.d(TAG, "  root pos=(${xform[12]}, ${xform[13]}, ${xform[14]}) scale=(${xform[0]}, ${xform[5]}, ${xform[10]})")
                    }
                }
            }

            if (_rendererState.value == FilamentRenderer.RendererState.IDLE) {
                _rendererState.value = FilamentRenderer.RendererState.RENDERING
            }

            // 1. Lazy-init camera stream renderer (needs valid texture from AR session)
            if (cameraStreamRenderer == null) {
                val textureId = arSessionManager.getCameraTextureId()
                if (textureId > 0) {
                    val eng = engine ?: return false
                    val scn = scene ?: return false
                    Log.d(TAG, "Lazy-init camera stream renderer with textureId=$textureId")
                    cameraStreamRenderer = ArCameraStreamRenderer(eng, scn, textureId).also {
                        it.initialize()
                    }
                }
            }

            // 1b. Lazy-init plane renderer and focus reticle
            if (planeRenderer == null) {
                val eng = engine ?: return false
                val scn = scene ?: return false
                planeRenderer = ArPlaneRenderer(eng, scn).also { it.initialize() }
                focusReticle = ArFocusReticle(eng, scn).also { it.initialize() }
                Log.d(TAG, "Plane renderer + focus reticle initialized")
            }

            // 2. Update ARCore frame
            val arFrame = arSessionManager.update()

            // 3. Update camera background UVs (per-frame from transformCoordinates2d)
            cameraStreamRenderer?.updateUvs(arFrame?.cameraUvs)

            // Compute delta time for this frame (always, even when not tracking,
            // so the next TRACKING frame has an accurate delta).
            val deltaSec = if (lastFrameTimeNanos > 0)
                ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
            else 0.016f

            // 4. Apply ARCore camera matrices (if tracking)
            if (arFrame != null && arFrame.trackingState == ArTrackingState.TRACKING) {
                applyArCameraMatrices(cam, arFrame)
                updateAvatarPosition(arFrame)
                applyArLookAt(arFrame, frameTimeNanos)
                updateArLighting(arFrame)

                // 4b. Update plane visualization + focus reticle
                if (!planeVisualizationHidden) {
                    planeRenderer?.updatePlanes(arFrame.updatedPlanes)
                    focusReticle?.updatePosition(
                        arSessionManager.centerHitResult.value,
                        deltaSec
                    )
                }
            }

            lastFrameTimeNanos = frameTimeNanos

            // 5. Render
            if (!rend.beginFrame(currentSwapChain, frameTimeNanos)) return false
            rend.render(v)
            rend.endFrame()

            // 6. Build blend shapes on first render (same as non-AR)
            if (!blendShapesInitialized.get() && pendingBlendShapes != null && !isDestroyed.get()) {
                val (asset, vrmBlendShapes) = pendingBlendShapes!!
                buildBlendShapeMapping(asset, vrmBlendShapes)
                blendShapesInitialized.set(true)
                pendingBlendShapes = null
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during AR render", e)
            return false
        } finally {
            frameInProgress.set(false)
        }
    }

    fun renderFrame(): Boolean = render(System.nanoTime())

    /**
     * Apply ARCore's camera view and projection matrices to Filament's camera.
     * This is what makes the 3D content align with the real world.
     */
    private fun applyArCameraMatrices(cam: Camera, arFrame: ArFrame) {
        // Set projection from ARCore (accounts for device camera intrinsics)
        val proj = arFrame.projectionMatrix
        cam.setCustomProjection(
            doubleArrayOf(
                proj[0].toDouble(), proj[1].toDouble(), proj[2].toDouble(), proj[3].toDouble(),
                proj[4].toDouble(), proj[5].toDouble(), proj[6].toDouble(), proj[7].toDouble(),
                proj[8].toDouble(), proj[9].toDouble(), proj[10].toDouble(), proj[11].toDouble(),
                proj[12].toDouble(), proj[13].toDouble(), proj[14].toDouble(), proj[15].toDouble()
            ),
            0.1, 100.0
        )

        // Invert view matrix to get camera model matrix
        val viewMat = arFrame.viewMatrix
        val modelMatrix = FloatArray(16)
        invertMatrix4(viewMat, modelMatrix)

        cam.setModelMatrix(
            floatArrayOf(
                modelMatrix[0], modelMatrix[1], modelMatrix[2], modelMatrix[3],
                modelMatrix[4], modelMatrix[5], modelMatrix[6], modelMatrix[7],
                modelMatrix[8], modelMatrix[9], modelMatrix[10], modelMatrix[11],
                modelMatrix[12], modelMatrix[13], modelMatrix[14], modelMatrix[15]
            )
        )

        // Store camera position for LookAt
        cameraEyeBuffer[0] = modelMatrix[12]
        cameraEyeBuffer[1] = modelMatrix[13]
        cameraEyeBuffer[2] = modelMatrix[14]
    }

    /**
     * Position the avatar at the AR anchor location.
     */
    // Diagnostic counter for updateAvatarPosition logging
    private var avatarPosLogCount = 0

    private fun updateAvatarPosition(arFrame: ArFrame) {
        val asset = currentAsset
        if (asset == null) {
            if (avatarPosLogCount < 3) { Log.d(TAG, "updateAvatarPosition: no asset loaded"); avatarPosLogCount++ }
            return
        }
        val eng = engine ?: return
        val tm = eng.transformManager
        val rootInstance = tm.getInstance(asset.root)
        if (rootInstance == 0) {
            if (avatarPosLogCount < 3) { Log.w(TAG, "updateAvatarPosition: rootInstance=0"); avatarPosLogCount++ }
            return
        }

        val modelTransform = modelCenterScaleTransform
        if (anchorTransform == null || modelTransform == null) {
            // Normal pre-placement state — don't spam logs
            return
        }

        // Read the LIVE anchor pose from ArCoreSessionManager every frame.
        // ARCore continuously refines anchor poses — using the initial snapshot
        // caused the avatar to drift from its real-world anchor position.
        val liveAnchor = arSessionManager.currentAnchor.value
        if (liveAnchor != null && liveAnchor.trackingState == ArTrackingState.TRACKING) {
            anchorTransform = liveAnchor.pose.copyOf()
        }

        val anchor = anchorTransform ?: return

        // Final transform: Anchor pose * Model center/scale
        val result = FloatArray(16)
        multiplyMatrices4(anchor, modelTransform, result)
        tm.setTransform(rootInstance, result)

        // Log first few times the avatar is actually positioned
        if (avatarPosLogCount < 5) {
            Log.d(TAG, "Avatar positioned at (${result[12]}, ${result[13]}, ${result[14]}) scale=(${result[0]}, ${result[5]}, ${result[10]})")
            avatarPosLogCount++
        }
    }

    /**
     * Apply LookAt using AR camera position (device position in AR space).
     * Avatar eyes track the user holding the device.
     */
    private fun applyArLookAt(arFrame: ArFrame, frameTimeNanos: Long) {
        if (!lookAtController.enabled) return

        val deltaNanos = if (lastFrameTimeNanos > 0) frameTimeNanos - lastFrameTimeNanos else 16_000_000L
        lastFrameTimeNanos = frameTimeNanos
        val deltaSeconds = (deltaNanos / 1_000_000_000f).coerceIn(0.001f, 0.1f)

        // Feed AR camera position to LookAt controller
        val weights = lookAtController.update(arFrame.cameraPosition, deltaSeconds)

        if (hasLookAtBlendShapes && weights.isNotEmpty()) {
            updateBlendShapes(weights)
        }

        if (leftEyeEntity != 0 || rightEyeEntity != 0) {
            val yaw = lookAtController.lastYawDeg
            val pitch = lookAtController.lastPitchDeg
            rotateEyeBone(leftEyeEntity, yaw, pitch, originalLeftEyeTransform)
            rotateEyeBone(rightEyeEntity, yaw, pitch, originalRightEyeTransform)
            try {
                currentAsset?.getInstance()?.animator?.updateBoneMatrices()
            } catch (_: Exception) { }
        }
    }

    /**
     * Update Filament lighting from ARCore light estimation.
     */
    private fun updateArLighting(arFrame: ArFrame) {
        val eng = engine ?: return
        if (sunEntity == 0) return

        val lm = eng.lightManager
        val instance = lm.getInstance(sunEntity)
        if (instance == 0) return

        // Scale sun intensity by ARCore light estimate
        val intensity = (arFrame.lightIntensity * 100000.0f).coerceIn(10000f, 200000f)
        lm.setIntensity(instance, intensity)
    }

    // ==================== Anchor Placement ====================

    /**
     * Set the anchor transform where the avatar should be placed.
     * Call this after [ArSessionManager.placeAnchor].
     */
    fun setAnchorTransform(anchor: ArAnchor) {
        anchorTransform = anchor.pose.copyOf()
        // Reset position log counter so we see the new placement
        avatarPosLogCount = 0
        // Hide plane visualization after placement (avatar is visible now)
        hidePlaneVisualization()
        Log.d(TAG, "=== ANCHOR SET === pos=(${anchor.pose[12]}, ${anchor.pose[13]}, ${anchor.pose[14]})")
        Log.d(TAG, "  currentAsset=${currentAsset != null}, modelTransform=${modelCenterScaleTransform != null}")
        if (currentAsset != null && modelCenterScaleTransform != null) {
            val result = FloatArray(16)
            multiplyMatrices4(anchor.pose, modelCenterScaleTransform!!, result)
            Log.d(TAG, "  Computed avatar pos=(${result[12]}, ${result[13]}, ${result[14]}) scale=(${result[0]}, ${result[5]}, ${result[10]})")
        } else {
            Log.w(TAG, "  !!! Avatar not loaded yet — placement may not work until model loads")
        }
    }

    fun clearAnchor() {
        anchorTransform = null
        // Show plane visualization again for re-placement
        showPlaneVisualization()
    }

    /**
     * Hide plane meshes and focus reticle after avatar placement.
     */
    fun hidePlaneVisualization() {
        planeVisualizationHidden = true
        planeRenderer?.setVisible(false)
        focusReticle?.setVisible(false)
    }

    /**
     * Show plane visualization for (re-)placement.
     */
    fun showPlaneVisualization() {
        planeVisualizationHidden = false
        planeRenderer?.setVisible(true)
        focusReticle?.setVisible(true)
    }

    // ==================== Model Loading ====================

    fun loadModel(
        buffer: ByteBuffer,
        vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape> = emptyList(),
        humanoidBoneNodeIndices: Map<String, Int> = emptyMap(),
        lookAtTypeName: String = "Bone",
        leftEyeNodeName: String? = null,
        rightEyeNodeName: String? = null
    ): FilamentAsset? {
        if (isDestroyed.get() || !isInitialized) return null

        val eng = engine ?: return null
        val scn = scene ?: return null
        val loader = assetLoader ?: return null
        val resLoader = resourceLoader ?: return null

        return try {
            Log.d(TAG, "loadModel (AR): buffer capacity=${buffer.capacity()}")

            currentAsset?.let { asset ->
                scn.removeEntities(asset.entities)
                loader.destroyAsset(asset)
            }

            blendShapeMapping.clear()
            hasLookAtBlendShapes = false
            lookAtDiagLogged = false
            leftEyeEntity = 0
            rightEyeEntity = 0
            originalLeftEyeTransform = null
            originalRightEyeTransform = null
            buffer.position(0)

            val asset = loader.createAsset(buffer)

            if (asset == null) {
                Log.e(TAG, "!!! createAsset returned NULL — GLB data invalid or unsupported")
                return null
            }

            Log.d(TAG, "Asset created: ${asset.entities.size} entities, root=${asset.root}")
            val aabb = asset.boundingBox
            Log.d(TAG, "  boundingBox center=(${aabb.center[0]}, ${aabb.center[1]}, ${aabb.center[2]})")
            Log.d(TAG, "  boundingBox halfExtent=(${aabb.halfExtent[0]}, ${aabb.halfExtent[1]}, ${aabb.halfExtent[2]})")

            resLoader.loadResources(asset)
            resLoader.asyncBeginLoad(asset)

            var pumpIterations = 0
            while (pumpIterations < 50 && !isDestroyed.get()) {
                resLoader.asyncUpdateLoad()
                Thread.sleep(1)
                pumpIterations++
            }
            Log.d(TAG, "Resource loading pumped $pumpIterations iterations")

            if (isDestroyed.get()) {
                loader.destroyAsset(asset)
                return null
            }

            scn.addEntities(asset.entities)
            currentAsset = asset
            Log.d(TAG, "Entities added to scene (${asset.entities.size} total)")

            pendingBlendShapes = Pair(asset, vrmBlendShapes)
            blendShapesInitialized.set(false)
            blendShapeMapping.clear()

            // In AR mode: scale to real-world 1.7m and store the transform
            computeArModelTransform(asset)
            findEyeBones(asset, leftEyeNodeName, rightEyeNodeName)

            val nodeNames = extractNodeNames(asset)
            if (!isDestroyed.get()) {
                modelLoadedListener?.onModelLoaded(asset, nodeNames)
            }

            Log.d(TAG, "AR model loaded OK. eyeBones: L=$leftEyeEntity R=$rightEyeEntity, " +
                "anchorTransform=${anchorTransform != null}, modelTransform=${modelCenterScaleTransform != null}")

            asset
        } catch (e: Exception) {
            Log.e(TAG, "Exception in AR loadModel", e)
            null
        }
    }

    /**
     * Compute the center/scale transform for AR.
     * In AR, 1 unit = 1 meter (ARCore uses meters).
     * We scale the avatar to 1.7m height and center it at origin
     * (the anchor pose will provide the world-space position).
     */
    private fun computeArModelTransform(asset: FilamentAsset) {
        val aabb = asset.boundingBox
        val center = aabb.center
        val halfExtent = aabb.halfExtent

        val heightExtent = halfExtent[1] * 2.0f
        val targetHeight = 1.7f  // Real-world 1.7 meters
        val scale = if (heightExtent > 0f) targetHeight / heightExtent else 1.0f

        Log.d(TAG, "computeArModelTransform: height=${heightExtent}m scale=$scale")
        Log.d(TAG, "  center=(${center[0]}, ${center[1]}, ${center[2]})")
        Log.d(TAG, "  halfExtent=(${halfExtent[0]}, ${halfExtent[1]}, ${halfExtent[2]})")

        val transform = FloatArray(16)
        // Identity base
        transform[15] = 1.0f
        // Scale
        transform[0] = scale
        transform[5] = scale
        transform[10] = scale
        // Center: place feet at Y=0 (the anchor surface)
        transform[12] = -center[0] * scale
        transform[13] = (-center[1] + halfExtent[1]) * scale  // Feet at origin
        transform[14] = -center[2] * scale

        Log.d(TAG, "  result translate=(${transform[12]}, ${transform[13]}, ${transform[14]}) scale=$scale")

        modelCenterScaleTransform = transform

        // Apply initial transform (will be updated when anchor is set)
        val eng = engine ?: return
        val tm = eng.transformManager
        val rootInstance = tm.getInstance(asset.root)
        if (rootInstance == 0) {
            Log.w(TAG, "  rootInstance=0! Cannot set transform on asset.root=${asset.root}")
            return
        }

        if (anchorTransform != null) {
            val result = FloatArray(16)
            multiplyMatrices4(anchorTransform!!, transform, result)
            tm.setTransform(rootInstance, result)
            Log.d(TAG, "  Applied anchor*model transform: pos=(${result[12]}, ${result[13]}, ${result[14]})")
        } else {
            // No anchor yet — place at origin (will be invisible/behind camera in AR)
            tm.setTransform(rootInstance, transform)
            Log.d(TAG, "  No anchor yet — model at origin (invisible in AR until placed)")
        }
    }

    // ==================== Eye Bones & Blend Shapes ====================
    // (Same logic as FilamentRenderer)

    fun setEyeBoneEntities(leftEye: Int, rightEye: Int) {
        if (leftEye != 0) leftEyeEntity = leftEye
        if (rightEye != 0) rightEyeEntity = rightEye
        storeOriginalEyeTransforms()
    }

    fun setLookAtEnabled(enabled: Boolean) {
        lookAtController.enabled = enabled
    }

    private fun findEyeBones(asset: FilamentAsset, vrmLeftEyeNodeName: String?, vrmRightEyeNodeName: String?) {
        leftEyeEntity = 0
        rightEyeEntity = 0
        originalLeftEyeTransform = null
        originalRightEyeTransform = null
        lookAtDiagLogged = false

        asset.entities.forEach { entity ->
            val name = try { asset.getName(entity) } catch (_: Exception) { null } ?: return@forEach

            if (leftEyeEntity == 0 && vrmLeftEyeNodeName != null && name == vrmLeftEyeNodeName) {
                leftEyeEntity = entity; return@forEach
            }
            if (rightEyeEntity == 0 && vrmRightEyeNodeName != null && name == vrmRightEyeNodeName) {
                rightEyeEntity = entity; return@forEach
            }

            val lower = name.lowercase()
            when {
                leftEyeEntity == 0 && (lower == "lefteye" || lower.contains("j_adj_l_faceeye") ||
                        lower.contains("j_bip_l_eye") || lower.contains("left_eye") || lower.contains("eye_l") || lower == "eye.l") -> {
                    leftEyeEntity = entity
                }
                rightEyeEntity == 0 && (lower == "righteye" || lower.contains("j_adj_r_faceeye") ||
                        lower.contains("j_bip_r_eye") || lower.contains("right_eye") || lower.contains("eye_r") || lower == "eye.r") -> {
                    rightEyeEntity = entity
                }
            }
        }

        storeOriginalEyeTransforms()
    }

    private fun storeOriginalEyeTransforms() {
        val tm = engine?.transformManager ?: return
        if (leftEyeEntity != 0 && originalLeftEyeTransform == null) {
            val inst = tm.getInstance(leftEyeEntity)
            if (inst != 0) { val o = FloatArray(16); tm.getTransform(inst, o); originalLeftEyeTransform = o.copyOf() }
        }
        if (rightEyeEntity != 0 && originalRightEyeTransform == null) {
            val inst = tm.getInstance(rightEyeEntity)
            if (inst != 0) { val o = FloatArray(16); tm.getTransform(inst, o); originalRightEyeTransform = o.copyOf() }
        }
    }

    private fun rotateEyeBone(entity: Int, yawDeg: Float, pitchDeg: Float, originalTransform: FloatArray?) {
        if (entity == 0 || originalTransform == null) return
        val eng = engine ?: return
        val tm = eng.transformManager
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        val m = originalTransform
        val yawRad = Math.toRadians(-yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cy = cos(yawRad); val sy = sin(yawRad)
        val cp = cos(pitchRad); val sp = sin(pitchRad)

        val r00 = cy; val r01 = sy * sp; val r02 = sy * cp
        val r10 = 0f; val r11 = cp; val r12 = -sp
        val r20 = -sy; val r21 = cy * sp; val r22 = cy * cp

        val r = boneResultBuffer
        r[0] = m[0]*r00 + m[4]*r10 + m[8]*r20
        r[1] = m[1]*r00 + m[5]*r10 + m[9]*r20
        r[2] = m[2]*r00 + m[6]*r10 + m[10]*r20
        r[3] = 0f
        r[4] = m[0]*r01 + m[4]*r11 + m[8]*r21
        r[5] = m[1]*r01 + m[5]*r11 + m[9]*r21
        r[6] = m[2]*r01 + m[6]*r11 + m[10]*r21
        r[7] = 0f
        r[8] = m[0]*r02 + m[4]*r12 + m[8]*r22
        r[9] = m[1]*r02 + m[5]*r12 + m[9]*r22
        r[10] = m[2]*r02 + m[6]*r12 + m[10]*r22
        r[11] = 0f
        r[12] = m[12]; r[13] = m[13]; r[14] = m[14]; r[15] = m[15]

        tm.setTransform(instance, r)
    }

    fun updateBlendShapes(blendShapes: Map<String, Float>) {
        if (isDestroyed.get() || !isInitialized) return
        if (blendShapes.isEmpty() || blendShapeMapping.isEmpty()) return

        val eng = engine ?: return
        val renderableManager = eng.renderableManager

        blendShapes.forEach { (name, weight) ->
            if (isDestroyed.get()) return
            val normalizedName = name.lowercase()
            val targetWeight = weight.coerceIn(0f, 1f)
            blendShapeMapping[normalizedName]?.forEach { (entity, morphTargetIndex) ->
                try {
                    val instance = renderableManager.getInstance(entity)
                    if (instance != 0 && !isDestroyed.get()) {
                        renderableManager.setMorphWeights(instance, floatArrayOf(targetWeight), morphTargetIndex)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun buildBlendShapeMapping(asset: FilamentAsset, vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape>) {
        if (vrmBlendShapes.isEmpty()) return
        val eng = engine ?: return
        val renderableManager = eng.renderableManager

        val entitiesWithMorphTargets = mutableListOf<Triple<Int, Int, Int>>()
        asset.entities.forEachIndexed { index, entity ->
            val instance = renderableManager.getInstance(entity)
            if (instance != 0) {
                val count = renderableManager.getMorphTargetCount(instance)
                if (count > 0) entitiesWithMorphTargets.add(Triple(entity, index, count))
            }
        }

        if (entitiesWithMorphTargets.isNotEmpty()) {
            val (targetEntity, _, targetMorphCount) = entitiesWithMorphTargets.first()
            vrmBlendShapes.forEach { vrmBlendShape ->
                val blendShapeName = vrmBlendShape.name.lowercase()
                val presetName = vrmBlendShape.preset?.name?.lowercase()
                vrmBlendShape.bindings.forEach { binding ->
                    if (binding.morphTargetIndex < targetMorphCount) {
                        blendShapeMapping.getOrPut(blendShapeName) { mutableListOf() }
                            .add(Pair(targetEntity, binding.morphTargetIndex))
                        if (presetName != null && presetName != "unknown") {
                            blendShapeMapping.getOrPut(presetName) { mutableListOf() }
                                .add(Pair(targetEntity, binding.morphTargetIndex))
                        }
                    }
                }
            }
        }

        // Check for lookAt blend shapes
        hasLookAtBlendShapes = blendShapeMapping.containsKey("lookup") ||
                blendShapeMapping.containsKey("lookleft")
    }

    private fun extractNodeNames(asset: FilamentAsset): List<String> {
        return asset.entities.mapIndexed { index, entity ->
            try { asset.getName(entity) ?: "node_$index" } catch (_: Exception) { "node_$index" }
        }
    }

    // ==================== Resize ====================

    private fun handleResizeWithDebounce(width: Int, height: Int) {
        if (isDestroyed.get() || width <= 0 || height <= 0) return
        if (abs(width - currentWidth.get()) < MIN_DIMENSION_CHANGE &&
            abs(height - currentHeight.get()) < MIN_DIMENSION_CHANGE) return

        pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }
        isResizing.set(true)
        pendingResize.set(true)
        _rendererState.value = FilamentRenderer.RendererState.RESIZING

        pendingResizeRunnable = Runnable { performResize(width, height) }
            .also { resizeHandler.postDelayed(it, RESIZE_DEBOUNCE_MS) }
    }

    private fun performResize(width: Int, height: Int) {
        if (isDestroyed.get()) return
        try {
            waitForFrameCompletion()
            view?.viewport = Viewport(0, 0, width, height)
            // Note: AR mode doesn't use configureCameraProjection — ARCore provides projection
            currentWidth.set(width)
            currentHeight.set(height)
            // Inform ARCore of new display geometry for plane detection
            arSessionManager.setDisplayGeometry(width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error during AR resize", e)
        } finally {
            isResizing.set(false)
            pendingResize.set(false)
            if (!isDestroyed.get()) _rendererState.value = FilamentRenderer.RendererState.RENDERING
            pendingResizeRunnable = null
        }
    }

    private fun waitForFrameCompletion() {
        if (!frameInProgress.get()) return
        val start = System.currentTimeMillis()
        while (frameInProgress.get()) {
            if (System.currentTimeMillis() - start > FRAME_WAIT_TIMEOUT_MS) break
            Thread.sleep(1)
        }
    }

    // ==================== Cleanup ====================

    fun cleanup() {
        if (!isDestroyed.compareAndSet(false, true)) return
        if (!isInitialized) return

        Log.d(TAG, "AR cleanup started")

        try {
            _rendererState.value = FilamentRenderer.RendererState.DESTROYED
            modelLoadedListener = null
            pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }
            pendingResizeRunnable = null
            waitForFrameCompletion()
            frameScope.cancel()

            // Clean up AR overlay renderers FIRST
            focusReticle?.cleanup()
            focusReticle = null
            planeRenderer?.cleanup()
            planeRenderer = null

            // Clean up camera stream renderer
            cameraStreamRenderer?.cleanup()
            cameraStreamRenderer = null

            val assetToDestroy = currentAsset
            currentAsset = null
            blendShapeMapping.clear()
            pendingBlendShapes = null
            anchorTransform = null
            modelCenterScaleTransform = null

            val eng = engine
            val rend = renderer
            val scn = scene
            val v = view
            val camEntity = cameraEntity
            val loader = assetLoader
            val resLoader = resourceLoader
            val matProvider = materialProvider
            val ui = uiHelper

            engine = null; renderer = null; scene = null; view = null; camera = null
            assetLoader = null; resourceLoader = null; materialProvider = null
            uiHelper = null; displayHelper = null; swapChain = null

            try { eng?.flushAndWait() } catch (_: Exception) { }

            if (eng != null && scn != null && loader != null && assetToDestroy != null) {
                try { scn.removeEntities(assetToDestroy.entities); loader.destroyAsset(assetToDestroy) }
                catch (_: Exception) { }
            }

            if (eng != null && sunEntity != 0) {
                try { eng.destroyEntity(sunEntity) } catch (_: Exception) { }
            }

            try { loader?.destroy() } catch (_: Exception) { }
            try { resLoader?.destroy() } catch (_: Exception) { }
            try { matProvider?.destroy() } catch (_: Exception) { }
            try { ui?.detach() } catch (_: Exception) { }

            if (eng != null) {
                try {
                    rend?.let { eng.destroyRenderer(it) }
                    v?.let { eng.destroyView(it) }
                    scn?.let { eng.destroyScene(it) }
                    if (camEntity != 0) {
                        eng.destroyCameraComponent(camEntity)
                        EntityManager.get().destroy(camEntity)
                    }
                } catch (_: Exception) { }
                try { eng.destroy() } catch (_: Exception) { }
            }

            isInitialized = false
            Log.d(TAG, "AR cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during AR cleanup", e)
        }
    }

    // ==================== Matrix Utilities ====================

    /**
     * Invert a 4x4 column-major matrix.
     * For rigid-body transforms (rotation + translation), uses the efficient
     * transpose-of-rotation + negated-translated approach.
     */
    private fun invertMatrix4(src: FloatArray, dst: FloatArray) {
        // For view matrices (rotation + translation), the inverse is:
        // R^T in the rotation part, -R^T * t in the translation part
        val r00 = src[0]; val r01 = src[4]; val r02 = src[8]
        val r10 = src[1]; val r11 = src[5]; val r12 = src[9]
        val r20 = src[2]; val r21 = src[6]; val r22 = src[10]
        val tx = src[12]; val ty = src[13]; val tz = src[14]

        // Transpose rotation
        dst[0] = r00; dst[1] = r01; dst[2] = r02; dst[3] = 0f
        dst[4] = r10; dst[5] = r11; dst[6] = r12; dst[7] = 0f
        dst[8] = r20; dst[9] = r21; dst[10] = r22; dst[11] = 0f

        // -R^T * t
        dst[12] = -(r00 * tx + r10 * ty + r20 * tz)
        dst[13] = -(r01 * tx + r11 * ty + r21 * tz)
        dst[14] = -(r02 * tx + r12 * ty + r22 * tz)
        dst[15] = 1f
    }

    /**
     * Multiply two 4x4 column-major matrices: result = a * b
     */
    private fun multiplyMatrices4(a: FloatArray, b: FloatArray, result: FloatArray) {
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += a[row + k * 4] * b[k + col * 4]
                }
                result[row + col * 4] = sum
            }
        }
    }
}

// Uses FilamentNativeLoader from FilamentRenderer.kt (internal visibility, same package)
