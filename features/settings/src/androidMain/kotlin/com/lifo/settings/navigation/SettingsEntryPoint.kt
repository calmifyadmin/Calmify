package com.lifo.settings.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState
import com.lifo.settings.SettingsContract
import com.lifo.settings.SettingsScreen
import com.lifo.settings.SettingsViewModel
import com.lifo.settings.subscreens.*
import java.io.File
import java.util.Locale

private const val SETTINGS_VM_KEY = "settings_shared_vm"
private const val PREFS_NAME = "calmify_prefs"
private const val KEY_LANGUAGE = "language_code"

/**
 * Reads the saved language code from SharedPreferences.
 */
private fun Context.getSavedLanguageCode(): String =
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, "") ?: ""

/**
 * Saves language code and restarts the activity to apply the new locale.
 */
private fun Context.applyAndSaveLanguage(code: String) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LANGUAGE, code)
        .apply()
    (this as? Activity)?.recreate()
}

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
    onNavigateToAiPreferences: () -> Unit = {},
    onNavigateToEnvironment: () -> Unit = {},
    onNavigateToAvatarDebug: () -> Unit = {},
    onLogout: () -> Unit
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)
    val context = LocalContext.current
    val currentLanguageCode by remember { mutableStateOf(context.getSavedLanguageCode()) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsContract.Effect.DataExported -> {
                    try {
                        val exportDir = File(context.cacheDir, "exports")
                        exportDir.mkdirs()
                        val file = File(exportDir, "calmify_data_export.json")
                        file.writeText(effect.json)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Esporta dati Calmify"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Errore nell'export: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                is SettingsContract.Effect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                else -> { /* handled elsewhere */ }
            }
        }
    }

    SettingsScreen(
        onNavigateBack = onNavigateBack,
        onNavigateToPersonalInfo = onNavigateToPersonalInfo,
        onNavigateToHealthInfo = onNavigateToHealthInfo,
        onNavigateToLifestyle = onNavigateToLifestyle,
        onNavigateToGoals = onNavigateToGoals,
        onNavigateToAiPreferences = onNavigateToAiPreferences,
        onNavigateToEnvironment = onNavigateToEnvironment,
        onNavigateToAvatarDebug = onNavigateToAvatarDebug,
        onLogout = onLogout,
        viewModel = viewModel
    )
}

/**
 * Applies a saved locale to a base Context. Call this in Activity.attachBaseContext.
 */
fun applyLocaleToContext(base: Context): Context {
    val langCode = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, "") ?: ""
    if (langCode.isEmpty()) return base
    val locale = Locale.forLanguageTag(langCode)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

// ─── Sub-screen entry points ────────────────────────────────────────────────

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

@Composable
fun SettingsAiPreferencesRouteContent(
    onNavigateBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel(key = SETTINGS_VM_KEY)
    val uiState by viewModel.uiState.collectAsState()

    AiPreferencesScreen(
        currentTone = uiState.profileSettings.aiTone,
        currentReminderFrequency = uiState.profileSettings.reminderFrequency,
        currentTopicsToAvoid = uiState.profileSettings.topicsToAvoid,
        onNavigateBack = onNavigateBack,
        onSave = { tone, reminderFreq, topics ->
            viewModel.updateProfileInfo(
                uiState.profileSettings.copy(
                    aiTone = tone,
                    reminderFrequency = reminderFreq,
                    topicsToAvoid = topics,
                )
            )
            onNavigateBack()
        },
        isSaving = uiState.isSaving,
    )
}
