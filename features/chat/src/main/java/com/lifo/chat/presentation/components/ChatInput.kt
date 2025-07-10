package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    onVoiceInput: (() -> Unit)? = null,
    onAttachment: (() -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    var isFocused by remember { mutableStateOf(false) }
    val hasText = value.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Main input container - Gemini style
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side content
                AnimatedContent(
                    targetState = hasText,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(150)) with
                                fadeOut(animationSpec = tween(150))
                    },
                    label = "LeftContent"
                ) { showAddButton ->
                    if (!showAddButton) {
                        // + button when no text
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Handle add action
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Input field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused },
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        enabled = isEnabled && !isStreaming,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (hasText) ImeAction.Send else ImeAction.Default
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (hasText) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSend()
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        singleLine = false,
                        maxLines = 5,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )

                    // Placeholder
                    if (value.isEmpty() && !isFocused) {
                        Text(
                            text = "Chiedi a Calmify",
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                // Right side content
                AnimatedContent(
                    targetState = when {
                        isStreaming -> "streaming"
                        hasText -> "send"
                        else -> "actions"
                    },
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(150)) +
                                scaleIn(animationSpec = tween(150))) with
                                (fadeOut(animationSpec = tween(150)) +
                                        scaleOut(animationSpec = tween(150)))
                    },
                    label = "RightContent"
                ) { state ->
                    when (state) {
                        "streaming" -> {
                            // Stop button durante lo streaming
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Handle stop streaming
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        "send" -> {
                            // Send button
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSend()
                                    keyboardController?.hide()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        "actions" -> {
                            // Voice and waveform buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Research button (al posto di Canvas)
                                TextButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        // Handle research
                                    },
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "Research",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Voice button
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onVoiceInput?.invoke()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Mic,
                                        contentDescription = "Voice",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Waveform/Audio visualization button
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        // Handle waveform
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        painter = rememberWaveformIcon(),
                                        contentDescription = "Audio",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-focus on mount
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}

@Composable
private fun rememberWaveformIcon() = rememberVectorPainter(
    Icons.Outlined.GraphicEq // Placeholder per l'icona waveform
)