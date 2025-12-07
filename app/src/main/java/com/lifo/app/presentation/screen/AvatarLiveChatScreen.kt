package com.lifo.app.presentation.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.app.integration.LiveEmotionBridge
import com.lifo.app.presentation.viewmodel.AvatarLiveChatViewModel
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.TurnState
import com.lifo.chat.presentation.components.GeminiLiquidVisualizer
import com.lifo.chat.presentation.components.SimpleLiveCameraPreview
import com.lifo.humanoid.api.HumanoidAvatarView
import com.lifo.humanoid.api.asHumanoidController
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.presentation.HumanoidViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing LipSyncController from Hilt
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AvatarLiveLipSyncEntryPoint {
    fun lipSyncController(): LipSyncController
}

/**
 * Avatar Live Chat Screen
 *
 * Combines VRM Avatar with Gemini Live API for real-time voice conversations.
 *
 * UI Architecture (6 Layers):
 * - Layer 1: VRM Avatar (fullscreen background)
 * - Layer 2: Camera Preview PIP (optional, top-right)
 * - Layer 3: Liquid Visualizer (bottom effect)
 * - Layer 4: Top Bar (status + close)
 * - Layer 5: Transcript Overlay (center)
 * - Layer 6: Bottom Controls (mute, camera)
 *
 * Features:
 * - Real-time lip-sync synchronized with Gemini audio
 * - Dynamic emotions based on AI state and transcript
 * - Camera streaming for vision capabilities
 * - Smart barge-in detection
 * - Elegant waveform visualizer
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AvatarLiveChatScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AvatarLiveChatViewModel = hiltViewModel(),
    humanoidViewModel: HumanoidViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Display mode: true = Avatar, false = Wave visualizer
    var showAvatar by remember { mutableStateOf(true) }

    // ViewModel states
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val avatarEmotion by viewModel.avatarEmotion.collectAsStateWithLifecycle()

    // Audio intelligence states for visualizer
    val userVoiceLevel by viewModel.userVoiceLevel.collectAsStateWithLifecycle()
    val aiVoiceLevel by viewModel.aiVoiceLevel.collectAsStateWithLifecycle()
    val emotionalIntensity by viewModel.emotionalIntensity.collectAsStateWithLifecycle()
    val conversationMode by viewModel.conversationMode.collectAsStateWithLifecycle()

    // Get LipSyncController from Hilt
    val lipSyncController = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AvatarLiveLipSyncEntryPoint::class.java
        )
        entryPoint.lipSyncController()
    }

    // Create HumanoidController adapter
    val humanoidController = remember(humanoidViewModel, lipSyncController) {
        humanoidViewModel.asHumanoidController(lipSyncController)
    }

    // Create LiveEmotionBridge
    val liveEmotionBridge = remember(humanoidController) {
        LiveEmotionBridge(humanoidController)
    }

    // Permission launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onAudioPermissionGranted()
        } else {
            viewModel.onAudioPermissionDenied()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onCameraPermissionGranted()
        } else {
            viewModel.onCameraPermissionDenied()
        }
    }

    // Connect HumanoidController for synchronized lip-sync
    LaunchedEffect(humanoidController) {
        viewModel.attachHumanoidController(humanoidController)
    }

    // Apply avatar emotions when they change
    LaunchedEffect(avatarEmotion) {
        humanoidController.setEmotion(avatarEmotion)
    }

    // Auto-connect when screen opens
    LaunchedEffect(Unit) {
        if (!uiState.hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (uiState.connectionStatus == ConnectionStatus.Disconnected) {
            viewModel.connectToRealtime()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.detachHumanoidController()
            viewModel.disconnectFromRealtime()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // LAYER 1: Background - Either Avatar or Fullscreen Wave Visualizer
        if (showAvatar) {
            // VRM Avatar (Background)
            HumanoidAvatarView(
                modifier = Modifier.fillMaxSize(),
                viewModel = humanoidViewModel,
                blurAmount = 0f
            )
        } else {
            // Fullscreen Liquid Visualizer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    GeminiLiquidVisualizer(
                        isSpeaking = uiState.aiEmotion == AIEmotion.Speaking ||
                                uiState.turnState == TurnState.UserTurn,
                        modifier = Modifier.fillMaxSize(),
                        primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        secondaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                        backgroundColor = Color.Transparent,
                        userVoiceLevel = userVoiceLevel,
                        aiVoiceLevel = aiVoiceLevel,
                        emotionalIntensity = emotionalIntensity,
                        conversationMode = conversationMode,
                        isUserSpeaking = uiState.turnState == TurnState.UserTurn && !uiState.isMuted,
                        isAiSpeaking = uiState.aiEmotion == AIEmotion.Speaking
                    )
                }
            } else {
                // Fallback for older Android versions
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }

        // LAYER 3: Camera Preview PIP (Optional)
        AnimatedVisibility(
            visible = uiState.wantsCameraOn && uiState.hasCameraPermission,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 72.dp, end = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .size(width = 120.dp, height = 160.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                SimpleLiveCameraPreview(
                    isCameraActive = uiState.isCameraActive,
                    hasCameraPermission = uiState.hasCameraPermission,
                    onRequestCameraPermission = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onSurfaceTextureReady = { surfaceTexture ->
                        if (uiState.hasCameraPermission && !uiState.isCameraActive) {
                            viewModel.startCameraPreview(surfaceTexture)
                        }
                    },
                    onSurfaceTextureDestroyed = {
                        if (uiState.isCameraActive) {
                            viewModel.stopCameraPreview()
                        }
                    }
                )
            }
        }

        // LAYER 4: Top Bar
        AvatarLiveTopBar(
            connectionStatus = uiState.connectionStatus,
            turnState = uiState.turnState,
            isMuted = uiState.isMuted,
            showAvatar = showAvatar,
            onToggleDisplayMode = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showAvatar = !showAvatar
            },
            onClose = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // LAYER 5: Transcript Overlay (Center)
        AnimatedVisibility(
            visible = currentTranscript.isNotEmpty() && uiState.connectionStatus == ConnectionStatus.Connected,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut() + slideOutVertically { -it / 4 },
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        ) {
            TranscriptCard(text = currentTranscript)
        }

        // LAYER 6: Bottom Controls
        AvatarLiveBottomControls(
            connectionStatus = uiState.connectionStatus,
            isMuted = uiState.isMuted,
            turnState = uiState.turnState,
            isChannelOpen = uiState.isChannelOpen,
            partialTranscript = uiState.partialTranscript,
            error = uiState.error,
            onToggleMute = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.toggleMute()
            },
            isCameraActive = uiState.isCameraActive,
            hasCameraPermission = uiState.hasCameraPermission,
            wantsCameraOn = uiState.wantsCameraOn,
            onToggleCamera = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!uiState.hasCameraPermission) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    viewModel.toggleCamera()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

/**
 * Transcript Card - Shows AI response text
 */
