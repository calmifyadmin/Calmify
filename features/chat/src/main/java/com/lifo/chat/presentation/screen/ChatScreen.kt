package com.lifo.chat.presentation.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.lifo.chat.domain.model.ChatEvent
import com.lifo.chat.presentation.components.ChatBubble
import com.lifo.chat.presentation.components.ChatInput
import com.lifo.chat.presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lifo.mongo.repository.ChatMessage
import com.lifo.mongo.repository.ChatSession
import com.lifo.mongo.repository.MessageStatus
// Import necessari
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.Offset
// Import necessari:
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin


// Import necessari:
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Get user info
    val userDisplayName = viewModel.getUserDisplayName()
    val userPhotoUrl = viewModel.getUserPhotoUrl()

    // Auto-scroll ottimizzato
    LaunchedEffect(uiState.messages.size, uiState.streamingMessage) {
        val totalItems = uiState.messages.size + (if (uiState.streamingMessage != null) 1 else 0)
        if (totalItems > 0) {
            delay(100)
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

    // Previeni navigazione durante operazioni
    DisposableEffect(uiState.isNavigating) {
        onDispose {
            if (uiState.isNavigating) {
                Log.w("ChatScreen", "Navigation interrupted")
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
                }
            )
        },
        bottomBar = {
            Column {
                // Suggestion buttons
                AnimatedVisibility(
                    visible = !uiState.sessionStarted &&
                            uiState.messages.isEmpty() &&
                            uiState.streamingMessage == null,
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
            // MAIN CONTENT - sempre mostra la lista se ci sono messaggi o streaming
            if (uiState.messages.isNotEmpty() || uiState.streamingMessage != null) {
                ChatMessagesList(
                    messages = uiState.messages,
                    streamingMessage = uiState.streamingMessage,
                    listState = listState,
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
            } else if (!uiState.isLoading) {
                // Empty state with user name
                EmptyStateContent(
                    userName = userDisplayName,
                    onStartChat = { /* handled by input */ },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Loading overlay
            AnimatedVisibility(
                visible = uiState.isLoading && uiState.messages.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error snackbar
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
    userPhotoUrl: String?,
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
            // User profile image
            userPhotoUrl?.let {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = userPhotoUrl,
                        error = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                        placeholder = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic)
                    ),
                    contentDescription = "User Profile Image",
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
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    streamingMessage: com.lifo.chat.domain.model.StreamingMessage?,
    listState: LazyListState,
    onRetry: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onCopy: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Più spazio tra messaggi
    ) {
        // Messaggi esistenti con chiavi stabili
        items(
            items = messages,
            key = { it.id },
            contentType = { if (it.isUser) "user" else "ai" }
        ) { message ->
            ChatMessageItem(
                message = message,
                onRetry = { onRetry(message) },
                onDelete = { onDelete(message) },
                onCopy = { onCopy(message) }
            )
        }

        // Messaggio streaming (se presente)
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
private fun ChatMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    // RIMOSSO: Animazioni di scale e alpha che causavano l'effetto espansione
    Box(
        modifier = modifier.fillMaxWidth()
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
private fun StreamingMessageItem(
    content: String,
    modifier: Modifier = Modifier
) {
    // STILE GEMINI: Niente bubble, full width
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // AI Avatar
        AvatarBubble()

        Spacer(modifier = Modifier.width(12.dp))

        // Content - occupa tutto lo spazio
        Column(modifier = Modifier.weight(1f)) {
            // AI Name
            Text(
                text = "Lifo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (content.isEmpty()) {
                // Typing indicator
                AiTypingIndicator()
            } else {
                // Streaming content
                Row {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Blinking cursor
                    BlinkingCursor()
                }
            }
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
private fun AvatarBubble() {
    // RIMOSSO: Animazione pulsante dell'avatar
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
private fun EmptyStateContent(
    userName: String?,
    onStartChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Stati per le animazioni
        var isVisible by remember { mutableStateOf(false) }

        // Trigger dell'animazione all'avvio
        LaunchedEffect(Unit) {
            delay(100) // Piccolo delay iniziale
            isVisible = true
        }

        // Animazioni multiple combinate
        val animationDuration = 1200

        // Opacità animata
        val alpha by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            ),
            label = "alpha"
        )

        // Animazione infinita per il gradiente
        val infiniteTransition = rememberInfiniteTransition(label = "gradient")
        val gradientOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradientOffset"
        )

        // Colori del gradiente animato
        val gradientColors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary
        )

        // Brush gradiente animato
        val brush = Brush.linearGradient(
            colors = gradientColors,
            start = Offset(0f, 0f),
            end = Offset(1000f * gradientOffset, 0f)
        )

        // Effetto stelline "pouf" - appare prima del testo
        if (isVisible) {
            SparklePoufEffect(
                modifier = Modifier
                    .fillMaxSize()
            )
        }

        // Contenitore per il testo con animazione uniforme
        Box(
            modifier = Modifier
                .offset(y = (-80).dp)
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            val text = "Ciao ${userName ?: ""}"

            // Animazione del blur/sfocatura
            val blurRadius by animateFloatAsState(
                targetValue = if (isVisible) 0f else 13f,
                animationSpec = tween(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing
                ),
                label = "blur"
            )

            // Animazione della scala uniforme
            val textScale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessVeryLow
                ),
                label = "textScale"
            )

            // Testo principale con effetto di comparsa dalla sfocatura
            Text(
                text = text,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 48.sp,
                    letterSpacing = (-1).sp,
                    brush = brush
                ),
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .scale(textScale)
                    .graphicsLayer {
                        // Effetto blur custom (richiede API 31+)
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(
                                    blurRadius,
                                    blurRadius,
                                    android.graphics.Shader.TileMode.DECAL
                                ).asComposeRenderEffect()
                        }
                    }
            )

            // Overlay per effetto di dissolvenza aggiuntivo
            if (blurRadius > 0) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 48.sp,
                        letterSpacing = (-1).sp,
                        brush = brush
                    ),
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .scale(textScale)
                        .alpha((20f - blurRadius) / 20f * 0.3f) // Sovrapposizione leggera
                )
            }
        }
    }
}

