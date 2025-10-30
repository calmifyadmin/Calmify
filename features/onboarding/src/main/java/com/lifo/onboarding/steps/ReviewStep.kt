package com.lifo.onboarding.steps

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.onboarding.OnboardingViewModel

/**
 * Step 5: Review & Privacy
 * Displays a comprehensive profile summary and collects privacy preferences.
 *
 * Features Material 3 Expressive design with:
 * - Organized profile summary cards
 * - Privacy preference toggles
 * - GDPR compliance messaging
 * - Final consent checkbox
 */
@Composable
fun ReviewStep(
    viewModel: OnboardingViewModel,
    onEditStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.profileSettings

    // Local state for privacy preferences
    var shareForResearch by remember(settings.shareDataForResearch) {
        mutableStateOf(settings.shareDataForResearch)
    }

    var enableAdvancedInsights by remember(settings.enableAdvancedInsights) {
        mutableStateOf(settings.enableAdvancedInsights)
    }

    // Update viewModel whenever local state changes
    LaunchedEffect(shareForResearch, enableAdvancedInsights) {
        viewModel.updatePrivacyPreferences(
            shareDataForResearch = shareForResearch,
            enableAdvancedInsights = enableAdvancedInsights
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        ExpressiveStepHeader(
            icon = Icons.Default.FactCheck,
            title = "Review Your Profile",
            subtitle = "Make sure everything looks good before we start"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Profile Summary Sections
        ProfileSection(
            title = "Personal Information",
            icon = Icons.Default.Person,
            onEdit = { onEditStep(0) }
        ) {
            SummaryItem("Name", settings.fullName)
            SummaryItem("Date of Birth", settings.dateOfBirth)
            SummaryItem("Gender", settings.gender)
            SummaryItem("Height", "${settings.height} cm")
            SummaryItem("Weight", "${settings.weight} kg")
            if (settings.location.isNotEmpty()) {
                SummaryItem("Location", settings.location)
            }
            settings.getAge()?.let { age ->
                SummaryItem("Age", "$age years")
            }
            settings.getBMI()?.let { bmi ->
                SummaryItem("BMI", String.format("%.1f", bmi))
            }
        }

        ProfileSection(
            title = "Mental Health",
            icon = Icons.Default.HealthAndSafety,
            onEdit = { onEditStep(1) }
        ) {
            if (settings.primaryConcerns.isNotEmpty()) {
                SummaryItem(
                    "Primary Concerns",
                    settings.primaryConcerns.joinToString(", ")
                )
            }
            SummaryItem("History", settings.mentalHealthHistory)
            SummaryItem(
                "Currently in Therapy",
                if (settings.currentTherapy) "Yes" else "No"
            )
            SummaryItem(
                "Taking Medication",
                if (settings.medication) "Yes" else "No"
            )
        }

        ProfileSection(
            title = "Lifestyle",
            icon = Icons.Default.SelfImprovement,
            onEdit = { onEditStep(2) }
        ) {
            SummaryItem("Occupation", settings.occupation)
            SummaryItem("Average Sleep", "${settings.sleepHoursAvg} hours/night")
            SummaryItem("Exercise Frequency", settings.exerciseFrequency)
            SummaryItem("Social Support", settings.socialSupport)
        }

        ProfileSection(
            title = "Wellness Goals",
            icon = Icons.Default.EmojiEvents,
            onEdit = { onEditStep(3) }
        ) {
            if (settings.primaryGoals.isNotEmpty()) {
                SummaryItem(
                    "Goals",
                    settings.primaryGoals.joinToString(", ")
                )
            }
            if (settings.preferredCopingStrategies.isNotEmpty()) {
                SummaryItem(
                    "Coping Strategies",
                    settings.preferredCopingStrategies.joinToString(", ")
                )
            }
        }

        Divider()

        // Privacy Section
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Privacy Preferences",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Privacy Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Your data is encrypted and securely stored. You have full control over how your information is used.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Privacy Toggles
        PrivacyToggle(
            checked = shareForResearch,
            onCheckedChange = { shareForResearch = it },
            title = "Share Data for Research",
            description = "Help improve mental health research by anonymously sharing your data with researchers (fully anonymized)"
        )

        PrivacyToggle(
            checked = enableAdvancedInsights,
            onCheckedChange = { enableAdvancedInsights = it },
            title = "Enable Advanced AI Insights",
            description = "Allow our AI to analyze your patterns and provide personalized recommendations based on your profile and journal entries"
        )

        Divider()

        // GDPR/Compliance Section
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Data Usage & Compliance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "By completing this onboarding, you agree to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                BulletPoint("Your data will be stored securely and encrypted")
                BulletPoint("Only you can access your personal information")
                BulletPoint("You can delete your data at any time")
                BulletPoint("We comply with GDPR and privacy regulations")
                BulletPoint("We will never sell your data to third parties")

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { /* TODO: Open privacy policy */ }) {
                        Text("Privacy Policy")
                    }
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    TextButton(onClick = { /* TODO: Open terms */ }) {
                        Text("Terms of Service")
                    }
                }
            }
        }

        // Medical Disclaimer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "This app is for wellness support and is not a substitute for professional medical care. In case of emergency, contact emergency services immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Bottom padding for completion button
        Spacer(modifier = Modifier.height(100.dp))
    }
}

/**
 * Profile summary section with edit button
 */
@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section Header with Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            Divider()

            // Section Content
            content()
        }
    }
}

/**
 * Summary item (label: value)
 */
@Composable
private fun SummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Privacy toggle with title and description
 */
@Composable
private fun PrivacyToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = checked,
                onCheckedChange = null // Handled by Card's toggleable
            )
        }
    }
}

/**
 * Bullet point for lists
 */
@Composable
private fun BulletPoint(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
