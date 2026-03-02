package com.lifo.onboarding

import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ProfileSettingsRepository
import com.lifo.util.model.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── Contract ────────────────────────────────────────────────────────────────────

object OnboardingContract {

    sealed interface Intent : MviContract.Intent {
        data object NextStep : Intent
        data object PreviousStep : Intent
        data class NavigateToStep(val step: Int) : Intent

        data class UpdatePersonalInfo(
            val fullName: String? = null,
            val dateOfBirth: String? = null,
            val gender: String? = null,
            val height: Int? = null,
            val weight: Float? = null,
            val location: String? = null
        ) : Intent

        data class UpdateHealthInfo(
            val primaryConcerns: List<String>? = null,
            val mentalHealthHistory: String? = null,
            val currentTherapy: Boolean? = null,
            val medication: Boolean? = null
        ) : Intent

        data class UpdateLifestyleInfo(
            val occupation: String? = null,
            val sleepHoursAvg: Float? = null,
            val exerciseFrequency: String? = null,
            val socialSupport: String? = null
        ) : Intent

        data class UpdateWellnessGoals(
            val primaryGoals: List<String>? = null,
            val preferredCopingStrategies: List<String>? = null
        ) : Intent

        data class UpdatePrivacyPreferences(
            val shareDataForResearch: Boolean? = null,
            val enableAdvancedInsights: Boolean? = null
        ) : Intent

        data object CompleteOnboarding : Intent
    }

    data class State(
        val currentStep: Int = 0,
        val totalSteps: Int = 5,
        val profileSettings: ProfileSettings = ProfileSettings(),
        val isSaving: Boolean = false,
        val error: String? = null
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object OnboardingCompleted : Effect
        data class ShowError(val message: String) : Effect
    }
}

// ── ViewModel ───────────────────────────────────────────────────────────────────

/**
 * OnboardingViewModel - Manages onboarding flow state (MVI)
 */
