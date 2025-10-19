package com.lifo.chat.presentation.components

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Simplified camera preview for LiveScreen - no borders, no title, full space
 */
@Composable
fun SimpleLiveCameraPreview(
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    onSurfaceTextureDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    // Handle surface texture lifecycle
    LaunchedEffect(surfaceTexture) {
        if (surfaceTexture != null) {
            Log.d("SimpleLiveCameraPreview", "📸 Surface texture ready")
            onSurfaceTextureReady(surfaceTexture!!)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("SimpleLiveCameraPreview", "📸 Surface texture destroyed")
            onSurfaceTextureDestroyed()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            !hasCameraPermission -> {
                // Permission request UI - minimal
                PermissionRequestMinimal(onRequestPermission = onRequestCameraPermission)
            }

            hasCameraPermission -> {
                // Camera preview - full screen
                AndroidView(
                    factory = { context ->
                        TextureView(context).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                    Log.d("SimpleLiveCameraPreview", "📸 Surface available: ${width}x${height}")
                                    surfaceTexture = surface
                                }

                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                    Log.d("SimpleLiveCameraPreview", "📸 Surface size changed: ${width}x${height}")
                                }

                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                    Log.d("SimpleLiveCameraPreview", "📸 Surface destroyed")
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                    // Camera feed updates
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                )

                // LIVE badge overlay - only when camera is active
                AnimatedVisibility(
                    visible = isCameraActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Status indicator - bottom
                AnimatedVisibility(
                    visible = isCameraActive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            )

                            Text(
                                text = "Sending frames every 3s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestMinimal(
    onRequestPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "Allow camera access to enhance Gemini's visual understanding",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Grant Permission")
        }
    }
}
