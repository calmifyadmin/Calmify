package com.lifo.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState
import com.lifo.settings.SettingsScreen
import com.lifo.settings.SettingsViewModel
import com.lifo.settings.subscreens.*

/**
 * Shared ViewModel key for all Settings screens.
 * This ensures the same ViewModel instance is used across the settings hub
 * and all its sub-screens, replicating the behavior of the old nested nav graph.
 */
private const val SETTINGS_VM_KEY = "settings_shared_vm"

/**
 * Public entry point for the main Settings hub screen.
 */
@Composable
fun SettingsMainRouteContent(
    onNavigateBack: () -> Unit,
    onNavigateToPersonalInfo: () -> Unit,
    onNavigateToHealthInfo: () -> Unit,
    onNavigateToLifestyle: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onLogout: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)

    SettingsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToPersonalInfo = onNavigateToPersonalInfo,
        onNavigateToHealthInfo = onNavigateToHealthInfo,
        onNavigateToLifestyle = onNavigateToLifestyle,
        onNavigateToGoals = onNavigateToGoals,
        onLogout = onLogout,
        viewModel = viewModel
    )
}

/**
 * Public entry point for the Personal Info settings sub-screen.
 */
@Composable
fun SettingsPersonalInfoRouteContent(
    onNavigateBack: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)
    val uiState by viewModel.uiState.collectAsState()

    PersonalInfoSettingsScreen(
        fullName = uiState.profileSettings.fullName,
        dateOfBirth = uiState.profileSettings.dateOfBirth,
        gender = uiState.profileSettings.gender,
        height = uiState.profileSettings.height,
        weight = uiState.profileSettings.weight,
        location = uiState.profileSettings.location,
        onNavigateBack = onNavigateBack,
        onSave = { fullName, dateOfBirth, gender, height, weight, location ->
            viewModel.updateProfileInfo(
                uiState.profileSettings.copy(
                    fullName = fullName,
                    dateOfBirth = dateOfBirth,
                    gender = gender,
                    height = height,
                    weight = weight,
                    location = location
                )
            )
            onNavigateBack()
        },
        isSaving = uiState.isSaving
    )
}

/**
 * Public entry point for the Health Info settings sub-screen.
 */
@Composable
fun SettingsHealthInfoRouteContent(
    onNavigateBack: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)
    val uiState by viewModel.uiState.collectAsState()

    HealthInfoSettingsScreen(
        primaryConcerns = uiState.profileSettings.primaryConcerns,
        mentalHealthHistory = uiState.profileSettings.mentalHealthHistory,
        currentTherapy = uiState.profileSettings.currentTherapy,
        medication = uiState.profileSettings.medication,
        onNavigateBack = onNavigateBack,
        onSave = { concerns, history, therapy, medication ->
            viewModel.updateProfileInfo(
                uiState.profileSettings.copy(
                    primaryConcerns = concerns,
                    mentalHealthHistory = history,
                    currentTherapy = therapy,
                    medication = medication
                )
            )
            onNavigateBack()
        },
        isSaving = uiState.isSaving
    )
}

/**
 * Public entry point for the Lifestyle settings sub-screen.
 */
@Composable
fun SettingsLifestyleRouteContent(
    onNavigateBack: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)
    val uiState by viewModel.uiState.collectAsState()

    LifestyleSettingsScreen(
        occupation = uiState.profileSettings.occupation,
        sleepHoursAvg = uiState.profileSettings.sleepHoursAvg,
        exerciseFrequency = uiState.profileSettings.exerciseFrequency,
        socialSupport = uiState.profileSettings.socialSupport,
        onNavigateBack = onNavigateBack,
        onSave = { occupation, sleep, exercise, support ->
            viewModel.updateProfileInfo(
                uiState.profileSettings.copy(
                    occupation = occupation,
                    sleepHoursAvg = sleep,
                    exerciseFrequency = exercise,
                    socialSupport = support
                )
            )
            onNavigateBack()
        },
        isSaving = uiState.isSaving
    )
}

/**
 * Public entry point for the Goals settings sub-screen.
 */
@Composable
fun SettingsGoalsRouteContent(
    onNavigateBack: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)
    val uiState by viewModel.uiState.collectAsState()

    GoalsSettingsScreen(
        primaryGoals = uiState.profileSettings.primaryGoals,
        preferredCopingStrategies = uiState.profileSettings.preferredCopingStrategies,
        onNavigateBack = onNavigateBack,
        onSave = { goals, strategies ->
            viewModel.updateProfileInfo(
                uiState.profileSettings.copy(
                    primaryGoals = goals,
                    preferredCopingStrategies = strategies
                )
            )
            onNavigateBack()
        },
        isSaving = uiState.isSaving
    )
}