class OnboardingViewModel constructor(
    private val profileSettingsRepository: ProfileSettingsRepository
) : MviViewModel<OnboardingContract.Intent, OnboardingContract.State, OnboardingContract.Effect>(
    initialState = OnboardingContract.State()
) {

    // ── Backward-compatible alias ───────────────────────────────────────────
    @Suppress("unused")
    val uiState: StateFlow<OnboardingContract.State> get() = state

    // ── Intent dispatch ─────────────────────────────────────────────────────

    override fun handleIntent(intent: OnboardingContract.Intent) {
        when (intent) {
            is OnboardingContract.Intent.NextStep -> doNextStep()
            is OnboardingContract.Intent.PreviousStep -> doPreviousStep()
            is OnboardingContract.Intent.NavigateToStep -> doNavigateToStep(intent.step)
            is OnboardingContract.Intent.UpdatePersonalInfo -> doUpdatePersonalInfo(intent)
            is OnboardingContract.Intent.UpdateHealthInfo -> doUpdateHealthInfo(intent)
            is OnboardingContract.Intent.UpdateLifestyleInfo -> doUpdateLifestyleInfo(intent)
            is OnboardingContract.Intent.UpdateWellnessGoals -> doUpdateWellnessGoals(intent)
            is OnboardingContract.Intent.UpdatePrivacyPreferences -> doUpdatePrivacyPreferences(intent)
            is OnboardingContract.Intent.CompleteOnboarding -> doCompleteOnboarding()
        }
    }

    // ── Backward-compatible public delegating functions ──────────────────────

    /** Navigate to next step */
    fun nextStep() = onIntent(OnboardingContract.Intent.NextStep)

    /** Navigate to previous step */
    fun previousStep() = onIntent(OnboardingContract.Intent.PreviousStep)

    /** Navigate to specific step (for edit from review screen) */
    fun navigateToStep(step: Int) = onIntent(OnboardingContract.Intent.NavigateToStep(step))

    /** Update personal info (Step 1) */
    fun updatePersonalInfo(
        fullName: String? = null,
        dateOfBirth: String? = null,
        gender: String? = null,
        height: Int? = null,
        weight: Float? = null,
        location: String? = null
    ) = onIntent(
        OnboardingContract.Intent.UpdatePersonalInfo(
            fullName = fullName,
            dateOfBirth = dateOfBirth,
            gender = gender,
            height = height,
            weight = weight,
            location = location
        )
    )

    /** Update health info (Step 2) */
    fun updateHealthInfo(
        primaryConcerns: List<String>? = null,
        mentalHealthHistory: String? = null,
        currentTherapy: Boolean? = null,
        medication: Boolean? = null
    ) = onIntent(
        OnboardingContract.Intent.UpdateHealthInfo(
            primaryConcerns = primaryConcerns,
            mentalHealthHistory = mentalHealthHistory,
            currentTherapy = currentTherapy,
            medication = medication
        )
    )

    /** Update lifestyle info (Step 3) */
    fun updateLifestyleInfo(
        occupation: String? = null,
        sleepHoursAvg: Float? = null,
        exerciseFrequency: String? = null,
        socialSupport: String? = null
    ) = onIntent(
        OnboardingContract.Intent.UpdateLifestyleInfo(
            occupation = occupation,
            sleepHoursAvg = sleepHoursAvg,
            exerciseFrequency = exerciseFrequency,
            socialSupport = socialSupport
        )
    )

    /** Update wellness goals (Step 4) */
    fun updateWellnessGoals(
        primaryGoals: List<String>? = null,
        preferredCopingStrategies: List<String>? = null
    ) = onIntent(
        OnboardingContract.Intent.UpdateWellnessGoals(
            primaryGoals = primaryGoals,
            preferredCopingStrategies = preferredCopingStrategies
        )
    )

    /** Update privacy preferences */
    fun updatePrivacyPreferences(
        shareDataForResearch: Boolean? = null,
        enableAdvancedInsights: Boolean? = null
    ) = onIntent(
        OnboardingContract.Intent.UpdatePrivacyPreferences(
            shareDataForResearch = shareDataForResearch,
            enableAdvancedInsights = enableAdvancedInsights
        )
    )

    /**
     * Save profile and complete onboarding.
     *
     * Backward-compatible overload: callers using callbacks will still compile,
     * but they should migrate to collecting [effects] for [OnboardingContract.Effect].
     */
    @Deprecated(
        message = "Use onIntent(CompleteOnboarding) and collect effects instead",
        replaceWith = ReplaceWith("onIntent(OnboardingContract.Intent.CompleteOnboarding)")
    )
    fun completeOnboarding(onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Fire the intent; callers relying on callbacks can bridge via effects
        onIntent(OnboardingContract.Intent.CompleteOnboarding)
    }

    /** Fire-and-forget variant — result delivered via effects. */
    fun completeOnboarding() = onIntent(OnboardingContract.Intent.CompleteOnboarding)

    /**
     * Validate current step — pure query, not a state mutation.
     */
    fun isCurrentStepValid(): Boolean {
        val s = currentState
        return when (s.currentStep) {
            0 -> { // Personal Info
                s.profileSettings.fullName.isNotBlank() &&
                    s.profileSettings.dateOfBirth.isNotBlank()
            }
            1 -> { // Health
                s.profileSettings.primaryConcerns.isNotEmpty()
            }
            2 -> { // Lifestyle
                s.profileSettings.occupation.isNotBlank() &&
                    s.profileSettings.sleepHoursAvg > 0
            }
            3 -> { // Goals
                s.profileSettings.primaryGoals.isNotEmpty()
            }
            4 -> true // Review — always valid
            else -> false
        }
    }

    // ── Private business logic ──────────────────────────────────────────────

    private fun doNextStep() {
        if (currentState.currentStep < 4) {
            updateState { copy(currentStep = currentStep + 1) }
        }
    }

    private fun doPreviousStep() {
        if (currentState.currentStep > 0) {
            updateState { copy(currentStep = currentStep - 1) }
        }
    }

    private fun doNavigateToStep(step: Int) {
        if (step in 0 until 5) {
            updateState { copy(currentStep = step) }
        }
    }

    private fun doUpdatePersonalInfo(intent: OnboardingContract.Intent.UpdatePersonalInfo) {
        updateState {
            copy(
                profileSettings = profileSettings.copy(
                    fullName = intent.fullName ?: profileSettings.fullName,
                    dateOfBirth = intent.dateOfBirth ?: profileSettings.dateOfBirth,
                    gender = intent.gender ?: profileSettings.gender,
                    height = intent.height ?: profileSettings.height,
                    weight = intent.weight ?: profileSettings.weight,
                    location = intent.location ?: profileSettings.location
                )
            )
        }
    }

    private fun doUpdateHealthInfo(intent: OnboardingContract.Intent.UpdateHealthInfo) {
        updateState {
            copy(
                profileSettings = profileSettings.copy(
                    primaryConcerns = intent.primaryConcerns ?: profileSettings.primaryConcerns,
                    mentalHealthHistory = intent.mentalHealthHistory ?: profileSettings.mentalHealthHistory,
                    currentTherapy = intent.currentTherapy ?: profileSettings.currentTherapy,
                    medication = intent.medication ?: profileSettings.medication
                )
            )
        }
    }

    private fun doUpdateLifestyleInfo(intent: OnboardingContract.Intent.UpdateLifestyleInfo) {
        updateState {
            copy(
                profileSettings = profileSettings.copy(
                    occupation = intent.occupation ?: profileSettings.occupation,
                    sleepHoursAvg = intent.sleepHoursAvg ?: profileSettings.sleepHoursAvg,
                    exerciseFrequency = intent.exerciseFrequency ?: profileSettings.exerciseFrequency,
                    socialSupport = intent.socialSupport ?: profileSettings.socialSupport
                )
            )
        }
    }

    private fun doUpdateWellnessGoals(intent: OnboardingContract.Intent.UpdateWellnessGoals) {
        updateState {
            copy(
                profileSettings = profileSettings.copy(
                    primaryGoals = intent.primaryGoals ?: profileSettings.primaryGoals,
                    preferredCopingStrategies = intent.preferredCopingStrategies ?: profileSettings.preferredCopingStrategies
                )
            )
        }
    }

    private fun doUpdatePrivacyPreferences(intent: OnboardingContract.Intent.UpdatePrivacyPreferences) {
        updateState {
            copy(
                profileSettings = profileSettings.copy(
                    shareDataForResearch = intent.shareDataForResearch ?: profileSettings.shareDataForResearch,
                    enableAdvancedInsights = intent.enableAdvancedInsights ?: profileSettings.enableAdvancedInsights
                )
            )
        }
    }

    private fun doCompleteOnboarding() {
        scope.launch {
            updateState { copy(isSaving = true, error = null) }

            val updatedSettings = currentState.profileSettings.copy(
                isOnboardingCompleted = true
            )

            when (val result = profileSettingsRepository.saveProfileSettings(updatedSettings)) {
                is RequestState.Success -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(OnboardingContract.Effect.OnboardingCompleted)
                }
                is RequestState.Error -> {
                    val errorMessage = result.error.message ?: "Failed to save profile"
                    updateState { copy(isSaving = false, error = errorMessage) }
                    sendEffect(OnboardingContract.Effect.ShowError(errorMessage))
                }
                else -> {
                    updateState { copy(isSaving = false) }
                }
            }
        }
    }
}
