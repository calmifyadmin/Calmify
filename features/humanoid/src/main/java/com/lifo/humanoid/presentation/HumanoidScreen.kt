package com.lifo.humanoid.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.domain.model.Emotion
import com.lifo.humanoid.presentation.components.FilamentView

/**
 * Main Humanoid Avatar screen.
 * Displays the 3D avatar with comprehensive controls for:
 * - Emotions
 * - Animations (VRMA)
 * - Lip-sync
 * - Blink control
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HumanoidScreen(
    navigateBack: () -> Unit,
    viewModel: HumanoidViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
    val vrmModelData by viewModel.vrmModelData.collectAsStateWithLifecycle()
    val vrmExtensions by viewModel.vrmExtensions.collectAsStateWithLifecycle()
    val blendShapeWeights by viewModel.blendShapeWeights.collectAsStateWithLifecycle()
    val availableAnimations by viewModel.availableAnimations.collectAsStateWithLifecycle()
    val currentAnimation by viewModel.currentAnimation.collectAsStateWithLifecycle()
    val isBlinking by viewModel.isBlinking.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isIdleRotationActive by viewModel.isIdleRotationActive.collectAsStateWithLifecycle()
    val currentIdleAnimation by viewModel.currentIdleAnimation.collectAsStateWithLifecycle()

    // State for control panel visibility
    var showControlPanel by remember { mutableStateOf(true) }

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
                    IconButton(onClick = { showControlPanel = !showControlPanel }) {
                        Icon(
                            imageVector = if (showControlPanel) Icons.Default.Settings else Icons.Outlined.Settings,
                            contentDescription = if (showControlPanel) "Hide controls" else "Show controls"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleDebugMode() }) {
                        Icon(
                            imageVector = if (uiState.debugMode) Icons.Default.BugReport else Icons.Default.Info,
                            contentDescription = "Toggle debug"
                        )
                    }
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
                        availableAnimations = availableAnimations,
                        currentAnimation = currentAnimation?.name,
                        isBlinking = isBlinking,
                        isSpeaking = isSpeaking,
                        isIdleRotationActive = isIdleRotationActive,
                        currentIdleAnimation = currentIdleAnimation?.displayName,
                        debugMode = uiState.debugMode,
                        showControlPanel = showControlPanel,
                        onEmotionChange = { viewModel.setEmotion(it) },
                        onSpeakingChange = { viewModel.setSpeaking(it) },
                        onListeningChange = { viewModel.setListening(it) },
                        onVisionToggle = { viewModel.setVisionEnabled(!avatarState.visionEnabled) },
                        onPlayAnimation = { viewModel.playAnimation(it) },
                        onStopAnimation = { viewModel.stopAnimation() },
                        onTriggerBlink = { viewModel.triggerBlink() },
                        onSpeakText = { text, duration -> viewModel.speakText(text, duration) },
                        onStopSpeaking = { viewModel.stopSpeaking() },
                        onToggleIdleRotation = { viewModel.toggleIdleRotation() },
                        onModelLoaded = { renderer, asset, nodeNames ->
                            viewModel.onModelLoaded(renderer, asset, nodeNames)
                        },
                        onBeforeCleanup = {
                            viewModel.stopAllControllersBeforeCleanup()
                        }
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
    availableAnimations: List<VrmaAnimationLoader.AnimationAsset>,
    currentAnimation: String?,
    isBlinking: Boolean,
    isSpeaking: Boolean,
    isIdleRotationActive: Boolean,
    currentIdleAnimation: String?,
    debugMode: Boolean,
    showControlPanel: Boolean,
    onEmotionChange: (Emotion) -> Unit,
    onSpeakingChange: (Boolean) -> Unit,
    onListeningChange: (Boolean) -> Unit,
    onVisionToggle: () -> Unit,
    onPlayAnimation: (VrmaAnimationLoader.AnimationAsset) -> Unit,
    onStopAnimation: () -> Unit,
    onTriggerBlink: () -> Unit,
    onSpeakText: (String, Long) -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleIdleRotation: () -> Unit,
    onModelLoaded: (com.lifo.humanoid.rendering.FilamentRenderer, com.google.android.filament.gltfio.FilamentAsset, List<String>) -> Unit,
    onBeforeCleanup: () -> Unit
) {
    var lipSyncText by remember { mutableStateOf("Hello, I am your AI assistant!") }
    var speechDuration by remember { mutableStateOf(3000L) }

    // Track panel visibility transition state
    val panelVisibilityState = remember { MutableTransitionState(showControlPanel) }
    panelVisibilityState.targetState = showControlPanel

    // Derive if layout is currently animating (panel showing/hiding)
    val isLayoutChanging = panelVisibilityState.isIdle.not()

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
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface  // ← Questo colore si vedrà sotto l'avatar!
            ) {
            FilamentView(
                modifier = Modifier.fillMaxSize(),
                vrmModelData = vrmModelData,
                vrmExtensions = vrmExtensions,
                blendShapeWeights = blendShapeWeights,
                isLayoutChanging = isLayoutChanging,
                onModelLoaded = onModelLoaded,
                onBeforeCleanup = onBeforeCleanup
            )
            }
            // Status indicator overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isBlinking) {
                    StatusChip(
                        text = "Blinking",
                        icon = Icons.Default.Visibility,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (isSpeaking) {
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
                if (currentAnimation != null) {
                    StatusChip(
                        text = currentAnimation,
                        icon = Icons.Default.PlayArrow,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isIdleRotationActive) {
                    StatusChip(
                        text = "Idle: ${currentIdleAnimation ?: "rotating"}",
                        icon = Icons.Default.Loop,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Debug panel
            if (debugMode) {
                DebugPanel(
                    blendShapeWeights = blendShapeWeights,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
        }

        // Control Panel (scrollable) - uses transition state for layout change tracking
        AnimatedVisibility(
            visibleState = panelVisibilityState,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Emotion section
                    Text(
                        text = "Emotion: ${avatarState.emotion.getName()}",
                        style = MaterialTheme.typography.titleMedium
                    )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmotionButton("😊", "Happy") { onEmotionChange(Emotion.Happy()) }
                    EmotionButton("😢", "Sad") { onEmotionChange(Emotion.Sad()) }
                    EmotionButton("😠", "Angry") { onEmotionChange(Emotion.Angry()) }
                    EmotionButton("😲", "Surprised") { onEmotionChange(Emotion.Surprised()) }
                    EmotionButton("🤔", "Thinking") { onEmotionChange(Emotion.Thinking()) }
                    EmotionButton("😌", "Calm") { onEmotionChange(Emotion.Calm()) }
                    EmotionButton("😐", "Neutral") { onEmotionChange(Emotion.Neutral) }
                }

                HorizontalDivider()

                // Animation section
                Text(
                    text = "Animations",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableAnimations.forEach { animation ->
                        AnimationButton(
                            name = animation.displayName,
                            isPlaying = currentAnimation == animation.displayName,
                            onClick = {
                                if (currentAnimation == animation.displayName) {
                                    onStopAnimation()
                                } else {
                                    onPlayAnimation(animation)
                                }
                            }
                        )
                    }
                }

                HorizontalDivider()

                // Lip-Sync section
                Text(
                    text = "Lip-Sync Test",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = lipSyncText,
                    onValueChange = { lipSyncText = it },
                    label = { Text("Text to speak") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duration: ${speechDuration}ms", modifier = Modifier.width(120.dp))
                    Slider(
                        value = speechDuration.toFloat(),
                        onValueChange = { speechDuration = it.toLong() },
                        valueRange = 1000f..10000f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSpeakText(lipSyncText, speechDuration) },
                        modifier = Modifier.weight(1f),
                        enabled = !isSpeaking
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Test Lip-Sync")
                    }

                    OutlinedButton(
                        onClick = onStopSpeaking,
                        modifier = Modifier.weight(1f),
                        enabled = isSpeaking
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                HorizontalDivider()

                // Quick actions
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onTriggerBlink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Blink")
                    }

                    FilledTonalButton(
                        onClick = { onListeningChange(!avatarState.isListening) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (avatarState.isListening) "Stop" else "Listen")
                    }

                    IconButton(onClick = onVisionToggle) {
                        Icon(
                            imageVector = if (avatarState.visionEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle vision"
                        )
                    }
                }

                HorizontalDivider()

                // Idle Rotation section
                Text(
                    text = "Idle Rotation",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isIdleRotationActive) {
                        Button(
                            onClick = onToggleIdleRotation,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Stop Idle Rotation")
                        }
                        Text(
                            text = currentIdleAnimation ?: "...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        OutlinedButton(
                            onClick = onToggleIdleRotation,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Loop, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Start Idle Rotation")
                        }
                    }
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
        modifier = Modifier.size(56.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun AnimationButton(
    name: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    if (isPlaying) {
        Button(onClick = onClick) {
            Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
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
private fun DebugPanel(
    blendShapeWeights: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Active Blend Shapes",
                style = MaterialTheme.typography.labelMedium
            )

            val activeWeights = blendShapeWeights.filter { it.value > 0.01f }
            if (activeWeights.isEmpty()) {
                Text(
                    text = "None",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activeWeights.entries.take(8).forEach { (name, weight) ->
                    Row(
                        modifier = Modifier.width(150.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%.2f".format(weight),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LinearProgressIndicator(
                        progress = { weight },
                        modifier = Modifier
                            .width(150.dp)
                            .height(4.dp),
                    )
                }
            }
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
