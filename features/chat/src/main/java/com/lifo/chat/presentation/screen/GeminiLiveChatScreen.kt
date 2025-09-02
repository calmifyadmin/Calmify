package com.lifo.chat.presentation.screen

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.lifo.chat.presentation.viewmodel.GeminiLiveChatViewModel
import com.lifo.chat.presentation.components.GeminiLiquidVisualizer
import com.lifo.chat.BuildConfig
import android.os.Build

/**
 * Screen for Gemini Live API demo
 * Uses the same beautiful liquid visualizer but with Gemini Live WebSocket API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiLiveChatScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeminiLiveChatViewModel = hiltViewModel()
) {
    Log.d("GeminiLiveScreen", "🏠 GeminiLiveChatScreen composing...")
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTranscript by viewModel.currentTranscript.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    Log.d("GeminiLiveScreen", "📱 UI state: connectionStatus=${uiState.connectionStatus}, hasPermission=${uiState.hasAudioPermission}")

    // Permission handler
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // Request permission on first composition if not granted
    LaunchedEffect(Unit) {
        if (!uiState.hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-connect when permission is granted
    LaunchedEffect(uiState.hasAudioPermission) {
        if (uiState.hasAudioPermission && uiState.connectionStatus == ConnectionStatus.Disconnected) {
            viewModel.connectToRealtime()
        }
    }

    // Full-screen background
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Liquid wave background covering the ENTIRE screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && uiState.hasAudioPermission) {
            GeminiLiquidVisualizer(
                isSpeaking = uiState.aiEmotion == AIEmotion.Speaking || uiState.audioLevel > 0.1f,
                modifier = Modifier.fillMaxSize(),
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
                GeminiLiveTopBar(
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
            // Main content
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Add top padding manually
                Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
                
                // Center space for status and transcript
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (!uiState.hasAudioPermission) {
                        PermissionRequiredContent(
                            onRetryPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    } else {
                        // Status and transcript display
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            // Connection status
                            Text(
                                text = when {
                                    uiState.connectionStatus != ConnectionStatus.Connected -> "Connecting to Gemini Live..."
                                    uiState.aiEmotion == AIEmotion.Speaking || uiState.audioLevel > 0.1f -> "🎙️ AI is speaking"
                                    uiState.isRecording -> "🎧 Listening..."
                                    else -> "✨ Ready to chat with Gemini Live"
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
                                            text = "🤖 Gemini Live",
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
                            if (BuildConfig.DEBUG && uiState.connectionStatus == ConnectionStatus.Connected) {
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
                GeminiLivePushToTalkSection(
                    connectionStatus = uiState.connectionStatus,
                    pushToTalkState = uiState.pushToTalkState,
                    isRecording = uiState.isRecording,
                    error = uiState.error,
                    onPushToTalkPressed = viewModel::onPushToTalkPressed,
                    onPushToTalkReleased = viewModel::onPushToTalkReleased,
                    onCancelPushToTalk = viewModel::cancelPushToTalk,
                    onRetryConnection = viewModel::retryConnection,
                    onClearError = viewModel::clearError,
                    onSendTestMessage = { viewModel.sendTextMessage("Hello Gemini Live!") }
                )
                
                // Add bottom padding
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiLiveTopBar(
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
                    text = "🚀 Gemini Live POC",
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
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun ConnectionStatusChip(connectionStatus: ConnectionStatus) {
    val (statusText, statusColor) = when (connectionStatus) {
        ConnectionStatus.Disconnected -> "Disconnected" to Color.Gray
        ConnectionStatus.Connecting -> "Connecting..." to Color(0xFFFFA500)
        ConnectionStatus.Connected -> "Live" to Color(0xFF34A853)
        ConnectionStatus.Error -> "Error" to Color(0xFFFF4444)
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
private fun GeminiLivePushToTalkSection(
    connectionStatus: ConnectionStatus,
    pushToTalkState: PTTState,
    isRecording: Boolean,
    error: String?,
    onPushToTalkPressed: () -> Unit,
    onPushToTalkReleased: () -> Unit,
    onCancelPushToTalk: () -> Unit,
    onRetryConnection: () -> Unit,
    onClearError: () -> Unit,
    onSendTestMessage: () -> Unit
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

        // Instructions
        Text(
            text = when {
                connectionStatus == ConnectionStatus.Disconnected -> "Starting Gemini Live..."
                connectionStatus == ConnectionStatus.Connecting -> "Connecting to Gemini Live API..."
                connectionStatus == ConnectionStatus.Error -> "Connection error"
                connectionStatus == ConnectionStatus.Connected && pushToTalkState == PTTState.Idle -> "Hold to talk with Gemini Live"
                pushToTalkState == PTTState.Listening -> "Release to send to AI"
                pushToTalkState == PTTState.Processing -> "Gemini is thinking..."
                else -> "Ready"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Control buttons row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Test text message button (debug)
            if (BuildConfig.DEBUG && connectionStatus == ConnectionStatus.Connected) {
                OutlinedButton(
                    onClick = onSendTestMessage,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Test", fontSize = 12.sp)
                }
            }
            
            // Main push-to-talk button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .pointerInput(connectionStatus) {
                        detectTapGestures(
                            onPress = {
                                if (connectionStatus == ConnectionStatus.Connected) {
                                    Log.d("GeminiLiveScreen", "🖱️ Button PRESSED")
                                    onPushToTalkPressed()
                                    tryAwaitRelease()
                                    Log.d("GeminiLiveScreen", "🖱️ Button RELEASED")
                                    onPushToTalkReleased()
                                } else {
                                    Log.w("GeminiLiveScreen", "🖱️ Button touched but not connected")
                                }
                            }
                        )
                    }
                    .background(
                        color = when {
                            connectionStatus != ConnectionStatus.Connected -> MaterialTheme.colorScheme.surfaceVariant
                            isRecording -> Color(0xFFFF4444)
                            else -> Color(0xFF4285F4) // Google Blue for Gemini
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Push to talk with Gemini Live",
                    modifier = Modifier.size(36.dp),
                    tint = when {
                        connectionStatus != ConnectionStatus.Connected -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.White
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    onRetryPermission: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(32.dp)
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
            text = "Gemini Live needs microphone access to enable voice conversations with AI.",
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