@Composable
private fun TranscriptCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        )
    }
}

/**
 * Top Bar with status, display mode toggle, and close button
 */
@Composable
private fun AvatarLiveTopBar(
    connectionStatus: ConnectionStatus,
    turnState: TurnState,
    isMuted: Boolean,
    showAvatar: Boolean,
    onToggleDisplayMode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status chip
        AvatarLiveStatusChip(
            connectionStatus = connectionStatus,
            turnState = turnState,
            isMuted = isMuted
        )

        // Right side buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Display mode toggle button (Avatar/Wave)
            IconButton(
                onClick = onToggleDisplayMode,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Crossfade(
                    targetState = showAvatar,
                    animationSpec = tween(200),
                    label = "displayModeIcon"
                ) { isAvatar ->
                    Icon(
                        imageVector = if (isAvatar) Icons.Outlined.Waves else Icons.Filled.Person,
                        contentDescription = if (isAvatar) "Switch to Wave" else "Switch to Avatar"
                    )
                }
            }

            // Close button
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
    }
}

/**
 * Status chip showing connection and turn state
 */
@Composable
private fun AvatarLiveStatusChip(
    connectionStatus: ConnectionStatus,
    turnState: TurnState,
    isMuted: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when (connectionStatus) {
            ConnectionStatus.Connected -> when (turnState) {
                TurnState.UserTurn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                TurnState.WaitingForUser -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
            ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
            ConnectionStatus.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Animated status indicator
            val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
            val shouldAnimate = (connectionStatus == ConnectionStatus.Connected &&
                    (turnState == TurnState.UserTurn || turnState == TurnState.AgentTurn)) ||
                    connectionStatus == ConnectionStatus.Connecting

            val statusPulse by infiniteTransition.animateFloat(
                initialValue = if (shouldAnimate) 0.5f else 1f,
                targetValue = if (shouldAnimate) 1f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "statusPulse"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when (connectionStatus) {
                            ConnectionStatus.Connected -> when (turnState) {
                                TurnState.UserTurn -> MaterialTheme.colorScheme.primary.copy(alpha = statusPulse)
                                TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary.copy(alpha = statusPulse)
                                TurnState.WaitingForUser -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = statusPulse)
                            ConnectionStatus.Error -> MaterialTheme.colorScheme.error
                            ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = CircleShape
                    )
            )

            Text(
                text = when (connectionStatus) {
                    ConnectionStatus.Connected -> when (turnState) {
                        TurnState.UserTurn -> "You"
                        TurnState.AgentTurn -> "Amica"
                        TurnState.WaitingForUser -> if (isMuted) "Muted" else "Live"
                    }
                    ConnectionStatus.Connecting -> "Connecting"
                    ConnectionStatus.Error -> "Error"
                    ConnectionStatus.Disconnected -> "Offline"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (connectionStatus == ConnectionStatus.Connected &&
                    turnState != TurnState.WaitingForUser)
                    FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

/**
 * Bottom controls - mute and camera buttons
 */
@Composable
private fun AvatarLiveBottomControls(
    connectionStatus: ConnectionStatus,
    isMuted: Boolean,
    turnState: TurnState,
    isChannelOpen: Boolean,
    partialTranscript: String,
    error: String?,
    onToggleMute: () -> Unit,
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    wantsCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Partial transcript display
        AnimatedVisibility(
            visible = partialTranscript.isNotEmpty() && connectionStatus == ConnectionStatus.Connected,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = partialTranscript,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()
                )
            }
        }

        // Error display
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }

        // Control buttons row
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera toggle button
            val cameraScale by animateFloatAsState(
                targetValue = if (wantsCameraOn && isCameraActive) 1.1f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "cameraScale"
            )

            FloatingActionButton(
                onClick = onToggleCamera,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = cameraScale
                        scaleY = cameraScale
                    },
                containerColor = if (wantsCameraOn && isCameraActive)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                contentColor = if (wantsCameraOn && isCameraActive)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = if (wantsCameraOn && isCameraActive)
                        Icons.Default.CameraAlt
                    else
                        Icons.Outlined.CameraAlt,
                    contentDescription = if (isCameraActive) "Disable Camera" else "Enable Camera",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Mute/Unmute button - larger, primary action
            val micScale by animateFloatAsState(
                targetValue = if (turnState == TurnState.UserTurn && !isMuted) 1.1f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "micScale"
            )

            FloatingActionButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = micScale
                        scaleY = micScale
                    },
                containerColor = if (isMuted)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isMuted)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = if (isMuted)
                        Icons.Outlined.VolumeOff
                    else
                        Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Subtle hint text
        AnimatedVisibility(
            visible = connectionStatus == ConnectionStatus.Connected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = when (turnState) {
                    TurnState.UserTurn -> "Speak freely"
                    TurnState.AgentTurn -> "Amica is responding"
                    TurnState.WaitingForUser -> if (isMuted) "Tap to unmute" else "Listening"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}
