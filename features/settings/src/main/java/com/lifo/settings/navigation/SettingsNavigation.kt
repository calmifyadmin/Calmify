package com.lifo.settings.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.lifo.settings.SettingsScreen
import com.lifo.settings.SettingsViewModel
import com.lifo.settings.subscreens.*
import com.lifo.util.Screen

/**
 * Navigation extension for settings module
 * Includes main settings screen and all subscreens
 */
fun NavGraphBuilder.settingsRoute(
    navController: NavController,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    // Nested navigation graph for settings
    navigation(
        startDestination = SettingsDestination.Main.route,
        route = Screen.Settings.route
    ) {
        // Main Settings Hub
        composable(route = SettingsDestination.Main.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Settings.route)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)

            SettingsScreen(
                onNavigateBack = onNavigateBack,
                onNavigateToPersonalInfo = {
                    navController.navigate(SettingsDestination.PersonalInfo.route)
                },
                onNavigateToHealthInfo = {
                    navController.navigate(SettingsDestination.HealthInfo.route)
                },
                onNavigateToLifestyle = {
                    navController.navigate(SettingsDestination.Lifestyle.route)
                },
                onNavigateToGoals = {
                    navController.navigate(SettingsDestination.Goals.route)
                },
                onLogout = onLogout,
                viewModel = viewModel
            )
        }

        // Personal Info Subscreen
        composable(route = SettingsDestination.PersonalInfo.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Settings.route)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PersonalInfoSettingsScreen(
                fullName = uiState.profileSettings.fullName,
                dateOfBirth = uiState.profileSettings.dateOfBirth,
                gender = uiState.profileSettings.gender,
                height = uiState.profileSettings.height,
                weight = uiState.profileSettings.weight,
                location = uiState.profileSettings.location,
                onNavigateBack = { navController.popBackStack() },
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
                    // Navigate back after save is triggered
                    navController.popBackStack()
                },
                isSaving = uiState.isSaving
            )
        }

        // Health Info Subscreen
        composable(route = SettingsDestination.HealthInfo.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Settings.route)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HealthInfoSettingsScreen(
                primaryConcerns = uiState.profileSettings.primaryConcerns,
                mentalHealthHistory = uiState.profileSettings.mentalHealthHistory,
                currentTherapy = uiState.profileSettings.currentTherapy,
                medication = uiState.profileSettings.medication,
                onNavigateBack = { navController.popBackStack() },
                onSave = { concerns, history, therapy, medication ->
                    viewModel.updateProfileInfo(
                        uiState.profileSettings.copy(
                            primaryConcerns = concerns,
                            mentalHealthHistory = history,
                            currentTherapy = therapy,
                            medication = medication
                        )
                    )
                    // Navigate back after save is triggered
                    navController.popBackStack()
                },
                isSaving = uiState.isSaving
            )
        }

        // Lifestyle Subscreen
        composable(route = SettingsDestination.Lifestyle.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Settings.route)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LifestyleSettingsScreen(
                occupation = uiState.profileSettings.occupation,
                sleepHoursAvg = uiState.profileSettings.sleepHoursAvg,
                exerciseFrequency = uiState.profileSettings.exerciseFrequency,
                socialSupport = uiState.profileSettings.socialSupport,
                onNavigateBack = { navController.popBackStack() },
                onSave = { occupation, sleep, exercise, support ->
                    viewModel.updateProfileInfo(
                        uiState.profileSettings.copy(
                            occupation = occupation,
                            sleepHoursAvg = sleep,
                            exerciseFrequency = exercise,
                            socialSupport = support
                        )
                    )
                    // Navigate back after save is triggered
                    navController.popBackStack()
                },
                isSaving = uiState.isSaving
            )
        }

        // Goals Subscreen
        composable(route = SettingsDestination.Goals.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Settings.route)
            }
            val viewModel: SettingsViewModel = hiltViewModel(parentEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            GoalsSettingsScreen(
                primaryGoals = uiState.profileSettings.primaryGoals,
                preferredCopingStrategies = uiState.profileSettings.preferredCopingStrategies,
                onNavigateBack = { navController.popBackStack() },
                onSave = { goals, strategies ->
                    viewModel.updateProfileInfo(
                        uiState.profileSettings.copy(
                            primaryGoals = goals,
                            preferredCopingStrategies = strategies
                        )
                    )
                    // Navigate back after save is triggered
                    navController.popBackStack()
                },
                isSaving = uiState.isSaving
            )
        }
    }
}

/**
 * Settings destinations for navigation
 */
sealed class SettingsDestination(val route: String) {
    data object Main : SettingsDestination("settings_main")
    data object PersonalInfo : SettingsDestination("settings_personal_info")
    data object HealthInfo : SettingsDestination("settings_health_info")
    data object Lifestyle : SettingsDestination("settings_lifestyle")
    data object Goals : SettingsDestination("settings_goals")
}
