package com.lifo.humanoid.presentation.components

import android.util.Log
import android.view.TextureView
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

private const val TAG = "FilamentView"

/**
 * Composable that displays a Filament 3D rendering surface with TRUE TRANSPARENCY.
 * Uses TextureView to properly composite with Compose - the Material Surface shows through.
 *
 * NOTE: Transparency works on physical devices but may show black on emulators.
 * See: https://www.droidcon.com/2023/07/17/a-guide-to-filament-for-android/
 *
 * Usage:
 * ```
 * Surface(color = MaterialTheme.colorScheme.surface) {
 *     FilamentView(
 *         modifier = Modifier.fillMaxSize(),
 *         vrmModelData = modelData
 *     )
 * }
 * ```
 *
 * @param modifier Compose modifier
 * @param vrmModelData ByteBuffer containing the VRM model data
 * @param vrmExtensions VRM extension data (blend shapes, etc.)
 * @param blendShapeWeights Current blend shape weights to apply
 * @param isLayoutChanging Set to true when parent layout is animating
 * @param onRendererReady Callback when renderer is initialized
 * @param onModelLoaded Callback when VRM model is loaded
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

    // Renderer state
    var renderer: FilamentRenderer? by remember { mutableStateOf(null) }

    // Track if renderer is ready for rendering
    var isRendererReady by remember { mutableStateOf(false) }

    // Track current size to detect changes
    var lastSize by remember { mutableStateOf(Pair(0, 0)) }

    // Handle layout change notifications
    LaunchedEffect(isLayoutChanging) {
        renderer?.let { r ->
            if (isLayoutChanging) {
                Log.d(TAG, "Layout change started - preparing renderer")
                r.prepareForLayoutChange()
            }
        }
    }

    // Create the TextureView and Filament renderer
    AndroidView(
        modifier = modifier.onSizeChanged { size ->
            val newSize = Pair(size.width, size.height)
            if (lastSize != newSize && lastSize.first > 0 && lastSize.second > 0) {
                Log.d(TAG, "Size changed: $lastSize -> $newSize")
            }
            lastSize = newSize
        },
        factory = { ctx ->
            Log.d(TAG, "Creating TextureView for transparent Filament rendering")

            TextureView(ctx).apply {
                // ═══════════════════════════════════════════════════════════
                // CRITICAL: Enable transparency on TextureView
                // ═══════════════════════════════════════════════════════════
                isOpaque = false

            }.also { textureView ->
                // Initialize Filament renderer
                val filamentRenderer = FilamentRenderer(
                    context = ctx,
                    textureView = textureView
                )

                // Set up model loaded listener BEFORE initialize
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

                // Initialize the renderer
                filamentRenderer.initialize()

                renderer = filamentRenderer
                isRendererReady = true

                onRendererReady(filamentRenderer)
                Log.d(TAG, "FilamentRenderer initialized with transparent TextureView")
            }
        },
        update = { _ ->
            // Update is handled in LaunchedEffects
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

    // Update blend shapes
    LaunchedEffect(blendShapeWeights) {
        if (blendShapeWeights.isNotEmpty() && !isLayoutChanging) {
            renderer?.let { r ->
                if (r.canRender()) {
                    r.updateBlendShapes(blendShapeWeights)
                }
            }
        }
    }

    // Render loop
    LaunchedEffect(isRendererReady) {
        if (!isRendererReady) return@LaunchedEffect

        Log.d(TAG, "Starting render loop")

        while (isActive) {
            renderer?.let { r ->
                if (r.canRender()) {
                    r.renderFrame()
                }
            }

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
        }
    }
}