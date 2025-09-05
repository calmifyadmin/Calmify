package com.lifo.chat.presentation.components

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

@Composable
fun LiveCameraPreview(
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    isCameraEnabled: Boolean,
    onToggleCamera: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    onSurfaceTextureDestroyed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    // Animation for expansion/collapse
    val expandedScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expandedScale"
    )

    // Handle surface texture lifecycle - removed isCameraActive dependency to fix circular logic
    LaunchedEffect(surfaceTexture) {
        Log.d("LiveCameraPreview", "📸 LaunchedEffect: surfaceTexture=$surfaceTexture")
        if (surfaceTexture != null) {
            Log.d("LiveCameraPreview", "📸 Calling onSurfaceTextureReady from LaunchedEffect...")
            onSurfaceTextureReady(surfaceTexture!!)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSurfaceTextureDestroyed()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = expandedScale
                    scaleY = expandedScale
                }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with toggle and expand controls
                CameraPreviewHeader(
                    isCameraEnabled = isCameraEnabled,
                    isCameraActive = isCameraActive,
                    isExpanded = isExpanded,
                    onToggleCamera = onToggleCamera,
                    onToggleExpand = { isExpanded = !isExpanded }
                )

                // Camera preview or placeholder
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    CameraPreviewContent(
                        isCameraActive = isCameraActive,
                        hasCameraPermission = hasCameraPermission,
                        onRequestPermission = onRequestCameraPermission,
                        onSurfaceTextureCreated = { texture ->
                            surfaceTexture = texture
                        },
                        onSurfaceTextureReady = onSurfaceTextureReady
                    )
                }

                // Status indicator
                CameraStatusIndicator(
                    isCameraActive = isCameraActive,
                    hasCameraPermission = hasCameraPermission,
                    isCameraEnabled = isCameraEnabled
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewHeader(
    isCameraEnabled: Boolean,
    isCameraActive: Boolean,
    isExpanded: Boolean,
    onToggleCamera: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isCameraActive) Icons.Default.CameraAlt else Icons.Outlined.CameraAlt,
                contentDescription = null,
                tint = if (isCameraActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Gemini Visual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Camera toggle
            IconButton(
                onClick = onToggleCamera,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isCameraEnabled) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isCameraEnabled) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (isCameraEnabled) Icons.Default.CameraAlt else Icons.Default.Close,
                    contentDescription = if (isCameraEnabled) "Disable Camera" else "Enable Camera",
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Expand toggle
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSurfaceTextureCreated: (SurfaceTexture) -> Unit,
    onSurfaceTextureReady: (SurfaceTexture) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            !hasCameraPermission -> {
                // Permission request UI
                PermissionRequestContent(onRequestPermission = onRequestPermission)
            }
            
            hasCameraPermission -> {
                // Camera preview - always show TextureView when permission granted
                AndroidView(
                    factory = { context ->
                        TextureView(context).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                    Log.d("LiveCameraPreview", "📸 Surface texture available: ${width}x${height}")
                                    Log.d("LiveCameraPreview", "📸 Setting surfaceTexture state...")
                                    onSurfaceTextureCreated(surface)
                                    Log.d("LiveCameraPreview", "📸 Calling onSurfaceTextureReady directly...")
                                    onSurfaceTextureReady(surface)
                                }
                                
                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                    Log.d("LiveCameraPreview", "📸 Surface texture size changed: ${width}x${height}")
                                }
                                
                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                    Log.d("LiveCameraPreview", "📸 Surface texture destroyed")
                                    return true
                                }
                                
                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                    // Called when camera feed updates - we don't need to do anything here
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
                
                // Overlay for active camera
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                        .padding(6.dp)
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            else -> {
                // Camera disabled placeholder
                CameraPlaceholderContent()
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "Allow camera access to enhance Gemini's visual understanding",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun CameraPlaceholderContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        
        Text(
            text = "Camera Preview Disabled",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CameraStatusIndicator(
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    isCameraEnabled: Boolean
) {
    val (statusText, statusColor) = when {
        !hasCameraPermission -> "Camera permission required" to MaterialTheme.colorScheme.error
        !isCameraEnabled -> "Camera disabled for this session" to MaterialTheme.colorScheme.onSurfaceVariant
        isCameraActive -> "Sending frames to Gemini every 3 seconds" to MaterialTheme.colorScheme.primary
        else -> "Camera ready but not active" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, shape = CircleShape)
        )
        
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor
        )
    }
}