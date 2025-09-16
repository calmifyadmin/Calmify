package com.lifo.chat.presentation.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.VolumeOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.chat.presentation.components.*
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import com.lifo.chat.presentation.viewmodel.LiveChatViewModel
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.TurnState
import com.lifo.util.model.ChatEmotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navigateBack: () -> Unit,
    navigateToWriteWithContent: (String) -> Unit,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    viewModel: ChatViewModel = hiltViewModel(),
    liveChatViewModel: LiveChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isVoiceActive by viewModel.isVoiceActive.collectAsStateWithLifecycle()
    val voiceEmotion by viewModel.voiceEmotion.collectAsStateWithLifecycle()
    val voiceLatency by viewModel.voiceLatency.collectAsStateWithLifecycle()

    // Live chat state
    val liveChatState by liveChatViewModel.uiState.collectAsStateWithLifecycle()
    val currentTranscript by liveChatViewModel.currentTranscript.collectAsStateWithLifecycle()

    // Advanced audio intelligence states for liquid visualizer
    val userVoiceLevel by liveChatViewModel.userVoiceLevel.collectAsStateWithLifecycle()
    val aiVoiceLevel by liveChatViewModel.aiVoiceLevel.collectAsStateWithLifecycle()
    val emotionalIntensity by liveChatViewModel.emotionalIntensity.collectAsStateWithLifecycle()
    val conversationMode by liveChatViewModel.conversationMode.collectAsStateWithLifecycle()

    // Track chat modes - regular voice vs live chat mode
    var isVoiceChatMode by remember { mutableStateOf(false) }
    var isLiveChatMode by remember { mutableStateOf(false) }

    // Permission handling for voice and camera modes
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (isLiveChatMode) {
                liveChatViewModel.onAudioPermissionGranted()
            } else if (isVoiceChatMode) {
                // Regular voice chat started with permission
            }
        } else {
            if (isLiveChatMode) {
                liveChatViewModel.onAudioPermissionDenied()
                isLiveChatMode = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            liveChatViewModel.onCameraPermissionGranted()
        } else {
            liveChatViewModel.onCameraPermissionDenied()
        }
    }

    // Load existing session if sessionId is provided
    LaunchedEffect(sessionId) {
        sessionId?.let {
            viewModel.loadExistingSession(it)
        }
    }

    // Handle export navigation
    LaunchedEffect(uiState.exportedContent) {
        uiState.exportedContent?.let { content ->
            navigateToWriteWithContent(content)
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Get user info
    val userDisplayName = viewModel.getUserDisplayName()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.streamingMessage) {
        if (uiState.messages.isNotEmpty() || uiState.streamingMessage != null) {
            delay(100)
            listState.animateScrollToItem(
                index = uiState.messages.size + (if (uiState.streamingMessage != null) 1 else 0) - 1
            )
        }
    }

    // Full-screen background with liquid visualizer
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Intelligent liquid wave background with real-time audio analytics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GeminiLiquidVisualizer(
                isSpeaking = voiceState.isSpeaking ||
                        isVoiceActive ||
                        uiState.isLoading ||
                        uiState.streamingMessage != null ||
                        (isLiveChatMode && (liveChatState.aiEmotion == AIEmotion.Speaking || liveChatState.audioLevel > 0.1f)),
                modifier = Modifier.fillMaxSize(),
                primaryColor = MaterialTheme.colorScheme.primary,
                secondaryColor = MaterialTheme.colorScheme.tertiary,
                backgroundColor = MaterialTheme.colorScheme.surface,
                // NEW: Real-time audio intelligence integration
                userVoiceLevel = if (isLiveChatMode) userVoiceLevel else 0f,
                aiVoiceLevel = if (isLiveChatMode) aiVoiceLevel else if (voiceState.isSpeaking) 0.8f else 0f,
                emotionalIntensity = if (isLiveChatMode) emotionalIntensity else when(voiceEmotion) {
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.HAPPY -> 0.9f
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.EXCITED -> 1.0f
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.SAD -> 0.2f
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.THOUGHTFUL -> 0.6f
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.EMPATHETIC -> 0.7f
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.CURIOUS -> 0.8f
                    com.lifo.chat.audio.GeminiNativeVoiceSystem.Emotion.NEUTRAL -> 0.5f
                },
                conversationMode = if (isLiveChatMode) conversationMode else "casual",
                isUserSpeaking = isLiveChatMode && liveChatState.turnState == TurnState.UserTurn && !liveChatState.isMuted,
                isAiSpeaking = (isLiveChatMode && liveChatState.aiEmotion == AIEmotion.Speaking) || voiceState.isSpeaking
            )
        } else {
            // Fallback gradient background
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


        // Scaffold on top of the background
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            topBar = {
                MinimalTopBar(
                    voiceEnabled = voiceState.isInitialized,
                    isVoiceSpeaking = voiceState.isSpeaking,
                    isVoiceChatMode = isVoiceChatMode,
                    isLiveChatMode = isLiveChatMode,
                    liveChatConnectionStatus = liveChatState.connectionStatus,
                    liveChatTurnState = liveChatState.turnState,
                    isLiveChatMuted = liveChatState.isMuted,
                    onNavigateBack = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isVoiceChatMode) {
                            viewModel.stopSpeaking()
                            isVoiceChatMode = false
                        }
                        if (isLiveChatMode) {
                            liveChatViewModel.disconnectFromRealtime()
                            isLiveChatMode = false
                        }
                        navigateBack()
                    },
                    onStopVoice = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.stopSpeaking()
                    },
                    onToggleVoiceChat = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isVoiceChatMode = !isVoiceChatMode
                        if (isVoiceChatMode) {
                            isLiveChatMode = false // Disable live chat mode
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.stopSpeaking()
                        }
                    },
                    onToggleLiveChatMode = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isLiveChatMode = !isLiveChatMode
                        if (isLiveChatMode) {
                            isVoiceChatMode = false // Disable regular voice chat
                            viewModel.stopSpeaking() // Stop any current voice
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            liveChatViewModel.disconnectFromRealtime()
                        }
                    }
                )
            },
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    // Live chat camera preview
                    AnimatedVisibility(
                        visible = isLiveChatMode,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        LiveCameraPreview(
                            isCameraActive = liveChatState.isCameraActive,
                            hasCameraPermission = liveChatState.hasCameraPermission,
                            isCameraEnabled = liveChatState.isCameraActive || liveChatState.hasCameraPermission,
                            onToggleCamera = {
                                if (liveChatState.hasCameraPermission) {
                                    if (liveChatState.isCameraActive) {
                                        liveChatViewModel.stopCameraPreview()
                                    } else {
                                        // Camera will start when surface is ready
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onRequestCameraPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onSurfaceTextureReady = { surfaceTexture ->
                                android.util.Log.d("ChatScreen", "📸 onSurfaceTextureReady called")
                                android.util.Log.d("ChatScreen", "📸 hasCameraPermission: ${liveChatState.hasCameraPermission}")
                                android.util.Log.d("ChatScreen", "📸 isCameraActive: ${liveChatState.isCameraActive}")
                                if (liveChatState.hasCameraPermission && !liveChatState.isCameraActive) {
                                    android.util.Log.d("ChatScreen", "📸 Calling startCameraPreview...")
                                    liveChatViewModel.startCameraPreview(surfaceTexture)
                                } else {
                                    android.util.Log.w("ChatScreen", "📸 NOT starting camera: permission=${liveChatState.hasCameraPermission}, active=${liveChatState.isCameraActive}")
                                }
                            },
                            onSurfaceTextureDestroyed = {
                                if (liveChatState.isCameraActive) {
                                    liveChatViewModel.stopCameraPreview()
                                }
                            }
                        )
                    }

                    // Live chat transcript display
                    AnimatedVisibility(
                        visible = isLiveChatMode && currentTranscript.isNotEmpty(),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🤖 Gemini Live",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = currentTranscript,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Global voice indicator - appears above input when speaking
                    AnimatedVisibility(
                        visible = isVoiceActive && !isLiveChatMode,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut()
                    ) {
                        FluidAudioIndicator(
                            isSpeaking = isVoiceActive,
                            emotion = voiceEmotion.name,
                            latencyMs = voiceLatency,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                        )
                    }

                    // Conditional input: Mute/Unmute for live mode, regular input for normal mode
                    if (isLiveChatMode) {
                        LiveChatMuteUnmuteSection(
                            connectionStatus = liveChatState.connectionStatus,
                            isMuted = liveChatState.isMuted,
                            turnState = liveChatState.turnState,
                            isChannelOpen = liveChatState.isChannelOpen,
                            partialTranscript = liveChatState.partialTranscript,
                            error = liveChatState.error,
                            onToggleMute = liveChatViewModel::toggleMute,
                            onRetryConnection = liveChatViewModel::retryConnection,
                            onClearError = liveChatViewModel::clearError
                        )
                    } else {
                        // Regular chat input - visible for text and regular voice messages
                        ChatInput(
                            value = uiState.inputText,
                            onValueChange = viewModel::updateInputText,
                            onSend = {
                                viewModel.sendMessage(uiState.inputText)
                            },
                            isEnabled = !uiState.isLoading && uiState.streamingMessage == null,
                            isStreaming = uiState.streamingMessage != null,
                            currentEmotion = voiceEmotion.name,
                            voiceNaturalness = voiceState.naturalness,
                            isVoiceChatMode = isVoiceChatMode,
                            onVoiceRecord = if (isVoiceChatMode) {
                                {
                                    // Il ChatInput gestisce già internamente la registrazione vocale
                                    // tramite il suo speechRecognizer quando si preme il pulsante mic
                                }
                            } else null
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        ChatBubble(
                            message = message,
                            isSpeaking = voiceState.isSpeaking &&
                                    voiceState.currentSpeakingMessageId == message.id,
                            voiceEmotion = voiceEmotion.name,
                            voiceLatency = voiceLatency,
                            onSpeak = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.speakMessage(message.id)
                            },
                            onDelete = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteMessage(message.id)
                            },
                            onCopy = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                copyToClipboard(context, message.content)
                            }
                        )
                    }

                    // Streaming message
                    uiState.streamingMessage?.let { streaming ->
                        item(key = streaming.id) {
                            StreamingMessage(
                                content = streaming.content.toString(),
                                isSpeaking = isVoiceActive
                            )
                        }
                    }
                }

                // Empty state handling
                if (uiState.messages.isEmpty() && uiState.streamingMessage == null && uiState.currentSession == null) {
                    NaturalAnimatedEmptyState(
                        userName = userDisplayName,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        } // End of Scaffold
    } // End of Box

    // Error handling
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error in a more subtle way
            delay(3000)
            viewModel.clearError()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalTopBar(
    voiceEnabled: Boolean,
    isVoiceSpeaking: Boolean,
    isVoiceChatMode: Boolean,
    isLiveChatMode: Boolean,
    liveChatConnectionStatus: ConnectionStatus,
    liveChatTurnState: TurnState,
    isLiveChatMuted: Boolean,
    onNavigateBack: () -> Unit,
    onStopVoice: () -> Unit,
    onToggleVoiceChat: () -> Unit,
    onToggleLiveChatMode: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    when {
                        isLiveChatMode -> "🚀 Gemini Live"
                        isVoiceChatMode -> "AI Voice Chat"
                        else -> "AI Chat"
                    }
                )

                // Status chips
                when {
                    isLiveChatMode -> {
                        // Enhanced Live chat status with turn-taking indicator
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = when (liveChatConnectionStatus) {
                                ConnectionStatus.Connected -> when (liveChatTurnState) {
                                    TurnState.UserTurn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                    TurnState.WaitingForUser -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                }
                                ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                ConnectionStatus.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            },
                            modifier = Modifier.height(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Animated status indicator
                                val statusInfiniteTransition = rememberInfiniteTransition(label = "statusAnimation")
                                val shouldAnimate = (liveChatConnectionStatus == ConnectionStatus.Connected &&
                                        (liveChatTurnState == TurnState.UserTurn || liveChatTurnState == TurnState.AgentTurn)) ||
                                        liveChatConnectionStatus == ConnectionStatus.Connecting

                                val statusPulse by statusInfiniteTransition.animateFloat(
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
                                            when (liveChatConnectionStatus) {
                                                ConnectionStatus.Connected -> when (liveChatTurnState) {
                                                    TurnState.UserTurn -> MaterialTheme.colorScheme.primary.copy(alpha = statusPulse)
                                                    TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary.copy(alpha = statusPulse)
                                                    TurnState.WaitingForUser -> MaterialTheme.colorScheme.tertiary
                                                }
                                                ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = statusPulse)
                                                ConnectionStatus.Error -> MaterialTheme.colorScheme.error
                                                ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                )

                                Text(
                                    text = when (liveChatConnectionStatus) {
                                        ConnectionStatus.Connected -> when (liveChatTurnState) {
                                            TurnState.UserTurn -> "Tu parli"
                                            TurnState.AgentTurn -> "AI parla"
                                            TurnState.WaitingForUser -> if (isLiveChatMuted) "Mutato" else "Live"
                                        }
                                        ConnectionStatus.Connecting -> "Setup VAD"
                                        ConnectionStatus.Error -> "Errore"
                                        ConnectionStatus.Disconnected -> "Offline"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (liveChatConnectionStatus == ConnectionStatus.Connected &&
                                        liveChatTurnState != TurnState.WaitingForUser)
                                        FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                    voiceEnabled -> {
                        // Regular voice status chip
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.small
                                        )
                                )
                                Text(
                                    text = "Voice Ready",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            // Enhanced Live Chat toggle with visual feedback
            IconButton(
                onClick = onToggleLiveChatMode,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isLiveChatMode) {
                        when (liveChatConnectionStatus) {
                            ConnectionStatus.Connected -> when (liveChatTurnState) {
                                TurnState.UserTurn -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            }
                            ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        }
                    } else Color.Transparent,
                    contentColor = if (isLiveChatMode) {
                        when (liveChatConnectionStatus) {
                            ConnectionStatus.Connected -> when (liveChatTurnState) {
                                TurnState.UserTurn -> MaterialTheme.colorScheme.primary
                                TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            else -> MaterialTheme.colorScheme.primary
                        }
                    } else MaterialTheme.colorScheme.onSurface
                )
            ) {
                // Animated microphone icon based on turn state
                val micScale by animateFloatAsState(
                    targetValue = if (isLiveChatMode && liveChatConnectionStatus == ConnectionStatus.Connected &&
                        liveChatTurnState == TurnState.UserTurn && !isLiveChatMuted) 1.2f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "micScale"
                )

                Icon(
                    imageVector = if (isLiveChatMode && isLiveChatMuted)
                        Icons.Outlined.VolumeOff
                    else
                        Icons.Default.Mic,
                    contentDescription = if (isLiveChatMode) "Live Chat Attiva" else "Attiva Live Chat",
                    modifier = Modifier.graphicsLayer {
                        scaleX = micScale
                        scaleY = micScale
                    }
                )
            }

            // Voice chat toggle button
            if (!isLiveChatMode) {
                IconButton(
                    onClick = onToggleVoiceChat,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isVoiceChatMode)
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        else Color.Transparent,
                        contentColor = if (isVoiceChatMode)
                            MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = if (isVoiceChatMode) "Voice Mode Active" else "Activate Voice Mode"
                    )
                }
            }

            // Stop voice button when speaking
            AnimatedVisibility(
                visible = isVoiceSpeaking,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onStopVoice,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Voice"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun StreamingMessage(
    content: String,
    isSpeaking: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // AI Avatar
        Box(modifier = Modifier.size(32.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✨",
                        fontSize = 16.sp
                    )
                }
            }

            // Speaking indicator
            if (isSpeaking) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(8.dp)
                        .offset(x = 2.dp, y = 2.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (content.isEmpty()) {
                        // Typing indicator
                        TypingIndicator()
                    } else {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Real-time voice indicator
            if (isSpeaking && content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                VoiceWaveform(
                    audioLevel = 0.8f,
                    isActive = true,
                    modifier = Modifier.height(16.dp).padding(start = 40.dp)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing$index")

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0f at 0 + (index * 100)
                        -4f at 150 + (index * 100)
                        0f at 300 + (index * 100)
                    }
                ),
                label = "offset$index"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = offsetY.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    )
            )
        }
    }
}

