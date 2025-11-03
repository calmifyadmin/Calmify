package com.lifo.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.mongo.repository.ProfileSettingsRepository
import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SettingsViewModel - Manages settings state and user profile updates
 *
 * Features:
 * - Profile settings management
 * - Account operations (logout, delete)
 * - Loading and error states
 * - Real-time profile updates
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileSettingsRepository: ProfileSettingsRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProfileSettings()
    }

    /**
     * Load user profile settings
     */
    private fun loadProfileSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val userId = auth.currentUser?.uid
            if (userId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                }
                return@launch
            }

            when (val result = profileSettingsRepository.getProfileSettings(userId)) {
                is RequestState.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profileSettings = result.data ?: ProfileSettings(),
                            error = null
                        )
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error.message ?: "Failed to load settings"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    /**
     * Update notification preferences
     * Note: These are local app preferences and could be stored in DataStore
     * For now, they're just UI state
     */
    fun updateNotificationSettings(
        dailyReminders: Boolean? = null,
        weeklyInsights: Boolean? = null,
        achievementAlerts: Boolean? = null
    ) {
        _uiState.update { state ->
            state.copy(
                notificationSettings = state.notificationSettings.copy(
                    dailyReminders = dailyReminders ?: state.notificationSettings.dailyReminders,
                    weeklyInsights = weeklyInsights ?: state.notificationSettings.weeklyInsights,
                    achievementAlerts = achievementAlerts ?: state.notificationSettings.achievementAlerts
                )
            )
        }
    }

    /**
     * Update profile information (name, location, etc.)
     */
    fun updateProfileInfo(updatedSettings: ProfileSettings) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            when (val result = profileSettingsRepository.saveProfileSettings(updatedSettings)) {
                is RequestState.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            profileSettings = updatedSettings,
                            error = null
                        )
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = result.error.message ?: "Failed to save profile"
                        )
                    }
                }
                else -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    /**
     * Update privacy settings
     */
    fun updatePrivacySettings(
        shareDataForResearch: Boolean? = null,
        enableAdvancedInsights: Boolean? = null
    ) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isSaving = true,
                    profileSettings = state.profileSettings.copy(
                        shareDataForResearch = shareDataForResearch ?: state.profileSettings.shareDataForResearch,
                        enableAdvancedInsights = enableAdvancedInsights ?: state.profileSettings.enableAdvancedInsights
                    )
                )
            }

            // Save to repository
            val result = profileSettingsRepository.saveProfileSettings(_uiState.value.profileSettings)

            _uiState.update { it.copy(isSaving = false) }

            if (result is RequestState.Error) {
                _uiState.update {
                    it.copy(error = result.error.message ?: "Failed to save settings")
                }
            }
        }
    }

    /**
     * Update app preferences
     * Note: These are local app preferences and could be stored in DataStore
     * For now, they're just UI state
     */
    fun updateAppPreferences(
        darkMode: Boolean? = null,
        language: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                appPreferences = state.appPreferences.copy(
                    darkMode = darkMode ?: state.appPreferences.darkMode,
                    language = language ?: state.appPreferences.language
                )
            )
        }
    }

    /**
     * Logout user
     */
    fun logout(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                auth.signOut()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to logout: ${e.message}")
                }
            }
        }
    }

    /**
     * Show delete account confirmation dialog
     */
    fun showDeleteAccountDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteAccountDialog = show) }
    }

    /**
     * Delete user account
     */
    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, error = null) }

            try {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    // Delete from Firestore
                    profileSettingsRepository.deleteProfileSettings(userId)
                }

                // Delete Firebase Auth account
                auth.currentUser?.delete()?.addOnSuccessListener {
                    _uiState.update { it.copy(isDeleting = false) }
                    onSuccess()
                }?.addOnFailureListener { e ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = "Failed to delete account: ${e.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        error = "Failed to delete account: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Navigate to edit profile
     */
    fun navigateToEditProfile() {
        // Will be handled by navigation in the UI layer
    }
}

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
    val showDeleteAccountDialog: Boolean = false,
    val error: String? = null
)

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
