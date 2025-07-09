package com.lifo.chat.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean = true,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val sendEnabled = value.trim().isNotEmpty() && isEnabled && !isStreaming

    // Animation for send button
    val sendButtonScale by animateFloatAsState(
        targetValue = if (sendEnabled) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SendButtonScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Input field
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = if (isStreaming) "AI is thinking..." else "Type a message...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    enabled = isEnabled && !isStreaming,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (value.contains('\n')) ImeAction.Default else ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (sendEnabled) {
                                onSend()
                                keyboardController?.hide()
                            }
                        }
                    ),
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send button
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FilledIconButton(
                    onClick = {
                        if (sendEnabled) {
                            onSend()
                            keyboardController?.hide()
                        }
                    },
                    enabled = sendEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = sendButtonScale
                            scaleY = sendButtonScale
                        }
                ) {
                    AnimatedContent(
                        targetState = isStreaming,
                        transitionSpec = {
                            fadeIn() with fadeOut()
                        },
                        label = "SendButtonIcon"
                    ) { streaming ->
                        if (streaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
}