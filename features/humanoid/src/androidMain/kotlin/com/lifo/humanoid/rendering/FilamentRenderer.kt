package com.lifo.humanoid.rendering

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.utils.*
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
 * Load Filament native libraries
 */
internal object FilamentNativeLoader {
    init {
        try {
            System.loadLibrary("filament-jni")
            System.loadLibrary("gltfio-jni")
            System.loadLibrary("filament-utils-jni")
            System.loadLibrary("filamat-jni")
            println("[FilamentRenderer] All Filament native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            println("[FilamentRenderer] ERROR: Failed to load Filament native libraries: ${e.message}")
            throw e
        }
    }

    fun ensureLoaded() {
        // Trigger static initialization
    }
}

/**
 * Core Filament rendering engine for the humanoid avatar.
 * Uses TextureView for true transparency - allows Material Surface to show through.
 *
 * NOTE: Transparency works on physical devices but may show black on emulators.
 */
class FilamentRenderer(
    private val context: Context,
    private val textureView: TextureView
) {
    // Core Filament components - nullable for safe cleanup
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0

    // Asset loading - nullable for safe cleanup
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var materialProvider: UbershaderProvider? = null

    // Surface management
    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var swapChain: SwapChain? = null

    // Current loaded avatar
    @Volatile
    private var currentAsset: FilamentAsset? = null

    // VRM blend shape mapping: blend shape name -> (entity, morph target index)
    private val blendShapeMapping = mutableMapOf<String, MutableList<Pair<Int, Int>>>()

    // Pending blend shapes to build after first render
    private var pendingBlendShapes: Pair<FilamentAsset, List<com.lifo.humanoid.data.vrm.VrmBlendShape>>? = null
    private val blendShapesInitialized = AtomicBoolean(false)

    // ==================== LIFECYCLE FLAGS ====================
    @Volatile
    private var isInitialized = false

    private val isDestroyed = AtomicBoolean(false)

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
        } catch (e: Exception) {
            null
        }
    }

    fun updateBoneMatrices() {
        if (isDestroyed.get() || !isInitialized) return
        try {
            val asset = currentAsset ?: return
            val instance = asset.getInstance() ?: return
            val animator = instance.animator ?: return
            if (isDestroyed.get()) return
            animator.updateBoneMatrices()
        } catch (e: Exception) {
            // Silently ignore during cleanup
        }
    }

    interface OnModelLoadedListener {
        fun onModelLoaded(asset: FilamentAsset, nodeNames: List<String>)
    }

    @Volatile
    private var modelLoadedListener: OnModelLoadedListener? = null

    fun setOnModelLoadedListener(listener: OnModelLoadedListener?) {
        if (!isDestroyed.get()) {
            modelLoadedListener = listener
        }
    }

    private val frameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Lighting components
    private var sunEntity: Int = 0

    // Camera manipulator for interactive orbit/pan/zoom
    private var manipulator: Manipulator? = null

    // LookAt — avatar eyes follow camera (Amica-style smoothing + saccades)
    private val lookAtController = com.lifo.humanoid.animation.LookAtController()
    private val cameraEyeBuffer = FloatArray(3)
    private val cameraTargetBuffer = FloatArray(3)
    private val cameraUpBuffer = FloatArray(3)
    private var lastFrameTimeNanos = 0L

    // Eye bone entities for bone-based lookAt
    private var leftEyeEntity: Int = 0
    private var rightEyeEntity: Int = 0
    private var hasLookAtBlendShapes = false
    private var lookAtDiagLogged = false
    // Original (rest-pose) eye bone transforms — prevents rotation accumulation
    private var originalLeftEyeTransform: FloatArray? = null
    private var originalRightEyeTransform: FloatArray? = null
    // Reusable buffers for bone rotation math
    private val boneTransformBuffer = FloatArray(16)
    private val boneResultBuffer = FloatArray(16)

    // ==================== Resize State Management ====================

    enum class RendererState {
        IDLE, RENDERING, RESIZING, PAUSED, DESTROYED
    }

    private val _rendererState = MutableStateFlow(RendererState.IDLE)
    val rendererState: StateFlow<RendererState> = _rendererState.asStateFlow()

    private val isResizing = AtomicBoolean(false)
    private val pendingResize = AtomicBoolean(false)
    private val frameInProgress = AtomicBoolean(false)

    private val resizeHandler = Handler(Looper.getMainLooper())
    private var pendingResizeRunnable: Runnable? = null

    private val currentWidth = AtomicInteger(0)
    private val currentHeight = AtomicInteger(0)

    companion object {
        private const val RESIZE_DEBOUNCE_MS = 100L
        private const val MIN_DIMENSION_CHANGE = 10
        private const val FRAME_WAIT_TIMEOUT_MS = 50L
    }

    fun canRender(): Boolean {
        return isInitialized &&
                !isDestroyed.get() &&
                !isResizing.get() &&
                !pendingResize.get() &&
                swapChain != null &&
                _rendererState.value == RendererState.RENDERING
    }

    fun pauseRendering() {
        if (isDestroyed.get()) return
        if (_rendererState.value == RendererState.RENDERING) {
            _rendererState.value = RendererState.PAUSED
            println("[FilamentRenderer] Rendering paused")
        }
    }

    fun resumeRendering() {
        if (isDestroyed.get()) return
        if (_rendererState.value == RendererState.PAUSED) {
            _rendererState.value = RendererState.RENDERING
            println("[FilamentRenderer] Rendering resumed")
        }
    }

    fun prepareForLayoutChange() {
        if (isDestroyed.get()) return
        pendingResize.set(true)
        println("[FilamentRenderer] Preparing for layout change")
    }

    /**
     * Initialize the Filament rendering engine with transparent background.
     */
    fun initialize() {
        if (isInitialized || isDestroyed.get()) return

        try {
            FilamentNativeLoader.ensureLoaded()

            val newEngine = Engine.Builder()
                .config(Engine.Config().apply {
                    minCommandBufferSizeMB = 16  // Default ~7MB too small for complex VRM models
                })
                .build()
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

            // IMPORTANT: Initialize displayHelper BEFORE uiHelper.attachTo()
            displayHelper = DisplayHelper(context)

            // ═══════════════════════════════════════════════════════════
            // TRANSPARENCY SETUP - Step 1: UiHelper with isOpaque = false
            // This must be set BEFORE attachTo()
            // ═══════════════════════════════════════════════════════════
            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                isOpaque = false  // CRITICAL: enables transparency

                renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        if (isDestroyed.get()) {
                            println("[FilamentRenderer] onNativeWindowChanged skipped - destroyed")
                            return
                        }

                        println("[FilamentRenderer] onNativeWindowChanged - creating SwapChain")
                        waitForFrameCompletion()

                        val eng = engine ?: return
                        swapChain?.let { eng.destroySwapChain(it) }
                        swapChain = eng.createSwapChain(surface)

                        displayHelper?.attach(renderer!!, textureView.display)
                        pendingResize.set(false)
                    }

                    override fun onDetachedFromSurface() {
                        // CRITICAL: Early return if already destroyed to prevent native crashes
                        if (isDestroyed.get()) {
                            println("[FilamentRenderer] onDetachedFromSurface skipped - renderer already destroyed")
                            return
                        }

                        println("[FilamentRenderer] onDetachedFromSurface - cleaning up SwapChain")
                        waitForFrameCompletion()
                        val eng = engine
                        if (eng != null && !isDestroyed.get()) {
                            swapChain?.let {
                                eng.destroySwapChain(it)
                                swapChain = null
                            }
                        } else {
                            swapChain = null
                        }
                        displayHelper?.detach()
                    }

                    override fun onResized(width: Int, height: Int) {
                        if (!isDestroyed.get()) {
                            handleResizeWithDebounce(width, height)
                        }
                    }
                }

                attachTo(textureView)
            }

            setupCamera()
            setupLighting()
            configureView()
            configureTransparency()

            isInitialized = true
            println("[FilamentRenderer] FilamentRenderer initialized with TextureView (transparent)")
        } catch (e: Exception) {
            println("[FilamentRenderer] ERROR: Failed to initialize Filament: ${e.message}")
            cleanup()
            throw RuntimeException("Failed to initialize Filament renderer", e)
        }
    }

    private fun setupCamera() {
        val cam = camera ?: return

        // Camera position: positioned to see full body
        val eyeX = 0.0f; val eyeY = 0.85f; val eyeZ = -2.4f
        val targetX = 0.0f; val targetY = 0.75f; val targetZ = 0.0f

        cam.lookAt(
            eyeX.toDouble(), eyeY.toDouble(), eyeZ.toDouble(),
            targetX.toDouble(), targetY.toDouble(), targetZ.toDouble(),
            0.0, 1.0, 0.0
        )

        // Initialize Manipulator for interactive orbit/pan/zoom
        val w = textureView.width.coerceAtLeast(1)
        val h = textureView.height.coerceAtLeast(1)
        manipulator = Manipulator.Builder()
            .targetPosition(targetX, targetY, targetZ)
            .orbitHomePosition(eyeX, eyeY, eyeZ)
            .viewport(w, h)
            .zoomSpeed(0.01f)
            .orbitSpeed(0.005f, 0.005f)
            .build(Manipulator.Mode.ORBIT)

        println("[FilamentRenderer] Camera + Manipulator initialized: eye=($eyeX, $eyeY, $eyeZ), target=($targetX, $targetY, $targetZ)")
    }

    private fun configureCameraProjection(width: Int, height: Int) {
        val cam = camera ?: return
        val aspect = width.toDouble() / height.toDouble()
        cam.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
    }

    private fun setupLighting() {
        val eng = engine ?: return
        val scn = scene ?: return

        sunEntity = EntityManager.get().create()

        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 0.95f)
            .intensity(120000.0f)
            .direction(0.3f, -1.0f, -0.5f)
            .castShadows(true)
            .build(eng, sunEntity)

        scn.addEntity(sunEntity)

        scn.indirectLight = IndirectLight.Builder()
            .intensity(60000.0f)
            .build(eng)
    }

    private fun configureView() {
        val v = view ?: return
        val eng = engine ?: return

        v.isPostProcessingEnabled = true
        v.antiAliasing = View.AntiAliasing.FXAA
        v.ambientOcclusion = View.AmbientOcclusion.SSAO

        v.bloomOptions = v.bloomOptions.apply {
            enabled = true
            strength = 0.08f
            levels = 4
            threshold = true
        }

        v.colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .exposure(0.6f)
            .contrast(1.03f)
            .saturation(1.02f)
            .build(eng)
    }

    /**
     * Configure transparency settings.
     * This is the key to making the background transparent.
     */
    private fun configureTransparency() {
        val v = view ?: return
        val scn = scene ?: return
        val rend = renderer ?: return

        v.blendMode = View.BlendMode.TRANSLUCENT
        scn.skybox = null

        val clearOptions = rend.clearOptions.apply {
            clear = true
        }
        rend.clearOptions = clearOptions

        v.dynamicResolutionOptions = v.dynamicResolutionOptions.apply {
            enabled = false
        }

        v.renderQuality = v.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        println("[FilamentRenderer] Transparency configured: blendMode=TRANSLUCENT, skybox=null, clear=true")
    }

    // ==================== Touch / Camera Manipulator ====================

    fun grabBegin(x: Int, y: Int, strafe: Boolean) {
        manipulator?.grabBegin(x, y, strafe)
    }

    fun grabUpdate(x: Int, y: Int) {
        manipulator?.grabUpdate(x, y)
    }

    fun grabEnd() {
        manipulator?.grabEnd()
    }

    fun scroll(x: Int, y: Int, scrollDelta: Float) {
        manipulator?.scroll(x, y, scrollDelta)
    }

    /** Enable/disable the LookAt system (avatar eyes follow camera). Enabled by default. */
    fun setLookAtEnabled(enabled: Boolean) {
        lookAtController.enabled = enabled
    }

    /**
     * Override eye bone entities (e.g., from VrmHumanoidBoneMapper after initialization).
     * Also stores original transforms for rotation accumulation prevention.
     */
    fun setEyeBoneEntities(leftEye: Int, rightEye: Int) {
        if (leftEye != 0) leftEyeEntity = leftEye
        if (rightEye != 0) rightEyeEntity = rightEye
        storeOriginalEyeTransforms()
        println("[FilamentRenderer] setEyeBoneEntities: L=$leftEyeEntity R=$rightEyeEntity " +
                "(origTransforms: L=${originalLeftEyeTransform != null} R=${originalRightEyeTransform != null})")
    }

    /** Apply manipulator state to the Filament camera. Called each frame. */
    private fun updateCameraFromManipulator() {
        val manip = manipulator ?: return
        val cam = camera ?: return
        manip.getLookAt(cameraEyeBuffer, cameraTargetBuffer, cameraUpBuffer)
        cam.lookAt(
            cameraEyeBuffer[0].toDouble(), cameraEyeBuffer[1].toDouble(), cameraEyeBuffer[2].toDouble(),
            cameraTargetBuffer[0].toDouble(), cameraTargetBuffer[1].toDouble(), cameraTargetBuffer[2].toDouble(),
            cameraUpBuffer[0].toDouble(), cameraUpBuffer[1].toDouble(), cameraUpBuffer[2].toDouble()
        )
    }

    /**
     * Find eye bone entities using multiple strategies:
     * 1. VRM humanoid data — exact node names resolved from glTF nodes array (definitive)
     * 2. Name pattern matching — fallback for common VRoid/UniVRM naming conventions
     *
     * IMPORTANT: Filament entity array indices ≠ glTF node indices!
     * We always search by NAME, never by index.
     *
     * Also stores original transforms for rotation accumulation prevention.
     */
    private fun findEyeBones(
        asset: FilamentAsset,
        vrmLeftEyeNodeName: String? = null,
        vrmRightEyeNodeName: String? = null
    ) {
        leftEyeEntity = 0
        rightEyeEntity = 0
        originalLeftEyeTransform = null
        originalRightEyeTransform = null
        lookAtDiagLogged = false

        val entities = asset.entities

        // Scan all entities once, matching against VRM names and fallback patterns
        entities.forEach { entity ->
            val name = try { asset.getName(entity) } catch (e: Exception) { null } ?: return@forEach

            // Strategy 1: Exact match with VRM humanoid node name (definitive)
            if (leftEyeEntity == 0 && vrmLeftEyeNodeName != null && name == vrmLeftEyeNodeName) {
                leftEyeEntity = entity
                println("[FilamentRenderer] Eye bone (VRM exact): leftEye '$name' → entity=$entity")
                return@forEach
            }
            if (rightEyeEntity == 0 && vrmRightEyeNodeName != null && name == vrmRightEyeNodeName) {
                rightEyeEntity = entity
                println("[FilamentRenderer] Eye bone (VRM exact): rightEye '$name' → entity=$entity")
                return@forEach
            }

            // Strategy 2: Pattern matching (fallback for missing/incomplete VRM data)
            val lower = name.lowercase()
            when {
                leftEyeEntity == 0 && (
                    lower == "lefteye" ||
                    lower.contains("j_adj_l_faceeye") ||  // VRoid Studio
                    lower.contains("j_bip_l_eye") ||
                    lower.contains("left_eye") ||
                    lower.contains("eye_l") ||
                    lower == "eye.l"
                ) -> {
                    leftEyeEntity = entity
                    println("[FilamentRenderer] Eye bone (pattern): leftEye '$name' → entity=$entity")
                }

                rightEyeEntity == 0 && (
                    lower == "righteye" ||
                    lower.contains("j_adj_r_faceeye") ||  // VRoid Studio
                    lower.contains("j_bip_r_eye") ||
                    lower.contains("right_eye") ||
                    lower.contains("eye_r") ||
                    lower == "eye.r"
                ) -> {
                    rightEyeEntity = entity
                    println("[FilamentRenderer] Eye bone (pattern): rightEye '$name' → entity=$entity")
                }
            }
        }

        // Store original (rest-pose) transforms — CRITICAL for preventing rotation accumulation
        storeOriginalEyeTransforms()

        println("[FilamentRenderer] Eye bones result: L=$leftEyeEntity R=$rightEyeEntity, " +
                "origTransform: L=${originalLeftEyeTransform != null} R=${originalRightEyeTransform != null}, " +
                "vrmNames: L='$vrmLeftEyeNodeName' R='$vrmRightEyeNodeName'")
    }

    /** Store rest-pose transforms for eye bones. */
    private fun storeOriginalEyeTransforms() {
        val tm = engine?.transformManager ?: return
        if (leftEyeEntity != 0 && originalLeftEyeTransform == null) {
            val inst = tm.getInstance(leftEyeEntity)
            if (inst != 0) {
                val orig = FloatArray(16)
                tm.getTransform(inst, orig)
                originalLeftEyeTransform = orig.copyOf()
            }
        }
        if (rightEyeEntity != 0 && originalRightEyeTransform == null) {
            val inst = tm.getInstance(rightEyeEntity)
            if (inst != 0) {
                val orig = FloatArray(16)
                tm.getTransform(inst, orig)
                originalRightEyeTransform = orig.copyOf()
            }
        }
    }

    /**
     * Rotate an eye bone by yaw/pitch (in degrees), starting from the ORIGINAL rest-pose transform.
     *
     * CRITICAL: Uses M_original * R_lookAt (not M_current * R_lookAt)
     * to prevent rotation accumulation frame after frame.
     *
     * Column-major layout (Filament convention):
     * R = Ry(yaw) * Rx(pitch)
     */
    private fun rotateEyeBone(entity: Int, yawDeg: Float, pitchDeg: Float, originalTransform: FloatArray?) {
        if (entity == 0 || originalTransform == null) return
        val eng = engine ?: return
        val tm = eng.transformManager
        val instance = tm.getInstance(entity)
        if (instance == 0) return

        // Use ORIGINAL rest-pose transform as base (prevents accumulation!)
        val m = originalTransform

        // Compute lookAt rotation: Ry(yaw) * Rx(pitch)
        val yawRad = Math.toRadians(-yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cy = cos(yawRad); val sy = sin(yawRad)
        val cp = cos(pitchRad); val sp = sin(pitchRad)

        // R = Ry * Rx (column-major):
        // col0: (cy,      0, -sy)
        // col1: (sy*sp,  cp,  cy*sp)
        // col2: (sy*cp, -sp,  cy*cp)
        val r00 = cy;     val r01 = sy * sp; val r02 = sy * cp
        val r10 = 0f;     val r11 = cp;      val r12 = -sp
        val r20 = -sy;    val r21 = cy * sp; val r22 = cy * cp

        // M_new = M_original * R  (right-multiply = local space rotation)
        val r = boneResultBuffer

        // Column 0
        r[0]  = m[0] * r00 + m[4] * r10 + m[8]  * r20
        r[1]  = m[1] * r00 + m[5] * r10 + m[9]  * r20
        r[2]  = m[2] * r00 + m[6] * r10 + m[10] * r20
        r[3]  = 0f
        // Column 1
        r[4]  = m[0] * r01 + m[4] * r11 + m[8]  * r21
        r[5]  = m[1] * r01 + m[5] * r11 + m[9]  * r21
        r[6]  = m[2] * r01 + m[6] * r11 + m[10] * r21
        r[7]  = 0f
        // Column 2
        r[8]  = m[0] * r02 + m[4] * r12 + m[8]  * r22
        r[9]  = m[1] * r02 + m[5] * r12 + m[9]  * r22
        r[10] = m[2] * r02 + m[6] * r12 + m[10] * r22
        r[11] = 0f
        // Column 3 (translation unchanged from original)
        r[12] = m[12]
        r[13] = m[13]
        r[14] = m[14]
        r[15] = m[15]

        tm.setTransform(instance, r)
    }

    /** Compute LookAt and apply via blend shapes AND/OR bone rotation. */
    private fun applyLookAt(frameTimeNanos: Long) {
        if (!lookAtController.enabled) return

        // Compute delta time in seconds (frame-rate independent smoothing)
        val deltaNanos = if (lastFrameTimeNanos > 0) frameTimeNanos - lastFrameTimeNanos else 16_000_000L
        lastFrameTimeNanos = frameTimeNanos
        val deltaSeconds = (deltaNanos / 1_000_000_000f).coerceIn(0.001f, 0.1f)

        val weights = lookAtController.update(cameraEyeBuffer, deltaSeconds)

        // One-time diagnostics log (after blend shapes are built)
        if (!lookAtDiagLogged && blendShapesInitialized.get()) {
            lookAtDiagLogged = true
            hasLookAtBlendShapes = blendShapeMapping.containsKey("lookup") ||
                    blendShapeMapping.containsKey("lookleft") ||
                    blendShapeMapping.containsKey("look_up") ||
                    blendShapeMapping.containsKey("look_left")
            println("[FilamentRenderer] LookAt DIAG: hasBlendShapes=$hasLookAtBlendShapes, " +
                    "eyeBones=(L=$leftEyeEntity, R=$rightEyeEntity), " +
                    "origTransforms=(L=${originalLeftEyeTransform != null}, R=${originalRightEyeTransform != null}), " +
                    "blendShapeKeys=${blendShapeMapping.keys.take(20)}, " +
                    "cameraEye=(${cameraEyeBuffer[0]}, ${cameraEyeBuffer[1]}, ${cameraEyeBuffer[2]}), " +
                    "yaw=${lookAtController.lastYawDeg}, pitch=${lookAtController.lastPitchDeg}")
        }

        // Apply blend shapes (works if model has lookAt morph targets)
        if (hasLookAtBlendShapes && weights.isNotEmpty()) {
            updateBlendShapes(weights)
        }

        // Apply bone rotation (works if model has eye bones — most VRM models)
        if (leftEyeEntity != 0 || rightEyeEntity != 0) {
            val yaw = lookAtController.lastYawDeg
            val pitch = lookAtController.lastPitchDeg
            rotateEyeBone(leftEyeEntity, yaw, pitch, originalLeftEyeTransform)
            rotateEyeBone(rightEyeEntity, yaw, pitch, originalRightEyeTransform)

            // Re-compute bone matrices so skinning picks up our eye rotation
            try {
                currentAsset?.getInstance()?.animator?.updateBoneMatrices()
            } catch (_: Exception) { }
        }
    }

    // ==================== Resize Handling ====================

    private fun handleResizeWithDebounce(width: Int, height: Int) {
        if (isDestroyed.get()) return
        if (width <= 0 || height <= 0) {
            println("[FilamentRenderer] WARNING: Invalid resize dimensions: ${width}x${height}")
            return
        }

        val widthChange = abs(width - currentWidth.get())
        val heightChange = abs(height - currentHeight.get())

        if (widthChange < MIN_DIMENSION_CHANGE && heightChange < MIN_DIMENSION_CHANGE) {
            return
        }

        println("[FilamentRenderer] Resize requested: ${currentWidth.get()}x${currentHeight.get()} -> ${width}x${height}")

        pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }

        isResizing.set(true)
        pendingResize.set(true)
        _rendererState.value = RendererState.RESIZING

        pendingResizeRunnable = Runnable {
            performResize(width, height)
        }.also {
            resizeHandler.postDelayed(it, RESIZE_DEBOUNCE_MS)
        }
    }

    private fun performResize(width: Int, height: Int) {
        if (isDestroyed.get()) {
            println("[FilamentRenderer] performResize skipped - destroyed")
            return
        }

        println("[FilamentRenderer] Performing resize to ${width}x${height}")

        try {
            waitForFrameCompletion()

            view?.viewport = Viewport(0, 0, width, height)
            configureCameraProjection(width, height)
            manipulator?.setViewport(width, height)

            currentWidth.set(width)
            currentHeight.set(height)

            println("[FilamentRenderer] Resize completed successfully")
        } catch (e: Exception) {
            println("[FilamentRenderer] ERROR: Error during resize: ${e.message}")
        } finally {
            isResizing.set(false)
            pendingResize.set(false)
            if (!isDestroyed.get()) {
                _rendererState.value = RendererState.RENDERING
            }
            pendingResizeRunnable = null
        }
    }

    private fun waitForFrameCompletion() {
        if (!frameInProgress.get()) return

        val startTime = System.currentTimeMillis()
        while (frameInProgress.get()) {
            if (System.currentTimeMillis() - startTime > FRAME_WAIT_TIMEOUT_MS) {
                println("[FilamentRenderer] WARNING: Timeout waiting for frame completion")
                break
            }
            Thread.sleep(1)
        }
    }

    /**
     * Pre-validate glTF bone count. Returns false if any skin exceeds 256 bones.
     * This prevents a native SIGABRT in Filament.
     */
    private fun validateBoneCount(buffer: ByteBuffer): Boolean {
        return try {
            buffer.position(0)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int
            if (magic != 0x46546C67) return true // Not glTF, let Filament handle it
            buffer.int // version
            buffer.int // total length
            val jsonLength = buffer.int
            val jsonType = buffer.int
            if (jsonType != 0x4E4F534A) return true
            val jsonBytes = ByteArray(jsonLength)
            buffer.get(jsonBytes)
            val json = org.json.JSONObject(String(jsonBytes, Charsets.UTF_8))
            val skins = json.optJSONArray("skins") ?: return true
            for (i in 0 until skins.length()) {
                val skin = skins.getJSONObject(i)
                val joints = skin.optJSONArray("joints") ?: continue
                if (joints.length() > 256) {
                    println("[FilamentRenderer] REJECTED: skin $i has ${joints.length()} bones (max 256)")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            println("[FilamentRenderer] Bone validation error: ${e.message}")
            true // If we can't validate, let Filament try
        }
    }

    /**
     * Load a VRM/glTF model into the scene.
     */
    fun loadModel(
        buffer: ByteBuffer,
        vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape> = emptyList(),
        humanoidBoneNodeIndices: Map<String, Int> = emptyMap(),
        lookAtTypeName: String = "Bone",
        leftEyeNodeName: String? = null,
        rightEyeNodeName: String? = null
    ): FilamentAsset? {
        if (isDestroyed.get() || !isInitialized) {
            println("[FilamentRenderer] loadModel skipped - not safe to use")
            return null
        }

        val eng = engine ?: return null
        val scn = scene ?: return null
        val loader = assetLoader ?: return null
        val resLoader = resourceLoader ?: return null

        return try {
            println("[FilamentRenderer] loadModel called with buffer: capacity=${buffer.capacity()}")

            currentAsset?.let { asset ->
                println("[FilamentRenderer] Removing previous asset")
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

            // Pre-validate: check bone count before passing to Filament (native SIGABRT if >256)
            if (!validateBoneCount(buffer)) {
                println("[FilamentRenderer] ERROR: Model exceeds Filament 256 bone limit — skipping load")
                return null
            }
            buffer.position(0)

            val asset = loader.createAsset(buffer)

            if (asset != null) {
                println("[FilamentRenderer] Asset created successfully! Entities: ${asset.entities.size}")

                // Use async loading pipeline (NOT loadResources which conflicts with async)
                resLoader.asyncBeginLoad(asset)

                // Pump async loader until complete or timeout (up to 5 seconds)
                var elapsed = 0
                val maxWaitMs = 5000
                while (elapsed < maxWaitMs && !isDestroyed.get()) {
                    resLoader.asyncUpdateLoad()
                    val progress = resLoader.asyncGetLoadProgress()
                    if (progress >= 1.0f) {
                        println("[FilamentRenderer] Async resource loading complete (${elapsed}ms)")
                        break
                    }
                    Thread.sleep(16) // ~60fps pump rate
                    elapsed += 16
                }

                if (isDestroyed.get()) {
                    loader.destroyAsset(asset)
                    return null
                }

                val finalProgress = resLoader.asyncGetLoadProgress()
                if (finalProgress < 1.0f) {
                    println("[FilamentRenderer] WARNING: Resource loading incomplete: ${(finalProgress * 100).toInt()}% after ${maxWaitMs}ms")
                }

                scn.addEntities(asset.entities)
                currentAsset = asset

                pendingBlendShapes = Pair(asset, vrmBlendShapes)
                blendShapesInitialized.set(false)
                blendShapeMapping.clear()

                centerAndScaleAsset(asset)
                findEyeBones(asset, leftEyeNodeName, rightEyeNodeName)

                val nodeNames = extractNodeNames(asset)

                if (!isDestroyed.get()) {
                    modelLoadedListener?.onModelLoaded(asset, nodeNames)
                }

                println("[FilamentRenderer] Model loaded successfully! eyeBones: L=$leftEyeEntity R=$rightEyeEntity")
            } else {
                println("[FilamentRenderer] ERROR: AssetLoader.createAsset() returned null")
            }

            asset
        } catch (e: Exception) {
            println("[FilamentRenderer] ERROR: Exception in loadModel: ${e.message}")
            null
        }
    }

    private fun centerAndScaleAsset(asset: FilamentAsset) {
        val eng = engine ?: return
        val tm = eng.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)

        val aabb = asset.boundingBox
        val center = aabb.center
        val halfExtent = aabb.halfExtent

        val heightExtent = halfExtent[1] * 2.0f
        val targetHeight = 1.7f
        val scale = targetHeight / heightExtent

        // Offset to lower the avatar so it's better framed by the camera
        // Negative value = avatar appears lower on screen
        val verticalOffset = -0.95f

        val transform = FloatArray(16)
        transform[0] = scale
        transform[5] = scale
        transform[10] = scale
        transform[12] = -center[0] * scale
        transform[13] = (-center[1] + halfExtent[1]) * scale + verticalOffset
        transform[14] = -center[2] * scale
        transform[15] = 1.0f

        tm.setTransform(instance, transform)
    }

    /**
     * Main render loop.
     */
    fun render(frameTimeNanos: Long): Boolean {
        if (!isInitialized || isDestroyed.get()) return false
        if (isResizing.get() || pendingResize.get()) return false

        val currentSwapChain = swapChain ?: return false
        val rend = renderer ?: return false
        val v = view ?: return false

        if (!frameInProgress.compareAndSet(false, true)) {
            return false
        }

        try {
            if (isResizing.get() || pendingResize.get() || isDestroyed.get()) {
                return false
            }

            if (_rendererState.value == RendererState.IDLE) {
                _rendererState.value = RendererState.RENDERING
            }

            updateCameraFromManipulator()
            applyLookAt(frameTimeNanos)

            if (!rend.beginFrame(currentSwapChain, frameTimeNanos)) {
                return false
            }

            rend.render(v)
            rend.endFrame()

            if (!blendShapesInitialized.get() && pendingBlendShapes != null && !isDestroyed.get()) {
                println("[FilamentRenderer] First render complete - initializing blend shapes")
                val (asset, vrmBlendShapes) = pendingBlendShapes!!
                buildBlendShapeMapping(asset, vrmBlendShapes)
                blendShapesInitialized.set(true)
                pendingBlendShapes = null
            }

            return true
        } catch (e: Exception) {
            println("[FilamentRenderer] ERROR: Error during render: ${e.message}")
            return false
        } finally {
            frameInProgress.set(false)
        }
    }

    fun renderFrame(): Boolean {
        return render(System.nanoTime())
    }

    private fun buildBlendShapeMapping(
        asset: FilamentAsset,
        vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape>
    ) {
        if (vrmBlendShapes.isEmpty()) {
            println("[FilamentRenderer] WARNING: No VRM blend shapes provided")
            return
        }

        val eng = engine ?: return
        println("[FilamentRenderer] Building blend shape mapping from ${vrmBlendShapes.size} VRM blend shapes")

        val renderableManager = eng.renderableManager
        var totalMappings = 0
        var failedMappings = 0

        val entitiesWithMorphTargets = mutableListOf<Triple<Int, Int, Int>>()

        asset.entities.forEachIndexed { index, entity ->
            val instance = renderableManager.getInstance(entity)
            if (instance != 0) {
                val morphTargetCount = renderableManager.getMorphTargetCount(instance)
                if (morphTargetCount > 0) {
                    entitiesWithMorphTargets.add(Triple(entity, index, morphTargetCount))
                }
            }
        }

        if (entitiesWithMorphTargets.isNotEmpty()) {
            val (targetEntity, _, targetMorphCount) = entitiesWithMorphTargets.first()

            vrmBlendShapes.forEach { vrmBlendShape ->
                val blendShapeName = vrmBlendShape.name.lowercase()
                val presetName = vrmBlendShape.preset?.name?.lowercase()

                vrmBlendShape.bindings.forEach { binding ->
                    val morphTargetIndex = binding.morphTargetIndex

                    if (morphTargetIndex < targetMorphCount) {
                        blendShapeMapping.getOrPut(blendShapeName) { mutableListOf() }
                            .add(Pair(targetEntity, morphTargetIndex))

                        if (presetName != null && presetName != "unknown") {
                            blendShapeMapping.getOrPut(presetName) { mutableListOf() }
                                .add(Pair(targetEntity, morphTargetIndex))
                        }
                        totalMappings++
                    } else {
                        failedMappings++
                    }
                }
            }
        }

        println("[FilamentRenderer] Blend shape mapping: $totalMappings created, $failedMappings failed")
    }

    private fun extractNodeNames(asset: FilamentAsset): List<String> {
        val nodeNames = mutableListOf<String>()
        asset.entities.forEachIndexed { index, entity ->
            val name = try {
                asset.getName(entity) ?: "node_$index"
            } catch (e: Exception) {
                "node_$index"
            }
            nodeNames.add(name)
        }
        return nodeNames
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
            val targets = blendShapeMapping[normalizedName]

            targets?.forEach { (entity, morphTargetIndex) ->
                try {
                    val instance = renderableManager.getInstance(entity)
                    if (instance != 0 && !isDestroyed.get()) {
                        renderableManager.setMorphWeights(
                            instance,
                            floatArrayOf(targetWeight),
                            morphTargetIndex
                        )
                    }
                } catch (e: Exception) {
                    // Ignore - likely during cleanup
                }
            }
        }
    }

    fun cleanup() {
        // Set destroyed flag FIRST to block ALL external access
        if (!isDestroyed.compareAndSet(false, true)) {
            println("[FilamentRenderer] Cleanup already called, skipping")
            return
        }

        if (!isInitialized) {
            println("[FilamentRenderer] Not initialized, nothing to clean up")
            return
        }

        println("[FilamentRenderer] Cleanup started")

        try {
            // Update state immediately
            _rendererState.value = RendererState.DESTROYED

            // Clear listener to prevent any callbacks
            modelLoadedListener = null

            // Cancel pending operations
            pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }
            pendingResizeRunnable = null

            // Wait for any in-progress frame
            waitForFrameCompletion()

            // Cancel coroutines
            frameScope.cancel()

            // Get local references before nullifying
            val assetToDestroy = currentAsset
            currentAsset = null
            blendShapeMapping.clear()
            pendingBlendShapes = null

            manipulator = null

            val eng = engine
            val rend = renderer
            val scn = scene
            val v = view
            val cam = camera
            val camEntity = cameraEntity
            val loader = assetLoader
            val resLoader = resourceLoader
            val matProvider = materialProvider
            val ui = uiHelper
            val disp = displayHelper
            val swap = swapChain

            // Nullify all references IMMEDIATELY
            engine = null
            renderer = null
            scene = null
            view = null
            camera = null
            assetLoader = null
            resourceLoader = null
            materialProvider = null
            uiHelper = null
            displayHelper = null
            swapChain = null

            // Flush engine to complete pending GPU work
            try {
                eng?.flushAndWait()
            } catch (e: Exception) {
                println("[FilamentRenderer] WARNING: flushAndWait failed: ${e.message}")
            }

            // Destroy asset first
            if (eng != null && scn != null && loader != null && assetToDestroy != null) {
                try {
                    scn.removeEntities(assetToDestroy.entities)
                    loader.destroyAsset(assetToDestroy)
                } catch (e: Exception) {
                    println("[FilamentRenderer] ERROR: Error destroying asset: ${e.message}")
                }
            }

            // Destroy lighting
            if (eng != null && sunEntity != 0) {
                try {
                    eng.destroyEntity(sunEntity)
                } catch (e: Exception) {
                    println("[FilamentRenderer] ERROR: Error destroying sun: ${e.message}")
                }
            }

            // Destroy loaders
            try { loader?.destroy() } catch (e: Exception) { println("[FilamentRenderer] ERROR: ${e.message}") }
            try { resLoader?.destroy() } catch (e: Exception) { println("[FilamentRenderer] ERROR: ${e.message}") }
            try { matProvider?.destroy() } catch (e: Exception) { println("[FilamentRenderer] ERROR: ${e.message}") }

            // CRITICAL: Detach UI helper FIRST - this internally calls destroySwapChain()
            // According to Filament docs: "Always detach the surface before destroying the engine"
            // See: https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/android/UiHelper.java
            try { ui?.detach() } catch (e: Exception) { println("[FilamentRenderer] ERROR: Error detaching UI: ${e.message}") }

            // NOTE: SwapChain is already destroyed by ui.detach() - do NOT destroy it again!

            // Destroy Filament components
            if (eng != null) {
                try {
                    rend?.let { eng.destroyRenderer(it) }
                    v?.let { eng.destroyView(it) }
                    scn?.let { eng.destroyScene(it) }
                    if (camEntity != 0) {
                        eng.destroyCameraComponent(camEntity)
                        EntityManager.get().destroy(camEntity)
                    }
                } catch (e: Exception) {
                    println("[FilamentRenderer] ERROR: Error destroying components: ${e.message}")
                }

                // Finally destroy engine
                try {
                    eng.destroy()
                } catch (e: Exception) {
                    println("[FilamentRenderer] ERROR: Error destroying engine: ${e.message}")
                }
            }

            isInitialized = false
            println("[FilamentRenderer] Cleanup completed")
        } catch (e: Exception) {
            println("[FilamentRenderer] ERROR: Error during cleanup: ${e.message}")
        }
    }
}