package com.lifo.chat.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.lifo.chat.domain.model.VoiceState
import com.lifo.chat.domain.model.StreamingMessage
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lifo.mongo.repository.ChatMessage
import com.lifo.mongo.repository.ChatSession
import com.lifo.mongo.repository.MessageStatus
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.BlendMode
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

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

    // Auto-scroll ottimizzato con smooth scrolling
    LaunchedEffect(uiState.messages.size, uiState.streamingMessage) {
        val totalItems = uiState.messages.size + (if (uiState.streamingMessage != null) 1 else 0)
        if (totalItems > 0) {
            delay(50) // Delay ridotto per scrolling più veloce
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            ChatTopBar(
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
                // Smart suggestions con animazioni fluide
                AnimatedVisibility(
                    visible = !uiState.sessionStarted &&
                            uiState.messages.isEmpty() &&
                            uiState.streamingMessage == null &&
                            suggestions.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SmartSuggestionsRow(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.onEvent(ChatEvent.UseSuggestion(suggestion))
                        }
                    )
                }

                // Enhanced chat input
                ChatInput(
                    value = uiState.inputText,
                    onValueChange = { viewModel.onEvent(ChatEvent.UpdateInputText(it)) },
                    onSend = {
                        viewModel.onEvent(ChatEvent.SendMessage(uiState.inputText))
                    },
                    isEnabled = !uiState.isLoading && uiState.streamingMessage == null,
                    isStreaming = uiState.streamingMessage != null
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
            // Main content
            if (uiState.messages.isNotEmpty() || uiState.streamingMessage != null) {
                ChatMessagesList(
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
                // Enhanced empty state
                EnhancedEmptyState(
                    userName = userDisplayName,
                    suggestions = suggestions,
                    onSuggestionClick = { suggestion ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.onEvent(ChatEvent.UseSuggestion(suggestion))
                    }
                )
            }

            // Loading overlay con animazione migliorata
            AnimatedVisibility(
                visible = uiState.isLoading && uiState.messages.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.scale(1.2f),
                        strokeWidth = 3.dp
                    )
                }
            }

            // Error snackbar con design migliorato
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
                    shape = RoundedCornerShape(12.dp)
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
private fun ChatTopBar(
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Lifo sta parlando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
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
            // Auto-speak toggle
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
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }

            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "New Chat"
                )
            }

            IconButton(onClick = onExportToDiary) {
                Icon(
                    imageVector = Icons.Outlined.Book,
                    contentDescription = "Export to Diary"
                )
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
private fun SmartSuggestionsRow(
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionChip(
    suggestion: SmartSuggestion,
    onClick: () -> Unit
) {
    val categoryColor = when (suggestion.category) {
        SuggestionCategory.MOOD -> MaterialTheme.colorScheme.tertiary
        SuggestionCategory.PLANNING -> MaterialTheme.colorScheme.primary
        SuggestionCategory.WELLNESS -> MaterialTheme.colorScheme.secondary
        SuggestionCategory.REFLECTION -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    SuggestionChip(
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (suggestion.icon.isNotEmpty()) {
                    Text(
                        text = suggestion.icon,
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = suggestion.text,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        modifier = Modifier.animateContentSize(),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = categoryColor.copy(alpha = 0.1f),
            labelColor = categoryColor
        ),
        border = BorderStroke(
            width = 1.dp,
            color = categoryColor.copy(alpha = 0.3f)
        )
    )
}

@Composable
private fun ChatMessagesList(
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = messages,
            key = { it.id },
            contentType = { if (it.isUser) "user" else "ai" }
        ) { message ->
            EnhancedChatMessageItem(
                message = message,
                isSpeaking = voiceState.currentSpeakingMessageId == message.id,
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
                StreamingMessageItem(
                    content = streaming.content.toString()
                )
            }
        }
    }
}

@Composable
private fun EnhancedChatMessageItem(
    message: ChatMessage,
    isSpeaking: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        ChatBubble(
            message = message,
            onRetry = onRetry,
            onDelete = onDelete,
            onCopy = onCopy
        )

        // Voice indicator per messaggi AI
        if (!message.isUser) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 4.dp)
            ) {
                IconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) {
                            Icons.Filled.Stop
                        } else {
                            Icons.Outlined.VolumeUp
                        },
                        contentDescription = if (isSpeaking) "Stop" else "Ascolta",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSpeaking) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingMessageItem(
    content: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AvatarBubble()

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
                AiTypingIndicator()
            } else {
                Row {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    BlinkingCursor()
                }
            }
        }
    }
}

@Composable
private fun EnhancedEmptyState(
    userName: String?,
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated greeting
        AnimatedGreeting(userName = userName)

        Spacer(modifier = Modifier.height(48.dp))

        // Suggestion cards in grid
        Text(
            text = "Come posso aiutarti oggi?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        suggestions.chunked(2).forEach { rowSuggestions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowSuggestions.forEach { suggestion ->
                    SuggestionCard(
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
private fun AnimatedGreeting(userName: String?) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        ),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "scale"
    )

    Text(
        text = "Ciao ${userName ?: ""}",
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = 48.sp,
            letterSpacing = (-1).sp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            )
        ),
        fontWeight = FontWeight.Normal,
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionCard(
    suggestion: SmartSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(100.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
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
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Utility components
@Composable
private fun AvatarBubble() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "AI",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BlinkingCursor() {
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
        text = "▌",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun AiTypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.3f at 0 + (index * 150)
                        1f at 300 + (index * 150)
                        0.3f at 600 + (index * 150)
                    }
                ),
                label = "DotAlpha$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        shape = MaterialTheme.shapes.small
                    )
            )

            if (index < 2) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
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
                placeholder = { Text("es. Riflessione serale") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreate(title.ifEmpty { null })
                }
            ) {
                Text("Crea")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Chat Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
}