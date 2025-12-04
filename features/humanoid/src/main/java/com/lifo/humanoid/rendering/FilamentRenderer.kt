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
            // Load all Filament native libraries in correct order
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
 * Handles scene setup, lighting, camera, and render loop.
 *
 * Architecture:
 * - Engine: Core Filament rendering engine
 * - Renderer: Handles the actual rendering
 * - Scene: Container for all renderable entities
 * - View: Camera viewport configuration
 * - Camera: Virtual camera for viewing the scene
 */
class FilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView
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

    // Background scene asset (space.glb, ecc.)
    private var backgroundAsset: FilamentAsset? = null

    // Background image texture (background.jpg)
    private var backgroundTexture: com.google.android.filament.Texture? = null
    private var backgroundEntity: Int = 0
    private var backgroundMaterial: com.google.android.filament.Material? = null

    // Background rotation state for slow animation
    private var backgroundRotationAngle = 0.0f
    private val backgroundRotationSpeed = 0.05f // 0.05 degrees per frame (~3 degrees/sec at 60fps -> 2 min per rotation)

    // Skybox (se vuoi ancora usarla in altri contesti)
    private var skyboxTexture: com.google.android.filament.Texture? = null
    private var skybox: com.google.android.filament.Skybox? = null

    // VRM blend shape mapping: blend shape name -> (entity, morph target index)
    private val blendShapeMapping = mutableMapOf<String, MutableList<Pair<Int, Int>>>()

    // Pending blend shapes to build after first render (AAA-grade deferred initialization)
    private var pendingBlendShapes: Pair<FilamentAsset, List<com.lifo.humanoid.data.vrm.VrmBlendShape>>? = null
    private val blendShapesInitialized = AtomicBoolean(false)

    // ==================== Public Accessors for Animation System ====================

    /**
     * Get the Filament Engine instance for animation systems.
     * Returns null if renderer is not initialized.
     */
    fun getEngine(): Engine? = if (isInitialized) engine else null

    /**
     * Get the currently loaded FilamentAsset.
     * Returns null if no model is loaded.
     */
    fun getCurrentAsset(): FilamentAsset? = currentAsset

    /**
     * Get the TransformManager for bone manipulation.
     * Returns null if renderer is not initialized.
     */
    fun getTransformManager(): TransformManager? = if (isInitialized) engine.transformManager else null

    /**
     * Get the Animator from the current asset for bone matrix updates.
     */
    fun getAnimator(): com.google.android.filament.gltfio.Animator? {
        return currentAsset?.getInstance()?.animator
    }

    /**
     * Update bone matrices for the current asset.
     * Must be called after modifying bone transforms to apply changes to skinning.
     */
    fun updateBoneMatrices() {
        currentAsset?.getInstance()?.animator?.updateBoneMatrices()
    }

    /**
     * Callback interface for when a model is loaded
     */
    interface OnModelLoadedListener {
        fun onModelLoaded(asset: FilamentAsset, nodeNames: List<String>)
    }

    private var modelLoadedListener: OnModelLoadedListener? = null

    /**
     * Set listener for model loaded events
     */
    fun setOnModelLoadedListener(listener: OnModelLoadedListener?) {
        modelLoadedListener = listener
    }

    // Rendering state
    private var isInitialized = false
    private val frameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Logging tag
    private val tag = "FilamentRenderer"

    // Lighting components
    private var iblEntity: Int = 0
    private var sunEntity: Int = 0

    // ==================== Resize State Management ====================

    enum class RendererState {
        IDLE,           // Not rendering
        RENDERING,      // Actively rendering frames
        RESIZING,       // Resize in progress - render paused
        PAUSED          // Explicitly paused
    }

    private val _rendererState = MutableStateFlow(RendererState.IDLE)
    val rendererState: StateFlow<RendererState> = _rendererState.asStateFlow()

    // Thread-safe flags for resize synchronization
    private val isResizing = AtomicBoolean(false)
    private val pendingResize = AtomicBoolean(false)
    private val frameInProgress = AtomicBoolean(false)

    // Resize debounce handler
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var pendingResizeRunnable: Runnable? = null

    // Current viewport dimensions for change detection
    private val currentWidth = AtomicInteger(0)
    private val currentHeight = AtomicInteger(0)

    // Resize configuration
    companion object {
        private const val RESIZE_DEBOUNCE_MS = 100L  // Debounce rapid resize events
        private const val MIN_DIMENSION_CHANGE = 10   // Minimum pixels change to trigger resize
        private const val FRAME_WAIT_TIMEOUT_MS = 50L // Max wait for frame completion
    }

    /**
     * Check if renderer is ready to render frames.
     */
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
     * Initialize the Filament rendering engine.
     */
    fun initialize() {
        if (isInitialized) return

        try {
            // Load native libraries first
            FilamentNativeLoader.ensureLoaded()

            // Create Filament engine
            engine = Engine.create()

            // Create renderer
            renderer = engine.createRenderer()

            // Create scene
            scene = engine.createScene()

            // Create view
            view = engine.createView()
            view.scene = scene

            // Create camera
            val cameraEntity = EntityManager.get().create()
            camera = engine.createCamera(cameraEntity)
            view.camera = camera

            // Setup material provider for glTF loading
            materialProvider = UbershaderProvider(engine)

            // Create asset loader
            assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())

            // Create resource loader for textures
            resourceLoader = ResourceLoader(engine)

            // Setup UI helper for surface management
            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        Log.d(tag, "onNativeWindowChanged - recreating SwapChain")
                        waitForFrameCompletion()

                        swapChain?.let { engine.destroySwapChain(it) }
                        swapChain = engine.createSwapChain(surface)
                        displayHelper.attach(renderer, surfaceView.display)

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

                attachTo(surfaceView)
            }

            // Setup display helper
            displayHelper = DisplayHelper(context)

            // Setup default camera position
            setupCamera()

            // Setup lighting
            setupLighting()

            // Configure view settings
            configureView()

            // Configure clear options (deep black for space)
            configureClearOptions()

            isInitialized = true
        } catch (e: Exception) {
            cleanup()
            throw RuntimeException("Failed to initialize Filament renderer", e)
        }
    }

    /**
     * Setup camera with default orbital position.
     */
    private fun setupCamera() {
        val eye = FloatArray(3).apply {
            this[0] = 0.0f
            this[1] = 0.9f
            this[2] = -3.0f
        }

        val center = FloatArray(3).apply {
            this[0] = 0.0f
            this[1] = 0.85f
            this[2] = 0.0f
        }

        val up = FloatArray(3).apply {
            this[0] = 0.0f
            this[1] = 1.0f
            this[2] = 0.0f
        }

        camera.lookAt(
            eye[0].toDouble(), eye[1].toDouble(), eye[2].toDouble(),
            center[0].toDouble(), center[1].toDouble(), center[2].toDouble(),
            up[0].toDouble(), up[1].toDouble(), up[2].toDouble()
        )

        Log.d(
            tag,
            "Camera positioned for full body view: eye=(${eye[0]}, ${eye[1]}, ${eye[2]}), " +
                    "looking at=(${center[0]}, ${center[1]}, ${center[2]})"
        )
    }

    /**
     * Configure camera projection based on viewport size.
     */
    private fun configureCameraProjection(width: Int, height: Int) {
        val aspect = width.toDouble() / height.toDouble()
        val fov = 45.0
        val near = 0.1
        val far = 100.0

        camera.setProjection(fov, aspect, near, far, Camera.Fov.VERTICAL)
    }

    /**
     * Setup realistic lighting for the avatar.
     */
    private fun setupLighting() {
        // Sun light
        sunEntity = EntityManager.get().create()

        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 0.95f)
            .intensity(100000.0f)
            .direction(0.3f, -1.0f, -0.5f)
            .castShadows(true)
            .build(engine, sunEntity)

        scene.addEntity(sunEntity)

        // Ambient light (simple default IBL-like)
        scene.indirectLight = IndirectLight.Builder()
            .intensity(30000.0f)
            .build(engine)
    }

    /**
     * Configure view rendering settings.
     */
    private fun configureView() {
        view.isPostProcessingEnabled = true
        view.antiAliasing = View.AntiAliasing.FXAA
        view.ambientOcclusion = View.AmbientOcclusion.SSAO

        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
            strength = 0.1f  // Vanilla bloom leggero
        }

        view.colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .build(engine)
    }

    /**
     * Configure clear options to prevent ghosting/trailing artifacts.
     * Using deep black for space.
     */
    private fun configureClearOptions() {
        val clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
        }
        renderer.clearOptions = clearOptions

        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = false
        }

        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        Log.d(tag, "Clear options configured: clear=${clearOptions.clear}")
    }

    // ==================== Resize Handling Methods ====================

    private fun handleResizeWithDebounce(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            Log.w(tag, "Invalid resize dimensions: ${width}x${height}")
            return
        }

        val widthChange = abs(width - currentWidth.get())
        val heightChange = abs(height - currentHeight.get())

        if (widthChange < MIN_DIMENSION_CHANGE && heightChange < MIN_DIMENSION_CHANGE) {
            Log.d(tag, "Resize ignored - change too small: ${widthChange}x${heightChange}")
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
            Log.d(tag, "loadModel called with buffer: capacity=${buffer.capacity()}, position=${buffer.position()}, limit=${buffer.limit()}")

            currentAsset?.let { asset ->
                Log.d(tag, "Removing previous asset")
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
            }

            blendShapeMapping.clear()

            buffer.position(0)
            Log.d(tag, "Buffer reset to position 0, attempting to create asset...")

            val asset = assetLoader.createAsset(buffer)

            if (asset != null) {
                Log.d(tag, "Asset created successfully! Entities: ${asset.entities.size}, Root: ${asset.root}")

                Log.d(tag, "Loading resources (textures, buffers) - BLOCKING until complete...")
                resourceLoader.loadResources(asset)

                resourceLoader.asyncBeginLoad(asset)
                Log.d(tag, "Pumping async resource loader...")
                var pumpIterations = 0
                while (pumpIterations < 50) {
                    resourceLoader.asyncUpdateLoad()
                    Thread.sleep(1)
                    pumpIterations++
                }
                Log.d(tag, "Async resource loading pumped for ${pumpIterations}ms")

                Log.d(tag, "Adding ${asset.entities.size} entities to scene")
                scene.addEntities(asset.entities)

                currentAsset = asset

                Log.d(tag, "Deferring blend shape mapping until after first GPU upload (AAA-grade pattern)")
                pendingBlendShapes = Pair(asset, vrmBlendShapes)
                blendShapesInitialized.set(false)
                blendShapeMapping.clear()

                Log.d(tag, "Centering and scaling asset")
                centerAndScaleAsset(asset)

                val nodeNames = extractNodeNames(asset)
                Log.d(tag, "Extracted ${nodeNames.size} node names for animation mapping")

                modelLoadedListener?.onModelLoaded(asset, nodeNames)

                Log.d(tag, "Model loaded and configured successfully!")
            } else {
                Log.e(tag, "AssetLoader.createAsset() returned null - failed to parse glTF/VRM file")
            }

            asset
        } catch (e: Exception) {
            Log.e(tag, "Exception in loadModel", e)
            e.printStackTrace()
            null
        }
    }

    // =========================================================
    // BACKGROUND: space.glb (o altre scene)
    // =========================================================

    /**
     * API usata da FilamentView:
     * carica un GLB come ambiente di sfondo (es. space.glb).
     */
    /**
     * Load an image as background (background.jpg)
     * Creates a simple colored skybox instead of trying to use the image as environment
     */
    fun loadBackgroundImage(imageData: ByteArray) {
        try {
            Log.d(tag, "Creating simple space background")

            // Remove existing skybox
            skybox?.let {
                scene.skybox = null
                engine.destroySkybox(it)
                skybox = null
            }

            // Create a deep space blue/black skybox
            val skyboxBuilder = com.google.android.filament.Skybox.Builder()
            skyboxBuilder.color(0.0f, 0.0f, 0.05f, 1.0f)  // Very dark blue for space
            skyboxBuilder.showSun(false)

            skybox = skyboxBuilder.build(engine)
            scene.skybox = skybox

            Log.d(tag, "Simple space background created successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to create background", e)
            e.printStackTrace()
        }
    }

    fun loadBackgroundEnvironment(
        glbData: ByteBuffer,
        scale: Float = 1.0f,
        position: FloatArray = floatArrayOf(0f, 0f, 0f)
    ): FilamentAsset? {
        Log.d(tag, "loadBackgroundEnvironment called (scale=$scale, position=${position.joinToString()})")
        return loadBackgroundAsset(glbData, scale, position)
    }


    fun loadBackgroundAsset(
        buffer: ByteBuffer,
        scale: Float = 200.0f,  // 👈 scala molto grande per zoom massimo
        position: FloatArray = floatArrayOf(0f, 0f, 5f) // 👈 mettiamo lo sfondo dietro l'avatar ma dentro il frustum
    ): FilamentAsset? {
        var result: FilamentAsset? = null

        try {
            Log.d(tag, "loadBackgroundAsset called with buffer: capacity=${buffer.capacity()}")

            // Rimuovi eventuale background precedente
            backgroundAsset?.let { asset ->
                Log.d(tag, "Removing previous background asset")
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
                backgroundAsset = null
            }

            // Riparti da inizio buffer
            buffer.position(0)

            // ⚠️ stesso loader che usi per il VRM (funziona sia per .gltf che .glb)
            val asset: FilamentAsset? = assetLoader.createAsset(buffer)
            if (asset == null) {
                Log.e(tag, "Failed to create background asset from buffer (createAsset returned null)")
                return null
            }

            Log.d(tag, "Background asset created successfully! Entities: ${asset.entities.size}")

            // Carica risorse
            resourceLoader.loadResources(asset)
            resourceLoader.asyncBeginLoad(asset)

            var pumpIterations = 0
            while (pumpIterations < 50) {
                resourceLoader.asyncUpdateLoad()
                Thread.sleep(1)
                pumpIterations++
            }
            Log.d(tag, "Background resources loaded (async pumped $pumpIterations steps)")

            // Crea le renderable
            try {
                asset.releaseSourceData()
                Log.d(tag, "Background source data released - renderables created")
            } catch (e: Exception) {
                Log.w(tag, "releaseSourceData not supported or failed: ${e.message}")
            }

            // Configurazione minima per rendering corretto senza modificare materiali
            val rm = engine.renderableManager
            asset.entities.forEach { entity ->
                val instance = rm.getInstance(entity)
                if (instance != 0) {
                    try {
                        // Solo fix anti-flickering, senza toccare materiali
                        rm.setCulling(instance, false)
                        rm.setCastShadows(instance, false)
                        rm.setReceiveShadows(instance, false)

                        Log.d(tag, "Configured background entity $entity (vanilla materials)")
                    } catch (e: Exception) {
                        Log.w(tag, "Error configuring background entity $entity: ${e.message}")
                    }
                }
            }

            // Aggiungi alla scena
            scene.addEntities(asset.entities)
            backgroundAsset = asset

            // Posiziona e scala il background
            positionAndScaleBackground(asset, scale, position)

            Log.d(tag, "Background asset loaded and configured successfully!")
            result = asset
        } catch (e: Exception) {
            Log.e(tag, "Exception in loadBackgroundAsset", e)
            e.printStackTrace()
        }

        return result
    }




    /**
     * Optional: create a very simple space skybox (still disponibile se ti serve altrove).
     */
    fun createSpaceSkybox(starCount: Int = 2000, starBrightness: Float = 1.0f) {
        try {
            Log.d(tag, "Creating space skybox")

            skybox?.let {
                scene.skybox = null
                engine.destroySkybox(it)
                skybox = null
            }
            skyboxTexture?.let {
                engine.destroyTexture(it)
                skyboxTexture = null
            }

            val skyboxBuilder = Skybox.Builder()
            skyboxBuilder.color(0.0f, 0.0f, 0.05f, 1.0f)

            skybox = skyboxBuilder.build(engine)
            scene.skybox = skybox

            Log.d(tag, "Space skybox created successfully!")
        } catch (e: Exception) {
            Log.e(tag, "Failed to create space skybox", e)
            e.printStackTrace()
        }
    }

    private fun positionAndScaleBackground(
        asset: FilamentAsset,
        scale: Float,
        position: FloatArray
    ) {
        val tm = engine.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)

        if (instance == 0) {
            Log.w(tag, "positionAndScaleBackground: invalid TransformManager instance for background root")
            return
        }

        val aabb = asset.boundingBox
        val center = aabb.center
        val halfExtent = aabb.halfExtent

        // Store for rotation updates
        backgroundInitialScale = scale
        backgroundInitialPosition = position
        backgroundCenter = center.clone()

        Log.d(
            tag,
            "Background bounding box - center: (${center[0]}, ${center[1]}, ${center[2]}), " +
                    "halfExtent: (${halfExtent[0]}, ${halfExtent[1]}, ${halfExtent[2]})"
        )

        val t = FloatArray(16) { 0f }

        // Scala (diagonale)
        t[0] = scale
        t[5] = scale
        t[10] = scale

        // Traslazione: lo centro rispetto al proprio bounding box
        // e poi applico l'offset richiesto (0,0,5 di default, quindi dietro l'avatar).
        t[12] = position[0] - center[0] * scale
        t[13] = position[1] - center[1] * scale
        t[14] = position[2] - center[2] * scale
        t[15] = 1.0f

        Log.d(
            tag,
            "Background transform applied - scale=$scale, translation=(${t[12]}, ${t[13]}, ${t[14]})"
        )

        tm.setTransform(instance, t)
    }

    // Store initial background transform to apply rotation correctly
    private var backgroundInitialScale = 15.0f
    private var backgroundInitialPosition = floatArrayOf(0f, 0f, 5f)
    private var backgroundCenter = floatArrayOf(0f, 0f, 0f)

    /**
     * Update background rotation for slow continuous animation.
     * Call this every frame to animate the background.
     * Rotates around its own center (Y axis), not around world origin.
     */
    fun updateBackgroundRotation() {
        val asset = backgroundAsset ?: return

        val tm = engine.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)

        if (instance == 0) return

        // Increment rotation angle (very slow)
        backgroundRotationAngle += backgroundRotationSpeed
        if (backgroundRotationAngle >= 360.0f) {
            backgroundRotationAngle -= 360.0f
        }

        // Create rotation matrix (rotation around Y axis at object's center)
        val angleRad = Math.toRadians(backgroundRotationAngle.toDouble()).toFloat()
        val cos = kotlin.math.cos(angleRad)
        val sin = kotlin.math.sin(angleRad)

        val scale = backgroundInitialScale

        // Calculate the final position: translate to position, then center the object
        val finalPosX = backgroundInitialPosition[0] - backgroundCenter[0] * scale
        val finalPosY = backgroundInitialPosition[1] - backgroundCenter[1] * scale
        val finalPosZ = backgroundInitialPosition[2] - backgroundCenter[2] * scale

        // Build transform: Translate to world position, then rotate around object center
        // Matrix = Translation * Rotation * Scale
        val t = FloatArray(16) { 0f }

        // Rotation + Scale combined (rotate around object's own Y axis)
        t[0] = scale * cos
        t[2] = scale * sin
        t[5] = scale
        t[8] = -scale * sin
        t[10] = scale * cos

        // Translation (apply to final position)
        t[12] = finalPosX
        t[13] = finalPosY
        t[14] = finalPosZ
        t[15] = 1.0f

        tm.setTransform(instance, t)
    }



    /**
     * Center and scale the avatar asset to fit nicely in view.
     */
    private fun centerAndScaleAsset(asset: FilamentAsset) {
        val tm = engine.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)

        val aabb = asset.boundingBox
        val center = aabb.center
        val halfExtent = aabb.halfExtent

        Log.d(
            tag,
            "Asset bounding box - center: (${center[0]}, ${center[1]}, ${center[2]}), " +
                    "halfExtent: (${halfExtent[0]}, ${halfExtent[1]}, ${halfExtent[2]})"
        )

        val heightExtent = halfExtent[1] * 2.0f
        val targetHeight = 1.7f
        val scale = targetHeight / heightExtent

        Log.d(tag, "Model height: ${heightExtent}m, scale factor: $scale")

        val transform = FloatArray(16)
        transform[0] = scale
        transform[5] = scale
        transform[10] = scale

        transform[12] = -center[0] * scale
        transform[13] = (-center[1] + halfExtent[1]) * scale
        transform[14] = -center[2] * scale
        transform[15] = 1.0f

        Log.d(
            tag,
            "Transform applied - translation: (${transform[12]}, ${transform[13]}, ${transform[14]})"
        )

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
                Log.d(tag, "═══ AAA-GRADE: First render complete - initializing blend shapes ═══")
                val (asset, vrmBlendShapes) = pendingBlendShapes!!
                buildBlendShapeMapping(asset, vrmBlendShapes)
                blendShapesInitialized.set(true)
                pendingBlendShapes = null
                Log.d(tag, "═══ AAA-GRADE: Blend shape initialization complete ═══")
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

    /**
     * Build blend shape mapping from VRM definitions.
     */
    private fun buildBlendShapeMapping(
        asset: FilamentAsset,
        vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape>
    ) {
        if (vrmBlendShapes.isEmpty()) {
            Log.w(tag, "No VRM blend shapes provided - blend shape mapping will be empty")
            return
        }

        Log.d(tag, "Building blend shape mapping from ${vrmBlendShapes.size} VRM blend shapes")

        val renderableManager = engine.renderableManager
        var totalMappings = 0
        var failedMappings = 0

        Log.d(tag, "═══ Scanning ${asset.entities.size} entities for morph targets ═══")
        val entitiesWithMorphTargets = mutableListOf<Triple<Int, Int, Int>>()

        asset.entities.forEachIndexed { index, entity ->
            val instance = renderableManager.getInstance(entity)
            if (instance != 0) {
                val morphTargetCount = renderableManager.getMorphTargetCount(instance)
                if (morphTargetCount > 0) {
                    entitiesWithMorphTargets.add(Triple(entity, index, morphTargetCount))
                    Log.d(tag, "  Entity $entity (index $index) has $morphTargetCount morph targets")
                }
            }
        }
        Log.d(tag, "═══ Found ${entitiesWithMorphTargets.size} entities with morph targets ═══")

        if (entitiesWithMorphTargets.isNotEmpty()) {
            val (targetEntity, targetIndex, targetMorphCount) = entitiesWithMorphTargets.first()
            Log.d(
                tag,
                "Using entity $targetEntity (index $targetIndex) with $targetMorphCount morph targets for ALL blend shapes"
            )

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
                        Log.d(
                            tag,
                            "✓ Mapped blend shape '$blendShapeName' (preset: $presetName) -> entity $targetEntity, " +
                                    "morph target $morphTargetIndex/$targetMorphCount"
                        )
                    } else {
                        Log.e(
                            tag,
                            "Morph target index $morphTargetIndex out of bounds (max: $targetMorphCount) for blend shape '$blendShapeName'"
                        )
                        failedMappings++
                    }
                }
            }
        } else {
            Log.e(tag, "⚠️ NO entities with morph targets found! Cannot map blend shapes!")
            failedMappings = vrmBlendShapes.sumOf { it.bindings.size }
        }

        Log.i(tag, "═══════════════════════════════════════════════════════════")
        Log.i(tag, "Blend shape mapping completed:")
        Log.i(tag, "  Total blend shapes: ${vrmBlendShapes.size}")
        Log.i(tag, "  Total mappings created: $totalMappings")
        Log.i(tag, "  Failed mappings: $failedMappings")
        Log.i(tag, "  Unique blend shape names: ${blendShapeMapping.keys.size}")
        Log.i(tag, "  Available blend shapes: ${blendShapeMapping.keys.joinToString()}")
        Log.i(tag, "═══════════════════════════════════════════════════════════")

        if (failedMappings > 0) {
            Log.e(
                tag,
                "⚠️ WARNING: $failedMappings blend shape mappings FAILED! Facial animations may not work correctly!"
            )
        }

        if (totalMappings == 0) {
            Log.e(
                tag,
                "⚠️ CRITICAL: NO blend shape mappings created! Lip-sync, blink, and expressions WILL NOT WORK!"
            )
        }
    }

    /**
     * Extract node names from FilamentAsset for animation bone mapping.
     */
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

    /**
     * Update blend shape weights for facial animation.
     */
    fun updateBlendShapes(blendShapes: Map<String, Float>) {
        if (blendShapes.isEmpty()) return

        if (blendShapeMapping.isEmpty()) {
            if (System.currentTimeMillis() % 1000 < 16) {
                Log.e(
                    tag,
                    "⚠️ Blend shape mapping is EMPTY! Cannot update blend shapes. Lip-sync/blink/expressions will not work!"
                )
            }
            return
        }

        val renderableManager = engine.renderableManager

        blendShapes.forEach { (name, weight) ->
            val normalizedName = name.lowercase()
            val targetWeight = weight.coerceIn(0f, 1f)

            val targets = blendShapeMapping[normalizedName]

            if (targets != null) {
                targets.forEach { (entity, morphTargetIndex) ->
                    val instance = renderableManager.getInstance(entity)
                    if (instance != 0) {
                        try {
                            renderableManager.setMorphWeights(
                                instance,
                                floatArrayOf(targetWeight),
                                morphTargetIndex
                            )
                        } catch (e: Exception) {
                            Log.e(
                                tag,
                                "Failed to set morph weight for blend shape '$name' (entity=$entity, index=$morphTargetIndex): ${e.message}"
                            )
                        }
                    } else {
                        Log.w(
                            tag,
                            "Blend shape '$name' has invalid renderable instance for entity $entity"
                        )
                    }
                }
            } else {
                if (weight > 0.01f) {
                    Log.w(
                        tag,
                        "Blend shape '$name' not found in mapping. Available: ${
                            blendShapeMapping.keys.take(
                                5
                            ).joinToString()
                        }"
                    )
                }
            }
        }
    }

    /**
     * Cleanup all Filament resources.
     */
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

            backgroundAsset?.let { asset ->
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
                backgroundAsset = null
            }

            skybox?.let {
                scene.skybox = null
                engine.destroySkybox(it)
                skybox = null
            }
            skyboxTexture?.let {
                engine.destroyTexture(it)
                skyboxTexture = null
            }

            engine.destroyEntity(sunEntity)
            engine.destroyEntity(iblEntity)

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
            e.printStackTrace()
        }
    }
}
