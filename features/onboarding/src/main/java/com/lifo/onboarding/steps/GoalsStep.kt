package com.lifo.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseInOutCubic
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
import com.lifo.util.model.CopingStrategies
import com.lifo.util.model.WellnessGoals

/**
 * Step 4: Wellness Goals
 * Collects user's primary goals and preferred coping strategies.
 *
 * Features Material 3 Expressive design with:
 * - Multi-select chip groups for goals and coping strategies
 * - Motivational messaging
 * - Goal-setting guidance
 */
@Composable
fun GoalsStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.profileSettings

    // Local state for multi-select chips
    var selectedGoals by remember(settings.primaryGoals) {
        mutableStateOf(settings.primaryGoals.toSet())
    }

    var selectedStrategies by remember(settings.preferredCopingStrategies) {
        mutableStateOf(settings.preferredCopingStrategies.toSet())
    }

    // Update viewModel whenever local state changes
    LaunchedEffect(selectedGoals, selectedStrategies) {
        viewModel.updateWellnessGoals(
            primaryGoals = selectedGoals.toList(),
            preferredCopingStrategies = selectedStrategies.toList()
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
            icon = Icons.Default.EmojiEvents,
            title = "Your Wellness Goals",
            subtitle = "Let's work together towards a healthier you"
        )

        // Motivational Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Setting clear goals and identifying effective coping strategies are key steps towards better mental health. Small, consistent steps lead to meaningful change.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section 1: Primary Goals
        Text(
            text = "What do you want to achieve?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Select your primary wellness goals",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-select chips for goals
        MultiSelectChipGroup(
            items = WellnessGoals.ALL,
            selectedItems = selectedGoals,
            onSelectionChange = { selectedGoals = it },
            icon = Icons.Default.Flag
        )

        Spacer(modifier = Modifier.height(8.dp))

        Divider()

        // Section 2: Coping Strategies
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "How do you like to cope?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Select coping strategies that work for you",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-select chips for coping strategies
        MultiSelectChipGroup(
            items = CopingStrategies.ALL,
            selectedItems = selectedStrategies,
            onSelectionChange = { selectedStrategies = it },
            icon = Icons.Default.Spa
        )

        // Progress Summary Card
        if (selectedGoals.isNotEmpty() || selectedStrategies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Your Plan",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (selectedGoals.isNotEmpty()) {
                        Text(
                            text = "${selectedGoals.size} goal${if (selectedGoals.size > 1) "s" else ""} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (selectedStrategies.isNotEmpty()) {
                        Text(
                            text = "${selectedStrategies.size} coping strateg${if (selectedStrategies.size > 1) "ies" else "y"} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "Great start! We'll use this to personalize your experience.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Bottom padding for navigation buttons
        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * Multi-select chip group with Material 3 Expressive animations
 */
@Composable
private fun MultiSelectChipGroup(
    items: List<String>,
    selectedItems: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Split into rows for better layout
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    val isSelected = item in selectedItems

                    ExpressiveMultiSelectChip(
                        text = item,
                        isSelected = isSelected,
                        onToggle = {
                            val newSelection = if (isSelected) {
                                selectedItems - item
                            } else {
                                selectedItems + item
                            }
                            onSelectionChange(newSelection)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Fill empty space if odd number of items
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Individual multi-select chip with expressive animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveMultiSelectChip(
    text: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(),
        label = "chip_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(),
        label = "chip_color"
    )

    FilterChip(
        selected = isSelected,
        onClick = onToggle,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null,
        modifier = modifier.scale(scale),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = if (isSelected) 2.dp else 1.dp,
            selectedBorderWidth = 2.dp
        )
    )
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
