package com.lifo.settings.subscreens

import androidx.compose.animation.animateColorAsState
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
import com.lifo.util.model.CopingStrategies
import com.lifo.util.model.WellnessGoals
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

/**
 * GoalsSettingsScreen - Edit wellness goals and coping strategies
 * Matches GoalsStep.kt from onboarding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsSettingsScreen(
    primaryGoals: List<String>,
    preferredCopingStrategies: List<String>,
    onNavigateBack: () -> Unit,
    onSave: (List<String>, List<String>) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Local state
    var selectedGoals by remember { mutableStateOf(primaryGoals.toSet()) }
    var selectedStrategies by remember { mutableStateOf(preferredCopingStrategies.toSet()) }

    // Validation
    val isValid = selectedGoals.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_gs_title)) },
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
                                selectedGoals.toList(),
                                selectedStrategies.toList()
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
                icon = Icons.Default.EmojiEvents,
                title = stringResource(Res.string.settings_gs_your_goals),
                subtitle = stringResource(Res.string.settings_gs_update_subtitle)
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
                        text = stringResource(Res.string.settings_gs_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 1: Primary Goals
            Text(
                text = stringResource(Res.string.settings_gs_achieve_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_gs_goals_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-select chips for goals
            MultiSelectChipGroup(
                items = WellnessGoals.ALL,
                selectedItems = selectedGoals,
                onSelectionChange = { selectedGoals = it },
                icon = Icons.Default.Flag,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            // Section 2: Coping Strategies
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_gs_cope_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_gs_strategies_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-select chips for coping strategies
            MultiSelectChipGroup(
                items = CopingStrategies.ALL,
                selectedItems = selectedStrategies,
                onSelectionChange = { selectedStrategies = it },
                icon = Icons.Default.Spa,
                enabled = !isSaving
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
                                text = stringResource(Res.string.settings_gs_your_plan),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (selectedGoals.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.settings_gs_goals_selected, selectedGoals.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (selectedStrategies.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.settings_gs_strategies_selected, selectedStrategies.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = stringResource(Res.string.settings_gs_great_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

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
 * Multi-select chip group
 */
@Composable
private fun MultiSelectChipGroup(
    items: List<String>,
    selectedItems: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Expressive multi-select chip
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveMultiSelectChip(
    text: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
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
        enabled = enabled,
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
