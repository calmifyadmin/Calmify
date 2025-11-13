package com.lifo.humanoid.presentation.components

import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.lifo.humanoid.rendering.FilamentRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer

/**
 * Composable that displays a Filament 3D rendering surface.
 * Integrates Filament's SurfaceView into Jetpack Compose.
 *
 * @param modifier Compose modifier
 * @param vrmModelData ByteBuffer containing the VRM model data
 * @param vrmExtensions VRM extension data (blend shapes, etc.)
 * @param blendShapeWeights Current blend shape weights to apply
 * @param onRendererReady Callback when renderer is initialized
 */
@Composable
fun FilamentView(
    modifier: Modifier = Modifier,
    vrmModelData: ByteBuffer? = null,
    vrmExtensions: com.lifo.humanoid.data.vrm.VrmExtensions? = null,
    blendShapeWeights: Map<String, Float> = emptyMap(),
    onRendererReady: (FilamentRenderer) -> Unit = {}
) {
    val context = LocalContext.current
    var renderer: FilamentRenderer? by remember { mutableStateOf(null) }
    var surfaceView: SurfaceView? by remember { mutableStateOf(null) }

    // Create the SurfaceView and Filament renderer
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { surface ->
                surfaceView = surface

                // Initialize Filament renderer
                val filamentRenderer = FilamentRenderer(ctx, surface)
                filamentRenderer.initialize()
                renderer = filamentRenderer

                onRendererReady(filamentRenderer)
            }
        },
        update = { _ ->
            // Update will be handled in LaunchedEffect
        }
    )

    // Load VRM model when data is available
    LaunchedEffect(vrmModelData, vrmExtensions) {
        if (vrmModelData != null) {
            val blendShapes = vrmExtensions?.blendShapes ?: emptyList()
            renderer?.loadModel(vrmModelData, blendShapes)
        }
    }

    // Update blend shapes
    LaunchedEffect(blendShapeWeights) {
        if (blendShapeWeights.isNotEmpty()) {
            renderer?.updateBlendShapes(blendShapeWeights)
        }
    }

    // Render loop
    LaunchedEffect(Unit) {
        while (isActive) {
            val frameTimeNanos = System.nanoTime()
            renderer?.render(frameTimeNanos)

            // Target 60 FPS
            delay(16) // ~60 FPS
        }
    }

    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            renderer?.cleanup()
            renderer = null
            surfaceView = null
        }
    }
}
