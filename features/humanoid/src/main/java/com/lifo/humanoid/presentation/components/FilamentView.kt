package com.lifo.humanoid.presentation.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
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
import kotlin.math.sqrt

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
    onModelLoaded: (FilamentRenderer, FilamentAsset, List<String>) -> Unit = { _, _, _ -> },
    onBeforeCleanup: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Renderer state
    var renderer: FilamentRenderer? by remember { mutableStateOf(null) }

    // Track if renderer is ready for rendering
    var isRendererReady by remember { mutableStateOf(false) }

    // Track current size to detect changes
    var lastSize by remember { mutableStateOf(Pair(0, 0)) }

    // Track if model has been loaded to prevent double loading
    var loadedModelData by remember { mutableStateOf<ByteBuffer?>(null) }

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
    @SuppressLint("ClickableViewAccessibility")
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

                // ═══════════════════════════════════════════════════════════
                // Touch handling: orbit (1 finger), pan (2 fingers), pinch zoom
                // Manipulator expects Y=0 at bottom, Android Y=0 at top → flip Y
                // ═══════════════════════════════════════════════════════════
                var activePointers = 0
                var previousPinchSpan = 0f

                textureView.setOnTouchListener { v, event ->
                    val viewHeight = v.height
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            activePointers = 1
                            filamentRenderer.grabBegin(
                                event.x.toInt(),
                                viewHeight - event.y.toInt(),
                                false // orbit
                            )
                        }
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            activePointers = event.pointerCount
                            if (activePointers >= 2) {
                                // Switch from orbit to pan
                                filamentRenderer.grabEnd()
                                val midX = ((event.getX(0) + event.getX(1)) / 2).toInt()
                                val midY = viewHeight - ((event.getY(0) + event.getY(1)) / 2).toInt()
                                filamentRenderer.grabBegin(midX, midY, true) // strafe/pan
                                previousPinchSpan = pointerSpan(event)
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (activePointers == 1) {
                                filamentRenderer.grabUpdate(
                                    event.x.toInt(),
                                    viewHeight - event.y.toInt()
                                )
                            } else if (activePointers >= 2 && event.pointerCount >= 2) {
                                val midX = ((event.getX(0) + event.getX(1)) / 2).toInt()
                                val midY = viewHeight - ((event.getY(0) + event.getY(1)) / 2).toInt()
                                filamentRenderer.grabUpdate(midX, midY)

                                // Pinch zoom
                                val span = pointerSpan(event)
                                val delta = (previousPinchSpan - span) * 0.01f
                                if (delta != 0f) {
                                    filamentRenderer.scroll(midX, midY, delta)
                                }
                                previousPinchSpan = span
                            }
                        }
                        MotionEvent.ACTION_POINTER_UP -> {
                            activePointers = event.pointerCount - 1
                            filamentRenderer.grabEnd()
                            if (activePointers == 1) {
                                // One finger remains — resume orbit
                                val remainIdx = if (event.actionIndex == 0) 1 else 0
                                filamentRenderer.grabBegin(
                                    event.getX(remainIdx).toInt(),
                                    viewHeight - event.getY(remainIdx).toInt(),
                                    false
                                )
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            activePointers = 0
                            filamentRenderer.grabEnd()
                        }
                    }
                    true
                }

                renderer = filamentRenderer
                isRendererReady = true

                onRendererReady(filamentRenderer)
                Log.d(TAG, "FilamentRenderer initialized with transparent TextureView + touch controls")
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

    // Load VRM model when data is available (only once per model)
    LaunchedEffect(vrmModelData, vrmExtensions, isRendererReady) {
        if (vrmModelData != null && isRendererReady && vrmModelData != loadedModelData) {
            Log.d(TAG, "Loading VRM model (new model detected)")
            val blendShapes = vrmExtensions?.blendShapes ?: emptyList()
            val humanoidBoneNodeIndices = vrmExtensions?.humanoidBoneNodeIndices ?: emptyMap()
            val lookAtTypeName = vrmExtensions?.lookAtTypeName ?: "Bone"
            renderer?.loadModel(
                vrmModelData, blendShapes, humanoidBoneNodeIndices, lookAtTypeName,
                vrmExtensions?.leftEyeNodeName, vrmExtensions?.rightEyeNodeName
            )
            loadedModelData = vrmModelData
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
            loadedModelData = null

            // CRITICAL: Stop all controllers BEFORE cleanup to prevent accessing destroyed assets
            // See: https://github.com/google/filament/issues/7650
            onBeforeCleanup()

            // Capture renderer and null out immediately to prevent new render calls
            val r = renderer
            renderer = null

            // Defer engine destruction by one message-loop cycle so that coroutine
            // cancellation handlers (from onBeforeCleanup) can execute first.
            // Without this, engine.destroy() blocks Main for ~200ms and the cancelled
            // coroutine's pending continuation may briefly access the destroyed engine.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Log.d(TAG, "Executing deferred cleanup")
                r?.cleanup()
            }
        }
    }
}

/** Distance between two touch pointers (for pinch zoom detection). */
private fun pointerSpan(event: MotionEvent): Float {
    val dx = event.getX(0) - event.getX(1)
    val dy = event.getY(0) - event.getY(1)
    return sqrt(dx * dx + dy * dy)
}