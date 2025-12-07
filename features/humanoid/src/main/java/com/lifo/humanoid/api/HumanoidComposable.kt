package com.lifo.humanoid.api

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.presentation.HumanoidViewModel
import com.lifo.humanoid.presentation.components.FilamentView

/**
 * Public composable for rendering the Humanoid VRM avatar.
 *
 * This is the main integration point for other modules (like features:chat)
 * to display the 3D avatar. It handles:
 * - VRM model rendering via Filament
 * - Blend shape updates (emotions, lip-sync, blink)
 * - Animation playback
 * - Optional blur effect (useful for chat history overlay)
 *
 * @param modifier Compose modifier
 * @param viewModel The HumanoidViewModel managing the avatar state
 * @param blurAmount Blur radius in DP (0 = no blur, useful for history mode)
 * @param onRendererReady Callback when the Filament renderer is initialized
 */
@Composable
fun HumanoidAvatarView(
    modifier: Modifier = Modifier,
    viewModel: HumanoidViewModel,
    blurAmount: Float = 0f,
    onRendererReady: () -> Unit = {}
) {
    // Observe VRM model data and blend shape weights
    val vrmModelData by viewModel.vrmModelData.collectAsStateWithLifecycle()
    val vrmExtensions by viewModel.vrmExtensions.collectAsStateWithLifecycle()
    val blendShapeWeights by viewModel.blendShapeWeights.collectAsStateWithLifecycle()

    // Track if renderer is ready
    var rendererReady by remember { mutableStateOf(false) }

    // Update blend shapes continuously (for smooth interpolation)
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateBlendShapes(deltaTime = 0.016f) // ~60 FPS
            kotlinx.coroutines.delay(16)
        }
    }

    Box(modifier = modifier) {
        FilamentView(
            modifier = if (blurAmount > 0f) {
                Modifier.blur(blurAmount.dp)
            } else {
                Modifier
            },
            vrmModelData = vrmModelData?.first,
            vrmExtensions = vrmExtensions,
            blendShapeWeights = blendShapeWeights,
            onRendererReady = { renderer ->
                rendererReady = true
                onRendererReady()
            },
            onModelLoaded = { renderer, asset, nodeNames ->
                viewModel.onModelLoaded(renderer, asset, nodeNames)
            }
        )
    }
}

/**
 * Extension function to create a HumanoidController from HumanoidViewModel.
 * Useful for chat integration with synchronized speech.
 *
 * @param lipSyncController The LipSyncController for synchronized audio-lipsync
 */
fun HumanoidViewModel.asHumanoidController(
    lipSyncController: LipSyncController
): HumanoidController {
    return HumanoidControllerImpl(this, lipSyncController)
}
