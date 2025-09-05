package com.lifo.chat.presentation.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import android.graphics.SurfaceTexture
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.PTTState
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.LiveChatUiState
import com.lifo.chat.presentation.viewmodel.LiveChatViewModel
import com.lifo.chat.presentation.components.LiveChatVisualizer
import com.lifo.chat.presentation.components.GeminiLiquidVisualizer
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChatScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveChatViewModel = hiltViewModel()
) {
    android.util.Log.d("LiveChatScreen", "🏠 LiveChatScreen composing...")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    android.util.Log.d("LiveChatScreen", "📱 UI state in screen: connectionStatus=${uiState.connectionStatus}, hasPermission=${uiState.hasAudioPermission}")
    val haptics = LocalHapticFeedback.current

    // Audio permission handler
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onAudioPermissionGranted()
        } else {
            viewModel.onAudioPermissionDenied()
        }
    }
    
    // Camera permission handler
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onCameraPermissionGranted()
        } else {
            viewModel.onCameraPermissionDenied()
        }
    }

    // Request permissions on first composition if not granted
    LaunchedEffect(Unit) {
        if (!uiState.hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (!uiState.hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-connect when permission is granted
    LaunchedEffect(uiState.hasAudioPermission) {
        if (uiState.hasAudioPermission && uiState.connectionStatus == ConnectionStatus.Disconnected) {
            viewModel.connectToRealtime()
        }
    }

    // Full-screen background (completely outside all constraints)
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Liquid wave background covering the ENTIRE screen (no padding, no bars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && uiState.hasAudioPermission) {
            GeminiLiquidVisualizer(
                isSpeaking = uiState.aiEmotion == AIEmotion.Speaking || uiState.audioLevel > 0.1f,
                modifier = Modifier.fillMaxSize(),
                primaryColor = MaterialTheme.colorScheme.primary,
                secondaryColor = MaterialTheme.colorScheme.tertiary,
                backgroundColor = MaterialTheme.colorScheme.surface
            )
        } else {
            // Fallback gradient background for older devices or no permission
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            )
        }

        // Scaffold on top of the background with system padding
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            topBar = {
                LiveChatTopBar(
                    connectionStatus = uiState.connectionStatus,
                    onBackClicked = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.disconnectFromRealtime()
                        onBackClicked()
                    }
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            // UI content without background (background is handled by outer Box)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Add top padding manually to account for TopBar
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
                // Center space for liquid globe
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.hasAudioPermission || !uiState.hasCameraPermission) {
                        PermissionRequiredContent(
                            hasAudioPermission = uiState.hasAudioPermission,
                            hasCameraPermission = uiState.hasCameraPermission,
                            onRetryAudioPermission = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRetryCameraPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        )
                    } else {
                        // Camera preview (background layer)
                        CameraPreviewSection(
                            isCameraActive = uiState.isCameraActive,
                            onCameraToggle = { active ->
                                if (active) {
                                    // Will be handled by TextureView listener
                                } else {
                                    viewModel.stopCameraPreview()
                                }
                            },
                            onSurfaceTextureAvailable = { surfaceTexture ->
                                viewModel.startCameraPreview(surfaceTexture)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Status and transcript display (overlay)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            // Connection status
                            Text(
                                text = when {
                                    uiState.connectionStatus != ConnectionStatus.Connected -> "Connecting to Gemini Live..."
                                    uiState.aiEmotion == AIEmotion.Speaking || uiState.audioLevel > 0.1f -> "Gemini is speaking"
                                    uiState.isRecording -> "Listening..."
                                    else -> "Ready to chat"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                            
                            // AI transcript display
                            AnimatedVisibility(
                                visible = currentTranscript.isNotEmpty(),
                                enter = slideInVertically { -it } + fadeIn(),
                                exit = slideOutVertically { -it } + fadeOut()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                    ),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Gemini Assistant",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = currentTranscript,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            
                            // Debug info (only in debug builds)
                            if (com.lifo.chat.BuildConfig.DEBUG && uiState.connectionStatus == ConnectionStatus.Connected) {
                                Text(
                                    text = "Audio Level: ${(uiState.audioLevel * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Bottom push-to-talk area
                PushToTalkSection(
                    connectionStatus = uiState.connectionStatus,
                    pushToTalkState = uiState.pushToTalkState,
                    isRecording = uiState.isRecording,
                    recordingDuration = uiState.recordingDuration,
                    error = uiState.error,
                    onPushToTalkPressed = viewModel::onPushToTalkPressed,
                    onPushToTalkReleased = viewModel::onPushToTalkReleased,
                    onCancelPushToTalk = viewModel::cancelPushToTalk,
                    onRetryConnection = viewModel::retryConnection,
                    onClearError = viewModel::clearError
                )
                
                // Add bottom padding manually to account for system navigation
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveChatTopBar(
    connectionStatus: ConnectionStatus,
    onBackClicked: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Gemini Live",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                ConnectionStatusChip(connectionStatus = connectionStatus)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClicked) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface // Use theme's onSurface color
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent // Transparent to blend with liquid background
        )
    )
}

@Composable
private fun ConnectionStatusChip(connectionStatus: ConnectionStatus) {
    val (statusText, statusColor) = when (connectionStatus) {
        ConnectionStatus.Disconnected -> "Disconnected" to MaterialTheme.colorScheme.outline
        ConnectionStatus.Connecting -> "Connecting..." to MaterialTheme.colorScheme.tertiary
        ConnectionStatus.Connected -> "Live" to MaterialTheme.colorScheme.primary
        ConnectionStatus.Error -> "Error" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = statusColor.copy(alpha = 0.15f),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(statusColor, shape = CircleShape)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
    }
}

@Composable
private fun LiquidGlobePlaceholder(
    aiEmotion: AIEmotion,
    audioLevel: Float,
    connectionStatus: ConnectionStatus
) {
    val infiniteTransition = rememberInfiniteTransition(label = "globe_animation")
    
    // Breathing animation for the globe
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Color based on AI emotion and connection
    val globeColor = when {
        connectionStatus != ConnectionStatus.Connected -> Color.Gray
        else -> when (aiEmotion) {
            AIEmotion.Neutral -> MaterialTheme.colorScheme.primary
            AIEmotion.Happy -> MaterialTheme.colorScheme.primary
            AIEmotion.Thinking -> MaterialTheme.colorScheme.secondary
            AIEmotion.Speaking -> MaterialTheme.colorScheme.tertiary
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Globe placeholder
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            globeColor.copy(alpha = 0.8f),
                            globeColor.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        radius = 120f
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🔮",
                fontSize = 48.sp,
                modifier = Modifier.alpha(0.8f)
            )
        }

        // Status text
        Text(
            text = when {
                connectionStatus != ConnectionStatus.Connected -> "Connecting to AI..."
                else -> when (aiEmotion) {
                    AIEmotion.Neutral -> "Ready to chat"
                    AIEmotion.Happy -> "AI is happy"
                    AIEmotion.Thinking -> "AI is thinking..."
                    AIEmotion.Speaking -> "AI is speaking"
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PushToTalkSection(
    connectionStatus: ConnectionStatus,
    pushToTalkState: PTTState,
    isRecording: Boolean,
    recordingDuration: Long,
    error: String?,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onCancelPushToTalk: () -> Unit,
    onRetryConnection: () -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error handling
        AnimatedVisibility(
            visible = error != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onClearError) {
                                Text("Dismiss")
                            }
                            if (connectionStatus == ConnectionStatus.Error) {
                                TextButton(onClick = onRetryConnection) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recording status
        AnimatedVisibility(
            visible = isRecording,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Text(
                text = "Recording: ${recordingDuration / 1000}s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Instructions with enhanced visibility
        Text(
            text = when {
                connectionStatus == ConnectionStatus.Disconnected -> "Connecting..."
                connectionStatus == ConnectionStatus.Connecting -> "Establishing connection..."
                connectionStatus == ConnectionStatus.Error -> "Connection error"
                connectionStatus == ConnectionStatus.Connected && pushToTalkState == PTTState.Idle -> "Hold to talk"
                pushToTalkState == PTTState.Listening -> "Release to send"
                pushToTalkState == PTTState.Processing -> "AI is processing..."
                else -> "Ready"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Push-to-talk button
        Box(
            modifier = Modifier
                .size(72.dp)
                .pointerInput(connectionStatus) {
                    detectTapGestures(
                        onPress = {
                            android.util.Log.d("LiveChatScreen", "🖱️ Button TOUCHED!")
                            android.util.Log.d("LiveChatScreen", "🖱️ Checking connection status: $connectionStatus (expected: ${ConnectionStatus.Connected})")
                            if (connectionStatus == ConnectionStatus.Connected) {
                                android.util.Log.d("LiveChatScreen", "🖱️ Button PRESSED - calling onPushToTalkPressed")
                                onPushToTalkPressed()
                                tryAwaitRelease() // Wait for finger lift
                                android.util.Log.d("LiveChatScreen", "🖱️ Button RELEASED - calling onPushToTalkReleased")
                                onPushToTalkReleased()
                            } else {
                                android.util.Log.w("LiveChatScreen", "🖱️ Button touched but not connected - current status: $connectionStatus")
                            }
                        }
                    )
                }
                .background(
                    color = when {
                        connectionStatus != ConnectionStatus.Connected -> MaterialTheme.colorScheme.surfaceVariant
                        isRecording -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Push to talk",
                modifier = Modifier.size(36.dp),
                tint = when {
                    connectionStatus != ConnectionStatus.Connected -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Color.White
                }
            )
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    hasAudioPermission: Boolean,
    hasCameraPermission: Boolean,
    onRetryAudioPermission: () -> Unit,
    onRetryCameraPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (hasAudioPermission) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Icon(
                imageVector = Icons.Default.Warning, // Using Warning as camera icon
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (hasCameraPermission) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }

        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Gemini Live needs microphone and camera access for full AI interaction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasAudioPermission) {
                Button(
                    onClick = onRetryAudioPermission,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Grant Microphone Permission")
                }
            }
            
            if (!hasCameraPermission) {
                Button(
                    onClick = onRetryCameraPermission,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

@Composable 
private fun CameraPreviewSection(
    isCameraActive: Boolean,
    onCameraToggle: (Boolean) -> Unit,
    onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (isCameraActive) {
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                onSurfaceTextureAvailable(surface)
                            }
                            
                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                            
                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                onCameraToggle(false)
                                return true
                            }
                            
                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Camera toggle button (top-right corner)
        FloatingActionButton(
            onClick = { onCameraToggle(!isCameraActive) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ) {
            Text(
                text = if (isCameraActive) "📹" else "📷",
                fontSize = 20.sp
            )
        }
    }
}