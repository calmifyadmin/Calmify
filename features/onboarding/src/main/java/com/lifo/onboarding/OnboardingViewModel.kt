package com.lifo.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.util.repository.ProfileSettingsRepository
import com.lifo.util.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * OnboardingViewModel - Manages onboarding flow state
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileSettingsRepository: ProfileSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Navigate to next step
     */
    fun nextStep() {
        if (_uiState.value.currentStep < 4) {
            _uiState.update { it.copy(currentStep = it.currentStep + 1) }
        }
    }

    /**
     * Navigate to previous step
     */
    fun previousStep() {
        if (_uiState.value.currentStep > 0) {
            _uiState.update { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    /**
     * Navigate to specific step (for edit from review screen)
     */
    fun navigateToStep(step: Int) {
        if (step in 0 until 5) {
            _uiState.update { it.copy(currentStep = step) }
        }
    }

    /**
     * Update personal info (Step 1)
     */
    fun updatePersonalInfo(
        fullName: String? = null,
        dateOfBirth: String? = null,
        gender: String? = null,
        height: Int? = null,
        weight: Float? = null,
        location: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                profileSettings = state.profileSettings.copy(
                    fullName = fullName ?: state.profileSettings.fullName,
                    dateOfBirth = dateOfBirth ?: state.profileSettings.dateOfBirth,
                    gender = gender ?: state.profileSettings.gender,
                    height = height ?: state.profileSettings.height,
                    weight = weight ?: state.profileSettings.weight,
                    location = location ?: state.profileSettings.location
                )
            )
        }
    }

    /**
     * Update health info (Step 2)
     */
    fun updateHealthInfo(
        primaryConcerns: List<String>? = null,
        mentalHealthHistory: String? = null,
        currentTherapy: Boolean? = null,
        medication: Boolean? = null
    ) {
        _uiState.update { state ->
            state.copy(
                profileSettings = state.profileSettings.copy(
                    primaryConcerns = primaryConcerns ?: state.profileSettings.primaryConcerns,
                    mentalHealthHistory = mentalHealthHistory ?: state.profileSettings.mentalHealthHistory,
                    currentTherapy = currentTherapy ?: state.profileSettings.currentTherapy,
                    medication = medication ?: state.profileSettings.medication
                )
            )
        }
    }

    /**
     * Update lifestyle info (Step 3)
     */
    fun updateLifestyleInfo(
        occupation: String? = null,
        sleepHoursAvg: Float? = null,
        exerciseFrequency: String? = null,
        socialSupport: String? = null
    ) {
        _uiState.update { state ->
            state.copy(
                profileSettings = state.profileSettings.copy(
                    occupation = occupation ?: state.profileSettings.occupation,
                    sleepHoursAvg = sleepHoursAvg ?: state.profileSettings.sleepHoursAvg,
                    exerciseFrequency = exerciseFrequency ?: state.profileSettings.exerciseFrequency,
                    socialSupport = socialSupport ?: state.profileSettings.socialSupport
                )
            )
        }
    }

    /**
     * Update wellness goals (Step 4)
     */
    fun updateWellnessGoals(
        primaryGoals: List<String>? = null,
        preferredCopingStrategies: List<String>? = null
    ) {
        _uiState.update { state ->
            state.copy(
                profileSettings = state.profileSettings.copy(
                    primaryGoals = primaryGoals ?: state.profileSettings.primaryGoals,
                    preferredCopingStrategies = preferredCopingStrategies ?: state.profileSettings.preferredCopingStrategies
                )
            )
        }
    }

    /**
     * Update privacy preferences
     */
    fun updatePrivacyPreferences(
        shareDataForResearch: Boolean? = null,
        enableAdvancedInsights: Boolean? = null
    ) {
        _uiState.update { state ->
            state.copy(
                profileSettings = state.profileSettings.copy(
                    shareDataForResearch = shareDataForResearch ?: state.profileSettings.shareDataForResearch,
                    enableAdvancedInsights = enableAdvancedInsights ?: state.profileSettings.enableAdvancedInsights
                )
            )
        }
    }

    /**
     * Save profile and complete onboarding
     */
    fun completeOnboarding(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            // Mark onboarding as completed
            val updatedSettings = _uiState.value.profileSettings.copy(
                isOnboardingCompleted = true
            )

            when (val result = profileSettingsRepository.saveProfileSettings(updatedSettings)) {
                is RequestState.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    onSuccess()
                }
                is RequestState.Error -> {
                    val errorMessage = result.error.message ?: "Failed to save profile"
                    _uiState.update { it.copy(isSaving = false, error = errorMessage) }
                    onError(errorMessage)
                }
                else -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    /**
     * Validate current step
     */
    fun isCurrentStepValid(): Boolean {
        val state = _uiState.value
        return when (state.currentStep) {
            0 -> { // Personal Info
                state.profileSettings.fullName.isNotBlank() &&
                state.profileSettings.dateOfBirth.isNotBlank()
                // Removed: height, weight, location are now optional
            }
            1 -> { // Health
                state.profileSettings.primaryConcerns.isNotEmpty()
            }
            2 -> { // Lifestyle
                state.profileSettings.occupation.isNotBlank() &&
                state.profileSettings.sleepHoursAvg > 0
            }
            3 -> { // Goals
                state.profileSettings.primaryGoals.isNotEmpty()
            }
            4 -> true // Review - always valid
            else -> false
        }
    }
}

/**
 * UI State for Onboarding
 */
data class OnboardingUiState(
    val currentStep: Int = 0,
    val totalSteps: Int = 5,
    val profileSettings: ProfileSettings = ProfileSettings(),
    val isSaving: Boolean = false,
    val error: String? = null
)
