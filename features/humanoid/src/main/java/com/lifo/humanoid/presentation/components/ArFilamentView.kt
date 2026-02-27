package com.lifo.humanoid.presentation.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.filament.gltfio.FilamentAsset
import com.lifo.humanoid.data.vrm.VrmExtensions
import com.lifo.humanoid.domain.ar.ArHitResult
import com.lifo.humanoid.domain.ar.ArSessionManager
import com.lifo.humanoid.rendering.ArFilamentRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer

private const val TAG = "ArFilamentView"

/**
 * Composable that displays a Filament 3D rendering surface with ARCore camera passthrough.
 * Uses SurfaceView (required by ARCore GL context) with the camera feed as background.
 *
 * Touch handling: single tap triggers hit-test for avatar placement.
 * No orbit/pan/zoom — the camera is driven by ARCore device tracking.
 */
@Composable
fun ArFilamentView(
    modifier: Modifier = Modifier,
    vrmModelData: ByteBuffer? = null,
    vrmExtensions: VrmExtensions? = null,
    blendShapeWeights: Map<String, Float> = emptyMap(),
    arSessionManager: ArSessionManager,
    onRendererReady: (ArFilamentRenderer) -> Unit = {},
    onModelLoaded: (ArFilamentRenderer, FilamentAsset, List<String>) -> Unit = { _, _, _ -> },
    onTapToPlace: (ArHitResult) -> Unit = {},
    onBeforeCleanup: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var renderer: ArFilamentRenderer? by remember { mutableStateOf(null) }
    var isRendererReady by remember { mutableStateOf(false) }
    var loadedModelData by remember { mutableStateOf<ByteBuffer?>(null) }

    // Create SurfaceView and AR Filament renderer
    @SuppressLint("ClickableViewAccessibility")
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Log.d(TAG, "Creating SurfaceView for AR Filament rendering")

            SurfaceView(ctx).also { surfaceView ->
                val arRenderer = ArFilamentRenderer(
                    context = ctx,
                    surfaceView = surfaceView,
                    arSessionManager = arSessionManager
                )

                arRenderer.setOnModelLoadedListener(
                    object : ArFilamentRenderer.OnModelLoadedListener {
                        override fun onModelLoaded(asset: FilamentAsset, nodeNames: List<String>) {
                            Log.d(TAG, "AR model loaded callback")
                            onModelLoaded(arRenderer, asset, nodeNames)
                        }
                    }
                )

                arRenderer.initialize()

                // Touch: tap-to-place (no orbit/pan/zoom in AR)
                surfaceView.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_UP -> {
                            // Perform hit-test at tap location
                            Log.d(TAG, "Tap at (${event.x}, ${event.y}) surface=${v.width}x${v.height}")
                            val hitResult = arSessionManager.hitTest(
                                event.x, event.y, v.width, v.height
                            )
                            if (hitResult != null) {
                                Log.d(TAG, "Hit-test OK: pos=(${hitResult.hitPose[12]}, ${hitResult.hitPose[13]}, ${hitResult.hitPose[14]})")
                                onTapToPlace(hitResult)
                            } else {
                                Log.w(TAG, "Hit-test returned null — no surface at tap location")
                            }
                        }
                    }
                    true
                }

                renderer = arRenderer
                isRendererReady = true
                onRendererReady(arRenderer)

                Log.d(TAG, "ArFilamentRenderer initialized with SurfaceView")
            }
        },
        update = { /* Updates handled in LaunchedEffects */ }
    )

    // Lifecycle: resume/pause ARCore session + renderer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME - resuming AR session + renderer")
                    arSessionManager.resume()
                    renderer?.resumeRendering()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "ON_PAUSE - pausing AR session + renderer")
                    renderer?.pauseRendering()
                    arSessionManager.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // If lifecycle is already RESUMED when we compose (common case: switching
        // from non-AR to AR mode while Activity is active), the observer may not
        // receive ON_RESUME. Explicitly resume to avoid SessionPausedException.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.d(TAG, "Lifecycle already RESUMED - resuming AR session + renderer immediately")
            arSessionManager.resume()
            renderer?.resumeRendering()
        }

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load VRM model when data is available
    LaunchedEffect(vrmModelData, vrmExtensions, isRendererReady) {
        if (vrmModelData != null && isRendererReady && vrmModelData != loadedModelData) {
            Log.d(TAG, "Loading VRM model for AR")
            renderer?.loadModel(
                vrmModelData,
                vrmExtensions?.blendShapes ?: emptyList(),
                vrmExtensions?.humanoidBoneNodeIndices ?: emptyMap(),
                vrmExtensions?.lookAtTypeName ?: "Bone",
                vrmExtensions?.leftEyeNodeName,
                vrmExtensions?.rightEyeNodeName
            )
            loadedModelData = vrmModelData
        }
    }

    // Update blend shapes
    LaunchedEffect(blendShapeWeights) {
        if (blendShapeWeights.isNotEmpty()) {
            renderer?.let { r ->
                if (r.canRender()) {
                    r.updateBlendShapes(blendShapeWeights)
                }
            }
        }
    }

    // Render loop (60 FPS — ARCore + Filament combined)
    LaunchedEffect(isRendererReady) {
        if (!isRendererReady) return@LaunchedEffect
        Log.d(TAG, "Starting AR render loop")

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

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing ArFilamentView")
            isRendererReady = false
            loadedModelData = null

            // 1) Stop all animation controllers immediately.
            //    This sets isDestroyed=true and cancels coroutines (non-blocking).
            onBeforeCleanup()

            // 2) Capture renderer reference and null it out immediately
            //    so no new render calls can happen.
            val r = renderer
            renderer = null

            // 3) CRITICAL: Delay engine destruction by one message-loop cycle.
            //    After cancel(), the coroutine's CancellationException handler is
            //    queued in the Main Handler. If we call engine.destroy() synchronously,
            //    it blocks Main for ~200ms (GPU resource cleanup), and when it unblocks,
            //    the coroutine's pending continuation may briefly access the destroyed
            //    engine before processing the cancellation. By posting the cleanup to
            //    the next Handler message, we allow the cancellation handler to run first.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Log.d(TAG, "Executing deferred AR cleanup")
                r?.cleanup()
            }
        }
    }
}
