package com.lifo.chat.presentation.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isStreaming: Boolean = false,
    currentEmotion: String = "NEUTRAL",
    voiceNaturalness: Float = 1.0f,
    isVoiceChatMode: Boolean = false,
    onVoiceRecord: (() -> Unit)? = null,
    onNavigateToLiveMode: (() -> Unit)? = null,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Voice states
    var isListening by remember { mutableStateOf(false) }
    var hasRecordPermission by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var voiceConfidence by remember { mutableStateOf(0f) }

    // Speech recognizer con configurazione italiana
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            startNaturalListening(
                context = context,
                speechRecognizer = speechRecognizer,
                onStart = {
                    isListening = true
                    showVoiceOverlay = true
                },
                onResult = { result, confidence ->
                    transcribedText = result
                    voiceConfidence = confidence
                },
                onEnd = {
                    isListening = false
                    if (transcribedText.isNotEmpty()) {
                        onValueChange(value + " " + transcribedText)
                        transcribedText = ""
                    }
                }
            )
        }
    }

    // Initialize speech recognizer
    DisposableEffect(Unit) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            // Usa il riconoscimento vocale italiano
        }

        onDispose {
            speechRecognizer?.destroy()
        }
    }

    // Check permission
    LaunchedEffect(Unit) {
        hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Auto-focus intelligente
    LaunchedEffect(isEnabled, isStreaming) {
        if (isEnabled && !isStreaming && value.isEmpty() && !isListening) {
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Animation states con dinamiche naturali
    val sendButtonScale = remember { Animatable(1f) }
    val micButtonScale = remember { Animatable(1f) }
    val inputFieldElevation = animateDpAsState(
        targetValue = if (value.isNotEmpty() || isListening) 4.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    // Pulsazione naturale per registrazione
    val recordingPulse = rememberInfiniteTransition(label = "recording")
    val pulseScale by recordingPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by recordingPulse.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Colore dinamico basato sull'emozione
    val emotionColor = when (currentEmotion) {
        "HAPPY", "EXCITED" -> MaterialTheme.colorScheme.tertiary
        "SAD", "THOUGHTFUL" -> MaterialTheme.colorScheme.secondary
        "EMPHATIC" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val animatedEmotionColor by animateColorAsState(
        targetValue = emotionColor,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "emotionColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Voice overlay naturale
        AnimatedVisibility(
            visible = showVoiceOverlay,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            NaturalVoiceInputOverlay(
                isListening = isListening,
                transcribedText = transcribedText,
                confidence = voiceConfidence,
                onDismiss = {
                    showVoiceOverlay = false
                    stopListening(speechRecognizer) {
                        isListening = false
                        if (transcribedText.isNotEmpty()) {
                            onValueChange(value + " " + transcribedText)
                            transcribedText = ""
                        }
                    }
                }
            )
        }

        // Indicatore di naturalezza vocale
        AnimatedVisibility(
            visible = isStreaming && voiceNaturalness > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            VoiceNaturalnessIndicator(
                naturalness = voiceNaturalness,
                emotion = currentEmotion,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp, top = 4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp, // Rimosso elevation per trasparenza
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), // Trasparente
                border = BorderStroke(
                    width = if (isListening) 2.dp else 0.5.dp,
                    color = if (isListening) {
                        MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) // Bordo molto trasparente
                    }
                )
            ) {
                Column {
                    // Input field principale
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Campo di testo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.15.sp
                                ),
                                enabled = isEnabled && !isStreaming && !isListening,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (value.isNotBlank() && isEnabled && !isStreaming) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSend()
                                            keyboardController?.hide()
                                        }
                                    }
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(animatedEmotionColor),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (value.isEmpty()) {
                                            Text(
                                                text = when {
                                                    isListening -> "Sto ascoltando..."
                                                    isStreaming -> when (currentEmotion) {
                                                        "THOUGHTFUL" -> "Lifo sta riflettendo..."
                                                        "EXCITED" -> "Lifo sta rispondendo con entusiasmo!"
                                                        "HAPPY" -> "Lifo sta sorridendo mentre risponde..."
                                                        else -> "Lifo sta rispondendo..."
                                                    }
                                                    else -> "Parla con Lifo"
                                                },
                                                style = TextStyle(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    letterSpacing = 0.15.sp
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Trailing actions (bottoni personalizzati per Live mode)
                        if (trailingActions != null) {
                            trailingActions()
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Send/Mic button con animazioni fluide
                        AnimatedContent(
                            targetState = when {
                                isListening -> "listening"
                                isStreaming -> "streaming"
                                value.isNotBlank() -> "send"
                                else -> "mic"
                            },
                            transitionSpec = {
                                (scaleIn(
                                    animationSpec = tween(200),
                                    initialScale = 0.8f
                                ) + fadeIn()) with
                                        (scaleOut(
                                            animationSpec = tween(200),
                                            targetScale = 0.8f
                                        ) + fadeOut())
                            },
                            label = "SendMicSwap"
                        ) { state ->
                            when (state) {
                                "listening" -> {
                                    // Pulsante di registrazione animato
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showVoiceOverlay = false
                                            stopListening(speechRecognizer) {
                                                isListening = false
                                                if (transcribedText.isNotEmpty()) {
                                                    onValueChange(value + " " + transcribedText)
                                                    transcribedText = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(pulseScale)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .graphicsLayer {
                                                    alpha = pulseAlpha
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Recording",
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onError
                                                )
                                            }
                                        }
                                    }
                                }
                                "streaming" -> {
                                    // Indicatore di streaming con animazione
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        StreamingIndicator(emotion = currentEmotion)
                                    }
                                }
                                "send" -> {
                                    IconButton(
                                        onClick = {
                                            if (!isStreaming && value.isNotBlank()) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scope.launch {
                                                    sendButtonScale.animateTo(0.85f, tween(80))
                                                    sendButtonScale.animateTo(1f, spring(
                                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ))
                                                }
                                                onSend()
                                                keyboardController?.hide()
                                            }
                                        },
                                        enabled = !isStreaming && value.isNotBlank(),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(sendButtonScale.value)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = animatedEmotionColor,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Invia",
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                                "mic" -> {
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            scope.launch {
                                                micButtonScale.animateTo(0.9f, tween(80))
                                                micButtonScale.animateTo(1f, spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                                ))
                                            }

                                            // Navigate to Live Mode instead of voice recognition
                                            onNavigateToLiveMode?.invoke()
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(micButtonScale.value),
                                        enabled = !isStreaming
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Live Mode",
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Quick actions row
                    AnimatedVisibility(
                        visible = !isStreaming && !isListening && value.isEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick emotion buttons
                            QuickEmotionButton(
                                icon = "😊",
                                text = "Felice",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange("Mi sento felice")
                                }
                            )

                            QuickEmotionButton(
                                icon = "💭",
                                text = "Riflessivo",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange("Sto riflettendo su")
                                }
                            )

                            QuickEmotionButton(
                                icon = "💪",
                                text = "Motivato",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange("Mi sento motivato")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NaturalVoiceInputOverlay(
    isListening: Boolean,
    transcribedText: String,
    confidence: Float,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Prevent clicks through */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Animated voice visualization
            if (isListening) {
                NaturalWaveAnimation()
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.dp
                )
            }

            // Transcribed text with confidence
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (transcribedText.isNotEmpty()) {
                        transcribedText
                    } else if (isListening) {
                        "Parla naturalmente..."
                    } else {
                        "Preparazione in corso..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Confidence indicator
                if (transcribedText.isNotEmpty() && confidence > 0) {
                    LinearProgressIndicator(
                        progress = confidence,
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(2.dp),
                        color = when {
                            confidence > 0.8f -> MaterialTheme.colorScheme.primary
                            confidence > 0.5f -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Annulla")
                }

                if (transcribedText.isNotEmpty()) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Usa testo")
                    }
                }
            }
        }
    }
}

@Composable
private fun NaturalWaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(60.dp)
    ) {
        repeat(7) { index ->
            val delay = index * 100

            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1400
                        0.3f at 0 + delay
                        1f at 350 + delay
                        0.3f at 700 + delay
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1400
                        0.5f at 0 + delay
                        1f at 350 + delay
                        0.5f at 700 + delay
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "waveAlpha$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(height)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun VoiceNaturalnessIndicator(
    naturalness: Float,
    emotion: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Emotion icon
        Text(
            text = when (emotion) {
                "HAPPY" -> "😊"
                "SAD" -> "😢"
                "EXCITED" -> "🎉"
                "THOUGHTFUL" -> "🤔"
                "CALM" -> "😌"
                else -> "🎙️"
            },
            fontSize = 16.sp
        )

        // Naturalness bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(naturalness)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    )
            )
        }

        // Naturalness percentage
        Text(
            text = "${(naturalness * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StreamingIndicator(emotion: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (emotion) {
                    "EXCITED" -> 800
                    "THOUGHTFUL" -> 2000
                    else -> 1200
                },
                easing = LinearEasing
            )
        ),
        label = "rotation"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                rotationZ = rotation
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Streaming",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QuickEmotionButton(
    icon: String,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 14.sp)
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper functions per riconoscimento vocale naturale
private fun startNaturalListening(
    context: Context,
    speechRecognizer: SpeechRecognizer?,
    onStart: () -> Unit,
    onResult: (String, Float) -> Unit,
    onEnd: () -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
    }

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
        private var lastPartialResult = ""

        override fun onReadyForSpeech(params: Bundle?) {
            onStart()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            if (!matches.isNullOrEmpty()) {
                val bestMatch = matches[0]
                val confidence = scores?.getOrNull(0) ?: 0.5f
                onResult(bestMatch, confidence)
            }

            onEnd()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty() && matches[0] != lastPartialResult) {
                lastPartialResult = matches[0]
                onResult(matches[0], 0.7f) // Confidence intermedia per risultati parziali
            }
        }

        override fun onEndOfSpeech() {
            // L'utente ha finito di parlare
        }

        override fun onError(error: Int) {
            println("[NaturalChatInput] ERROR: Speech recognition error: $error")
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NETWORK -> "Errore di rete"
                SpeechRecognizer.ERROR_NO_MATCH -> "Non ho capito, riprova"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout, riprova"
                else -> "Errore riconoscimento vocale"
            }
            onResult(errorMessage, 0f)
            onEnd()
        }

        override fun onBeginningOfSpeech() {
            // Iniziato a parlare
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Livello audio cambiato - può essere usato per animazioni
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    speechRecognizer?.startListening(intent)
}

private fun stopListening(
    speechRecognizer: SpeechRecognizer?,
    onEnd: () -> Unit
) {
    speechRecognizer?.stopListening()
    onEnd()
}