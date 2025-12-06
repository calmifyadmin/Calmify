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
    // Core Filament components
    internal lateinit var engine: Engine
    internal lateinit var renderer: Renderer
    internal lateinit var scene: Scene
    internal lateinit var view: View
    internal lateinit var camera: Camera

    // Asset loading
    internal lateinit var assetLoader: AssetLoader
    internal lateinit var resourceLoader: ResourceLoader
    internal lateinit var materialProvider: UbershaderProvider

    // Surface management
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper
    private var swapChain: SwapChain? = null

    // Current loaded avatar
    private var currentAsset: FilamentAsset? = null

    // VRM blend shape mapping: blend shape name -> (entity, morph target index)
    private val blendShapeMapping = mutableMapOf<String, MutableList<Pair<Int, Int>>>()

    // Pending blend shapes to build after first render
    private var pendingBlendShapes: Pair<FilamentAsset, List<com.lifo.humanoid.data.vrm.VrmBlendShape>>? = null
    private val blendShapesInitialized = AtomicBoolean(false)

    // ==================== Public Accessors ====================

    fun getEngine(): Engine? = if (isInitialized) engine else null
    fun getCurrentAsset(): FilamentAsset? = currentAsset
    fun getTransformManager(): TransformManager? = if (isInitialized) engine.transformManager else null

    fun getAnimator(): com.google.android.filament.gltfio.Animator? {
        return currentAsset?.getInstance()?.animator
    }

    fun updateBoneMatrices() {
        currentAsset?.getInstance()?.animator?.updateBoneMatrices()
    }

    interface OnModelLoadedListener {
        fun onModelLoaded(asset: FilamentAsset, nodeNames: List<String>)
    }

    private var modelLoadedListener: OnModelLoadedListener? = null

    fun setOnModelLoadedListener(listener: OnModelLoadedListener?) {
        modelLoadedListener = listener
    }

    // Rendering state
    private var isInitialized = false
    private val frameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val tag = "FilamentRenderer"

    // Lighting components
    private var sunEntity: Int = 0

    // ==================== Resize State Management ====================

    enum class RendererState {
        IDLE, RENDERING, RESIZING, PAUSED
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
                !isResizing.get() &&
                !pendingResize.get() &&
                swapChain != null &&
                _rendererState.value == RendererState.RENDERING
    }

    fun pauseRendering() {
        if (_rendererState.value == RendererState.RENDERING) {
            _rendererState.value = RendererState.PAUSED
            Log.d(tag, "Rendering paused")
        }
    }

    fun resumeRendering() {
        if (_rendererState.value == RendererState.PAUSED) {
            _rendererState.value = RendererState.RENDERING
            Log.d(tag, "Rendering resumed")
        }
    }

    fun prepareForLayoutChange() {
        pendingResize.set(true)
        Log.d(tag, "Preparing for layout change")
    }

    /**
     * Initialize the Filament rendering engine with transparent background.
     */
    fun initialize() {
        if (isInitialized) return

        try {
            FilamentNativeLoader.ensureLoaded()

            engine = Engine.create()
            renderer = engine.createRenderer()
            scene = engine.createScene()

            view = engine.createView()
            view.scene = scene

            val cameraEntity = EntityManager.get().create()
            camera = engine.createCamera(cameraEntity)
            view.camera = camera

            materialProvider = UbershaderProvider(engine)
            assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
            resourceLoader = ResourceLoader(engine)

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
                        Log.d(tag, "onNativeWindowChanged - creating SwapChain")
                        waitForFrameCompletion()

                        swapChain?.let { engine.destroySwapChain(it) }

                        // Let UiHelper handle transparency - DO NOT pass flags manually
                        // The isOpaque = false setting handles everything
                        swapChain = engine.createSwapChain(surface)

                        displayHelper.attach(renderer, textureView.display)
                        pendingResize.set(false)
                    }

                    override fun onDetachedFromSurface() {
                        Log.d(tag, "onDetachedFromSurface - cleaning up SwapChain")
                        waitForFrameCompletion()
                        swapChain?.let {
                            engine.destroySwapChain(it)
                            swapChain = null
                        }
                        displayHelper.detach()
                    }

                    override fun onResized(width: Int, height: Int) {
                        handleResizeWithDebounce(width, height)
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
        val eye = floatArrayOf(0.0f, 1.0f, -2.5f)
        val center = floatArrayOf(0.0f, 0.85f, 0.0f)
        val up = floatArrayOf(0.0f, 1.0f, 0.0f)

        camera.lookAt(
            eye[0].toDouble(), eye[1].toDouble(), eye[2].toDouble(),
            center[0].toDouble(), center[1].toDouble(), center[2].toDouble(),
            up[0].toDouble(), up[1].toDouble(), up[2].toDouble()
        )

        Log.d(tag, "Camera positioned: eye=(${eye[0]}, ${eye[1]}, ${eye[2]})")
    }

    private fun configureCameraProjection(width: Int, height: Int) {
        val aspect = width.toDouble() / height.toDouble()
        camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
    }

    private fun setupLighting() {
        sunEntity = EntityManager.get().create()

        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 0.95f)
            .intensity(120000.0f)
            .direction(0.3f, -1.0f, -0.5f)
            .castShadows(true)
            .build(engine, sunEntity)

        scene.addEntity(sunEntity)

        scene.indirectLight = IndirectLight.Builder()
            .intensity(60000.0f)
            .build(engine)
    }

    private fun configureView() {
        view.isPostProcessingEnabled = true
        view.antiAliasing = View.AntiAliasing.FXAA
        view.ambientOcclusion = View.AmbientOcclusion.SSAO

        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
            strength = 0.08f
            levels = 4
            threshold = true
        }

        view.colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .exposure(0.6f)
            .contrast(1.03f)
            .saturation(1.02f)
            .build(engine)
    }

    /**
     * Configure transparency settings.
     * This is the key to making the background transparent.
     */
    private fun configureTransparency() {
        // ═══════════════════════════════════════════════════════════
        // TRANSPARENCY SETUP - Step 2: View blend mode
        // ═══════════════════════════════════════════════════════════
        view.blendMode = View.BlendMode.TRANSLUCENT

        // ═══════════════════════════════════════════════════════════
        // TRANSPARENCY SETUP - Step 3: No skybox
        // ═══════════════════════════════════════════════════════════
        scene.skybox = null

        // ═══════════════════════════════════════════════════════════
        // TRANSPARENCY SETUP - Step 4: Clear options
        // ═══════════════════════════════════════════════════════════
        val clearOptions = renderer.clearOptions.apply {
            clear = true
        }
        renderer.clearOptions = clearOptions

        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = false
        }

        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        Log.d(tag, "Transparency configured: blendMode=TRANSLUCENT, skybox=null, clear=true")
    }

    // ==================== Resize Handling ====================

    private fun handleResizeWithDebounce(width: Int, height: Int) {
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
        Log.d(tag, "Performing resize to ${width}x${height}")

        try {
            waitForFrameCompletion()

            view.viewport = Viewport(0, 0, width, height)
            configureCameraProjection(width, height)

            currentWidth.set(width)
            currentHeight.set(height)

            Log.d(tag, "Resize completed successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error during resize", e)
        } finally {
            isResizing.set(false)
            pendingResize.set(false)
            _rendererState.value = RendererState.RENDERING
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
        return try {
            Log.d(tag, "loadModel called with buffer: capacity=${buffer.capacity()}")

            currentAsset?.let { asset ->
                Log.d(tag, "Removing previous asset")
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
            }

            blendShapeMapping.clear()
            buffer.position(0)

            val asset = assetLoader.createAsset(buffer)

            if (asset != null) {
                Log.d(tag, "Asset created successfully! Entities: ${asset.entities.size}")

                resourceLoader.loadResources(asset)
                resourceLoader.asyncBeginLoad(asset)

                var pumpIterations = 0
                while (pumpIterations < 50) {
                    resourceLoader.asyncUpdateLoad()
                    Thread.sleep(1)
                    pumpIterations++
                }

                scene.addEntities(asset.entities)
                currentAsset = asset

                pendingBlendShapes = Pair(asset, vrmBlendShapes)
                blendShapesInitialized.set(false)
                blendShapeMapping.clear()

                centerAndScaleAsset(asset)

                val nodeNames = extractNodeNames(asset)
                modelLoadedListener?.onModelLoaded(asset, nodeNames)

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
        val tm = engine.transformManager
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
        if (!isInitialized) return false
        if (isResizing.get() || pendingResize.get()) return false

        val currentSwapChain = swapChain ?: return false

        if (!frameInProgress.compareAndSet(false, true)) {
            return false
        }

        try {
            if (isResizing.get() || pendingResize.get()) {
                return false
            }

            if (_rendererState.value == RendererState.IDLE) {
                _rendererState.value = RendererState.RENDERING
            }

            if (!renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
                return false
            }

            renderer.render(view)
            renderer.endFrame()

            if (!blendShapesInitialized.get() && pendingBlendShapes != null) {
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

        Log.d(tag, "Building blend shape mapping from ${vrmBlendShapes.size} VRM blend shapes")

        val renderableManager = engine.renderableManager
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
        if (blendShapes.isEmpty() || blendShapeMapping.isEmpty()) return

        val renderableManager = engine.renderableManager

        blendShapes.forEach { (name, weight) ->
            val normalizedName = name.lowercase()
            val targetWeight = weight.coerceIn(0f, 1f)
            val targets = blendShapeMapping[normalizedName]

            targets?.forEach { (entity, morphTargetIndex) ->
                val instance = renderableManager.getInstance(entity)
                if (instance != 0) {
                    try {
                        renderableManager.setMorphWeights(
                            instance,
                            floatArrayOf(targetWeight),
                            morphTargetIndex
                        )
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to set morph weight: ${e.message}")
                    }
                }
            }
        }
    }

    fun cleanup() {
        if (!isInitialized) return

        Log.d(tag, "Cleanup started")

        try {
            _rendererState.value = RendererState.IDLE

            pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }
            pendingResizeRunnable = null

            waitForFrameCompletion()
            frameScope.cancel()

            currentAsset?.let { asset ->
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
                currentAsset = null
            }

            engine.destroyEntity(sunEntity)

            assetLoader.destroy()
            resourceLoader.destroy()
            materialProvider.destroy()

            uiHelper.detach()

            swapChain?.let { engine.destroySwapChain(it) }

            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroyCameraComponent(camera.entity)
            EntityManager.get().destroy(camera.entity)

            engine.destroy()

            isInitialized = false
            Log.d(tag, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(tag, "Error during cleanup", e)
        }
    }
}