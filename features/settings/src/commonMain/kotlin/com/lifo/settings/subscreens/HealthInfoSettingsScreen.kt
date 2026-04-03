package com.lifo.settings.subscreens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lifo.util.model.MentalHealthConcerns
import com.lifo.util.model.MentalHealthHistory
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

/**
 * HealthInfoSettingsScreen - Edit mental health information
 * Matches HealthInfoStep.kt from onboarding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthInfoSettingsScreen(
    primaryConcerns: List<String>,
    mentalHealthHistory: String,
    currentTherapy: Boolean,
    medication: Boolean,
    onNavigateBack: () -> Unit,
    onSave: (List<String>, String, Boolean, Boolean) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Local state
    var selectedConcerns by remember { mutableStateOf(primaryConcerns.toSet()) }
    var selectedHistory by remember { mutableStateOf(mentalHealthHistory) }
    var inTherapy by remember { mutableStateOf(currentTherapy) }
    var takingMedication by remember { mutableStateOf(medication) }

    // Validation - at least one concern should be selected
    val isValid = selectedConcerns.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_hi_title)) },
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
                                selectedConcerns.toList(),
                                selectedHistory,
                                inTherapy,
                                takingMedication
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
                icon = Icons.Default.HealthAndSafety,
                title = stringResource(Res.string.settings_hi_your_health),
                subtitle = stringResource(Res.string.settings_hi_update_subtitle)
            )

            // Privacy Info Card
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
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(Res.string.settings_hi_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 1: Primary Concerns
            Text(
                text = stringResource(Res.string.settings_hi_brings_you_here),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_hi_select_all),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-select chips for concerns
            MultiSelectChipGroup(
                items = MentalHealthConcerns.ALL,
                selectedItems = selectedConcerns,
                onSelectionChange = { selectedConcerns = it },
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            // Section 2: Mental Health History
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_hi_diagnosis_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(Res.string.settings_hi_diagnosed_question),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ExpressiveDropdown(
                value = selectedHistory,
                onValueChange = { selectedHistory = it },
                options = MentalHealthHistory.entries.map { it.displayName },
                label = stringResource(Res.string.settings_hi_history_label),
                leadingIcon = Icons.Default.History,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            Divider()

            // Section 3: Current Treatment
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.settings_hi_treatment_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Therapy Switch
            ExpressiveSwitch(
                checked = inTherapy,
                onCheckedChange = { inTherapy = it },
                label = stringResource(Res.string.settings_hi_therapy_label),
                icon = Icons.Default.Psychology,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Medication Switch
            ExpressiveSwitch(
                checked = takingMedication,
                onCheckedChange = { takingMedication = it },
                label = stringResource(Res.string.settings_hi_medication_label),
                icon = Icons.Default.Medication,
                enabled = !isSaving
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Medical Disclaimer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(Res.string.settings_hi_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
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

/**
 * Expressive switch
 */
@Composable
private fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                enabled = enabled
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked && enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = null, // Handled by Row's toggleable
            enabled = enabled
        )
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
