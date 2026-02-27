package com.lifo.chat.presentation.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.media.AudioManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.TurnState
import com.lifo.chat.presentation.components.GeminiLiquidVisualizer
import com.lifo.chat.presentation.components.SimpleLiveCameraPreview
import com.lifo.chat.presentation.viewmodel.LiveChatViewModel

/**
 * Dedicated Live Screen with minimalist UI inspired by Gemini Live
 *
 * Features:
 * - Full-screen waveform visualization OR custom avatar content
 * - Minimal controls (Close, Mute, Camera, Display Mode Toggle)
 * - Real-time audio-reactive animations
 * - Elegant gradient background
 * - Optional avatar integration via Composable slot
 *
 * @param onClose Callback when user closes the screen
 * @param showAvatar If true, shows avatar content slot instead of visualizer
 * @param avatarContent Optional Composable content for avatar (required if showAvatar=true)
 * @param arContent Optional Composable content for AR avatar mode
 * @param isArMode Whether AR mode is currently active
 * @param onToggleArMode Callback to toggle AR mode on/off
 * @param onAvatarSetup Optional callback for avatar integration setup
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LiveScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
    avatarContent: (@Composable () -> Unit)? = null,
    arContent: (@Composable () -> Unit)? = null,
    isArMode: Boolean = false,
    onToggleArMode: (() -> Unit)? = null,
    onAvatarSetup: ((com.lifo.util.speech.SpeechAnimationTarget) -> Unit)? = null,
    viewModel: LiveChatViewModel = hiltViewModel()
) {
    val liveChatState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()

    // Audio intelligence states for waveform
    val userVoiceLevel by viewModel.userVoiceLevel.collectAsStateWithLifecycle()
    val aiVoiceLevel by viewModel.aiVoiceLevel.collectAsStateWithLifecycle()
    val emotionalIntensity by viewModel.emotionalIntensity.collectAsStateWithLifecycle()
    val conversationMode by viewModel.conversationMode.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current

    // Local state to track if user wants camera on
    var wantsCameraOn by remember { mutableStateOf(false) }

    // Display mode toggle: true = Avatar, false = Wave visualizer
    var displayAvatar by remember { mutableStateOf(showAvatar) }

    // Chat input visibility toggle
    var showChatInput by remember { mutableStateOf(false) }
    var chatInputText by remember { mutableStateOf("") }

    // Permission handling
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

    // Auto-connect when screen opens
    LaunchedEffect(Unit) {
        if (!liveChatState.hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (liveChatState.connectionStatus == ConnectionStatus.Disconnected) {
            viewModel.connectToRealtime()
        }
    }

    // MODE_IN_COMMUNICATION forces hardware volume buttons to STREAM_VOICE_CALL.
    // Our AI audio uses USAGE_MEDIA (STREAM_MUSIC) for quality.
    // Workaround: max out STREAM_VOICE_CALL so hardware buttons don't reduce AI volume.
    val ctx = LocalContext.current
    val audioMgr = remember(ctx) { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager }
    DisposableEffect(Unit) {
        val prevCallVol = audioMgr?.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val maxCallVol = audioMgr?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 0
        audioMgr?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVol, 0)
        onDispose {
            if (prevCallVol != null) {
                audioMgr?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, prevCallVol, 0)
            }
        }
    }

    // Disconnect when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnectFromRealtime()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // LAYER 1: Background - AR Avatar, Avatar, or Waveform Visualizer
        if (isArMode && arContent != null) {
            // AR Avatar Content (camera passthrough + avatar in real world)
            arContent()
        } else if (showAvatar && displayAvatar && avatarContent != null) {
            // Avatar Content (provided from outside)
            avatarContent()
        } else {
            // Waveform visualizer - full screen with same style as ChatScreen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                GeminiLiquidVisualizer(
                    isSpeaking = liveChatState.aiEmotion == AIEmotion.Speaking ||
                                liveChatState.turnState == TurnState.UserTurn,
                    modifier = Modifier.fillMaxSize(),
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.tertiary,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    userVoiceLevel = userVoiceLevel,
                    aiVoiceLevel = aiVoiceLevel,
                    emotionalIntensity = emotionalIntensity,
                    conversationMode = conversationMode,
                    isUserSpeaking = liveChatState.turnState == TurnState.UserTurn && !liveChatState.isMuted,
                    isAiSpeaking = liveChatState.aiEmotion == AIEmotion.Speaking
                )
            } else {
                // Fallback gradient background for older devices
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
        }



        // LAYER 3: Top bar with close button, display toggle, AR toggle, and status
        LiveTopBar(
            connectionStatus = liveChatState.connectionStatus,
            turnState = liveChatState.turnState,
            isMuted = liveChatState.isMuted,
            showAvatar = showAvatar,
            displayAvatar = displayAvatar,
            isArMode = isArMode,
            onToggleArMode = if (arContent != null) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleArMode?.invoke()
                }
            } else null,
            onToggleDisplayMode = if (showAvatar) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    displayAvatar = !displayAvatar
                }
            } else null,
            onClose = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // Simplified camera preview - shows when user wants camera on and has permission
        AnimatedVisibility(
            visible = wantsCameraOn && liveChatState.hasCameraPermission,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = 96.dp,  // Space for top bar + extra margin
                    bottom = 220.dp,  // Space for bottom controls + extra margin
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            SimpleLiveCameraPreview(
                isCameraActive = liveChatState.isCameraActive,
                hasCameraPermission = liveChatState.hasCameraPermission,
                onRequestCameraPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onSurfaceTextureReady = { surfaceTexture ->
                    if (liveChatState.hasCameraPermission && !liveChatState.isCameraActive) {
                        viewModel.startCameraPreview(surfaceTexture)
                    }
                },
                onSurfaceTextureDestroyed = {
                    if (liveChatState.isCameraActive) {
                        viewModel.stopCameraPreview()
                    }
                }
            )
        }

        // Chat Input overlay (when enabled) - con bottoni integrati
        AnimatedVisibility(
            visible = showChatInput,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            com.lifo.chat.presentation.components.ChatInput(
                value = chatInputText,
                onValueChange = { chatInputText = it },
                onSend = {
                    if (chatInputText.isNotBlank()) {
                        // Invia messaggio testuale tramite WebSocket
                        viewModel.sendTextMessage(chatInputText)
                        chatInputText = ""
                        showChatInput = false
                    }
                },
                isEnabled = liveChatState.connectionStatus == ConnectionStatus.Connected,
                isVoiceChatMode = false,
                trailingActions = {
                    // Camera button
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!liveChatState.hasCameraPermission) {
                                wantsCameraOn = true
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                wantsCameraOn = !wantsCameraOn
                                if (!wantsCameraOn && liveChatState.isCameraActive) {
                                    viewModel.stopCameraPreview()
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (liveChatState.isCameraActive)
                                Icons.Filled.CameraAlt
                            else
                                Icons.Outlined.CameraAlt,
                            contentDescription = "Toggle Camera",
                            tint = if (liveChatState.isCameraActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Chat toggle button (chiude la chat input)
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showChatInput = false
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Hide Chat Input",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    // Mute button
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleMute()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (liveChatState.isMuted)
                                Icons.Outlined.VolumeOff
                            else
                                Icons.Filled.Mic,
                            contentDescription = if (liveChatState.isMuted) "Unmute" else "Mute",
                            tint = if (liveChatState.isMuted)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Bottom controls - nascosti quando chat input è attiva
        AnimatedVisibility(
            visible = !showChatInput,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            LiveBottomControls(
                connectionStatus = liveChatState.connectionStatus,
                isMuted = liveChatState.isMuted,
                turnState = liveChatState.turnState,
                isChannelOpen = liveChatState.isChannelOpen,
                partialTranscript = liveChatState.partialTranscript,
                transcript = liveChatState.transcript,  // NEW: pass final transcript
                error = liveChatState.error,
                onToggleMute = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleMute()
                },
                isCameraActive = liveChatState.isCameraActive,
                hasCameraPermission = liveChatState.hasCameraPermission,
                wantsCameraOn = wantsCameraOn,
                onToggleCamera = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!liveChatState.hasCameraPermission) {
                        // Request permission first
                        wantsCameraOn = true
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        // Toggle camera on/off
                        wantsCameraOn = !wantsCameraOn
                        if (!wantsCameraOn && liveChatState.isCameraActive) {
                            // Stop camera if turning off
                            viewModel.stopCameraPreview()
                        }
                    }
                },
                onToggleChatInput = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showChatInput = !showChatInput
                },
                showChatInput = showChatInput,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
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

@Composable
private fun LiveTopBar(
    connectionStatus: ConnectionStatus,
    turnState: TurnState,
    isMuted: Boolean,
    showAvatar: Boolean,
    displayAvatar: Boolean,
    isArMode: Boolean = false,
    onToggleArMode: (() -> Unit)? = null,
    onToggleDisplayMode: (() -> Unit)?,
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
        // Left side - Status indicator
        LiveStatusChip(
            connectionStatus = connectionStatus,
            turnState = turnState,
            isMuted = isMuted
        )

        // Right side - Display toggle (if avatar enabled) + Close button
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AR toggle button (only if AR is available)
            if (showAvatar && displayAvatar && onToggleArMode != null) {
                IconButton(
                    onClick = onToggleArMode,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isArMode)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = if (isArMode)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewInAr,
                        contentDescription = if (isArMode) "Disattiva AR" else "Attiva AR"
                    )
                }
            }

            // Display mode toggle button (only if avatar mode enabled)
            if (showAvatar && onToggleDisplayMode != null) {
                IconButton(
                    onClick = onToggleDisplayMode,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Crossfade(
                        targetState = displayAvatar,
                        animationSpec = tween(200),
                        label = "displayModeIcon"
                    ) { isAvatar ->
                        Icon(
                            imageVector = if (isAvatar) Icons.Outlined.Waves else Icons.Filled.Person,
                            contentDescription = if (isAvatar) "Switch to Wave" else "Switch to Avatar"
                        )
                    }
                }
            }

            // Close button
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Live Chat"
                )
            }
        }
    }
}

@Composable
private fun LiveStatusChip(
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
                TurnState.WaitingForUser -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
            ConnectionStatus.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        TurnState.AgentTurn -> "Gemini"
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


@Composable
private fun LiveBottomControls(
    connectionStatus: ConnectionStatus,
    isMuted: Boolean,
    turnState: TurnState,
    isChannelOpen: Boolean,
    partialTranscript: String,
    transcript: String = "",  // NEW: final transcript
    error: String?,
    onToggleMute: () -> Unit,
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    wantsCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onToggleChatInput: (() -> Unit)? = null,
    showChatInput: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spacer per spingere tutto verso il basso
        Spacer(modifier = Modifier.weight(1f))

        // Error display
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            error?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
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

        // Mute/Unmute and Camera buttons row
        Row(
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (wantsCameraOn && isCameraActive)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = if (wantsCameraOn && isCameraActive) Icons.Default.CameraAlt else Icons.Outlined.CameraAlt,
                    contentDescription = if (isCameraActive) "Disable Camera" else "Enable Camera",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Chat Input toggle button (only if onToggleChatInput is provided)
            if (onToggleChatInput != null) {
                FloatingActionButton(
                    onClick = onToggleChatInput,
                    modifier = Modifier.size(64.dp),
                    containerColor = if (showChatInput)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (showChatInput)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = if (showChatInput) Icons.Filled.Edit else Icons.Outlined.Edit,
                        contentDescription = if (showChatInput) "Hide Chat Input" else "Show Chat Input",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Mute/Unmute button - large, prominent
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
                    .size(72.dp)
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
                    imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    modifier = Modifier.size(32.dp)
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
                    TurnState.AgentTurn -> "Gemini is responding"
                    TurnState.WaitingForUser -> if (isMuted) "Tap to unmute" else "Listening"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
