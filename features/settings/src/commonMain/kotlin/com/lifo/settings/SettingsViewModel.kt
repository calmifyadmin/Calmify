package com.lifo.settings

import com.lifo.util.currentTimeMillis
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ChatRepository
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.ProfileSettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────────────────────────
// MVI Contract
// ──────────────────────────────────────────────────────────────────────────────

object SettingsContract {

    sealed interface Intent : MviContract.Intent {
        data class UpdateNotificationSettings(
            val dailyReminders: Boolean? = null,
            val weeklyInsights: Boolean? = null,
            val achievementAlerts: Boolean? = null
        ) : Intent

        data class UpdateProfileInfo(val updatedSettings: ProfileSettings) : Intent

        data class UpdatePrivacySettings(
            val shareDataForResearch: Boolean? = null,
            val enableAdvancedInsights: Boolean? = null
        ) : Intent

        data class UpdateAppPreferences(
            val darkMode: Boolean? = null,
            val language: String? = null
        ) : Intent

        data object Logout : Intent
        data class ShowDeleteAccountDialog(val show: Boolean) : Intent
        data object DeleteAccount : Intent
        data object ExportUserData : Intent
        data object ClearError : Intent
    }

    // State = SettingsUiState (defined below, implements MviContract.State)

    sealed interface Effect : MviContract.Effect {
        data object LogoutSuccess : Effect
        data object AccountDeleted : Effect
        data class ShowError(val message: String) : Effect
        data object ProfileSaved : Effect
        data class DataExported(val json: String) : Effect
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────────────────────────

/**
 * SettingsViewModel - Manages settings state and user profile updates (MVI)
 *
 * Features:
 * - Profile settings management
 * - Account operations (logout, delete)
 * - Loading and error states
 * - Real-time profile updates
 */
class SettingsViewModel constructor(
    private val profileSettingsRepository: ProfileSettingsRepository,
    private val authProvider: AuthProvider,
    private val diaryRepository: MongoRepository,
    private val chatRepository: ChatRepository,
    private val mediaUploadRepository: MediaUploadRepository,
) : MviViewModel<SettingsContract.Intent, SettingsUiState, SettingsContract.Effect>(
    initialState = SettingsUiState()
) {

    /** Backward-compatible alias so existing callers keep compiling. */
    val uiState: StateFlow<SettingsUiState> get() = state

    init {
        loadProfileSettings()
    }

    // ── Intent dispatch ─────────────────────────────────────────────────────

    override fun handleIntent(intent: SettingsContract.Intent) {
        when (intent) {
            is SettingsContract.Intent.UpdateNotificationSettings -> handleUpdateNotificationSettings(
                intent.dailyReminders,
                intent.weeklyInsights,
                intent.achievementAlerts
            )
            is SettingsContract.Intent.UpdateProfileInfo -> handleUpdateProfileInfo(intent.updatedSettings)
            is SettingsContract.Intent.UpdatePrivacySettings -> handleUpdatePrivacySettings(
                intent.shareDataForResearch,
                intent.enableAdvancedInsights
            )
            is SettingsContract.Intent.UpdateAppPreferences -> handleUpdateAppPreferences(
                intent.darkMode,
                intent.language
            )
            is SettingsContract.Intent.Logout -> handleLogout()
            is SettingsContract.Intent.ShowDeleteAccountDialog -> handleShowDeleteAccountDialog(intent.show)
            is SettingsContract.Intent.DeleteAccount -> handleDeleteAccount()
            is SettingsContract.Intent.ExportUserData -> handleExportUserData()
            is SettingsContract.Intent.ClearError -> updateState { copy(error = null) }
        }
    }

    // ── Backward-compatible public wrappers ─────────────────────────────────

    fun updateNotificationSettings(
        dailyReminders: Boolean? = null,
        weeklyInsights: Boolean? = null,
        achievementAlerts: Boolean? = null
    ) = onIntent(
        SettingsContract.Intent.UpdateNotificationSettings(dailyReminders, weeklyInsights, achievementAlerts)
    )

    fun updateProfileInfo(updatedSettings: ProfileSettings) =
        onIntent(SettingsContract.Intent.UpdateProfileInfo(updatedSettings))

    fun updatePrivacySettings(
        shareDataForResearch: Boolean? = null,
        enableAdvancedInsights: Boolean? = null
    ) = onIntent(
        SettingsContract.Intent.UpdatePrivacySettings(shareDataForResearch, enableAdvancedInsights)
    )

    fun updateAppPreferences(
        darkMode: Boolean? = null,
        language: String? = null
    ) = onIntent(SettingsContract.Intent.UpdateAppPreferences(darkMode, language))

    fun logout(onSuccess: () -> Unit) {
        // Legacy wrapper: store callback BEFORE dispatching so it's available when the
        // handler completes. For full MVI, callers should observe effects instead.
        _legacyLogoutCallback = onSuccess
        onIntent(SettingsContract.Intent.Logout)
    }

    fun showDeleteAccountDialog(show: Boolean) =
        onIntent(SettingsContract.Intent.ShowDeleteAccountDialog(show))

    fun deleteAccount(onSuccess: () -> Unit) {
        // Legacy wrapper — see logout() above
        _legacyDeleteCallback = onSuccess
        onIntent(SettingsContract.Intent.DeleteAccount)
    }

    fun clearError() = onIntent(SettingsContract.Intent.ClearError)

    // ── Legacy callback bridge ──────────────────────────────────────────────
    // These temporary fields bridge the old callback-based API until
    // SettingsScreen is migrated to collect effects directly.

    private var _legacyLogoutCallback: (() -> Unit)? = null
    private var _legacyDeleteCallback: (() -> Unit)? = null

    // ── Private handlers ────────────────────────────────────────────────────

    private fun loadProfileSettings() {
        scope.launch {
            updateState {
                copy(
                    isLoading = true,
                    error = null
                )
            }

            val userId = authProvider.currentUserId
            if (userId == null) {
                updateState { copy(isLoading = false, error = "User not authenticated") }
                return@launch
            }

            when (val result = profileSettingsRepository.getProfileSettings(userId)) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            isLoading = false,
                            profileSettings = result.data ?: ProfileSettings(),
                            userProfileImageUrl = authProvider.currentUserPhotoUrl,
                            error = null
                        )
                    }
                }
                is RequestState.Error -> {
                    updateState {
                        copy(
                            isLoading = false,
                            error = result.error.message ?: "Failed to load settings"
                        )
                    }
                }
                else -> {
                    updateState { copy(isLoading = false) }
                }
            }
        }
    }

    private fun handleUpdateNotificationSettings(
        dailyReminders: Boolean?,
        weeklyInsights: Boolean?,
        achievementAlerts: Boolean?
    ) {
        updateState {
            copy(
                notificationSettings = notificationSettings.copy(
                    dailyReminders = dailyReminders ?: notificationSettings.dailyReminders,
                    weeklyInsights = weeklyInsights ?: notificationSettings.weeklyInsights,
                    achievementAlerts = achievementAlerts ?: notificationSettings.achievementAlerts
                )
            )
        }
    }

    private fun handleUpdateProfileInfo(updatedSettings: ProfileSettings) {
        scope.launch {
            updateState { copy(isSaving = true, error = null) }

            when (val result = profileSettingsRepository.saveProfileSettings(updatedSettings)) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            isSaving = false,
                            profileSettings = updatedSettings,
                            error = null
                        )
                    }
                    sendEffect(SettingsContract.Effect.ProfileSaved)
                }
                is RequestState.Error -> {
                    updateState {
                        copy(
                            isSaving = false,
                            error = result.error.message ?: "Failed to save profile"
                        )
                    }
                    sendEffect(
                        SettingsContract.Effect.ShowError(
                            result.error.message ?: "Failed to save profile"
                        )
                    )
                }
                else -> {
                    updateState { copy(isSaving = false) }
                }
            }
        }
    }

    private fun handleUpdatePrivacySettings(
        shareDataForResearch: Boolean?,
        enableAdvancedInsights: Boolean?
    ) {
        scope.launch {
            updateState {
                copy(
                    isSaving = true,
                    profileSettings = profileSettings.copy(
                        shareDataForResearch = shareDataForResearch ?: profileSettings.shareDataForResearch,
                        enableAdvancedInsights = enableAdvancedInsights ?: profileSettings.enableAdvancedInsights
                    )
                )
            }

            val result = profileSettingsRepository.saveProfileSettings(currentState.profileSettings)

            updateState { copy(isSaving = false) }

            if (result is RequestState.Error) {
                updateState { copy(error = result.error.message ?: "Failed to save settings") }
                sendEffect(
                    SettingsContract.Effect.ShowError(
                        result.error.message ?: "Failed to save settings"
                    )
                )
            }
        }
    }

    private fun handleUpdateAppPreferences(
        darkMode: Boolean?,
        language: String?
    ) {
        updateState {
            copy(
                appPreferences = appPreferences.copy(
                    darkMode = darkMode ?: appPreferences.darkMode,
                    language = language ?: appPreferences.language
                )
            )
        }
    }

    private fun handleLogout() {
        scope.launch {
            try {
                authProvider.signOut()
                sendEffect(SettingsContract.Effect.LogoutSuccess)
                _legacyLogoutCallback?.invoke()
                _legacyLogoutCallback = null
            } catch (e: Exception) {
                updateState { copy(error = "Failed to logout: ${e.message}") }
                sendEffect(SettingsContract.Effect.ShowError("Failed to logout: ${e.message}"))
            }
        }
    }

    private fun handleShowDeleteAccountDialog(show: Boolean) {
        updateState { copy(showDeleteAccountDialog = show) }
    }

    private fun handleExportUserData() {
        val userId = authProvider.currentUserId ?: return

        scope.launch {
            updateState { copy(isExporting = true) }

            try {
                val sb = StringBuilder()
                sb.appendLine("{")
                sb.appendLine("  \"exportDate\": \"${currentTimeMillis()}\",")
                sb.appendLine("  \"userId\": \"$userId\",")

                // Profile
                val ps = currentState.profileSettings
                sb.appendLine("  \"profile\": {")
                sb.appendLine("    \"fullName\": \"${ps.fullName.replace("\"", "\\\"")}\",")
                sb.appendLine("    \"dateOfBirth\": \"${ps.dateOfBirth}\",")
                sb.appendLine("    \"gender\": \"${ps.gender}\",")
                sb.appendLine("    \"location\": \"${ps.location.replace("\"", "\\\"")}\"")
                sb.appendLine("  },")

                // Diaries
                val diariesResult = diaryRepository.getAllDiaries().first { it !is RequestState.Loading }
                sb.appendLine("  \"diaries\": [")
                if (diariesResult is RequestState.Success) {
                    val allDiaries = diariesResult.data.values.flatten()
                    allDiaries.forEachIndexed { idx, diary ->
                        sb.append("    {")
                        sb.append("\"id\":\"${diary._id}\",")
                        sb.append("\"title\":\"${diary.title.replace("\"", "\\\"")}\",")
                        sb.append("\"description\":\"${diary.description.replace("\"", "\\\"").replace("\n", "\\n")}\",")
                        sb.append("\"mood\":\"${diary.mood}\",")
                        sb.append("\"date\":${diary.dateMillis},")
                        sb.append("\"dayKey\":\"${diary.dayKey}\"")
                        sb.append("}")
                        if (idx < allDiaries.size - 1) sb.append(",")
                        sb.appendLine()
                    }
                }
                sb.appendLine("  ],")

                // Chat sessions
                val sessionsResult = chatRepository.getAllSessions().first { it !is RequestState.Loading }
                sb.appendLine("  \"chatSessions\": [")
                if (sessionsResult is RequestState.Success) {
                    sessionsResult.data.forEachIndexed { idx, session ->
                        sb.append("    {")
                        sb.append("\"id\":\"${session.id}\",")
                        sb.append("\"title\":\"${session.title.replace("\"", "\\\"")}\",")
                        sb.append("\"createdAt\":${session.createdAt},")
                        sb.append("\"messageCount\":${session.messageCount}")
                        sb.append("}")
                        if (idx < sessionsResult.data.size - 1) sb.append(",")
                        sb.appendLine()
                    }
                }
                sb.appendLine("  ]")
                sb.appendLine("}")

                updateState { copy(isExporting = false) }
                sendEffect(SettingsContract.Effect.DataExported(sb.toString()))
            } catch (e: Exception) {
                updateState { copy(isExporting = false) }
                sendEffect(SettingsContract.Effect.ShowError("Errore nell'export: ${e.message}"))
            }
        }
    }

    private fun handleDeleteAccount() {
        scope.launch {
            updateState { copy(isDeleting = true, error = null) }

            try {
                val userId = authProvider.currentUserId
                if (userId != null) {
                    // 1. Delete all diaries
                    diaryRepository.deleteAllDiaries()

                    // 2. Delete all chat sessions
                    chatRepository.deleteAllSessions()

                    // 3. Delete all uploaded images from Storage
                    mediaUploadRepository.deleteAllUserMedia(userId)

                    // 4. Delete profile settings from Firestore
                    profileSettingsRepository.deleteProfileSettings(userId)
                }

                // 5. Sign out from Firebase Auth
                authProvider.signOut()

                updateState { copy(isDeleting = false) }
                sendEffect(SettingsContract.Effect.AccountDeleted)
                _legacyDeleteCallback?.invoke()
                _legacyDeleteCallback = null
            } catch (e: Exception) {
                updateState {
                    copy(
                        isDeleting = false,
                        error = "Errore nella cancellazione: ${e.message}"
                    )
                }
                sendEffect(
                    SettingsContract.Effect.ShowError(
                        "Errore nella cancellazione: ${e.message}"
                    )
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// State & supporting data classes
// ──────────────────────────────────────────────────────────────────────────────

/**
 * UI State for Settings screen
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val profileSettings: ProfileSettings = ProfileSettings(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val appPreferences: AppPreferences = AppPreferences(),
    val userProfileImageUrl: String? = null,
    val showDeleteAccountDialog: Boolean = false,
    val error: String? = null,
    val isExporting: Boolean = false,
) : MviContract.State

/**
 * Notification Settings
 */
data class NotificationSettings(
    val dailyReminders: Boolean = true,
    val weeklyInsights: Boolean = true,
    val achievementAlerts: Boolean = true
)

/**
 * App Preferences
 */
data class AppPreferences(
    val darkMode: Boolean = false,
    val language: String = "English"
)