// Effetto stelline "pouf" elegante che appare solo all'inizio
@Composable
private fun SparklePoufEffect(modifier: Modifier = Modifier) {
    // Stato per controllare quando far partire l'animazione
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    // Recupera i colori dal tema
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // Crea 5 stelline con animazioni individuali
    val sparkles = remember {
        List(5) { index ->
            SparkleData(
                id = index,
                startX = 0.5f + (index - 2) * 0.15f, // Posizioni intorno al centro
                startY = 0.45f,
                targetX = when (index) {
                    0 -> 0.2f
                    1 -> 0.35f
                    2 -> 0.5f
                    3 -> 0.65f
                    else -> 0.8f
                },
                targetY = when (index) {
                    0 -> 0.3f
                    1 -> 0.25f
                    2 -> 0.2f
                    3 -> 0.25f
                    else -> 0.3f
                },
                delay = index * 100L, // Delay sequenziale
                color = when (index % 3) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> tertiaryColor
                }
            )
        }
    }

    // Animazioni per ogni stellina - FUORI dal Canvas
    val animationProgresses = sparkles.map { sparkle ->
        val animationProgress = remember { Animatable(0f) }

        LaunchedEffect(sparkle.id, startAnimation) {
            if (startAnimation) {
                delay(sparkle.delay)
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 800,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }

        animationProgress.value
    }

    Canvas(modifier = modifier) {
        if (startAnimation) {
            sparkles.forEachIndexed { index, sparkle ->
                val progress = animationProgresses[index]

                // Calcola posizione interpolata
                val currentX = size.width * (sparkle.startX + (sparkle.targetX - sparkle.startX) * progress)
                val currentY = size.height * (sparkle.startY + (sparkle.targetY - sparkle.startY) * progress)

                // Alpha che sfuma elegantemente
                val alpha = when {
                    progress < 0.7f -> 1f
                    else -> 1f - ((progress - 0.7f) / 0.3f) // Sfuma negli ultimi 30%
                }

                // Scala che cresce e poi diminuisce
                val scale = when {
                    progress < 0.3f -> progress / 0.3f
                    progress < 0.7f -> 1f
                    else -> 1f - ((progress - 0.7f) / 0.3f) * 0.5f
                }

                // Disegna la stellina
                drawPath(
                    path = createStarPath(
                        center = Offset(currentX, currentY),
                        outerRadius = 4.dp.toPx() * scale,
                        innerRadius = 2.dp.toPx() * scale
                    ),
                    color = sparkle.color.copy(alpha = alpha * 0.8f)
                )

                // Piccolo alone luminoso
                drawCircle(
                    color = sparkle.color.copy(alpha = alpha * 0.2f),
                    radius = 6.dp.toPx() * scale,
                    center = Offset(currentX, currentY),
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

// Data class per le stelline
private data class SparkleData(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val delay: Long,
    val color: Color
)

// Funzione helper per creare una stella
private fun createStarPath(center: Offset, outerRadius: Float, innerRadius: Float): Path {
    return Path().apply {
        val angleStep = PI.toFloat() / 2f
        moveTo(center.x, center.y - outerRadius)

        for (i in 0 until 4) {
            val outerAngle = i * angleStep - PI.toFloat() / 2f
            val innerAngle = outerAngle + angleStep / 2f

            lineTo(
                center.x + cos(innerAngle) * innerRadius,
                center.y + sin(innerAngle) * innerRadius
            )
            lineTo(
                center.x + cos(outerAngle + angleStep) * outerRadius,
                center.y + sin(outerAngle + angleStep) * outerRadius
            )
        }
        close()
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
    // STILE GEMINI: Solo i puntini, senza sfondo
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