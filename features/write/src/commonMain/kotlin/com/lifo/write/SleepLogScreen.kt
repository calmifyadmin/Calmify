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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BedtimeOff
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.util.model.SleepDisturbance
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SleepLogScreen(
    state: SleepContract.State,
    onIntent: (SleepContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Time picker dialog state
    var showBedtimePicker by remember { mutableStateOf(false) }
    var showWaketimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Diario del Sonno",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.savedToday) "Aggiorna il log di oggi" else "Come hai dormito?",
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
                        onClick = { onIntent(SleepContract.Intent.Save) },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Salva", tint = colorScheme.primary)
                        }
                    }
                }
            )
        },
        modifier = modifier
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
            // Sleep summary card
            AnimatedSection(0) {
                SleepSummaryCard(sleepHours = state.sleepHours, quality = state.quality)
            }

            // Bedtime — conversational
            AnimatedSection(1) {
                TimeDisplayCard(
                    question = "Quando sei andato a letto?",
                    icon = Icons.Outlined.Bedtime,
                    hour = state.bedtimeHour,
                    minute = state.bedtimeMinute,
                    onClick = { showBedtimePicker = true }
                )
            }

            // Waketime — conversational
            AnimatedSection(2) {
                TimeDisplayCard(
                    question = "A che ore ti sei svegliato?",
                    icon = Icons.Outlined.WbSunny,
                    hour = state.waketimeHour,
                    minute = state.waketimeMinute,
                    onClick = { showWaketimePicker = true }
                )
            }

            // Quality — tappable icons
            AnimatedSection(3) {
                QualitySelector(
                    quality = state.quality,
                    onQualityChanged = { onIntent(SleepContract.Intent.SetQuality(it)) }
                )
            }

            // Disturbances — chips
            AnimatedSection(4) {
                DisturbancesSection(
                    selected = state.disturbances,
                    onToggle = { onIntent(SleepContract.Intent.ToggleDisturbance(it)) }
                )
            }

            // Screen free
            AnimatedSection(5) {
                ScreenFreeCard(
                    screenFree = state.screenFreeLastHour,
                    onToggle = { onIntent(SleepContract.Intent.SetScreenFree(it)) }
                )
            }

            // Save button
            Button(
                onClick = { onIntent(SleepContract.Intent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    if (state.savedToday) "Aggiorna" else "Salva log sonno",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Bedtime picker dialog
    if (showBedtimePicker) {
        TimePickerDialog(
            initialHour = state.bedtimeHour,
            initialMinute = state.bedtimeMinute,
            title = "Quando sei andato a letto?",
            onConfirm = { h, m ->
                onIntent(SleepContract.Intent.SetBedtime(h, m))
                showBedtimePicker = false
            },
            onDismiss = { showBedtimePicker = false }
        )
    }

    // Waketime picker dialog
    if (showWaketimePicker) {
        TimePickerDialog(
            initialHour = state.waketimeHour,
            initialMinute = state.waketimeMinute,
            title = "A che ore ti sei svegliato?",
            onConfirm = { h, m ->
                onIntent(SleepContract.Intent.SetWaketime(h, m))
                showWaketimePicker = false
            },
            onDismiss = { showWaketimePicker = false }
        )
    }
}

// ==================== SLEEP SUMMARY ====================

@Composable
private fun SleepSummaryCard(sleepHours: Float, quality: Int) {
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
                    sleepIcon(sleepHours),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(sleepHours * 10).roundToInt() / 10f}h",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = colorScheme.onPrimaryContainer
                )
                Text(
                    "di sonno",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            // Divider
            Surface(
                modifier = Modifier
                    .width(1.dp)
                    .height(60.dp),
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            ) {}

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    qualityIcon(quality),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$quality/5",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = colorScheme.onPrimaryContainer
                )
                Text(
                    "qualita'",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== TIME DISPLAY (tappable) ====================

@Composable
private fun TimeDisplayCard(
    question: String,
    icon: ImageVector,
    hour: Int,
    minute: Int,
    onClick: () -> Unit
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
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colorScheme.primary
                )
                Text(
                    question,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurface
                )
            }

            // Large tappable time display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hours
                    Text(
                        text = "%02d".format(hour),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2).sp
                        ),
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    // Minutes
                    Text(
                        text = "%02d".format(minute),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2).sp
                        ),
                        color = colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "Tocca per modificare",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== TIME PICKER DIALOG ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("Conferma")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

// ==================== QUALITY SELECTOR ====================

@Composable
private fun QualitySelector(
    quality: Int,
    onQualityChanged: (Int) -> Unit
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
                "Come hai dormito?",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (level in 1..5) {
                    val isSelected = level == quality
                    val icon = qualityIcon(level)
                    val label = qualityLabel(level)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onQualityChanged(level) }
                            .padding(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
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
                        Spacer(Modifier.height(4.dp))
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

// ==================== DISTURBANCES (chips) ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisturbancesSection(
    selected: Set<SleepDisturbance>,
    onToggle: (SleepDisturbance) -> Unit
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
                "Cosa ti ha disturbato durante il sonno?",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepDisturbance.entries.forEach { disturbance ->
                    val isSelected = disturbance in selected

                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggle(disturbance) },
                        label = {
                            Text(
                                disturbance.displayName,
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

            if (selected.isEmpty()) {
                Text(
                    "Nessun disturbo? Ottimo!",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ==================== SCREEN FREE ====================

@Composable
private fun ScreenFreeCard(screenFree: Boolean, onToggle: (Boolean) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Schermi evitati?",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurface
                )
                Text(
                    "Ultima ora prima di dormire",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Switch(checked = screenFree, onCheckedChange = onToggle)
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

private fun sleepIcon(hours: Float): ImageVector = when {
    hours < 5 -> Icons.Outlined.BedtimeOff
    hours < 7 -> Icons.Outlined.Bedtime
    hours < 9 -> Icons.Outlined.NightsStay
    else -> Icons.Outlined.Hotel
}

private fun qualityIcon(quality: Int): ImageVector = when (quality) {
    1 -> Icons.Outlined.SentimentVeryDissatisfied
    2 -> Icons.Outlined.SentimentDissatisfied
    3 -> Icons.Outlined.SentimentNeutral
    4 -> Icons.Outlined.SentimentSatisfied
    5 -> Icons.Outlined.SentimentVerySatisfied
    else -> Icons.Outlined.SentimentNeutral
}

private fun qualityLabel(quality: Int): String = when (quality) {
    1 -> "Pessima"
    2 -> "Male"
    3 -> "Cosi'"
    4 -> "Bene"
    5 -> "Ottima"
    else -> ""
}
