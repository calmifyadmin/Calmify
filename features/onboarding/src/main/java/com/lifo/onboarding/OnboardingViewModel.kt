package com.lifo.onboarding

import com.lifo.util.auth.AuthProvider
import com.lifo.util.auth.UserIdentityResolver
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ProfileSettingsRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.model.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── Contract ────────────────────────────────────────────────────────────────────

object OnboardingContract {

    sealed interface Intent : MviContract.Intent {
        data object NextStep : Intent
        data object PreviousStep : Intent

        data class UpdateName(val name: String) : Intent
        data class UpdateUsername(val username: String) : Intent
        data class UpdateMotivations(val motivations: List<String>) : Intent
        data class UpdatePreference(val preference: String) : Intent

        data object CompleteOnboarding : Intent
    }

    data class State(
        val currentStep: Int = 0,
        val totalSteps: Int = 3,
        val name: String = "",
        val username: String = "",
        val usernameError: String? = null,
        val motivations: List<String> = emptyList(),
        val preference: String = "", // "write", "speak", "both"
        val isSaving: Boolean = false,
        val error: String? = null
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object OnboardingCompleted : Effect
        data class ShowError(val message: String) : Effect
    }
}

// ── ViewModel ───────────────────────────────────────────────────────────────────

class OnboardingViewModel constructor(
    private val profileSettingsRepository: ProfileSettingsRepository,
    private val socialGraphRepository: SocialGraphRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<OnboardingContract.Intent, OnboardingContract.State, OnboardingContract.Effect>(
    initialState = OnboardingContract.State()
) {

    @Suppress("unused")
    val uiState: StateFlow<OnboardingContract.State> get() = state

    override fun handleIntent(intent: OnboardingContract.Intent) {
        when (intent) {
            is OnboardingContract.Intent.NextStep -> doNextStep()
            is OnboardingContract.Intent.PreviousStep -> doPreviousStep()
            is OnboardingContract.Intent.UpdateName -> updateState { copy(name = intent.name) }
            is OnboardingContract.Intent.UpdateUsername -> doUpdateUsername(intent.username)
            is OnboardingContract.Intent.UpdateMotivations -> updateState { copy(motivations = intent.motivations) }
            is OnboardingContract.Intent.UpdatePreference -> updateState { copy(preference = intent.preference) }
            is OnboardingContract.Intent.CompleteOnboarding -> doCompleteOnboarding()
        }
    }

    // ── Public delegating functions ──────────────────────────────────────────

    fun nextStep() = onIntent(OnboardingContract.Intent.NextStep)
    fun previousStep() = onIntent(OnboardingContract.Intent.PreviousStep)
    fun updateName(name: String) = onIntent(OnboardingContract.Intent.UpdateName(name))
    fun updateUsername(username: String) = onIntent(OnboardingContract.Intent.UpdateUsername(username))
    fun updateMotivations(motivations: List<String>) = onIntent(OnboardingContract.Intent.UpdateMotivations(motivations))
    fun updatePreference(preference: String) = onIntent(OnboardingContract.Intent.UpdatePreference(preference))

    @Deprecated(
        message = "Use onIntent(CompleteOnboarding) and collect effects instead",
        replaceWith = ReplaceWith("onIntent(OnboardingContract.Intent.CompleteOnboarding)")
    )
    fun completeOnboarding(onSuccess: () -> Unit, onError: (String) -> Unit) {
        onIntent(OnboardingContract.Intent.CompleteOnboarding)
    }

    fun completeOnboarding() = onIntent(OnboardingContract.Intent.CompleteOnboarding)

    fun isCurrentStepValid(): Boolean {
        val s = currentState
        return when (s.currentStep) {
            0 -> s.name.isNotBlank() && (s.username.isBlank() || UserIdentityResolver.isValidUsername(s.username))
            1 -> s.motivations.isNotEmpty()
            2 -> s.preference.isNotBlank()
            else -> false
        }
    }

    // ── Private business logic ──────────────────────────────────────────────

    private fun doNextStep() {
        if (currentState.currentStep < 2) {
            updateState { copy(currentStep = currentStep + 1) }
        }
    }

    private fun doPreviousStep() {
        if (currentState.currentStep > 0) {
            updateState { copy(currentStep = currentStep - 1) }
        }
    }

    private fun doUpdateUsername(username: String) {
        val error = UserIdentityResolver.getUsernameError(username)
        updateState { copy(username = username, usernameError = if (username.isBlank()) null else error) }
    }

    private fun doCompleteOnboarding() {
        scope.launch {
            updateState { copy(isSaving = true, error = null) }

            val s = currentState
            val userId = authProvider.currentUserId

            // Validate username availability if provided
            if (s.username.isNotBlank() && userId != null) {
                when (val availResult = socialGraphRepository.isUsernameAvailable(s.username)) {
                    is RequestState.Success -> {
                        if (!availResult.data) {
                            updateState { copy(isSaving = false, usernameError = "Username gia' in uso") }
                            return@launch
                        }
                    }
                    is RequestState.Error -> {
                        updateState { copy(isSaving = false, error = "Errore verifica username") }
                        return@launch
                    }
                    else -> {}
                }
            }

            // Use the chosen name as displayName; store Google name as fullName fallback
            val googleName = authProvider.currentUserDisplayName ?: ""
            val settings = ProfileSettings(
                displayName = s.name,
                fullName = googleName,
                primaryGoals = s.motivations,
                preferredCopingStrategies = listOf(s.preference),
                isOnboardingCompleted = true
            )

            when (val result = profileSettingsRepository.saveProfileSettings(settings)) {
                is RequestState.Success -> {
                    // Create initial social profile with username and displayName
                    if (userId != null) {
                        val socialUpdates = mutableMapOf<String, Any?>(
                            "displayName" to s.name,
                        )
                        if (s.username.isNotBlank()) {
                            socialUpdates["username"] = s.username
                        }
                        // Set Google photo as initial avatar if available
                        authProvider.currentUserPhotoUrl?.let {
                            socialUpdates["avatarUrl"] = it
                        }
                        socialGraphRepository.updateProfile(userId, socialUpdates)
                    }

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
