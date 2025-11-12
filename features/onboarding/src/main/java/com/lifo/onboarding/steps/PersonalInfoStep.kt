package com.lifo.onboarding.steps

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.*
import com.lifo.onboarding.OnboardingViewModel
import com.lifo.util.model.Gender
import com.maxkeppeker.sheets.core.models.base.rememberSheetState
import com.maxkeppeler.sheets.calendar.CalendarDialog
import com.maxkeppeler.sheets.calendar.models.CalendarConfig
import com.maxkeppeler.sheets.calendar.models.CalendarSelection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Step 1: Personal Information
 * Material 3 Expressive design with fluid animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoStep(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(com.lifo.ui.R.raw.robot))
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.profileSettings

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        ExpressiveStepHeader(
            title = "Let's get to know you",
            subtitle = "Personalize your mental wellness journey",
            lottieComposition = composition
        )

        // Full Name
        ExpressiveTextField(
            value = settings.fullName,
            onValueChange = { viewModel.updatePersonalInfo(fullName = it) },
            label = "Full Name",
            placeholder = "Enter your full name"
        )

        // Date of Birth - Date Picker
        DateOfBirthPicker(
            selectedDate = settings.dateOfBirth,
            onDateSelected = { dateString ->
                viewModel.updatePersonalInfo(dateOfBirth = dateString)
            }
        )

        // Gender
        ExpressiveDropdown(
            value = settings.gender,
            onValueChange = { gender ->
                viewModel.updatePersonalInfo(gender = gender)
            },
            label = "Gender",
            options = Gender.entries.map { it.name to it.displayName },
            leadingIcon = Icons.Outlined.Wc
        )

        // Info card
        ExpressiveInfoCard(
            message = "Your personal information helps us provide tailored insights and recommendations for your mental wellness journey."
        )

        Spacer(modifier = Modifier.height(80.dp)) // Space for bottom buttons
    }
}

/**
 * Expressive Step Header with animated icon or Lottie animation
 */
@Composable
private fun ExpressiveStepHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    lottieComposition: LottieComposition? = null
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

    val lottieProgress by animateLottieCompositionAsState(
        composition = lottieComposition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Lottie animation or Icon
        if (lottieComposition != null) {
            LottieAnimation(
                composition = lottieComposition,
                progress = { lottieProgress },
                modifier = Modifier.size(240.dp)
            )
        } else if (icon != null) {
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
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        trailingIcon = {
            IconButton(onClick = { dateDialog.show() }) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = "Select date"
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { dateDialog.show() },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    )

    // Calendar Dialog
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

/**
 * Expressive TextField with Material 3 design
 */
@Composable
private fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null
                )
            }
        } else null,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = MaterialTheme.shapes.large
    )
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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDisplayName = options.find { it.first == value }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
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

/**
 * Expressive Info Card
 */
@Composable
private fun ExpressiveInfoCard(
    message: String,
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
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
