package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isStreaming: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Animation states
    val sendButtonScale = remember { Animatable(1f) }
    val micButtonScale = remember { Animatable(1f) }

    // Auto-focus solo quando non c'è streaming
    LaunchedEffect(isEnabled, isStreaming) {
        if (isEnabled && !isStreaming && value.isEmpty()) {
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {

        // STILE GEMINI: Container con due righe come prima
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
                border = BorderStroke(width = 0.8.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
            ) {
                Column {
                    // RIGA 1: Input field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Input field
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
                                enabled = isEnabled && !isStreaming,
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
                                                text = if (isStreaming) "Lifo sta rispondendo..." else "Chiedi a Lifo",
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

                    // RIGA 2: Bottom action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Plus button - STILE NEUTRO
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.size(44.dp),
                            enabled = !isStreaming
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aggiungi",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isStreaming) 0.4f else 0.9f
                                )
                            )
                        }

                        // Canvas text button - STILE NEUTRO
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.height(44.dp),
                            enabled = !isStreaming,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isStreaming) 0.4f else 0.9f
                                )
                            )
                        ) {
                            Text(
                                text = "Canvas",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }

                        // Immagine text button - STILE NEUTRO
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.height(44.dp),
                            enabled = !isStreaming,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isStreaming) 0.4f else 0.9f
                                )
                            )
                        ) {
                            Text(
                                text = "Immagine",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Normal
                                )
                            )
                        }

                        // Spacer
                        Spacer(modifier = Modifier.weight(1f))

                        // Send/Mic button con animazione swap
                        AnimatedContent(
                            targetState = when {
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
                                "streaming" -> {
                                    // Stop button - STILE NEUTRO
                                    IconButton(
                                        onClick = { /* TODO: implementa stop streaming */ },
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
                                    // Send button - STILE NEUTRO
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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Invia",
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                "mic" -> {
                                    // Mic button
                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            scope.launch {
                                                micButtonScale.animateTo(0.9f, tween(80))
                                                micButtonScale.animateTo(1f, spring(dampingRatio = 0.3f))
                                            }
                                            // Handle voice input
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
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = if (isStreaming) 0.4f else 0.9f
                                            )
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