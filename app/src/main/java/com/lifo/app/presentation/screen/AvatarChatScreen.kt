package com.lifo.app.presentation.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.app.integration.EmotionBridge
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import com.lifo.humanoid.api.HumanoidAvatarView
import com.lifo.humanoid.api.asHumanoidController
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.presentation.HumanoidViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Entry point for accessing LipSyncController from Hilt
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LipSyncControllerEntryPoint {
    fun lipSyncController(): LipSyncController
}

/**
 * Avatar Chat Screen - JARVIS/Amica Integration
 *
 * Combines the VRM avatar with the chat system:
 * - Layer 1: Fullscreen VRM avatar (background)
 * - Layer 2: Toggleable chat history (with blur effect)
 * - Layer 3: TopBar with history toggle
 * - Layer 4: Chat input
 *
 * The avatar reacts to chat events in real-time:
 * - Emotions sync automatically from voice system
 * - Lip sync ULTRA-SYNCHRONIZED with TTS audio playback
 * - Blur effect applied when history is visible
 */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarChatScreen(
    navigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    chatViewModel: ChatViewModel = hiltViewModel(),
    humanoidViewModel: HumanoidViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Chat state
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by chatViewModel.voiceState.collectAsStateWithLifecycle()
    val isVoiceActive by chatViewModel.isVoiceActive.collectAsStateWithLifecycle()
    val voiceEmotion by chatViewModel.voiceEmotion.collectAsStateWithLifecycle()

    // UI state
    var showHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Get LipSyncController from Hilt
    val lipSyncController = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            LipSyncControllerEntryPoint::class.java
        )
        entryPoint.lipSyncController()
    }

    // Integration components - now with synchronized speech
    val humanoidController = remember(humanoidViewModel, lipSyncController) {
        humanoidViewModel.asHumanoidController(lipSyncController)
    }

    val emotionBridge = remember(humanoidController) {
        EmotionBridge(humanoidController)
    }

    // Connect HumanoidController for synchronized lip-sync
    LaunchedEffect(humanoidController) {
        chatViewModel.attachHumanoidController(humanoidController)
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.detachHumanoidController()
        }
    }

    // Load session
    LaunchedEffect(sessionId) {
        sessionId?.let { chatViewModel.loadExistingSession(it) }
    }

    // Auto-scroll
    LaunchedEffect(chatState.messages.size) {
        if (showHistory && chatState.messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(chatState.messages.size - 1)
        }
    }

    // EVENT BRIDGE: Emotion sync
    LaunchedEffect(voiceEmotion) {
        emotionBridge.applyChatEmotion(voiceEmotion)
    }

    // NOTE: Lip-sync is now automatically handled by the SynchronizedSpeechController
    // No need for manual LaunchedEffect - it's all event-driven!

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        // LAYER 1: Avatar VRM
        HumanoidAvatarView(
            modifier = Modifier.fillMaxSize(),
            viewModel = humanoidViewModel,
            blurAmount = if (showHistory) 8f else 0f
        )

        // LAYER 2: Chat History
        AnimatedVisibility(
            visible = showHistory,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .statusBarsPadding()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, bottom = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatState.messages) { message ->
                        ChatBubble(
                            message = message,
                            isSpeaking = isVoiceActive,
                            voiceEmotion = voiceEmotion.name,
                            voiceLatency = voiceState.latencyMs,
                            onSpeak = { chatViewModel.speakMessage(message.id) },
                            onDelete = { chatViewModel.deleteMessage(message.id) },
                            onCopy = { /* TODO */ }
                        )
                    }
                }
            }
        }

        // LAYER 3: TopBar
        TopAppBar(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter),
            title = { Text("Amica") },
            navigationIcon = {
                IconButton(onClick = {
                    chatViewModel.stopSpeaking()
                    navigateBack()
                }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { showHistory = !showHistory }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = if (showHistory)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (showHistory)
                    MaterialTheme.colorScheme.surface
                else
                    Color.Transparent
            )
        )

        // LAYER 4: ChatInput
        ChatInput(
            value = chatState.inputText,
            onValueChange = { chatViewModel.updateInputText(it) },
            onSend = { chatViewModel.sendMessage(chatState.inputText) },
            isEnabled = !isVoiceActive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}
