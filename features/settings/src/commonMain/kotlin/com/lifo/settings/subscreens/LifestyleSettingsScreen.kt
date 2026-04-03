package com.lifo.settings.subscreens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ExerciseFrequency
import com.lifo.util.model.SocialSupport
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

/**
 * LifestyleSettingsScreen - Edit lifestyle information
 * Matches LifestyleStep.kt from onboarding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifestyleSettingsScreen(
    occupation: String,
    sleepHoursAvg: Float,
    exerciseFrequency: String,
    socialSupport: String,
    onNavigateBack: () -> Unit,
    onSave: (String, Float, String, String) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Local state
    var editedOccupation by remember { mutableStateOf(occupation) }
    var editedSleepHours by remember { mutableFloatStateOf(sleepHoursAvg) }
    var editedExerciseFrequency by remember { mutableStateOf(exerciseFrequency) }
    var editedSocialSupport by remember { mutableStateOf(socialSupport) }

    // Validation
    val isValid = editedOccupation.isNotBlank() && editedSleepHours > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_ls_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back_cd)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }

                    Button(
                        onClick = {
                            onSave(
                                editedOccupation,
                                editedSleepHours,
                                editedExerciseFrequency,
                                editedSocialSupport
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isValid && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSaving) stringResource(Res.string.settings_saving) else stringResource(Res.string.settings_save_changes))
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            ExpressiveStepHeader(
                icon = Icons.Default.SelfImprovement,
                title = stringResource(Res.string.settings_ls_your_lifestyle),
                subtitle = stringResource(Res.string.settings_ls_update_subtitle)
            )

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(Res.string.settings_ls_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 1: Occupation
            Text(
                text = stringResource(Res.string.settings_ls_occupation_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            ExpressiveDropdown(
                value = editedOccupation,
                onValueChange = { editedOccupation = it },
                options = listOf(
                    "Student",
                    "Employed Full-Time",
                    "Employed Part-Time",
                    "Self-Employed",
                    "Freelancer",
                    "Unemployed",
                    "Retired",
                    "Homemaker",
                    "Other"
                ),
                label = stringResource(Res.string.settings_ls_occupation_label),
                leadingIcon = Icons.Default.Work,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            // Section 2: Sleep
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_ls_sleep_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_ls_sleep_hours),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Custom sleep slider
            ExpressiveSleepSlider(
                value = editedSleepHours,
                onValueChange = { editedSleepHours = it },
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Sleep quality indicator
            SleepQualityIndicator(hours = editedSleepHours)

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            // Section 3: Exercise
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_ls_activity_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_ls_exercise_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExpressiveDropdown(
                value = editedExerciseFrequency,
                onValueChange = { editedExerciseFrequency = it },
                options = ExerciseFrequency.entries.map { it.displayName },
                label = stringResource(Res.string.settings_ls_exercise_label),
                leadingIcon = Icons.Default.FitnessCenter,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            // Section 4: Social Support
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_ls_social_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_ls_social_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExpressiveDropdown(
                value = editedSocialSupport,
                onValueChange = { editedSocialSupport = it },
                options = SocialSupport.entries.map { it.displayName },
                label = stringResource(Res.string.settings_ls_social_label),
                leadingIcon = Icons.Default.Groups,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Expressive Step Header
 */
@Composable
private fun ExpressiveStepHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val scale by rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "icon_pulse"
        )

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .scale(scale),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Expressive sleep slider
 */
@Composable
private fun ExpressiveSleepSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Large display of current value
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(120.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "${value.roundToInt()}",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = stringResource(Res.string.settings_ls_hours_unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..12f,
            steps = 23, // 0.5 hour increments
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Min/Max labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "12h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sleep quality indicator
 */
@Composable
private fun SleepQualityIndicator(
    hours: Float,
    modifier: Modifier = Modifier
) {
    val sleepQualityLow = stringResource(Res.string.settings_ls_sleep_quality_low)
    val sleepQualityMedium = stringResource(Res.string.settings_ls_sleep_quality_medium)
    val sleepQualityGood = stringResource(Res.string.settings_ls_sleep_quality_good)
    val sleepQualityHigh = stringResource(Res.string.settings_ls_sleep_quality_high)
    val (color, icon, message) = when {
        hours < 5f -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Default.ErrorOutline,
            sleepQualityLow
        )
        hours < 7f -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Warning,
            sleepQualityMedium
        )
        hours <= 9f -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Default.CheckCircle,
            sleepQualityGood
        )
        else -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Info,
            sleepQualityHigh
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

/**
 * Expressive dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    leadingIcon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            enabled = enabled,
            shape = MaterialTheme.shapes.large
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
