package com.lifo.chat.presentation.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isStreaming: Boolean = false,
    onStartListening: (() -> Unit)? = null,
    onStopListening: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Voice states
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var hasRecordPermission by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    var showVoiceOverlay by remember { mutableStateOf(false) }

    // TTS
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    // Speech recognizer
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            startListening(
                context = context,
                speechRecognizer = speechRecognizer,
                onStart = { isListening = true },
                onResult = { result ->
                    transcribedText = result
                    onValueChange(value + " " + result)
                },
                onEnd = { isListening = false }
            )
        }
    }

    // Initialize TTS
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        onDispose {
            tts?.shutdown()
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

    // Auto-focus
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

    // Animation states
    val sendButtonScale = remember { Animatable(1f) }
    val micButtonScale = remember { Animatable(1f) }
    val recordingPulse = rememberInfiniteTransition(label = "recording")
    val pulseScale by recordingPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Voice overlay
        AnimatedVisibility(
            visible = showVoiceOverlay,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            VoiceInputOverlay(
                isListening = isListening,
                transcribedText = transcribedText,
                onDismiss = {
                    showVoiceOverlay = false
                    stopListening(speechRecognizer) { isListening = false }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp, top = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 0.dp,
                border = BorderStroke(
                    width = if (isListening) 2.dp else 0.8.dp,
                    color = if (isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    }
                )
            ) {
                Column {
                    // Row 1: Input field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
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
                                    fontWeight = FontWeight.Normal
                                ),
                                enabled = isEnabled && !isStreaming && !isListening,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (value.isNotBlank() && isEnabled && !isStreaming) {
                                            onSend()
                                            keyboardController?.hide()
                                        }
                                    }
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (value.isEmpty()) {
                                            Text(
                                                text = when {
                                                    isListening -> "Sto ascoltando..."
                                                    isStreaming -> "Lifo sta rispondendo..."
                                                    else -> "Chiedi a Lifo"
                                                },
                                                style = TextStyle(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    // Row 2: Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Plus button
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.size(44.dp),
                            enabled = !isStreaming && !isListening
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aggiungi",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isStreaming || isListening) 0.4f else 0.9f
                                )
                            )
                        }

                        // TTS button - parla l'ultimo messaggio
                        AnimatedVisibility(
                            visible = !isListening && !isStreaming,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // TODO: Implementa TTS per l'ultimo messaggio dell'AI
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.height(44.dp),
                                enabled = !isStreaming && !isListening,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (isStreaming || isListening) 0.4f else 0.9f
                                    )
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VolumeUp,
                                    contentDescription = "Ascolta",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ascolta",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Normal
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Send/Mic/Stop button
                        AnimatedContent(
                            targetState = when {
                                isListening -> "listening"
                                isStreaming -> "streaming"
                                value.isNotBlank() -> "send"
                                else -> "mic"
                            },
                            transitionSpec = {
                                scaleIn() + fadeIn() with scaleOut() + fadeOut()
                            },
                            label = "SendMicSwap"
                        ) { state ->
                            when (state) {
                                "listening" -> {
                                    // Recording button with pulse
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showVoiceOverlay = false
                                            stopListening(speechRecognizer) { isListening = false }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .scale(pulseScale)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(40.dp)
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
                                    IconButton(
                                        onClick = { /* TODO: Stop streaming */ },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Stop,
                                                    contentDescription = "Stop",
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                "send" -> {
                                    IconButton(
                                        onClick = {
                                            if (!isStreaming && value.isNotBlank()) {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                scope.launch {
                                                    sendButtonScale.animateTo(0.9f, tween(80))
                                                    sendButtonScale.animateTo(1f, spring(dampingRatio = 0.3f))
                                                }
                                                onSend()
                                                keyboardController?.hide()
                                            }
                                        },
                                        enabled = !isStreaming && value.isNotBlank(),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .scale(sendButtonScale.value)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
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
                                                micButtonScale.animateTo(1f, spring(dampingRatio = 0.3f))
                                            }

                                            if (hasRecordPermission) {
                                                showVoiceOverlay = true
                                                startListening(
                                                    context = context,
                                                    speechRecognizer = speechRecognizer,
                                                    onStart = { isListening = true },
                                                    onResult = { result ->
                                                        transcribedText = result
                                                        onValueChange(value + " " + result)
                                                    },
                                                    onEnd = { isListening = false }
                                                )
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .scale(micButtonScale.value),
                                        enabled = !isStreaming
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = "Voce",
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceInputOverlay(
    isListening: Boolean,
    transcribedText: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Prevent clicks through */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Wave animation
            if (isListening) {
                WaveAnimation()
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (transcribedText.isNotEmpty()) {
                    transcribedText
                } else if (isListening) {
                    "Parla ora..."
                } else {
                    "Preparazione..."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    }
}

@Composable
private fun WaveAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 10f,
                targetValue = 30f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300 + (index * 100),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave$index"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

// Helper functions
private fun startListening(
    context: Context,
    speechRecognizer: SpeechRecognizer?,
    onStart: () -> Unit,
    onResult: (String) -> Unit,
    onEnd: () -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStart()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                onResult(matches[0])
            }
        }

        override fun onEndOfSpeech() {
            onEnd()
        }

        override fun onError(error: Int) {
            Log.e("ChatInput", "Speech recognition error: $error")
            onEnd()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                onResult(matches[0])
            }
        }
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