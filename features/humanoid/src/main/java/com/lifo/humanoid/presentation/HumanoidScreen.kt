package com.lifo.humanoid.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.humanoid.domain.model.Emotion
import com.lifo.humanoid.presentation.components.FilamentView

/**
 * Main Humanoid Avatar screen.
 * Displays the 3D avatar with controls and status indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanoidScreen(
    navigateBack: () -> Unit,
    viewModel: HumanoidViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
    val vrmModelData by viewModel.vrmModelData.collectAsStateWithLifecycle()
    val vrmExtensions by viewModel.vrmExtensions.collectAsStateWithLifecycle()
    val blendShapeWeights by viewModel.blendShapeWeights.collectAsStateWithLifecycle()

    // Update blend shapes every frame
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateBlendShapes(0.016f) // ~60 FPS
            kotlinx.coroutines.delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Humanoid Avatar") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetAvatar() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset avatar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingState()
                }
                uiState.error != null -> {
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadDefaultAvatar() }
                    )
                }
                uiState.avatarLoaded -> {
                    AvatarContent(
                        vrmModelData = vrmModelData?.first,
                        vrmExtensions = vrmExtensions,
                        blendShapeWeights = blendShapeWeights,
                        avatarState = avatarState,
                        onEmotionChange = { viewModel.setEmotion(it) },
                        onSpeakingChange = { viewModel.setSpeaking(it) },
                        onListeningChange = { viewModel.setListening(it) },
                        onVisionToggle = { viewModel.setVisionEnabled(!avatarState.visionEnabled) }
                    )
                }
                else -> {
                    EmptyState(onLoad = { viewModel.loadDefaultAvatar() })
                }
            }
        }
    }
}

@Composable
private fun AvatarContent(
    vrmModelData: java.nio.ByteBuffer?,
    vrmExtensions: com.lifo.humanoid.data.vrm.VrmExtensions?,
    blendShapeWeights: Map<String, Float>,
    avatarState: com.lifo.humanoid.domain.model.AvatarState,
    onEmotionChange: (Emotion) -> Unit,
    onSpeakingChange: (Boolean) -> Unit,
    onListeningChange: (Boolean) -> Unit,
    onVisionToggle: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 3D Avatar View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A1A))
        ) {
            FilamentView(
                modifier = Modifier.fillMaxSize(),
                vrmModelData = vrmModelData,
                vrmExtensions = vrmExtensions,
                blendShapeWeights = blendShapeWeights
            )

            // Status indicator overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (avatarState.isSpeaking) {
                    StatusChip(
                        text = "Speaking",
                        icon = Icons.Default.RecordVoiceOver,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (avatarState.isListening) {
                    StatusChip(
                        text = "Listening",
                        icon = Icons.Default.Mic,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (avatarState.visionEnabled) {
                    StatusChip(
                        text = "Vision ON",
                        icon = Icons.Default.Videocam,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Control Panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Emotion: ${avatarState.emotion.getName()}",
                    style = MaterialTheme.typography.titleMedium
                )

                // Emotion selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmotionButton("😊", "Happy") { onEmotionChange(Emotion.Happy()) }
                    EmotionButton("😢", "Sad") { onEmotionChange(Emotion.Sad()) }
                    EmotionButton("😠", "Angry") { onEmotionChange(Emotion.Angry()) }
                    EmotionButton("😲", "Surprised") { onEmotionChange(Emotion.Surprised()) }
                    EmotionButton("🤔", "Thinking") { onEmotionChange(Emotion.Thinking()) }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onSpeakingChange(!avatarState.isSpeaking) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (avatarState.isSpeaking) "Stop Speaking" else "Speak")
                    }

                    FilledTonalButton(
                        onClick = { onListeningChange(!avatarState.isListening) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (avatarState.isListening) "Stop Listening" else "Listen")
                    }

                    IconButton(onClick = onVisionToggle) {
                        Icon(
                            imageVector = if (avatarState.visionEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle vision"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionButton(
    emoji: String,
    name: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.size(60.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text("Loading avatar...")
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Error loading avatar",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState(onLoad: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No avatar loaded",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onLoad) {
                Text("Load Default Avatar")
            }
        }
    }
}
