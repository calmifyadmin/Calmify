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
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera

    // Asset loading
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var materialProvider: UbershaderProvider

    // Surface management
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper
    private var swapChain: SwapChain? = null

    // Current loaded avatar
    private var currentAsset: FilamentAsset? = null

    // VRM blend shape mapping: blend shape name -> (entity, morph target index)
    private val blendShapeMapping = mutableMapOf<String, MutableList<Pair<Int, Int>>>()

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
     * The Animator is crucial for skinning - it updates bone matrices after transform changes.
     *
     * Workflow: After modifying bone transforms via TransformManager,
     * call updateBoneMatrices() on the Animator to apply changes to skinning.
     *
     * Note: Animator is obtained from FilamentInstance (asset.getInstance()), not directly from FilamentAsset.
     */
    fun getAnimator(): com.google.android.filament.gltfio.Animator? {
        return currentAsset?.getInstance()?.animator
    }

    /**
     * Update bone matrices for the current asset.
     * Must be called after modifying bone transforms to apply changes to skinning.
     *
     * This is the key step that was missing - without this call,
     * transform changes don't affect the skinned mesh.
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

    /**
     * Renderer state machine for safe resize handling.
     * Prevents race conditions between resize events and render loop.
     */
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
     * Returns false during resize operations to prevent race conditions.
     */
    fun canRender(): Boolean {
        return isInitialized &&
               !isResizing.get() &&
               !pendingResize.get() &&
               swapChain != null &&
               _rendererState.value == RendererState.RENDERING
    }

    /**
     * Pause rendering explicitly.
     * Call before layout changes that will trigger resize.
     */
    fun pauseRendering() {
        if (_rendererState.value == RendererState.RENDERING) {
            _rendererState.value = RendererState.PAUSED
            Log.d(tag, "Rendering paused")
        }
    }

    /**
     * Resume rendering after pause.
     * Call after layout changes are complete.
     */
    fun resumeRendering() {
        if (_rendererState.value == RendererState.PAUSED) {
            _rendererState.value = RendererState.RENDERING
            Log.d(tag, "Rendering resumed")
        }
    }

    /**
     * Notify renderer that a layout change is about to happen.
     * This prepares the renderer for an incoming resize.
     */
    fun prepareForLayoutChange() {
        pendingResize.set(true)
        Log.d(tag, "Preparing for layout change")
    }

    /**
     * Initialize the Filament rendering engine.
     * Must be called before any rendering operations.
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
                        // Wait for any frame in progress to complete
                        waitForFrameCompletion()

                        swapChain?.let { engine.destroySwapChain(it) }
                        swapChain = engine.createSwapChain(surface)
                        displayHelper.attach(renderer, surfaceView.display)

                        // Clear pending resize flag
                        pendingResize.set(false)
                    }

                    override fun onDetachedFromSurface() {
                        Log.d(tag, "onDetachedFromSurface - cleaning up SwapChain")
                        // Wait for any frame in progress
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

            // CRITICAL: Configure clear options to prevent ghosting/trails
            // Without this, the frame buffer isn't cleared between frames causing visual artifacts
            configureClearOptions()

            isInitialized = true
        } catch (e: Exception) {
            cleanup()
            throw RuntimeException("Failed to initialize Filament renderer", e)
        }
    }

    /**
     * Setup camera with default orbital position.
     * Avatar will be at origin, camera looks at it from the front, framing full body.
     */
    private fun setupCamera() {
        // Position camera in front of the avatar, positioned to see full body
        // In Filament's coordinate system: +X = right, +Y = up, +Z = towards viewer (out of screen)
        val eye = FloatArray(3).apply {
            this[0] = 0.0f   // x: centered horizontally
            this[1] = 0.9f   // y: Lower camera to see full body (chest/torso level)
            this[2] = -3.0f  // z: NEGATIVE = camera in front, farther for full body view
        }

        // Look at the avatar's chest/torso area to frame full body nicely
        val center = FloatArray(3).apply {
            this[0] = 0.0f
            this[1] = 0.85f  // Look at center of body (torso level, not face)
            this[2] = 0.0f
        }

        // Up vector (Y axis points up)
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

        Log.d(tag, "Camera positioned for full body view: eye=(${eye[0]}, ${eye[1]}, ${eye[2]}), looking at=(${center[0]}, ${center[1]}, ${center[2]})")
    }

    /**
     * Configure camera projection based on viewport size.
     */
    private fun configureCameraProjection(width: Int, height: Int) {
        val aspect = width.toDouble() / height.toDouble()
        val fov = 45.0 // Field of view in degrees
        val near = 0.1  // Near clipping plane
        val far = 20.0  // Far clipping plane

        camera.setProjection(fov, aspect, near, far, Camera.Fov.VERTICAL)
    }

    /**
     * Setup realistic lighting for the avatar.
     * Uses Image-Based Lighting (IBL) for ambient + directional sun light.
     */
    private fun setupLighting() {
        // Create IBL (Image-Based Lighting)
        // TODO: Load actual IBL texture from assets
        // For now, use default ambient light

        // Create sun light (directional light)
        sunEntity = EntityManager.get().create()

        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 1.0f, 0.95f) // Warm white
            .intensity(100000.0f)
            .direction(0.3f, -1.0f, -0.5f) // From top-front
            .castShadows(true)
            .build(engine, sunEntity)

        scene.addEntity(sunEntity)

        // Set default ambient light
        scene.indirectLight = IndirectLight.Builder()
            .intensity(30000.0f)
            .build(engine)
    }

    /**
     * Configure view rendering settings.
     */
    private fun configureView() {
        // Enable post-processing for better quality
        view.isPostProcessingEnabled = true

        // Enable anti-aliasing
        view.antiAliasing = View.AntiAliasing.FXAA

        // Enable ambient occlusion for depth
        view.ambientOcclusion = View.AmbientOcclusion.SSAO

        // Enable bloom for nice glow effects
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
            strength = 0.1f
        }

        // Configure tone mapping
        view.colorGrading = ColorGrading.Builder()
            .toneMapping(ColorGrading.ToneMapping.ACES)
            .build(engine)
    }

    /**
     * Configure clear options to prevent ghosting/trailing artifacts.
     *
     * CRITICAL: This is essential to clear the frame buffer between frames.
     * Without proper clearing, previous frame data remains visible causing:
     * - Ghosting/trails on high-end devices (S24)
     * - Visual glitches (green/blue/red artifacts) on low-end devices
     *
     * Reference: https://github.com/google/filament/discussions/4677
     */
    private fun configureClearOptions() {
        // Configure renderer to clear the frame buffer every frame
        val clearOptions = renderer.clearOptions.apply {
            // Enable clearing of the color buffer - ESSENTIAL to prevent trails
            clear = true
            // Set clear color (dark blue-gray background)
            // Format: RGBA with values 0.0-1.0
            clearColor = floatArrayOf(0.1f, 0.1f, 0.15f, 1.0f)
        }
        renderer.clearOptions = clearOptions

        // Also configure view for proper depth handling on low-end devices
        // This helps prevent visual glitches caused by depth buffer issues
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            // Enable dynamic resolution for better performance on low-end devices
            enabled = false // Disabled for now - can cause artifacts if not tuned properly
        }

        // Configure depth prepass for better rendering stability
        // This ensures proper depth sorting which prevents visual glitches
        view.renderQuality = view.renderQuality.apply {
            // Use medium quality for better compatibility
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

        Log.d(tag, "Clear options configured: clear=${clearOptions.clear}")
    }

    // ==================== Resize Handling Methods ====================

    /**
     * Handle resize with debouncing to prevent rapid consecutive resizes.
     * This is crucial when hiding/showing UI panels that cause layout changes.
     */
    private fun handleResizeWithDebounce(width: Int, height: Int) {
        // Validate dimensions
        if (width <= 0 || height <= 0) {
            Log.w(tag, "Invalid resize dimensions: ${width}x${height}")
            return
        }

        // Check if this is a significant change
        val widthChange = abs(width - currentWidth.get())
        val heightChange = abs(height - currentHeight.get())

        if (widthChange < MIN_DIMENSION_CHANGE && heightChange < MIN_DIMENSION_CHANGE) {
            Log.d(tag, "Resize ignored - change too small: ${widthChange}x${heightChange}")
            return
        }

        Log.d(tag, "Resize requested: ${currentWidth.get()}x${currentHeight.get()} -> ${width}x${height}")

        // Cancel any pending resize
        pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }

        // Mark as resizing
        isResizing.set(true)
        pendingResize.set(true)
        _rendererState.value = RendererState.RESIZING

        // Debounce the actual resize
        pendingResizeRunnable = Runnable {
            performResize(width, height)
        }.also {
            resizeHandler.postDelayed(it, RESIZE_DEBOUNCE_MS)
        }
    }

    /**
     * Perform the actual resize operation.
     * Called after debounce delay.
     */
    private fun performResize(width: Int, height: Int) {
        Log.d(tag, "Performing resize to ${width}x${height}")

        try {
            // Wait for any frame in progress
            waitForFrameCompletion()

            // Update viewport
            view.viewport = Viewport(0, 0, width, height)

            // Update camera projection
            configureCameraProjection(width, height)

            // Store new dimensions
            currentWidth.set(width)
            currentHeight.set(height)

            Log.d(tag, "Resize completed successfully")

        } catch (e: Exception) {
            Log.e(tag, "Error during resize", e)
        } finally {
            // Clear resize flags and resume rendering
            isResizing.set(false)
            pendingResize.set(false)
            _rendererState.value = RendererState.RENDERING
            pendingResizeRunnable = null
        }
    }

    /**
     * Wait for any in-progress frame to complete.
     * Prevents race conditions between resize and render.
     */
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
     *
     * @param buffer ByteBuffer containing the glTF/VRM file data
     * @param vrmBlendShapes VRM blend shape definitions (optional)
     * @return FilamentAsset representing the loaded model
     */
    fun loadModel(buffer: ByteBuffer, vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape> = emptyList()): FilamentAsset? {
        return try {
            Log.d(tag, "loadModel called with buffer: capacity=${buffer.capacity()}, position=${buffer.position()}, limit=${buffer.limit()}")

            // Remove previous asset if exists
            currentAsset?.let { asset ->
                Log.d(tag, "Removing previous asset")
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
            }

            // Clear previous blend shape mapping
            blendShapeMapping.clear()

            // Ensure buffer is positioned at the start
            buffer.position(0)
            Log.d(tag, "Buffer reset to position 0, attempting to create asset...")

            // Load new asset
            val asset = assetLoader.createAsset(buffer)

            if (asset != null) {
                Log.d(tag, "Asset created successfully! Entities: ${asset.entities.size}, Root: ${asset.root}")

                // Load resources (textures, buffers)
                Log.d(tag, "Loading resources (textures, buffers)...")
                resourceLoader.loadResources(asset)

                // Add to scene
                Log.d(tag, "Adding ${asset.entities.size} entities to scene")
                scene.addEntities(asset.entities)

                // Store reference
                currentAsset = asset

                // Build blend shape mapping from VRM data
                buildBlendShapeMapping(asset, vrmBlendShapes)

                // Center and scale the model
                Log.d(tag, "Centering and scaling asset")
                centerAndScaleAsset(asset)

                // Extract node names for animation system
                val nodeNames = extractNodeNames(asset)
                Log.d(tag, "Extracted ${nodeNames.size} node names for animation mapping")

                // Notify listener that model is loaded
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

    /**
     * Center and scale the asset to fit nicely in view.
     * Positions avatar so feet are at Y=0 (ground) and centered at X=0, Z=0.
     */
    private fun centerAndScaleAsset(asset: FilamentAsset) {
        val tm = engine.transformManager
        val root = asset.root
        val instance = tm.getInstance(root)

        // Get bounding box
        val aabb = asset.boundingBox
        val center = aabb.center
        val halfExtent = aabb.halfExtent

        Log.d(tag, "Asset bounding box - center: (${center[0]}, ${center[1]}, ${center[2]}), halfExtent: (${halfExtent[0]}, ${halfExtent[1]}, ${halfExtent[2]})")

        // Calculate scale to fit avatar (assume human is ~1.7m tall)
        val heightExtent = halfExtent[1] * 2.0f // Full height of the model
        val targetHeight = 1.7f
        val scale = targetHeight / heightExtent

        Log.d(tag, "Model height: ${heightExtent}m, scale factor: $scale")

        // Create transform matrix
        val transform = FloatArray(16)
        // Scale
        transform[0] = scale
        transform[5] = scale
        transform[10] = scale

        // Translation: Center horizontally (X, Z) but put feet at ground (Y=0)
        transform[12] = -center[0] * scale  // Center horizontally on X
        transform[13] = (-center[1] + halfExtent[1]) * scale  // Feet at Y=0 (ground level)
        transform[14] = -center[2] * scale  // Center on Z
        transform[15] = 1.0f

        Log.d(tag, "Transform applied - translation: (${transform[12]}, ${transform[13]}, ${transform[14]})")

        tm.setTransform(instance, transform)
    }

    /**
     * Main render loop. Call this continuously to render frames.
     * Thread-safe with respect to resize operations.
     *
     * @param frameTimeNanos Current frame time in nanoseconds
     * @return true if frame was rendered, false if skipped
     */
    fun render(frameTimeNanos: Long): Boolean {
        // Quick checks first (no synchronization needed)
        if (!isInitialized) return false

        // Check resize state - skip rendering during resize
        if (isResizing.get() || pendingResize.get()) {
            return false
        }

        val currentSwapChain = swapChain ?: return false

        // Mark frame as in progress
        if (!frameInProgress.compareAndSet(false, true)) {
            // Another frame is already in progress
            return false
        }

        try {
            // Double-check resize state after acquiring frame lock
            if (isResizing.get() || pendingResize.get()) {
                return false
            }

            // Update state to rendering if not already
            if (_rendererState.value == RendererState.IDLE) {
                _rendererState.value = RendererState.RENDERING
            }

            // Check if we can begin frame
            if (!renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
                return false
            }

            // Render the view
            renderer.render(view)

            // End frame and present
            renderer.endFrame()

            return true

        } catch (e: Exception) {
            Log.e(tag, "Error during render", e)
            return false
        } finally {
            // Always clear frame in progress flag
            frameInProgress.set(false)
        }
    }

    /**
     * Render with automatic frame timing.
     * Convenience method that uses System.nanoTime().
     */
    fun renderFrame(): Boolean {
        return render(System.nanoTime())
    }

    /**
     * Build blend shape mapping from VRM definitions.
     * Maps VRM blend shape names to Filament morph target indices.
     */
    private fun buildBlendShapeMapping(asset: FilamentAsset, vrmBlendShapes: List<com.lifo.humanoid.data.vrm.VrmBlendShape>) {
        if (vrmBlendShapes.isEmpty()) {
            Log.w(tag, "No VRM blend shapes provided - blend shape mapping will be empty")
            return
        }

        Log.d(tag, "Building blend shape mapping from ${vrmBlendShapes.size} VRM blend shapes")

        // Process each VRM blend shape
        vrmBlendShapes.forEach { vrmBlendShape ->
            val blendShapeName = vrmBlendShape.name.lowercase()

            // Also map preset names (Joy, Angry, etc.)
            val presetName = vrmBlendShape.preset?.name?.lowercase()

            // Process each binding (mesh + morph target index)
            vrmBlendShape.bindings.forEach { binding ->
                // Find the entity for this mesh index
                val entity = asset.entities.getOrNull(binding.meshIndex)

                if (entity != null) {
                    val morphTargetIndex = binding.morphTargetIndex

                    // Add to mapping for blend shape name
                    blendShapeMapping.getOrPut(blendShapeName) { mutableListOf() }
                        .add(Pair(entity, morphTargetIndex))

                    // Also map preset name if available
                    if (presetName != null && presetName != "unknown") {
                        blendShapeMapping.getOrPut(presetName) { mutableListOf() }
                            .add(Pair(entity, morphTargetIndex))
                    }

                    Log.d(tag, "Mapped blend shape '$blendShapeName' (preset: $presetName) -> entity $entity, morph target $morphTargetIndex")
                }
            }
        }

        Log.d(tag, "Blend shape mapping built: ${blendShapeMapping.keys.joinToString()}")
    }

    /**
     * Extract node names from FilamentAsset for animation bone mapping.
     * Uses the Filament naming convention to retrieve entity names.
     *
     * @param asset The loaded FilamentAsset
     * @return List of node names in entity order
     */
    private fun extractNodeNames(asset: FilamentAsset): List<String> {
        val nodeNames = mutableListOf<String>()
        val nameManager = engine.entityManager

        asset.entities.forEachIndexed { index, entity ->
            // Try to get node name from the asset's name component
            // Filament stores names via the NameComponentManager
            val name = try {
                // The asset provides entity names through its internal structure
                // We use index-based naming as fallback since Filament doesn't expose
                // name retrieval directly in all versions
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
     * Uses VRM blend shape mapping to apply weights correctly.
     *
     * @param blendShapes Map of blend shape names to weights (0.0-1.0)
     */
    fun updateBlendShapes(blendShapes: Map<String, Float>) {
        if (blendShapes.isEmpty()) return

        val renderableManager = engine.renderableManager

        // Apply each blend shape weight using the mapping
        blendShapes.forEach { (name, weight) ->
            val normalizedName = name.lowercase()
            val targetWeight = weight.coerceIn(0f, 1f)

            // Get all morph targets for this blend shape name
            val targets = blendShapeMapping[normalizedName]

            if (targets != null) {
                targets.forEach { (entity, morphTargetIndex) ->
                    val instance = renderableManager.getInstance(entity)
                    if (instance != 0) {
                        renderableManager.setMorphWeights(
                            instance,
                            floatArrayOf(targetWeight),
                            morphTargetIndex
                        )
                    }
                }
            } else {
                // Log warning only once per unknown blend shape
                if (weight > 0.01f) {
                    Log.w(tag, "Blend shape '$name' not found in mapping. Available: ${blendShapeMapping.keys.take(5).joinToString()}")
                }
            }
        }
    }

    /**
     * Cleanup all Filament resources.
     * Must be called when renderer is no longer needed.
     */
    fun cleanup() {
        if (!isInitialized) return

        Log.d(tag, "Cleanup started")

        try {
            // Stop rendering
            _rendererState.value = RendererState.IDLE

            // Cancel any pending resize operations
            pendingResizeRunnable?.let { resizeHandler.removeCallbacks(it) }
            pendingResizeRunnable = null

            // Wait for any frame in progress
            waitForFrameCompletion()

            frameScope.cancel()

            // Destroy current asset
            currentAsset?.let { asset ->
                scene.removeEntities(asset.entities)
                assetLoader.destroyAsset(asset)
                currentAsset = null
            }

            // Destroy lights
            engine.destroyEntity(sunEntity)
            engine.destroyEntity(iblEntity)

            // Cleanup loaders
            assetLoader.destroy()
            resourceLoader.destroy()
            materialProvider.destroy()

            // Cleanup UI helper
            uiHelper.detach()

            // Destroy swap chain
            swapChain?.let { engine.destroySwapChain(it) }

            // Destroy Filament components
            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroyCameraComponent(camera.entity)
            EntityManager.get().destroy(camera.entity)

            // Destroy engine last
            engine.destroy()

            isInitialized = false

            Log.d(tag, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(tag, "Error during cleanup", e)
            e.printStackTrace()
        }
    }
}
