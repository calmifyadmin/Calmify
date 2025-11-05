package com.lifo.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.lifo.settings.components.SettingsNavigationItem
import com.lifo.settings.components.SettingsSectionHeader
import com.lifo.settings.components.SettingsActionButton

/**
 * Settings Screen - Navigation Hub for Profile Settings
 *
 * Clean, organized interface with:
 * - Profile overview card
 * - Navigation to detail screens
 * - Account management
 *
 * Material3 Expressive Design 2025
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToHealthInfo: () -> Unit,
    onNavigateToLifestyle: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Delete account confirmation dialog
    if (uiState.showDeleteAccountDialog) {
        DeleteAccountConfirmationDialog(
            onConfirm = {
                viewModel.deleteAccount(onSuccess = onLogout)
            },
            onDismiss = {
                viewModel.showDeleteAccountDialog(false)
            },
            isDeleting = uiState.isDeleting
        )
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SettingsTopBar(
                onNavigateBack = onNavigateBack,
                userName = uiState.profileSettings.fullName
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Profile Overview Card
                ProfileOverviewCard(
                    profileSettings = uiState.profileSettings,
                    onEditProfile = onNavigateToPersonalInfo
                )

                // Profile Settings Section
                SettingsSectionHeader(title = "Profile Settings")

                ProfileSettingsSection(
                    onNavigateToPersonalInfo = onNavigateToPersonalInfo,
                    onNavigateToHealthInfo = onNavigateToHealthInfo,
                    onNavigateToLifestyle = onNavigateToLifestyle,
                    onNavigateToGoals = onNavigateToGoals
                )

                // Privacy Section
                SettingsSectionHeader(title = "Privacy & Data")
                PrivacySection(
                    profileSettings = uiState.profileSettings,
                    onUpdateSettings = viewModel::updatePrivacySettings,
                    isSaving = uiState.isSaving
                )

                // Account Actions Section
                SettingsSectionHeader(title = "Account")
                AccountActionsSection(
                    onLogout = { viewModel.logout(onLogout) },
                    onDeleteAccount = { viewModel.showDeleteAccountDialog(true) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Settings Top Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    onNavigateBack: () -> Unit,
    userName: String,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (userName.isNotBlank()) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Profile Overview Card
 */
@Composable
private fun ProfileOverviewCard(
    profileSettings: com.lifo.util.model.ProfileSettings,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by rememberInfiniteTransition(label = "profile_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Get user profile image from Firebase Auth
    val user = FirebaseAuth.getInstance().currentUser
    val userProfileImageUrl = user?.photoUrl?.toString()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with animated scale and user profile image
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = userProfileImageUrl,
                        error = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                        placeholder = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic)
                    ),
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // User info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = profileSettings.fullName.ifBlank { "User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val age = profileSettings.getAge()
                if (age != null) {
                    Text(
                        text = "$age years old",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = profileSettings.location.ifBlank { "No location set" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            // Edit button
            FilledTonalIconButton(
                onClick = onEditProfile,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Profile"
                )
            }
        }
    }
}

/**
 * Profile Settings Navigation Section
 */
@Composable
private fun ProfileSettingsSection(
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToHealthInfo: () -> Unit,
    onNavigateToLifestyle: () -> Unit,
    onNavigateToGoals: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsNavigationItem(
            title = "Personal Information",
            subtitle = "Name, age, physical details, location",
            icon = Icons.Outlined.Person,
            onClick = onNavigateToPersonalInfo
        )

        SettingsNavigationItem(
            title = "Mental Health",
            subtitle = "Concerns, history, current treatment",
            icon = Icons.Outlined.Psychology,
            onClick = onNavigateToHealthInfo
        )

        SettingsNavigationItem(
            title = "Lifestyle",
            subtitle = "Sleep, exercise, work, social support",
            icon = Icons.Outlined.SelfImprovement,
            onClick = onNavigateToLifestyle
        )

        SettingsNavigationItem(
            title = "Goals & Strategies",
            subtitle = "Wellness goals and coping strategies",
            icon = Icons.Outlined.EmojiEvents,
            onClick = onNavigateToGoals
        )
    }
}

/**
 * Privacy Settings Section
 */
@Composable
private fun PrivacySection(
    profileSettings: com.lifo.util.model.ProfileSettings,
    onUpdateSettings: (Boolean?, Boolean?) -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrivacySwitchItem(
            title = "Share Data for Research",
            subtitle = "Help improve mental health research with anonymized data",
            icon = Icons.Outlined.Science,
            checked = profileSettings.shareDataForResearch,
            onCheckedChange = { onUpdateSettings(it, null) },
            enabled = !isSaving
        )

        PrivacySwitchItem(
            title = "Advanced AI Insights",
            subtitle = "Enable detailed psychological analysis and recommendations",
            icon = Icons.Outlined.Psychology,
            checked = profileSettings.enableAdvancedInsights,
            onCheckedChange = { onUpdateSettings(null, it) },
            enabled = !isSaving
        )

        if (isSaving) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Privacy Switch Item
 */
@Composable
private fun PrivacySwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * Account Actions Section
 */
@Composable
private fun AccountActionsSection(
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsActionButton(
            title = "Logout",
            icon = Icons.Default.Logout,
            onClick = onLogout,
            isDestructive = false
        )

        SettingsActionButton(
            title = "Delete Account",
            icon = Icons.Default.DeleteForever,
            onClick = onDeleteAccount,
            isDestructive = true
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.medium
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
                    text = "Deleting your account will permanently remove all your data. This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * Delete Account Confirmation Dialog
 */
@Composable
private fun DeleteAccountConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDeleting: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Delete Account?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "This will permanently delete your account and all associated data. This action cannot be undone.\n\nAre you absolutely sure?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onError,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isDeleting) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}
