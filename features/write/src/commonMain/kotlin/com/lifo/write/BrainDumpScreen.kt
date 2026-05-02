package com.lifo.write

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.lifo.ui.i18n.Strings
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

/**
 * Brain Dump Screen — minimal journaling for mental unloading.
 *
 * No mood, no title, no metrics. Just write.
 * "Non analizzare, non correggere. Solo scrivi."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrainDumpScreen(
    description: String,
    onDescriptionChanged: (String) -> Unit,
    onBackPressed: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val canSave = description.isNotBlank()

    // Auto-focus the text field
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    // Character count animation
    val charCount = description.length
    val progressAlpha by animateFloatAsState(
        targetValue = if (charCount > 0) 1f else 0.4f,
        animationSpec = tween(300),
        label = "progress_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(Strings.Wellness.brainDumpTitle),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(Strings.Wellness.brainDumpSubtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Strings.Action.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSaveClicked,
                        enabled = canSave
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(Strings.Wellness.brainDumpSaveCd),
                            tint = if (canSave) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Minimal text editor — full screen, no distractions
            BasicTextField(
                value = description,
                onValueChange = onDescriptionChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (description.isEmpty()) {
                            Text(
                                text = stringResource(Strings.Wellness.brainDumpPlaceholderHint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Bottom bar with character count and save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$charCount caratteri",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = progressAlpha)
                )

                Button(
                    onClick = onSaveClicked,
                    enabled = canSave
                ) {
                    Text(stringResource(Res.string.brain_dump_save))
                }
            }
        }
    }
}
