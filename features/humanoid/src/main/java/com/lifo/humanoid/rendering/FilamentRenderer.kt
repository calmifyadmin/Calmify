package com.lifo.humanoid.rendering

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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

/**
 * Load Filament native libraries
 */
private object FilamentNativeLoader {
    init {
        try {
            System.loadLibrary("filament-jni")
            System.loadLibrary("gltfio-jni")
            System.loadLibrary("filament-utils-jni")
            System.loadLibrary("filamat-jni")
            android.util.Log.d("FilamentRenderer", "All Filament native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("FilamentRenderer", "Failed to load Filament native libraries", e)
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

    private val tag = "FilamentRenderer"

    // Lighting components
    private var sunEntity: Int = 0

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
            Log.d(tag, "Rendering paused")
        }
    }

    fun resumeRendering() {
        if (isDestroyed.get()) return
        if (_rendererState.value == RendererState.PAUSED) {
            _rendererState.value = RendererState.RENDERING
            Log.d(tag, "Rendering resumed")
        }
    }

    fun prepareForLayoutChange() {
        if (isDestroyed.get()) return
        pendingResize.set(true)
        Log.d(tag, "Preparing for layout change")
    }

    /**
     * Initialize the Filament rendering engine with transparent background.
     */
    fun initialize() {
        if (isInitialized || isDestroyed.get()) return

        try {
            FilamentNativeLoader.ensureLoaded()

            val newEngine = Engine.create()
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
                            Log.d(tag, "onNativeWindowChanged skipped - destroyed")
                            return
                        }

                        Log.d(tag, "onNativeWindowChanged - creating SwapChain")
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
                            Log.d(tag, "onDetachedFromSurface skipped - renderer already destroyed")
                            return
                        }

                        Log.d(tag, "onDetachedFromSurface - cleaning up SwapChain")
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
            Log.d(tag, "FilamentRenderer initialized with TextureView (transparent)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Filament", e)
            cleanup()
            throw RuntimeException("Failed to initialize Filament renderer", e)
        }
    }

    private fun setupCamera() {
        val cam = camera ?: return
        val eye = floatArrayOf(0.0f, 1.0f, -2.5f)
        val center = floatArrayOf(0.0f, 0.85f, 0.0f)
        val up = floatArrayOf(0.0f, 1.0f, 0.0f)

        cam.lookAt(
            eye[0].toDouble(), eye[1].toDouble(), eye[2].toDouble(),
            center[0].toDouble(), center[1].toDouble(), center[2].toDouble(),
            up[0].toDouble(), up[1].toDouble(), up[2].toDouble()
        )

        Log.d(tag, "Camera positioned: eye=(${eye[0]}, ${eye[1]}, ${eye[2]})")
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

        Log.d(tag, "Transparency configured: blendMode=TRANSLUCENT, skybox=null, clear=true")
    }

    // ==================== Resize Handling ====================

    private fun handleResizeWithDebounce(width: Int, height: Int) {
        if (isDestroyed.get()) return
        if (width <= 0 || height <= 0) {
            Log.w(tag, "Invalid resize dimensions: ${width}x${height}")
            return
        }

        val widthChange = abs(width - currentWidth.get())
        val heightChange = abs(height - currentHeight.get())

        if (widthChange < MIN_DIMENSION_CHANGE && heightChange < MIN_DIMENSION_CHANGE) {
            return
        }

        Log.d(tag, "Resize requested: ${currentWidth.get()}x${currentHeight.get()} -> ${width}x${height}")

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
            Log.d(tag, "performResize skipped - destroyed")
            return
        }

        Log.d(tag, "Performing resize to ${width}x${height}")

        try {
            waitForFrameCompletion()

            view?.viewport = Viewport(0, 0, width, height)
            configureCameraProjection(width, height)

            currentWidth.set(width)
            currentHeight.set(height)

            Log.d(tag, "Resize completed successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error during resize", e)
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
                Log.w(tag, "Timeout waiting for frame completion")
                break
            }
            Thread.sleep(1)
        }
    }

    /**
     * Load a VRM/glTF model into the scene.
     */
    fun loadModel(
        buffer: ByteBuffer,
        vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape> = emptyList()
    ): FilamentAsset? {
        if (isDestroyed.get() || !isInitialized) {
            Log.d(tag, "loadModel skipped - not safe to use")
            return null
        }

        val eng = engine ?: return null
        val scn = scene ?: return null
        val loader = assetLoader ?: return null
        val resLoader = resourceLoader ?: return null

        return try {
            Log.d(tag, "loadModel called with buffer: capacity=${buffer.capacity()}")

            currentAsset?.let { asset ->
                Log.d(tag, "Removing previous asset")
                scn.removeEntities(asset.entities)
                loader.destroyAsset(asset)
            }

            blendShapeMapping.clear()
            buffer.position(0)

            val asset = loader.createAsset(buffer)

            if (asset != null) {
                Log.d(tag, "Asset created successfully! Entities: ${asset.entities.size}")

                resLoader.loadResources(asset)
                resLoader.asyncBeginLoad(asset)

                var pumpIterations = 0
                while (pumpIterations < 50 && !isDestroyed.get()) {
                    resLoader.asyncUpdateLoad()
                    Thread.sleep(1)
                    pumpIterations++
                }

                if (isDestroyed.get()) {
                    loader.destroyAsset(asset)
                    return null
                }

                scn.addEntities(asset.entities)
                currentAsset = asset

                pendingBlendShapes = Pair(asset, vrmBlendShapes)
                blendShapesInitialized.set(false)
                blendShapeMapping.clear()

                centerAndScaleAsset(asset)

                val nodeNames = extractNodeNames(asset)

                if (!isDestroyed.get()) {
                    modelLoadedListener?.onModelLoaded(asset, nodeNames)
                }

                Log.d(tag, "Model loaded successfully!")
            } else {
                Log.e(tag, "AssetLoader.createAsset() returned null")
            }

            asset
        } catch (e: Exception) {
            Log.e(tag, "Exception in loadModel", e)
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

        val transform = FloatArray(16)
        transform[0] = scale
        transform[5] = scale
        transform[10] = scale
        transform[12] = -center[0] * scale
        transform[13] = (-center[1] + halfExtent[1]) * scale
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

            if (!rend.beginFrame(currentSwapChain, frameTimeNanos)) {
                return false
            }

            rend.render(v)
            rend.endFrame()

            if (!blendShapesInitialized.get() && pendingBlendShapes != null && !isDestroyed.get()) {
                Log.d(tag, "First render complete - initializing blend shapes")
                val (asset, vrmBlendShapes) = pendingBlendShapes!!
                buildBlendShapeMapping(asset, vrmBlendShapes)
                blendShapesInitialized.set(true)
                pendingBlendShapes = null
            }

            return true
        } catch (e: Exception) {
            Log.e(tag, "Error during render", e)
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
            Log.w(tag, "No VRM blend shapes provided")
            return
        }

        val eng = engine ?: return
        Log.d(tag, "Building blend shape mapping from ${vrmBlendShapes.size} VRM blend shapes")

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

        Log.i(tag, "Blend shape mapping: $totalMappings created, $failedMappings failed")
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
            Log.d(tag, "Cleanup already called, skipping")
            return
        }

        if (!isInitialized) {
            Log.d(tag, "Not initialized, nothing to clean up")
            return
        }

        Log.d(tag, "Cleanup started")

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
                Log.w(tag, "flushAndWait failed: ${e.message}")
            }

            // Destroy asset first
            if (eng != null && scn != null && loader != null && assetToDestroy != null) {
                try {
                    scn.removeEntities(assetToDestroy.entities)
                    loader.destroyAsset(assetToDestroy)
                } catch (e: Exception) {
                    Log.e(tag, "Error destroying asset: ${e.message}")
                }
            }

            // Destroy lighting
            if (eng != null && sunEntity != 0) {
                try {
                    eng.destroyEntity(sunEntity)
                } catch (e: Exception) {
                    Log.e(tag, "Error destroying sun: ${e.message}")
                }
            }

            // Destroy loaders
            try { loader?.destroy() } catch (e: Exception) { Log.e(tag, "Error: ${e.message}") }
            try { resLoader?.destroy() } catch (e: Exception) { Log.e(tag, "Error: ${e.message}") }
            try { matProvider?.destroy() } catch (e: Exception) { Log.e(tag, "Error: ${e.message}") }

            // CRITICAL: Detach UI helper FIRST - this internally calls destroySwapChain()
            // According to Filament docs: "Always detach the surface before destroying the engine"
            // See: https://github.com/google/filament/blob/main/android/filament-android/src/main/java/com/google/android/filament/android/UiHelper.java
            try { ui?.detach() } catch (e: Exception) { Log.e(tag, "Error detaching UI: ${e.message}") }

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
                    Log.e(tag, "Error destroying components: ${e.message}")
                }

                // Finally destroy engine
                try {
                    eng.destroy()
                } catch (e: Exception) {
                    Log.e(tag, "Error destroying engine: ${e.message}")
                }
            }

            isInitialized = false
            Log.d(tag, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(tag, "Error during cleanup", e)
        }
    }
}