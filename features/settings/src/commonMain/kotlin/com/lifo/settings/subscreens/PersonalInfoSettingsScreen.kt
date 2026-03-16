package com.lifo.settings.subscreens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifo.util.model.Gender
import com.maxkeppeker.sheets.core.models.base.rememberSheetState
import com.maxkeppeler.sheets.calendar.CalendarDialog
import com.maxkeppeler.sheets.calendar.models.CalendarConfig
import com.maxkeppeler.sheets.calendar.models.CalendarSelection

/**
 * PersonalInfoSettingsScreen - Edit personal information
 * Matches PersonalInfoStep.kt from onboarding
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoSettingsScreen(
    fullName: String,
    dateOfBirth: String,
    gender: String,
    height: Int,
    weight: Float,
    location: String,
    onNavigateBack: () -> Unit,
    onSave: (String, String, String, Int, Float, String) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Local state
    var editedFullName by remember { mutableStateOf(fullName) }
    var editedDateOfBirth by remember { mutableStateOf(dateOfBirth) }
    var editedGender by remember { mutableStateOf(gender) }
    var editedHeight by remember { mutableIntStateOf(height) }
    var editedWeight by remember { mutableFloatStateOf(weight) }
    var editedLocation by remember { mutableStateOf(location) }

    // Validation
    val isValid = editedFullName.isNotBlank() &&
                  editedDateOfBirth.isNotBlank() &&
                  editedHeight > 0 &&
                  editedWeight > 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Personal Information") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(
                                editedFullName,
                                editedDateOfBirth,
                                editedGender,
                                editedHeight,
                                editedWeight,
                                editedLocation
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
                        Text(if (isSaving) "Saving..." else "Save Changes")
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
                title = "Let's get to know you",
                subtitle = "Update your personal information",
                icon = Icons.Outlined.Person
            )

            // Full Name
            OutlinedTextField(
                value = editedFullName,
                onValueChange = { editedFullName = it },
                label = { Text("Full Name") },
                placeholder = { Text("Enter your full name") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                shape = MaterialTheme.shapes.large
            )

            // Date of Birth - Date Picker
            DateOfBirthPicker(
                selectedDate = editedDateOfBirth,
                onDateSelected = { editedDateOfBirth = it },
                enabled = !isSaving
            )

            // Gender
            ExpressiveDropdown(
                value = editedGender,
                onValueChange = { editedGender = it },
                label = "Gender",
                options = Gender.entries.map { it.name to it.displayName },
                leadingIcon = Icons.Outlined.Wc,
                enabled = !isSaving
            )

            // Height & Weight Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Height
                OutlinedTextField(
                    value = if (editedHeight > 0) editedHeight.toString() else "",
                    onValueChange = {
                        editedHeight = it.toIntOrNull() ?: 0
                    },
                    label = { Text("Height (cm)") },
                    placeholder = { Text("170") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Height,
                            contentDescription = null
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.large
                )

                // Weight
                OutlinedTextField(
                    value = if (editedWeight > 0) editedWeight.toString() else "",
                    onValueChange = {
                        editedWeight = it.toFloatOrNull() ?: 0f
                    },
                    label = { Text("Weight (kg)") },
                    placeholder = { Text("70") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.MonitorWeight,
                            contentDescription = null
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.large
                )
            }

            // Location (Optional)
            OutlinedTextField(
                value = editedLocation,
                onValueChange = { editedLocation = it },
                label = { Text("Location (Optional)") },
                placeholder = { Text("City, Country") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                shape = MaterialTheme.shapes.large
            )

            // Info card
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
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Your personal information helps us provide tailored insights and recommendations for your mental wellness journey.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp)) // Space for bottom buttons
        }
    }
}

/**
 * Expressive Step Header with animated icon
 */
@Composable
private fun ExpressiveStepHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val scale by rememberInfiniteTransition(label = "icon_scale").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
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
 * Date of Birth Picker with Calendar Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthPicker(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val dateDialog = rememberSheetState()

    // Parse the selected date if available
    val localDate = remember(selectedDate) {
        if (selectedDate.isNotEmpty()) {
            try {
                java.time.LocalDate.parse(selectedDate)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val formattedDate = remember(localDate) {
        localDate?.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: ""
    }

    OutlinedTextField(
        value = formattedDate,
        onValueChange = {},
        readOnly = true,
        label = { Text("Date of Birth") },
        placeholder = { Text("Select your birth date") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = null
            )
        },
        trailingIcon = {
            IconButton(
                onClick = { if (enabled) dateDialog.show() },
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = "Select date"
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { dateDialog.show() },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    )

    // Calendar Dialog
    if (enabled) {
        CalendarDialog(
            state = dateDialog,
            selection = CalendarSelection.Date { date ->
                // Format date as YYYY-MM-DD for storage
                onDateSelected(date.toString())
            },
            config = CalendarConfig(
                monthSelection = true,
                yearSelection = true
            )
        )
    }
}

/**
 * Expressive Dropdown with Material 3 design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<Pair<String, String>>, // value to displayName
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDisplayName = options.find { it.first == value }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedDisplayName,
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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled,
            shape = MaterialTheme.shapes.large
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (optionValue, displayName) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onValueChange(optionValue)
                        expanded = false
                    }
                )
            }
        }
    }
}
