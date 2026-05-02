package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.MovementType
import com.lifo.util.model.PostMovementFeeling
import kotlinx.coroutines.delay
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementScreen(
    state: MovementContract.State,
    onIntent: (MovementContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(Strings.Wellness.movementTitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(Strings.Wellness.movementSubtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
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
                        onClick = { onIntent(MovementContract.Intent.Save) },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = stringResource(Strings.Wellness.movementSaveCd), tint = colorScheme.primary)
                        }
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Weekly summary
            if (state.weeklyCount > 0) {
                AnimatedSection(0) {
                    WeeklySummaryCard(weeklyCount = state.weeklyCount)
                }
            }

            // Movement type — tappable grid
            AnimatedSection(1) {
                MovementTypeSelectorCard(
                    selectedType = state.movementType,
                    onTypeSelected = { onIntent(MovementContract.Intent.SetMovementType(it)) }
                )
            }

            // Duration
            AnimatedSection(2) {
                DurationCard(
                    durationMinutes = state.durationMinutes,
                    onDurationChanged = { onIntent(MovementContract.Intent.SetDuration(it)) }
                )
            }

            // Feeling after
            AnimatedSection(3) {
                FeelingCard(
                    feeling = state.feeling,
                    onFeelingChanged = { onIntent(MovementContract.Intent.SetFeeling(it)) }
                )
            }

            // Note
            AnimatedSection(4) {
                NoteCard(
                    note = state.note,
                    onNoteChanged = { onIntent(MovementContract.Intent.SetNote(it)) }
                )
            }

            // Save button
            Button(
                onClick = { onIntent(MovementContract.Intent.Save) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.movement_record), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ==================== WEEKLY SUMMARY ====================

@Composable
private fun WeeklySummaryCard(weeklyCount: Int) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$weeklyCount/7",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = colorScheme.onPrimaryContainer
                )
                Text(
                    "giorni attivi",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            if (weeklyCount >= 4) {
                Surface(
                    modifier = Modifier
                        .width(1.dp)
                        .height(60.dp),
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                ) {}

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Strings.Wellness.movementStreakGreat),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onPrimaryContainer
                    )
                    Text(
                        stringResource(Strings.Wellness.movementStreakKeep),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ==================== MOVEMENT TYPE SELECTOR ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovementTypeSelectorCard(
    selectedType: MovementType,
    onTypeSelected: (MovementType) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val types = MovementType.entries.filter { it != MovementType.NESSUNO }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Strings.Wellness.movementSectionWhat),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { type ->
                    val isSelected = type == selectedType

                    FilterChip(
                        selected = isSelected,
                        onClick = { onTypeSelected(type) },
                        label = {
                            Text(
                                type.displayName,
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

// ==================== DURATION ====================

@Composable
private fun DurationCard(
    durationMinutes: Int,
    onDurationChanged: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colorScheme.primary
                )
                Text(
                    stringResource(Strings.Wellness.movementSectionDuration),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurface
                )
            }

            // Large display
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "$durationMinutes min",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp
                    ),
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }

            Slider(
                value = durationMinutes.toFloat(),
                onValueChange = { onDurationChanged(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "5 min",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    "120 min",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ==================== FEELING ====================

@Composable
private fun FeelingCard(
    feeling: PostMovementFeeling,
    onFeelingChanged: (PostMovementFeeling) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(Strings.Wellness.movementSectionAfter),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PostMovementFeeling.entries.forEach { f ->
                    val isSelected = f == feeling
                    val icon = feelingIcon(f)
                    val label = f.displayName

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onFeelingChanged(f) }
                            .padding(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected)
                                colorScheme.primaryContainer
                            else
                                colorScheme.surfaceContainerHigh,
                            border = if (isSelected)
                                BorderStroke(2.dp, colorScheme.primary)
                            else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(28.dp),
                                    tint = if (isSelected)
                                        colorScheme.primary
                                    else
                                        colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                colorScheme.primary
                            else
                                colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ==================== NOTE ====================

@Composable
private fun NoteCard(
    note: String,
    onNoteChanged: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Strings.Wellness.movementSectionNotes),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            OutlinedTextField(
                value = note,
                onValueChange = onNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.movement_notes_placeholder)) },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            )
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

// ==================== UTILS ====================

private fun feelingIcon(feeling: PostMovementFeeling): ImageVector = when (feeling) {
    PostMovementFeeling.MEGLIO -> Icons.Outlined.SentimentVerySatisfied
    PostMovementFeeling.UGUALE -> Icons.Outlined.SentimentNeutral
    PostMovementFeeling.PEGGIO -> Icons.Outlined.SentimentDissatisfied
}
