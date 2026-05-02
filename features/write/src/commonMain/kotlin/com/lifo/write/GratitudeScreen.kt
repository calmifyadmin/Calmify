package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.components.graphics.ParticleConfig
import com.lifo.ui.components.graphics.ParticleSystem
import com.lifo.ui.i18n.Strings
import com.lifo.util.model.GratitudeCategory
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GratitudeScreen(
    state: GratitudeContract.State,
    onSetItem: (index: Int, text: String) -> Unit,
    onSetCategory: (index: Int, GratitudeCategory) -> Unit,
    onSaveClicked: () -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val canSave = state.item1.isNotBlank() || state.item2.isNotBlank() || state.item3.isNotBlank()

    var showCelebration by remember { mutableStateOf(false) }
    var wasSaving by remember { mutableStateOf(false) }
    var saveButtonCenter by remember { mutableStateOf(Offset.Unspecified) }

    LaunchedEffect(state.isSaving) {
        if (wasSaving && !state.isSaving && state.savedToday) {
            showCelebration = true
        }
        wasSaving = state.isSaving
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "3 Cose Belle",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(if (state.savedToday) Strings.Wellness.gratitudeSubtitleUpdate else Strings.Wellness.gratitudeSubtitleToday),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Strings.Action.back))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onSaveClicked,
                            enabled = canSave && !state.isSaving
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(Strings.Action.save),
                                    tint = if (canSave) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            if (state.isLoading) {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val items = listOf(state.item1, state.item2, state.item3)
                val categories = listOf(state.category1, state.category2, state.category3)
                val labels = listOf(
                    stringResource(Strings.Wellness.gratitudeLabelFirst),
                    stringResource(Strings.Wellness.gratitudeLabelSecond),
                    stringResource(Strings.Wellness.gratitudeLabelThird),
                )
                val numbering = listOf("1.", "2.", "3.")

                items.forEachIndexed { index, itemText ->
                    AnimatedSection(index) {
                        GratitudeItemCard(
                            number = numbering[index],
                            label = labels[index],
                            text = itemText,
                            category = categories[index],
                            onTextChanged = { onSetItem(index, it) },
                            onCategoryChanged = { onSetCategory(index, it) },
                            focusRequester = if (index == 0) focusRequester else null,
                        )
                    }
                }

                // Save button
                Button(
                    onClick = onSaveClicked,
                    enabled = canSave && !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            saveButtonCenter = Offset(
                                x = pos.x + coords.size.width / 2f,
                                y = pos.y + coords.size.height / 2f,
                            )
                        },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(if (state.savedToday) Strings.Wellness.gratitudeSaveUpdate else Strings.Wellness.gratitudeSaveCreate),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        ParticleSystem(
            trigger = showCelebration,
            origin = saveButtonCenter,
            config = ParticleConfig(
                colors = listOf(
                    Color(0xFFFFD700),
                    Color(0xFFFF6B9D),
                    Color(0xFF4ECDC4),
                ),
                count = 30,
                lifetime = 1.5f,
            ),
            onComplete = { showCelebration = false },
        )
    }
}

// ==================== GRATITUDE ITEM CARD ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GratitudeItemCard(
    number: String,
    label: String,
    text: String,
    category: GratitudeCategory,
    onTextChanged: (String) -> Unit,
    onCategoryChanged: (GratitudeCategory) -> Unit,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Number + label
            Text(
                text = "$number $label",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.primary
            )

            // Text input
            val textModifier = if (focusRequester != null) {
                Modifier.fillMaxWidth().focusRequester(focusRequester)
            } else {
                Modifier.fillMaxWidth()
            }

            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = textModifier,
                textStyle = TextStyle(
                    color = colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(Strings.Wellness.gratitudeJournalQuestion),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Category chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GratitudeCategory.entries.forEach { cat ->
                    val isSelected = category == cat

                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategoryChanged(cat) },
                        label = {
                            Text(
                                cat.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }
    }
}

// ==================== ANIMATED SECTION ====================

@Composable
private fun AnimatedSection(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 80L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 }
    ) {
        content()
    }
}
