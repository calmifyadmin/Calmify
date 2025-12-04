package com.lifo.humanoid.presentation.components

import android.util.Log
import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.filament.gltfio.FilamentAsset
import com.lifo.humanoid.data.vrm.VrmExtensions
import com.lifo.humanoid.rendering.FilamentRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "FilamentView"

/**
 * Composable that displays a Filament 3D rendering surface.
 * Integrates Filament's SurfaceView into Jetpack Compose with robust resize handling.
 *
 * Features:
 * - Thread-safe render loop that respects resize state
 * - Automatic pause/resume on lifecycle changes
 * - Debounced resize handling to prevent race conditions
 * - Proper cleanup on disposal
 *
 * @param modifier Compose modifier
 * @param vrmModelData ByteBuffer containing the VRM model data
 * @param vrmExtensions VRM extension data (blend shapes, etc.)
 * @param blendShapeWeights Current blend shape weights to apply
 * @param isLayoutChanging Set to true when parent layout is animating (e.g., panel hide/show)
 * @param onRendererReady Callback when renderer is initialized
 * @param onModelLoaded Callback when the VRM model is loaded with asset and node names
 */
@Composable
fun FilamentView(
    modifier: Modifier = Modifier,
    vrmModelData: ByteBuffer? = null,
    vrmExtensions: VrmExtensions? = null,
    blendShapeWeights: Map<String, Float> = emptyMap(),
    isLayoutChanging: Boolean = false,
    onRendererReady: (FilamentRenderer) -> Unit = {},
    onModelLoaded: (FilamentRenderer, FilamentAsset, List<String>) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Renderer and surface state
    var renderer: FilamentRenderer? by remember { mutableStateOf(null) }
    var surfaceView: SurfaceView? by remember { mutableStateOf(null) }

    // Track if renderer is ready for rendering
    var isRendererReady by remember { mutableStateOf(false) }

    // Track current size to detect changes
    var lastSize by remember { mutableStateOf(Pair(0, 0)) }

    // Load background GLB (space.glb) ONCE and keep it in memory
    val spaceBackgroundData: ByteBuffer? by remember {
        mutableStateOf(
            try {
                context.assets.open("space.glb").use { input ->
                    val bytes = input.readBytes()
                    ByteBuffer
                        .allocateDirect(bytes.size)
                        .order(ByteOrder.nativeOrder())
                        .apply {
                            put(bytes)
                            rewind()
                        }
                }.also {
                    Log.d(TAG, "Loaded space.glb from assets (${it.capacity()} bytes)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load space.glb from assets", e)
                null
            }
        )
    }

    // Handle layout change notifications
    LaunchedEffect(isLayoutChanging) {
        renderer?.let { r ->
            if (isLayoutChanging) {
                Log.d(TAG, "Layout change started - preparing renderer")
                r.prepareForLayoutChange()
            }
        }
    }
// Load space.glb background when renderer is ready
    LaunchedEffect(spaceBackgroundData, isRendererReady) {
        if (spaceBackgroundData != null && isRendererReady) {
            try {
                Log.d(TAG, "Loading space.glb as background environment")
                renderer?.loadBackgroundEnvironment(
                    glbData = spaceBackgroundData!!,
                    scale = 200.0f,
                    position = floatArrayOf(0f, 0f, 5f)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load background environment", e)
            }
        }
    }

    // Create the SurfaceView and Filament renderer
    AndroidView(
        modifier = modifier.onSizeChanged { size ->
            // Detect significant size changes
            val newSize = Pair(size.width, size.height)
            if (lastSize != newSize && lastSize.first > 0 && lastSize.second > 0) {
                Log.d(TAG, "Size changed: $lastSize -> $newSize")
                // Renderer's internal debounce will handle this via UiHelper callback
            }
            lastSize = newSize
        },
        factory = { ctx ->
            Log.d(TAG, "Creating SurfaceView and FilamentRenderer")
            SurfaceView(ctx).also { surface ->
                surfaceView = surface

                // Initialize Filament renderer
                val filamentRenderer = FilamentRenderer(ctx, surface)
                filamentRenderer.initialize()

                // Set up model loaded listener for animation system
                filamentRenderer.setOnModelLoadedListener(
                    object : FilamentRenderer.OnModelLoadedListener {
                        override fun onModelLoaded(
                            asset: FilamentAsset,
                            nodeNames: List<String>
                        ) {
                            Log.d(TAG, "Model loaded callback - notifying parent")
                            onModelLoaded(filamentRenderer, asset, nodeNames)
                        }
                    }
                )

                renderer = filamentRenderer
                isRendererReady = true

                onRendererReady(filamentRenderer)
                Log.d(TAG, "FilamentRenderer initialized successfully")
            }
        },
        update = { _ ->
            // Update is handled in LaunchedEffects for proper coroutine integration
        }
    )

    // Lifecycle observer for pause/resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "Lifecycle ON_PAUSE - pausing renderer")
                    renderer?.pauseRendering()
                }

                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Lifecycle ON_RESUME - resuming renderer")
                    renderer?.resumeRendering()
                }

                Lifecycle.Event.ON_DESTROY -> {
                    Log.d(TAG, "Lifecycle ON_DESTROY")
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load VRM model when data is available
    LaunchedEffect(vrmModelData, vrmExtensions, isRendererReady) {
        if (vrmModelData != null && isRendererReady) {
            Log.d(TAG, "Loading VRM model")
            val blendShapes = vrmExtensions?.blendShapes ?: emptyList()
            renderer?.loadModel(vrmModelData, blendShapes)
        }
    }

    // Update blend shapes - only when not resizing
    LaunchedEffect(blendShapeWeights) {
        if (blendShapeWeights.isNotEmpty() && !isLayoutChanging) {
            renderer?.let { r ->
                if (r.canRender()) {
                    r.updateBlendShapes(blendShapeWeights)
                }
            }
        }
    }

    // Render loop with resize-aware scheduling
    LaunchedEffect(isRendererReady) {
        if (!isRendererReady) return@LaunchedEffect

        Log.d(TAG, "Starting render loop")

        while (isActive) {
            renderer?.let { r ->
                // Only render if the renderer says it's safe
                if (r.canRender()) {
                    r.renderFrame()
                }
            }

            // Target ~60 FPS with adaptive delay
            val canCurrentlyRender = renderer?.canRender() ?: false
            delay(if (canCurrentlyRender) 16L else 8L)
        }
    }

    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing FilamentView - cleaning up renderer")
            isRendererReady = false
            renderer?.cleanup()
            renderer = null
            surfaceView = null
        }
    }
}
