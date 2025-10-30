package com.lifo.onboarding.steps

import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifo.onboarding.OnboardingViewModel
import com.lifo.util.model.ExerciseFrequency
import com.lifo.util.model.SocialSupport
import kotlin.math.roundToInt

/**
 * Step 3: Lifestyle Information
 * Collects occupation, sleep patterns, exercise, and social support data.
 *
 * Features Material 3 Expressive design with:
 * - Custom slider for sleep hours with visual feedback
 * - Dropdown menus for categorical data
 * - Info cards explaining the importance of lifestyle factors
 */
@Composable
fun LifestyleStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.profileSettings

    // Local state
    var occupation by remember(settings.occupation) {
        mutableStateOf(settings.occupation)
    }

    var sleepHours by remember(settings.sleepHoursAvg) {
        mutableFloatStateOf(settings.sleepHoursAvg)
    }

    var exerciseFrequency by remember(settings.exerciseFrequency) {
        mutableStateOf(settings.exerciseFrequency)
    }

    var socialSupport by remember(settings.socialSupport) {
        mutableStateOf(settings.socialSupport)
    }

    // Update viewModel whenever local state changes
    LaunchedEffect(occupation, sleepHours, exerciseFrequency, socialSupport) {
        viewModel.updateLifestyleInfo(
            occupation = occupation,
            sleepHoursAvg = sleepHours,
            exerciseFrequency = exerciseFrequency,
            socialSupport = socialSupport
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        ExpressiveStepHeader(
            icon = Icons.Default.SelfImprovement,
            title = "Your Lifestyle",
            subtitle = "Understanding your daily habits helps us provide better insights"
        )

        // Info Card
        ExpressiveInfoCard(
            icon = Icons.Default.Info,
            text = "Lifestyle factors like sleep, exercise, and social connections significantly impact mental well-being."
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Section 1: Occupation
        Text(
            text = "Occupation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExpressiveDropdown(
            value = occupation,
            onValueChange = { occupation = it },
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
            label = "Current Occupation",
            leadingIcon = Icons.Default.Work
        )

        Spacer(modifier = Modifier.height(8.dp))

        Divider()

        // Section 2: Sleep
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sleep",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Average hours per night",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom sleep slider
        ExpressiveSleepSlider(
            value = sleepHours,
            onValueChange = { sleepHours = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sleep quality indicator
        SleepQualityIndicator(hours = sleepHours)

        Spacer(modifier = Modifier.height(8.dp))

        Divider()

        // Section 3: Exercise
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Physical Activity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "How often do you exercise?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveDropdown(
            value = exerciseFrequency,
            onValueChange = { exerciseFrequency = it },
            options = ExerciseFrequency.entries.map { it.displayName },
            label = "Exercise Frequency",
            leadingIcon = Icons.Default.FitnessCenter
        )

        Spacer(modifier = Modifier.height(8.dp))

        Divider()

        // Section 4: Social Support
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Social Support",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "How would you describe your social support network?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveDropdown(
            value = socialSupport,
            onValueChange = { socialSupport = it },
            options = SocialSupport.entries.map { it.displayName },
            label = "Social Support Level",
            leadingIcon = Icons.Default.Groups
        )

        // Bottom padding for navigation buttons
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Custom expressive sleep slider with visual feedback
 */
@Composable
private fun ExpressiveSleepSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
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
                        text = "hours",
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
 * Sleep quality indicator based on recommended hours (7-9)
 */
@Composable
private fun SleepQualityIndicator(
    hours: Float,
    modifier: Modifier = Modifier
) {
    val (color, icon, message) = when {
        hours < 5f -> Triple(
            MaterialTheme.colorScheme.error,
            Icons.Default.ErrorOutline,
            "Too little sleep can impact mental health"
        )
        hours < 7f -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Warning,
            "Try to aim for 7-9 hours for optimal well-being"
        )
        hours <= 9f -> Triple(
            MaterialTheme.colorScheme.primary,
            Icons.Default.CheckCircle,
            "Great! This is in the recommended range"
        )
        else -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Info,
            "Excessive sleep can also affect mood"
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
 * Reusable expressive dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
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

/**
 * Reusable info card
 */
@Composable
private fun ExpressiveInfoCard(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Reusable step header
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
        // Animated icon
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
