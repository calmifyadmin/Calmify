package com.lifo.chat.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.lifo.chat.domain.model.ChatEvent
import com.lifo.chat.domain.model.SmartSuggestion
import com.lifo.chat.domain.model.SuggestionCategory
import com.lifo.chat.presentation.viewmodel.VoiceState
import com.lifo.chat.domain.model.StreamingMessage
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lifo.mongo.repository.ChatMessage
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
@RequiresApi(Build.VERSION_CODES.S_V2)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatScreen(
    navigateBack: () -> Unit,
    navigateToWriteWithContent: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Get user info
    val userDisplayName = viewModel.getUserDisplayName()
    val userPhotoUrl = viewModel.getUserPhotoUrl()

    // Handle export navigation
    LaunchedEffect(uiState.exportedContent) {
        uiState.exportedContent?.let { content ->
            navigateToWriteWithContent(content)
        }
    }

    // Auto-scroll naturale con smooth scrolling
    LaunchedEffect(uiState.messages.size, uiState.streamingMessage) {
        val totalItems = uiState.messages.size + (if (uiState.streamingMessage != null) 1 else 0)
        if (totalItems > 0) {
            delay(100) // Delay breve per animazione fluida
            try {
                listState.animateScrollToItem(
                    index = totalItems - 1,
                    scrollOffset = 0
                )
            } catch (e: Exception) {
                Log.e("ChatScreen", "Error scrolling", e)
            }
        }
    }

    // Voice state animation
    val voiceIndicatorAlpha by animateFloatAsState(
        targetValue = if (voiceState.isSpeaking) 1f else 0f,
        animationSpec = tween(300),
        label = "voiceAlpha"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            NaturalChatTopBar(
                title = uiState.currentSession?.title ?: "AI Chat",
                scrollBehavior = scrollBehavior,
                userPhotoUrl = userPhotoUrl,
                voiceState = voiceState,
                onNavigateBack = {
                    if (!uiState.isNavigating) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navigateBack()
                    }
                },
                onNewChat = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.onEvent(ChatEvent.ShowNewSessionDialog)
                },
                onExportToDiary = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    uiState.currentSession?.let { session ->
                        viewModel.onEvent(ChatEvent.ExportToDiary(session.id))
                    }
                },
                onToggleAutoSpeak = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleAutoSpeak()
                }
            )
        },
        bottomBar = {
            Column {
                // Natural voice input con feedback emotivo
                ChatInput(
                    value = uiState.inputText,
                    onValueChange = { viewModel.onEvent(ChatEvent.UpdateInputText(it)) },
                    onSend = {
                        viewModel.onEvent(ChatEvent.SendMessage(uiState.inputText))
                    },
                    isEnabled = !uiState.isLoading && uiState.streamingMessage == null,
                    isStreaming = uiState.streamingMessage != null,
                    currentEmotion = voiceState.currentEmotion,
                    voiceNaturalness = voiceState.naturalness
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Voice wave background quando parla
            if (voiceState.isSpeaking) {
                VoiceWaveBackground(
                    emotion = voiceState.currentEmotion,
                    intensity = voiceState.emotionalIntensity,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(voiceIndicatorAlpha * 0.3f)
                )
            }

            // Main content
            if (uiState.messages.isNotEmpty() || uiState.streamingMessage != null) {
                NaturalChatMessagesList(
                    messages = uiState.messages,
                    streamingMessage = uiState.streamingMessage,
                    listState = listState,
                    voiceState = voiceState,
                    onRetry = { message ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(ChatEvent.RetryMessage(message.id))
                    },
                    onDelete = { message ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onEvent(ChatEvent.DeleteMessage(message.id))
                    },
                    onCopy = { message ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        copyToClipboard(context, message.content)
                    },
                    onSpeak = { message ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (voiceState.currentSpeakingMessageId == message.id) {
                            viewModel.onEvent(ChatEvent.StopSpeaking)
                        } else {
                            viewModel.onEvent(ChatEvent.SpeakMessage(message.id))
                        }
                    }
                )
            } else if (!uiState.isLoading) {
                // Enhanced empty state con animazioni naturali
                NaturalEmptyState(
                    userName = userDisplayName,
                    suggestions = suggestions,
                    onSuggestionClick = { suggestion ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(ChatEvent.UseSuggestion(suggestion))
                    }
                )
            }

            // Loading overlay naturale
            AnimatedVisibility(
                visible = uiState.isLoading && uiState.messages.isEmpty(),
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    NaturalLoadingIndicator()
                }
            }

            // Error snackbar elegante
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    action = {
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onEvent(ChatEvent.ClearError)
                            }
                        ) {
                            Text("Chiudi")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(uiState.error ?: "")
                }
            }
        }
    }

    // New session dialog
    if (uiState.showNewSessionDialog) {
        NewSessionDialog(
            onDismiss = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onEvent(ChatEvent.HideNewSessionDialog)
            },
            onCreate = { title ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onEvent(ChatEvent.CreateNewSession(title))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NaturalChatTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    userPhotoUrl: String?,
    voiceState: VoiceState,
    onNavigateBack: () -> Unit,
    onNewChat: () -> Unit,
    onExportToDiary: () -> Unit,
    onToggleAutoSpeak: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                AnimatedVisibility(
                    visible = voiceState.isSpeaking,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    VoiceStatusIndicator(
                        emotion = voiceState.currentEmotion,
                        naturalness = voiceState.naturalness
                    )
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
            // Voice quality indicator
            if (voiceState.isSpeaking) {
                VoiceQualityIndicator(
                    naturalness = voiceState.naturalness,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Auto-speak toggle con animazione
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconToggleButton(
                    checked = voiceState.autoSpeak,
                    onCheckedChange = { onToggleAutoSpeak() }
                ) {
                    Icon(
                        imageVector = if (voiceState.autoSpeak) {
                            Icons.Filled.VolumeUp
                        } else {
                            Icons.Outlined.VolumeOff
                        },
                        contentDescription = "Auto-speak",
                        tint = if (voiceState.autoSpeak) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // User profile
            userPhotoUrl?.let {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = userPhotoUrl,
                        error = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                        placeholder = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic)
                    ),
                    contentDescription = "User Profile",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                )
            }

            // Menu actions
            var showMenu by remember { mutableStateOf(false) }

            Box {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nuova Chat") },
                        onClick = {
                            onNewChat()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Esporta nel Diario") },
                        onClick = {
                            onExportToDiary()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Book, contentDescription = null)
                        }
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun VoiceStatusIndicator(
    emotion: String,
    naturalness: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Animated speaking icon
        val infiniteTransition = rememberInfiniteTransition(label = "speaking")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            modifier = Modifier
                .size(12.dp)
                .scale(scale),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = when (emotion) {
                "HAPPY", "EXCITED" -> "Lifo sta parlando con entusiasmo..."
                "SAD" -> "Lifo sta parlando con empatia..."
                "THOUGHTFUL" -> "Lifo sta riflettendo..."
                else -> "Lifo sta parlando..."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun VoiceQualityIndicator(
    naturalness: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = when {
            naturalness > 0.8f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            naturalness > 0.5f -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        },
        modifier = modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = naturalness,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = when {
                    naturalness > 0.8f -> MaterialTheme.colorScheme.primary
                    naturalness > 0.5f -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = "${(naturalness * 100).toInt()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun NaturalChatMessagesList(
    messages: List<ChatMessage>,
    streamingMessage: StreamingMessage?,
    listState: LazyListState,
    voiceState: VoiceState,
    onRetry: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onSpeak: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = messages,
            key = { it.id },
            contentType = { if (it.isUser) "user" else "ai" }
        ) { message ->
            NaturalChatMessageItem(
                message = message,
                isSpeaking = voiceState.currentSpeakingMessageId == message.id,
                voiceState = voiceState,
                onRetry = { onRetry(message) },
                onDelete = { onDelete(message) },
                onCopy = { onCopy(message) },
                onSpeak = { onSpeak(message) }
            )
        }

        streamingMessage?.let { streaming ->
            item(
                key = streaming.id,
                contentType = "streaming"
            ) {
                NaturalStreamingMessageItem(
                    content = streaming.content.toString(),
                    emotion = voiceState.currentEmotion
                )
            }
        }
    }
}

@Composable
private fun NaturalChatMessageItem(
    message: ChatMessage,
    isSpeaking: Boolean,
    voiceState: VoiceState,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
    ) {
        ChatBubble(
            message = message,
            onRetry = onRetry,
            onDelete = onDelete,
            onCopy = onCopy,
            onSpeak = onSpeak,
            isSpeaking = isSpeaking,
            currentEmotion = voiceState.currentEmotion,
            voiceNaturalness = voiceState.naturalness
        )
    }
}

@Composable
private fun NaturalStreamingMessageItem(
    content: String,
    emotion: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Animated avatar per streaming
        StreamingAvatarBubble(emotion = emotion)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lifo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (content.isEmpty()) {
                NaturalAiTypingIndicator(emotion = emotion)
            } else {
                Row {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    NaturalBlinkingCursor()
                }
            }
        }
    }
}

@Composable
private fun StreamingAvatarBubble(emotion: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Box(modifier = Modifier.size(32.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
        ) {}

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.Center)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = "AI",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun NaturalEmptyState(
    userName: String?,
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated natural greeting
        NaturalAnimatedGreeting(userName = userName)

        Spacer(modifier = Modifier.height(48.dp))

        // Descriptive text
        Text(
            text = "Come posso aiutarti oggi?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Natural suggestion cards
        suggestions.chunked(2).forEach { rowSuggestions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowSuggestions.forEach { suggestion ->
                    NaturalSuggestionCard(
                        suggestion = suggestion,
                        onClick = { onSuggestionClick(suggestion) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSuggestions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NaturalAnimatedGreeting(userName: String?) {
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
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .alpha(alpha)
            .offset(y = offsetY)
    ) {
        Text(
            text = "Ciao${userName?.let { " $it" } ?: ""}",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 48.sp,
                letterSpacing = (-1).sp
            ),
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

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
            fontSize = 32.sp,
            modifier = Modifier.graphicsLayer {
                rotationZ = waveRotation
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NaturalSuggestionCard(
    suggestion: SmartSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "scale"
    )

    Card(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = modifier
            .height(110.dp)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            if (suggestion.icon.isNotEmpty()) {
                Text(
                    text = suggestion.icon,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(200)
            isPressed = false
        }
    }
}

@Composable
private fun NaturalLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.5f at 0
                1f at 400
                0.5f at 800
            }
        ),
        label = "scale1"
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.5f at 200
                1f at 600
                0.5f at 1000
            }
        ),
        label = "scale2"
    )

    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.5f at 400
                1f at 800
                0.5f at 1200
            }
        ),
        label = "scale3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(scale1)
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(scale2)
                .background(
                    MaterialTheme.colorScheme.secondary,
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .scale(scale3)
                .background(
                    MaterialTheme.colorScheme.tertiary,
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun VoiceWaveBackground(
    emotion: String,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waves")

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (emotion) {
                    "EXCITED" -> 2000
                    "CALM", "THOUGHTFUL" -> 6000
                    else -> 4000
                },
                easing = LinearEasing
            )
        ),
        label = "wavePhase"
    )

    // Get the colors outside the Canvas scope
    val waveColor = when (emotion) {
        "HAPPY", "EXCITED" -> MaterialTheme.colorScheme.tertiary
        "SAD" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }.copy(alpha = 0.1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val amplitude = 50f * intensity

        val path = Path()
        path.moveTo(0f, height / 2)

        for (x in 0..width.toInt()) {
            val y = height / 2 + amplitude * sin(x * 0.01f + wavePhase)
            path.lineTo(x.toFloat(), y)
        }

        path.lineTo(width, height)
        path.lineTo(0f, height)
        path.close()

        drawPath(
            path = path,
            color = waveColor  // Use the pre-calculated color
        )
    }
}

@Composable
private fun NaturalAiTypingIndicator(emotion: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing$index")

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0f at 0 + (index * 100)
                        -8f at 150 + (index * 100)
                        0f at 300 + (index * 100)
                    }
                ),
                label = "offsetY$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offsetY.dp)
                    .background(
                        color = when (emotion) {
                            "EXCITED" -> MaterialTheme.colorScheme.tertiary
                            "THOUGHTFUL" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        }.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun NaturalBlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Text(
        text = "│",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        modifier = Modifier.padding(start = 1.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Nuova Conversazione")
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titolo (opzionale)") },
                placeholder = { Text("es. Chat del ${java.time.LocalDate.now()}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreate(title.ifEmpty { null })
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Crea")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Annulla")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Chat Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
}