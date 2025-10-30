package com.lifo.onboarding.steps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lifo.onboarding.OnboardingViewModel
import com.lifo.util.model.MentalHealthConcerns
import com.lifo.util.model.MentalHealthHistory

/**
 * Step 2: Health Information
 * Collects mental health concerns, history, and treatment status.
 *
 * Features Material 3 Expressive design with:
 * - Multi-select chip groups with spring animations
 * - Conditional fields based on switches
 * - Privacy and confidentiality information
 * - Medical disclaimer
 */
@Composable
fun HealthInfoStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.profileSettings

    // Local state for multi-select chips
    var selectedConcerns by remember(settings.primaryConcerns) {
        mutableStateOf(settings.primaryConcerns.toSet())
    }

    var selectedHistory by remember(settings.mentalHealthHistory) {
        mutableStateOf(settings.mentalHealthHistory)
    }

    var inTherapy by remember(settings.currentTherapy) {
        mutableStateOf(settings.currentTherapy)
    }

    var takingMedication by remember(settings.medication) {
        mutableStateOf(settings.medication)
    }

    // Update viewModel whenever local state changes
    LaunchedEffect(selectedConcerns, selectedHistory, inTherapy, takingMedication) {
        viewModel.updateHealthInfo(
            primaryConcerns = selectedConcerns.toList(),
            mentalHealthHistory = selectedHistory,
            currentTherapy = inTherapy,
            medication = takingMedication
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
            icon = Icons.Default.HealthAndSafety,
            title = "Your Mental Health",
            subtitle = "Help us understand how we can support you better"
        )

        // Privacy Info Card
        ExpressiveInfoCard(
            icon = Icons.Default.Lock,
            text = "Your mental health information is confidential and securely stored. It will only be used to provide personalized insights."
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Section 1: Primary Concerns
        Text(
            text = "What brings you here today?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Select all that apply",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Multi-select chips for concerns
        MultiSelectChipGroup(
            items = MentalHealthConcerns.ALL,
            selectedItems = selectedConcerns,
            onSelectionChange = { selectedConcerns = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Divider()

        // Section 2: Mental Health History
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Previous Diagnosis",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Have you been diagnosed with any mental health conditions?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveDropdown(
            value = selectedHistory,
            onValueChange = { selectedHistory = it },
            options = MentalHealthHistory.entries.map { it.displayName },
            label = "Mental Health History",
            leadingIcon = Icons.Default.History
        )

        Spacer(modifier = Modifier.height(8.dp))

        Divider()

        // Section 3: Current Treatment
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Current Treatment",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Therapy Switch
        ExpressiveSwitch(
            checked = inTherapy,
            onCheckedChange = { inTherapy = it },
            label = "Currently in therapy or counseling",
            icon = Icons.Default.Psychology
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Medication Switch
        ExpressiveSwitch(
            checked = takingMedication,
            onCheckedChange = { takingMedication = it },
            label = "Taking medication for mental health",
            icon = Icons.Default.Medication
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
                    text = "This app is not a substitute for professional medical advice, diagnosis, or treatment. If you're experiencing a mental health emergency, please contact emergency services immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
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
 * Expressive switch with label and icon
 */
@Composable
private fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = null // Handled by Row's toggleable
        )
    }
}

/**
 * Reusable expressive dropdown from Step 1
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
 * Reusable info card from Step 1
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
 * Reusable step header from Step 1
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
