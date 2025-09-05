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
import com.lifo.chat.data.realtime.LiveChatState
import com.lifo.chat.data.realtime.PushToTalkState
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.PTTState
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
    
    // Live Chat state - now using dedicated LiveChatViewModel
    val liveChatUiState by liveChatViewModel.uiState.collectAsStateWithLifecycle()
    val liveChatState by viewModel.liveChatState.collectAsStateWithLifecycle()
    val pushToTalkState by viewModel.pushToTalkState.collectAsStateWithLifecycle()
    val realtimeSessionState by viewModel.realtimeSessionState.collectAsStateWithLifecycle()
    
    // Track live chat mode - now defaults to true (globe-based voice)
    var isLiveChatMode by remember { mutableStateOf(true) }
    
    // Permission handling for LiveChat mode
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            liveChatViewModel.onAudioPermissionGranted()
        } else {
            liveChatViewModel.onAudioPermissionDenied()
        }
    }
    
    // Load existing session if sessionId is provided and start live chat
    LaunchedEffect(sessionId) {
        sessionId?.let {
            viewModel.loadExistingSession(it)
        }
        // Auto-start live chat mode since it's now the default voice system
        if (isLiveChatMode) {
            viewModel.startLiveChat()
        }
    }

    // Request audio permission when entering LiveChat mode
    LaunchedEffect(isLiveChatMode) {
        if (isLiveChatMode && !liveChatUiState.hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    // Auto-connect when permission is granted in LiveChat mode  
    LaunchedEffect(liveChatUiState.hasAudioPermission, isLiveChatMode) {
        if (isLiveChatMode && liveChatUiState.hasAudioPermission && 
            liveChatUiState.connectionStatus == ConnectionStatus.Disconnected) {
            liveChatViewModel.connectToRealtime()
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
        // Liquid wave background covering the ENTIRE screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GeminiLiquidVisualizer(
                isSpeaking = when {
                    // Live chat mode - react only when AI is speaking
                    isLiveChatMode && liveChatUiState.hasAudioPermission -> {
                        liveChatUiState.aiEmotion == AIEmotion.Speaking || 
                        liveChatUiState.audioLevel > 0.1f
                        // Removed: liveChatUiState.isRecording (user speaking)
                    }
                    // Traditional mode - react only when AI is speaking/generating
                    !isLiveChatMode -> {
                        voiceState.isSpeaking || 
                        isVoiceActive ||
                        uiState.isLoading ||
                        uiState.streamingMessage != null
                        // These are all AI-related activities, not user input
                    }
                    // Default case
                    else -> false
                },
                modifier = Modifier.fillMaxSize(),
                primaryColor = MaterialTheme.colorScheme.primary,
                secondaryColor = MaterialTheme.colorScheme.tertiary,
                backgroundColor = MaterialTheme.colorScheme.surface
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
                    isLiveChatMode = isLiveChatMode,
                    liveChatState = liveChatState,
                    onNavigateBack = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isLiveChatMode) {
                            viewModel.endLiveChat()
                            isLiveChatMode = false
                        }
                        navigateBack()
                    },
                    onStopVoice = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.stopSpeaking()
                    },
                    onToggleLiveChat = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isLiveChatMode) {
                            isLiveChatMode = true
                            viewModel.startLiveChat()
                        } else {
                            isLiveChatMode = false
                            viewModel.endLiveChat()
                        }
                    }
                )
            },
            containerColor = Color.Transparent,
        bottomBar = {
            Column {
                // Global voice indicator - appears above input when speaking
                AnimatedVisibility(
                    visible = isVoiceActive,
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

                // Live Chat push-to-talk interface - simplified version like in LiveChatScreen
                if (isLiveChatMode && liveChatUiState.hasAudioPermission) {
                    LiveChatPushToTalkSection(
                        connectionStatus = liveChatUiState.connectionStatus,
                        pushToTalkState = liveChatUiState.pushToTalkState,
                        isRecording = liveChatUiState.isRecording,
                        recordingDuration = 0L, // TODO: Add recording duration 
                        error = liveChatUiState.error,
                        onPushToTalkPressed = liveChatViewModel::onPushToTalkPressed,
                        onPushToTalkReleased = liveChatViewModel::onPushToTalkReleased,
                        onRetryConnection = liveChatViewModel::retryConnection,
                        onClearError = liveChatViewModel::clearError
                    )
                } else {
                    // Traditional chat input
                    ChatInput(
                        value = uiState.inputText,
                        onValueChange = viewModel::updateInputText,
                        onSend = {
                            viewModel.sendMessage(uiState.inputText)
                        },
                        isEnabled = !uiState.isLoading && uiState.streamingMessage == null,
                        isStreaming = uiState.streamingMessage != null,
                        currentEmotion = voiceEmotion.name,
                        voiceNaturalness = voiceState.naturalness
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

            // Empty state handling when in live chat mode or new sessions
            if (uiState.messages.isEmpty() && uiState.streamingMessage == null) {
                if (isLiveChatMode) {
                    if (!liveChatUiState.hasAudioPermission) {
                        // Show permission required screen
                        PermissionRequiredContent(
                            onRetryPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    // Note: No globe visualizer here anymore - using background GeminiLiquidVisualizer instead
                } else if (uiState.currentSession == null) {
                    // Show welcome screen only for new sessions (no existing session loaded)
                    NaturalAnimatedEmptyState(
                        userName = userDisplayName,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
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
    isLiveChatMode: Boolean,
    liveChatState: LiveChatState,
    onNavigateBack: () -> Unit,
    onStopVoice: () -> Unit,
    onToggleLiveChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (isLiveChatMode) "AI Voice Chat" else "AI Chat")

                // Live Chat status chip
                if (isLiveChatMode) {
                    val chipColor = when (liveChatState) {
                        is LiveChatState.Idle -> MaterialTheme.colorScheme.outline
                        is LiveChatState.Connecting -> MaterialTheme.colorScheme.tertiary
                        is LiveChatState.Connected -> MaterialTheme.colorScheme.primary
                        is LiveChatState.Recording -> MaterialTheme.colorScheme.error
                        is LiveChatState.Processing -> MaterialTheme.colorScheme.secondary
                        is LiveChatState.Speaking -> MaterialTheme.colorScheme.primaryContainer
                        is LiveChatState.Error -> MaterialTheme.colorScheme.error
                    }
                    
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = chipColor.copy(alpha = 0.2f),
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
                                    .background(chipColor, shape = MaterialTheme.shapes.small)
                            )
                            Text(
                                text = when (liveChatState) {
                                    is LiveChatState.Idle -> "Idle"
                                    is LiveChatState.Connecting -> "Connecting..."
                                    is LiveChatState.Connected -> "Live"
                                    is LiveChatState.Recording -> "Recording"
                                    is LiveChatState.Processing -> "Processing"
                                    is LiveChatState.Speaking -> "Speaking"
                                    is LiveChatState.Error -> "Error"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else if (voiceEnabled) {
                    // Traditional voice status chip
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
            // Traditional Chat toggle button (now secondary mode)
            IconButton(
                onClick = onToggleLiveChat,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (!isLiveChatMode) 
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) 
                    else Color.Transparent,
                    contentColor = if (!isLiveChatMode) 
                        MaterialTheme.colorScheme.secondary 
                    else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = if (isLiveChatMode) Icons.Default.VolumeUp else Icons.Outlined.VolumeOff,
                    contentDescription = if (isLiveChatMode) "Switch to Text Chat" else "Switch to Voice Chat"
                )
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
private fun LiveChatInterface(
    liveChatState: LiveChatState,
    pushToTalkState: PushToTalkState,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onCancelPushToTalk: () -> Unit,
    onClearError: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), // Trasparenza
        shadowElevation = 0.dp // Rimossa ombra per trasparenza
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status display
            LiveChatStatusDisplay(liveChatState)
            
            // Push-to-talk button with effects
            PushToTalkButton(
                isPressed = pushToTalkState.isRecording,
                onPressStart = onPushToTalkPressed,
                onPressEnd = onPushToTalkReleased,
                isEnabled = when (liveChatState) {
                    is LiveChatState.Connected,
                    is LiveChatState.Recording -> true
                    else -> false
                },
                emotion = when (liveChatState) {
                    is LiveChatState.Recording -> ChatEmotion.ANXIOUS
                    is LiveChatState.Processing -> ChatEmotion.CALM
                    is LiveChatState.Speaking -> ChatEmotion.HAPPY
                    is LiveChatState.Error -> ChatEmotion.SAD
                    else -> ChatEmotion.NEUTRAL
                }
            )
            
            // Instructions
            Text(
                text = when (liveChatState) {
                    is LiveChatState.Idle -> "Tocca il pulsante per iniziare la live chat"
                    is LiveChatState.Connecting -> "Connessione in corso..."
                    is LiveChatState.Connected -> "Tieni premuto per parlare"
                    is LiveChatState.Recording -> "Rilascia per inviare"
                    is LiveChatState.Processing -> "L'AI sta elaborando..."
                    is LiveChatState.Speaking -> "L'AI sta parlando..."
                    is LiveChatState.Error -> "Errore: ${liveChatState.message}"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = when (liveChatState) {
                    is LiveChatState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            
            // Error handling
            if (liveChatState is LiveChatState.Error) {
                TextButton(onClick = onClearError) {
                    Text("Riprova")
                }
            }
        }
    }
}

@Composable
private fun LiveChatStatusDisplay(liveChatState: LiveChatState) {
    when (liveChatState) {
        is LiveChatState.Recording -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔴 Registrazione: ${liveChatState.duration / 1000}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                
                // Audio level visualization
                VoiceWaveform(
                    audioLevel = liveChatState.audioLevel,
                    isActive = true,
                    modifier = Modifier.height(20.dp).width(60.dp)
                )
            }
        }
        
        is LiveChatState.Speaking -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "💬 ${liveChatState.transcript}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                VoiceWaveform(
                    audioLevel = liveChatState.audioLevel,
                    isActive = true,
                    modifier = Modifier.height(24.dp).width(120.dp)
                )
            }
        }
        
        is LiveChatState.Processing -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = liveChatState.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        else -> {
            // Default status display
            Box(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable  
private fun LiveChatPushToTalkSection(
    connectionStatus: ConnectionStatus,
    pushToTalkState: PTTState,
    isRecording: Boolean,
    recordingDuration: Long,
    error: String?,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
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

        // Instructions
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
                            if (connectionStatus == ConnectionStatus.Connected) {
                                onPushToTalkPressed()
                                tryAwaitRelease()
                                onPushToTalkReleased()
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
    onRetryPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = "Audio Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Voice Chat needs microphone access to enable voice conversations with AI.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onRetryPermission,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Grant Permission")
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Chat Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
}