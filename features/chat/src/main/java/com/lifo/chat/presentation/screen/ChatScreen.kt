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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.chat.domain.model.ChatEvent
import com.lifo.chat.domain.model.ChatMessage
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Debug log
    LaunchedEffect(uiState) {
        Log.d("ChatScreen", "UI State updated - Messages: ${uiState.messages.size}, Session: ${uiState.currentSession?.id}")
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
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
                onNavigateBack = navigateBack,
                onNewChat = { viewModel.onEvent(ChatEvent.ShowNewSessionDialog) },
                onExportToDiary = {
                    uiState.currentSession?.let { session ->
                        viewModel.onEvent(ChatEvent.ExportToDiary(session.id))
                        // TODO: Navigate with exported content
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                value = uiState.inputText,
                onValueChange = { viewModel.onEvent(ChatEvent.UpdateInputText(it)) },
                onSend = {
                    Log.d("ChatScreen", "Send button clicked with text: ${uiState.inputText}")
                    viewModel.onEvent(ChatEvent.SendMessage(uiState.inputText))
                },
                isEnabled = !uiState.isLoading,
                isStreaming = uiState.isStreamingResponse
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = uiState.messages.isNotEmpty() || uiState.isStreamingResponse,
                transitionSpec = {
                    if (targetState) {
                        // Entering chat view
                        fadeIn(animationSpec = tween(300)) with
                                fadeOut(animationSpec = tween(300))
                    } else {
                        // Entering empty state
                        fadeIn(animationSpec = tween(500)) with
                                fadeOut(animationSpec = tween(200))
                    }
                },
                label = "ChatContent"
            ) { hasMessages ->
                if (hasMessages) {
                    // Show messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show messages
                        items(
                            items = uiState.messages,
                            key = { it.id }
                        ) { message ->
                            ChatMessageItem(
                                message = message,
                                onRetry = { viewModel.onEvent(ChatEvent.RetryMessage(message.id)) },
                                onDelete = { viewModel.onEvent(ChatEvent.DeleteMessage(message.id)) },
                                onCopy = { copyToClipboard(context, message.content) }
                            )
                        }

                        // Loading indicator at the bottom
                        if (uiState.isStreamingResponse && uiState.messages.lastOrNull()?.isUser == true) {
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
                } else {
                    // Show empty state
                    EmptyStateContent(
                        onStartChat = {
                            // Focus on input is already handled
                        }
                    )
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(
                            onClick = { viewModel.onEvent(ChatEvent.ClearError) }
                        ) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // New session dialog
    if (uiState.showNewSessionDialog) {
        NewSessionDialog(
            onDismiss = { viewModel.onEvent(ChatEvent.HideNewSessionDialog) },
            onCreate = { title ->
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
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "AI Assistant",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun ChatMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val animatedAlpha = remember { Animatable(0f) }

    LaunchedEffect(message) {
        animatedAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = animatedAlpha.value }
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
    onStartChat: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to Calmify AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "I'm here to listen and support you. Share your thoughts, feelings, or anything on your mind.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Suggestion chips
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "You can ask me about:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SuggestionChip(
                        onClick = onStartChat,
                        label = { Text("Managing stress") },
                        icon = {
                            Icon(
                                Icons.Outlined.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    SuggestionChip(
                        onClick = onStartChat,
                        label = { Text("Daily reflection") },
                        icon = {
                            Icon(
                                Icons.Outlined.Book,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SuggestionChip(
                        onClick = onStartChat,
                        label = { Text("Mindfulness tips") },
                        icon = {
                            Icon(
                                Icons.Outlined.SelfImprovement,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    SuggestionChip(
                        onClick = onStartChat,
                        label = { Text("Mood tracking") },
                        icon = {
                            Icon(
                                Icons.Outlined.Mood,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Chat Message", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}