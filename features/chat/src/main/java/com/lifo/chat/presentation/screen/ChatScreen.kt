package com.lifo.chat.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.chat.domain.model.ChatEvent
import com.lifo.chat.domain.model.ChatMessage
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatScreen(
    navigateBack: () -> Unit,
    navigateToWriteWithContent: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Smooth auto-scroll to bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(100) // Small delay for better UX
            listState.animateScrollToItem(
                index = uiState.messages.size - 1,
                scrollOffset = 0
            )
        }
    }

    // Create or load session on startup
    LaunchedEffect(Unit) {
        if (uiState.currentSession == null && uiState.sessions.isEmpty()) {
            viewModel.onEvent(ChatEvent.CreateNewSession())
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
                onNavigateBack = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navigateBack()
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
                }
            )
        },
        bottomBar = {
            Column {
                // Suggestion buttons sopra l'input bar
                AnimatedVisibility(
                    visible = !uiState.sessionStarted && uiState.messages.isEmpty() && !uiState.isStreamingResponse,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionButton(
                            text = "Aiutami a pianificare",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onEvent(ChatEvent.UpdateInputText("Aiutami a pianificare "))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        SuggestionButton(
                            text = "Parliamo della giornata",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onEvent(ChatEvent.UpdateInputText("Parliamo della giornata "))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Chat input
                ChatInput(
                    value = uiState.inputText,
                    onValueChange = { viewModel.onEvent(ChatEvent.UpdateInputText(it)) },
                    onSend = {
                        viewModel.onEvent(ChatEvent.SendMessage(uiState.inputText))
                    },
                    isEnabled = !uiState.isLoading,
                    isStreaming = uiState.isStreamingResponse
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
            AnimatedContent(
                targetState = uiState.sessionStarted || uiState.messages.isNotEmpty() || uiState.isStreamingResponse,
                transitionSpec = {
                    if (targetState) {
                        fadeIn(animationSpec = tween(300)) with
                                fadeOut(animationSpec = tween(300))
                    } else {
                        fadeIn(animationSpec = tween(500)) with
                                fadeOut(animationSpec = tween(200))
                    }
                },
                label = "ChatContent"
            ) { hasMessages ->
                if (hasMessages) {
                    // Messages list with performance optimizations
                    ChatMessagesList(
                        messages = uiState.messages,
                        listState = listState,
                        isStreaming = uiState.isStreamingResponse,
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
                        }
                    )
                } else {
                    // Empty state with animations
                    EmptyStateContent(
                        onStartChat = {
                            // Focus is handled by ChatInput
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Error snackbar with animation
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.onEvent(ChatEvent.ClearError)
                            }
                        ) {
                            Text("Dismiss")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
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
    onNavigateBack: () -> Unit,
    onNewChat: () -> Unit,
    onExportToDiary: () -> Unit
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
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = "AI Assistant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    isStreaming: Boolean,
    onRetry: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onCopy: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            ChatMessageItem(
                message = message,
                onRetry = { onRetry(message) },
                onDelete = { onDelete(message) },
                onCopy = { onCopy(message) }
            )
        }

        // Loading indicator
        if (isStreaming && messages.lastOrNull()?.isUser == true) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    AiTypingIndicator()
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAlpha = remember { Animatable(0f) }
    val animatedScale = remember { Animatable(0.8f) }

    LaunchedEffect(message) {
        launch {
            animatedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            )
        }
        launch {
            animatedScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha.value
                scaleX = animatedScale.value
                scaleY = animatedScale.value
            }
    ) {
        ChatBubble(
            message = message,
            onRetry = onRetry,
            onDelete = onDelete,
            onCopy = onCopy
        )
    }
}

@Composable
private fun EmptyStateContent(
    onStartChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Solo il testo "Ciao EN!MA" centrato
        Text(
            text = "Ciao EN!MA",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 48.sp,
                letterSpacing = (-1).sp
            ),
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .offset(y = (-80).dp)
        )
    }
}

@Composable
private fun SuggestionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Chat Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
private fun AiTypingIndicator() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                            shape = MaterialTheme.shapes.small
                        )
                )

                if (index < 2) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
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
            Text("New Chat Session")
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Session title (optional)") },
                placeholder = { Text("e.g., Evening reflection") },
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
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}