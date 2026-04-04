package com.lifo.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import coil3.compose.rememberAsyncImagePainter
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
    onNavigateToAiPreferences: () -> Unit = {},
    onNavigateToEnvironment: () -> Unit = {},
    onNavigateToAvatarDebug: () -> Unit = {},
    onLogout: () -> Unit,
    currentLanguageCode: String = "",
    onLanguageSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

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
                    userProfileImageUrl = uiState.userProfileImageUrl,
                    onEditProfile = onNavigateToPersonalInfo
                )

                // Profile Settings Section
                SettingsSectionHeader(title = stringResource(Res.string.settings_profile_section))

                ProfileSettingsSection(
                    onNavigateToPersonalInfo = onNavigateToPersonalInfo,
                    onNavigateToHealthInfo = onNavigateToHealthInfo,
                    onNavigateToLifestyle = onNavigateToLifestyle,
                    onNavigateToGoals = onNavigateToGoals
                )

                // AI Preferences Section
                SettingsSectionHeader(title = stringResource(Res.string.settings_ai_preferences_section))
                SettingsNavigationItem(
                    title = stringResource(Res.string.settings_ai_tone_topic),
                    subtitle = stringResource(Res.string.settings_ai_tone_subtitle),
                    icon = Icons.Default.Psychology,
                    onClick = onNavigateToAiPreferences,
                )

                // Environment Design Section
                SettingsSectionHeader(title = stringResource(Res.string.settings_environment_section))
                SettingsNavigationItem(
                    title = stringResource(Res.string.settings_environment_item_title),
                    subtitle = stringResource(Res.string.settings_environment_item_subtitle),
                    icon = Icons.Default.Spa,
                    onClick = onNavigateToEnvironment,
                )

                // Language Section
                SettingsSectionHeader(title = "Lingua / Language")
                LanguageSection(
                    currentCode = currentLanguageCode,
                    onLanguageSelected = onLanguageSelected
                )

                // Privacy Section
                SettingsSectionHeader(title = stringResource(Res.string.settings_privacy_section))
                PrivacySection(
                    profileSettings = uiState.profileSettings,
                    onUpdateSettings = viewModel::updatePrivacySettings,
                    isSaving = uiState.isSaving
                )

                SettingsActionButton(
                    title = stringResource(Res.string.settings_export_data),
                    icon = Icons.Default.Download,
                    onClick = { viewModel.onIntent(SettingsContract.Intent.ExportUserData) },
                    isLoading = uiState.isExporting,
                )

                // Health Disclaimer
                SettingsSectionHeader(title = stringResource(Res.string.settings_info_section))
                HealthDisclaimerCard()

                // Legal Section — Privacy Policy & ToS
                SettingsSectionHeader(title = stringResource(Res.string.settings_legal_section))
                SettingsNavigationItem(
                    title = stringResource(Res.string.settings_privacy_policy),
                    subtitle = "calmify.app/privacy",
                    icon = Icons.Outlined.PrivacyTip,
                    onClick = { uriHandler.openUri("https://calmify.app/privacy") },
                )
                SettingsNavigationItem(
                    title = stringResource(Res.string.settings_terms_of_service),
                    subtitle = "calmify.app/terms",
                    icon = Icons.Outlined.Description,
                    onClick = { uriHandler.openUri("https://calmify.app/terms") },
                )

                // Developer / Debug
                SettingsSectionHeader(title = stringResource(Res.string.settings_developer_section))
                SettingsNavigationItem(
                    title = stringResource(Res.string.settings_avatar_debug),
                    subtitle = stringResource(Res.string.settings_avatar_debug_subtitle),
                    icon = Icons.Default.BugReport,
                    onClick = onNavigateToAvatarDebug,
                )

                // Account Actions Section
                SettingsSectionHeader(title = stringResource(Res.string.settings_account_section))
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
                    text = stringResource(Res.string.settings_title),
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
                    contentDescription = stringResource(Res.string.back_cd)
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
    userProfileImageUrl: String?,
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
                color = MaterialTheme.colorScheme.onSurface
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = userProfileImageUrl,
                        error = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                        placeholder = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic)
                    ),
                    contentDescription = stringResource(Res.string.settings_profile_picture_cd),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // User info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = profileSettings.fullName.ifBlank { stringResource(Res.string.settings_user_fallback) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val age = profileSettings.getAge()
                if (age != null) {
                    Text(
                        text = stringResource(Res.string.settings_age_format, age),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = profileSettings.location.ifBlank { stringResource(Res.string.settings_no_location) },
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
                    contentDescription = stringResource(Res.string.settings_edit_profile_cd)
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
            title = stringResource(Res.string.settings_personal_info),
            subtitle = stringResource(Res.string.settings_personal_info_subtitle),
            icon = Icons.Outlined.Person,
            onClick = onNavigateToPersonalInfo
        )

        SettingsNavigationItem(
            title = stringResource(Res.string.settings_mental_health),
            subtitle = stringResource(Res.string.settings_mental_health_subtitle),
            icon = Icons.Outlined.Psychology,
            onClick = onNavigateToHealthInfo
        )

        SettingsNavigationItem(
            title = stringResource(Res.string.settings_lifestyle),
            subtitle = stringResource(Res.string.settings_lifestyle_subtitle),
            icon = Icons.Outlined.SelfImprovement,
            onClick = onNavigateToLifestyle
        )

        SettingsNavigationItem(
            title = stringResource(Res.string.settings_goals_strategies),
            subtitle = stringResource(Res.string.settings_goals_subtitle),
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
            title = stringResource(Res.string.settings_share_research_title),
            subtitle = stringResource(Res.string.settings_share_research_subtitle),
            icon = Icons.Outlined.Science,
            checked = profileSettings.shareDataForResearch,
            onCheckedChange = { onUpdateSettings(it, null) },
            enabled = !isSaving
        )

        PrivacySwitchItem(
            title = stringResource(Res.string.settings_advanced_insights_title),
            subtitle = stringResource(Res.string.settings_advanced_insights_subtitle),
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
            title = stringResource(Res.string.settings_logout),
            icon = Icons.Default.Logout,
            onClick = onLogout,
            isDestructive = false
        )

        SettingsActionButton(
            title = stringResource(Res.string.settings_delete_account),
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
                    text = stringResource(Res.string.settings_delete_warning),
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
                text = stringResource(Res.string.settings_delete_confirm_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.settings_delete_confirm_message),
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
                Text(if (isDeleting) stringResource(Res.string.settings_deleting) else stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

/**
 * Language picker section — shows current language and opens a selection dialog.
 */
@Composable
private fun LanguageSection(
    currentCode: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    val languages = listOf(
        Triple("it", "\uD83C\uDDEE\uD83C\uDDF9", "Italiano"),
        Triple("en", "\uD83C\uDDEC\uD83C\uDDE7", "English"),
        Triple("es", "\uD83C\uDDEA\uD83C\uDDF8", "Español"),
        Triple("fr", "\uD83C\uDDEB\uD83C\uDDF7", "Français"),
        Triple("de", "\uD83C\uDDE9\uD83C\uDDEA", "Deutsch"),
        Triple("pt", "\uD83C\uDDE7\uD83C\uDDF7", "Português")
    )

    val currentDisplay = languages.find { it.first == currentCode }
        ?.let { "${it.second} ${it.third}" }
        ?: "\uD83C\uDDEE\uD83C\uDDF9 Italiano"

    SettingsNavigationItem(
        title = "Lingua",
        subtitle = currentDisplay,
        icon = Icons.Default.Language,
        onClick = { showDialog = true },
        modifier = modifier
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Seleziona lingua") },
            text = {
                Column {
                    languages.forEach { (code, flag, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(code)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = flag,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (code == currentCode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Chiudi")
                }
            }
        )
    }
}

/**
 * Health Disclaimer Card (Play Store requirement)
 */
@Composable
private fun HealthDisclaimerCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.health_disclaimer_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