@Composable
private fun NaturalAnimatedEmptyState(
    userName: String?,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "alpha"
    )

    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offsetY"
    )

    Column(
        modifier = modifier
            .padding(32.dp)
            .alpha(alpha)
            .offset(y = offsetY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Animated greeting with name
        Text(
            text = "Ciao${userName?.let { " $it" } ?: ""}",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 42.sp,
                letterSpacing = (-0.5).sp
            ),
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Animated wave emoji
        val waveRotation by rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = -10f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "waveRotation"
        )

        Text(
            text = "👋",
            fontSize = 48.sp,
            modifier = Modifier.graphicsLayer {
                rotationZ = waveRotation
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Come posso aiutarti oggi?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Inizia una conversazione per sperimentare la voce naturale",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun LiveChatMuteUnmuteSection(
    connectionStatus: ConnectionStatus,
    isMuted: Boolean,
    turnState: TurnState,
    isChannelOpen: Boolean,
    partialTranscript: String,
    error: String?,
    onToggleMute: () -> Unit,
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

        // VAD Status & Instructions with enhanced visual feedback
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main conversation status with animated indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator circle with animation
                val indicatorColor = when (connectionStatus) {
                    ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outline
                    ConnectionStatus.Connecting -> MaterialTheme.colorScheme.tertiary
                    ConnectionStatus.Error -> MaterialTheme.colorScheme.error
                    ConnectionStatus.Connected -> when (turnState) {
                        TurnState.UserTurn -> MaterialTheme.colorScheme.primary
                        TurnState.AgentTurn -> MaterialTheme.colorScheme.secondary
                        TurnState.WaitingForUser -> if (isMuted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    }
                }

                // Animated pulsing indicator for active states
                val shouldPulse = (connectionStatus == ConnectionStatus.Connected &&
                        (turnState == TurnState.UserTurn || turnState == TurnState.AgentTurn)) ||
                        connectionStatus == ConnectionStatus.Connecting

                val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = if (shouldPulse) 0.3f else 1f,
                    targetValue = if (shouldPulse) 1f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            indicatorColor.copy(alpha = pulseAlpha),
                            shape = CircleShape
                        )
                )

                // Status text with enhanced messaging
                Text(
                    text = when (connectionStatus) {
                        ConnectionStatus.Disconnected -> "Inizializzazione Gemini Live..."
                        ConnectionStatus.Connecting -> "Configurazione VAD in corso..."
                        ConnectionStatus.Error -> "⚠️ Errore di connessione"
                        ConnectionStatus.Connected -> when (turnState) {
                            TurnState.UserTurn -> "🎤 È il tuo turno - Parla ora"
                            TurnState.AgentTurn -> "🤖 Gemini sta rispondendo..."
                            TurnState.WaitingForUser -> if (isMuted)
                                "🔇 Microfono disattivato - Premi per attivare"
                            else
                                "🎤 In ascolto - VAD attivo"
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Show partial transcript while speaking
            AnimatedVisibility(
                visible = partialTranscript.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = partialTranscript,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // Enhanced Mute/Unmute toggle with visual feedback
        FilledTonalButton(
            onClick = onToggleMute,
            enabled = connectionStatus == ConnectionStatus.Connected && isChannelOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isMuted)
                    MaterialTheme.colorScheme.errorContainer
                else
                    MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated microphone icon
                val micScale by animateFloatAsState(
                    targetValue = if (turnState == TurnState.UserTurn && !isMuted) 1.2f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "micScale"
                )

                Icon(
                    imageVector = if (isMuted) Icons.Outlined.VolumeOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Attiva microfono" else "Disattiva microfono",
                    tint = if (isMuted)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.graphicsLayer {
                        scaleX = micScale
                        scaleY = micScale
                    }
                )

                Column {
                    Text(
                        text = if (isMuted) "Attiva Microfono" else "Disattiva Microfono",
                        style = MaterialTheme.typography.labelLarge
                    )

                    // Subtitle with turn-taking hint
                    Text(
                        text = when {
                            isMuted -> "VAD in pausa"
                            turnState == TurnState.UserTurn -> "Parla liberamente"
                            turnState == TurnState.AgentTurn -> "AI sta rispondendo"
                            else -> "Sempre in ascolto"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Enhanced VAD and turn-taking indicators
        if (connectionStatus == ConnectionStatus.Connected && isChannelOpen) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // VAD status row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // VAD status indicator with pulse animation
                    val vadInfiniteTransition = rememberInfiniteTransition(label = "vadPulse")
                    val vadPulseAlpha by vadInfiniteTransition.animateFloat(
                        initialValue = if (!isMuted && turnState == TurnState.UserTurn) 0.4f else 1f,
                        targetValue = if (!isMuted && turnState == TurnState.UserTurn) 1f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "vadPulseAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (!isMuted)
                                    MaterialTheme.colorScheme.primary.copy(alpha = vadPulseAlpha)
                                else
                                    MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )

                    Text(
                        text = "VAD: ${if (!isMuted) "Attivo" else "In pausa"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Turn-taking guide
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // User turn indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (turnState == TurnState.UserTurn)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = "Tu",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (turnState == TurnState.UserTurn)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    // AI turn indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (turnState == TurnState.AgentTurn)
                                        MaterialTheme.colorScheme.secondary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (turnState == TurnState.AgentTurn)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Chat Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
}