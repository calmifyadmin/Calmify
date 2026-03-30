package com.lifo.chat.presentation.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.draw.blur
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
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.AudioDevice
import com.lifo.chat.domain.model.AudioDeviceType
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.TurnState
import com.lifo.chat.presentation.components.AiAwakeningOverlay
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
 * @param onAvatarSetup Optional callback for avatar integration setup
 */
@Composable
fun LiveScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = false,
    avatarContent: (@Composable () -> Unit)? = null,
    onAvatarSetup: ((com.lifo.util.speech.SpeechAnimationTarget) -> Unit)? = null,
    speechAnimationTarget: com.lifo.util.speech.SpeechAnimationTarget? = null,
    gestureAnimationCallback: ((String) -> Unit)? = null,
    viewModel: LiveChatViewModel = koinViewModel()
) {
    // Connect avatar lip-sync controller to the live chat audio pipeline
    LaunchedEffect(speechAnimationTarget) {
        speechAnimationTarget?.let { viewModel.attachHumanoidController(it) }
    }

    // Connect gesture animation callback for AI-triggered animations (play_animation tool)
    LaunchedEffect(gestureAnimationCallback) {
        gestureAnimationCallback?.let { viewModel.attachGestureCallback(it) }
    }

    val liveChatState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()

    // Audio intelligence states for waveform
    val userVoiceLevel by viewModel.userVoiceLevel.collectAsStateWithLifecycle()
    val aiVoiceLevel by viewModel.aiVoiceLevel.collectAsStateWithLifecycle()
    val emotionalIntensity by viewModel.emotionalIntensity.collectAsStateWithLifecycle()
    val conversationMode by viewModel.conversationMode.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current

    // AI Awakening overlay — plays once during initial connection
    var hasShownAwakening by remember { mutableStateOf(false) }
    val showAwakening by remember {
        derivedStateOf {
            !hasShownAwakening &&
            (liveChatState.connectionStatus == ConnectionStatus.Connecting ||
             liveChatState.connectionStatus == ConnectionStatus.Connected)
        }
    }

    // Local state to track if user wants camera on
    var wantsCameraOn by remember { mutableStateOf(false) }

    // Display mode toggle: true = Avatar, false = Wave visualizer
    var displayAvatar by remember { mutableStateOf(showAvatar) }

    // Chat input visibility toggle
    var showChatInput by remember { mutableStateOf(false) }
    var chatInputText by remember { mutableStateOf("") }

    // Audio device selector
    var showDeviceSelector by remember { mutableStateOf(false) }

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
        // LAYER 1: Background - Avatar or Waveform Visualizer
        // When time limit reached, blur the entire background so avatar is visible but inaccessible
        val blurModifier = if (liveChatState.showTimeLimitReached && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.fillMaxSize().blur(32.dp)
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = blurModifier) {
            if (showAvatar && displayAvatar && avatarContent != null) {
                avatarContent()
            } else {
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
        }



        // LAYER 1.5: AI Awakening overlay — dissolves organically to reveal avatar
        AiAwakeningOverlay(
            isActive = showAwakening,
            modifier = Modifier.fillMaxSize(),
            onComplete = { hasShownAwakening = true }
        )

        // LAYER 2: Dark scrim when time limit reached — avatar still animates underneath but inaccessible
        if (liveChatState.showTimeLimitReached) {
            val scrimAlpha by animateFloatAsState(
                targetValue = 0.6f,
                animationSpec = tween(800),
                label = "scrimAlpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }

        // LAYER 3: Top bar — hidden during awakening animation
        AnimatedVisibility(
            visible = hasShownAwakening,
            enter = fadeIn(tween(400)),
            exit = fadeOut()
        ) {
            LiveTopBar(
                connectionStatus = liveChatState.connectionStatus,
                turnState = liveChatState.turnState,
                isMuted = liveChatState.isMuted,
                showAvatar = showAvatar,
                displayAvatar = displayAvatar,
                onToggleDisplayMode = if (showAvatar) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        displayAvatar = !displayAvatar
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            )
        }

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

        // Session time limit reached — paywall overlay
        AnimatedVisibility(
            visible = liveChatState.showTimeLimitReached,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            com.lifo.ui.components.InlinePaywallCard(
                title = "Sessione terminata",
                message = "Con Calmify Pro hai sessioni live fino a 15 minuti con Eve, " +
                    "avatar 3D, insight avanzati e molto altro.",
                ctaText = "Scopri Pro",
                onUpgradeClick = { onClose() }, // Navigate back, then user can go to subscription
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Session timer display (top-right, shown when connected and there's a time limit)
        if (liveChatState.connectionStatus == ConnectionStatus.Connected &&
            liveChatState.sessionTimeLimitSeconds > 0 &&
            !liveChatState.showTimeLimitReached
        ) {
            val elapsed = liveChatState.sessionElapsedSeconds
            val limit = liveChatState.sessionTimeLimitSeconds
            val remaining = (limit - elapsed).coerceAtLeast(0)
            val minutes = remaining / 60
            val seconds = remaining % 60
            val isLow = remaining <= 30

            Text(
                text = "%d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.labelMedium,
                color = if (isLow) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Bottom controls - nascosti quando chat input è attiva
        AnimatedVisibility(
            visible = !showChatInput && !liveChatState.showTimeLimitReached && hasShownAwakening,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            LiveBottomControls(
                connectionStatus = liveChatState.connectionStatus,
                isMuted = liveChatState.isMuted,
                turnState = liveChatState.turnState,
                isChannelOpen = liveChatState.isChannelOpen,
                partialTranscript = liveChatState.partialTranscript,
                transcript = liveChatState.transcript,
                error = liveChatState.error,
                onToggleMute = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleMute()
                },
                onClose = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClose()
                },
                isCameraActive = liveChatState.isCameraActive,
                hasCameraPermission = liveChatState.hasCameraPermission,
                wantsCameraOn = wantsCameraOn,
                onToggleCamera = {
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
                onToggleChatInput = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showChatInput = !showChatInput
                },
                showChatInput = showChatInput,
                activeDevice = liveChatState.activeDevice,
                onOpenDeviceSelector = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showDeviceSelector = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    // Audio device selector bottom sheet
    if (showDeviceSelector) {
        AudioDeviceSelectorSheet(
            devices = liveChatState.availableDevices,
            onDeviceSelected = { device ->
                viewModel.selectAudioDevice(device.id)
                showDeviceSelector = false
            },
            onDismiss = { showDeviceSelector = false }
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

@Composable
private fun LiveTopBar(
    connectionStatus: ConnectionStatus,
    turnState: TurnState,
    isMuted: Boolean,
    showAvatar: Boolean,
    displayAvatar: Boolean,
    onToggleDisplayMode: (() -> Unit)?,
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

        // Right side - Display toggle (if avatar enabled)
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
        } else {
            // Empty spacer to keep status chip aligned left
            Spacer(modifier = Modifier.size(48.dp))
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
    transcript: String = "",
    error: String?,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    wantsCameraOn: Boolean,
    onToggleCamera: () -> Unit,
    onToggleChatInput: (() -> Unit)? = null,
    showChatInput: Boolean = false,
    activeDevice: AudioDevice? = null,
    onOpenDeviceSelector: (() -> Unit)? = null,
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

        // Toolbar pill + End call button
        Row(
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Toolbar pill — unified container with all action icons
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera toggle
                    IconButton(
                        onClick = onToggleCamera,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (wantsCameraOn && isCameraActive)
                                MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                            contentColor = if (wantsCameraOn && isCameraActive)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (wantsCameraOn && isCameraActive)
                                Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                            contentDescription = if (isCameraActive) "Disable Camera" else "Enable Camera"
                        )
                    }

                    // Mic toggle
                    val micScale by animateFloatAsState(
                        targetValue = if (turnState == TurnState.UserTurn && !isMuted) 1.08f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "micScale"
                    )

                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.graphicsLayer {
                            scaleX = micScale
                            scaleY = micScale
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isMuted)
                                MaterialTheme.colorScheme.errorContainer
                            else Color.Transparent,
                            contentColor = if (isMuted)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Filled.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute"
                        )
                    }

                    // Chat Input toggle
                    if (onToggleChatInput != null) {
                        IconButton(
                            onClick = onToggleChatInput,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showChatInput)
                                    MaterialTheme.colorScheme.tertiaryContainer
                                else Color.Transparent,
                                contentColor = if (showChatInput)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (showChatInput) Icons.Filled.Edit else Icons.Outlined.Edit,
                                contentDescription = if (showChatInput) "Hide Chat Input" else "Show Chat Input"
                            )
                        }
                    }

                    // Audio device selector
                    if (onOpenDeviceSelector != null) {
                        IconButton(
                            onClick = onOpenDeviceSelector,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = when (activeDevice?.type) {
                                    AudioDeviceType.BLUETOOTH -> Icons.Default.BluetoothAudio
                                    AudioDeviceType.WIRED_HEADSET -> Icons.Default.Headphones
                                    AudioDeviceType.USB -> Icons.Default.Usb
                                    AudioDeviceType.EARPIECE -> Icons.Default.PhoneInTalk
                                    else -> Icons.Default.VolumeUp
                                },
                                contentDescription = "Audio device: ${activeDevice?.name ?: "Speaker"}"
                            )
                        }
                    }
                }
            }

            // End call button — subtle outlined, slightly larger
            OutlinedIconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                ),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "End session"
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

/**
 * Bottom sheet showing available audio output devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioDeviceSelectorSheet(
    devices: List<AudioDevice>,
    onDeviceSelected: (AudioDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Audio Output",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            if (devices.isEmpty()) {
                Text(
                    text = "No devices available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }

            devices.forEach { device ->
                val icon = when (device.type) {
                    AudioDeviceType.SPEAKER -> Icons.Default.VolumeUp
                    AudioDeviceType.EARPIECE -> Icons.Default.PhoneInTalk
                    AudioDeviceType.BLUETOOTH -> Icons.Default.BluetoothAudio
                    AudioDeviceType.WIRED_HEADSET -> Icons.Default.Headphones
                    AudioDeviceType.USB -> Icons.Default.Usb
                }

                Surface(
                    onClick = { onDeviceSelected(device) },
                    color = if (device.isActive)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (device.isActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (device.isActive)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (device.isActive) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (device.isActive) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
