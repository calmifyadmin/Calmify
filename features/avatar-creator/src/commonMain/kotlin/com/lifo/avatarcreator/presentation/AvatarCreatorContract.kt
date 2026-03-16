package com.lifo.avatarcreator.presentation

import com.lifo.util.model.*
import com.lifo.util.mvi.MviContract

object AvatarCreatorContract {

    sealed interface Intent : MviContract.Intent {
        // ── Navigation ──
        data object NextSection : Intent
        data object PreviousSection : Intent
        data class GoToSection(val index: Int) : Intent

        // ── Section 1: Identity ──
        data class UpdateName(val name: String) : Intent
        data class UpdateAge(val age: Int) : Intent
        data class UpdateGender(val gender: GenderIdentity) : Intent
        data class ToggleLanguage(val language: String) : Intent

        // ── Section 2: Appearance ──
        data class UpdateBodyType(val type: String) : Intent
        data class UpdateHeight(val height: Float) : Intent
        data class UpdateSkinTone(val tone: String) : Intent
        data class UpdateHairStyle(val style: String) : Intent
        data class UpdateHairColor(val color: String) : Intent
        data class UpdateOutfit(val outfit: String) : Intent
        data class ToggleExtra(val extra: String) : Intent

        // ── Section 3: Personality ──
        data class ToggleTrait(val trait: String) : Intent
        data class UpdateStressResponse(val response: StressResponse) : Intent
        data class UpdateDirectness(val value: Float) : Intent
        data class UpdateHumor(val value: Float) : Intent
        data class UpdateDecisionStyle(val style: DecisionStyle) : Intent
        data class UpdateAuthorityRelation(val value: Float) : Intent

        // ── Section 4: Values & Bias ──
        data class UpdateValues(val values: String) : Intent
        data class ToggleBias(val bias: String) : Intent
        data class UpdateCoreWound(val wound: String) : Intent
        data class UpdateCoreStrength(val strength: String) : Intent
        data class UpdateCulturalBackground(val bg: String) : Intent
        data class UpdateCulturalReference(val ref: String) : Intent

        // ── Section 5: Needs & Desires ──
        data class UpdatePrimaryNeed(val need: PrimaryNeed) : Intent
        data class UpdateGoals(val goals: String) : Intent
        data class UpdateAvoidTopics(val topics: String) : Intent
        data class UpdateEngagement(val freq: EngagementFrequency) : Intent

        // ── Section 6: Emotional Style ──
        data class UpdateAttachmentStyle(val style: AttachmentStyle) : Intent
        data class UpdateJealousy(val level: Float) : Intent
        data class UpdateAffectionStyle(val style: String) : Intent
        data class UpdateVulnerabilityTriggers(val triggers: String) : Intent
        data class UpdateEmotionalBarrier(val barrier: String) : Intent

        // ── Section 7: Voice & Style ──
        data class UpdateVoiceId(val voiceId: String) : Intent
        data class UpdateSpeakingRate(val rate: Float) : Intent
        data class UpdateVoiceTone(val tone: VoiceTone) : Intent
        data class TogglePauseStyle(val enabled: Boolean) : Intent
        data class UpdateVolumeGain(val gain: Float) : Intent

        // ── Submit ──
        data object SubmitForm : Intent
        data object RetrySubmit : Intent
    }

    data class State(
        val currentSection: Int = 0,
        val totalSections: Int = 7,
        val form: AvatarCreationForm = AvatarCreationForm(),
        val creationStatus: CreationStatus = CreationStatus.IDLE,
        val creationProgress: Int = 0,
        val avatarId: String? = null,
        val errorMessage: String? = null,
    ) : MviContract.State {
        val isFirstSection: Boolean get() = currentSection == 0
        val isLastSection: Boolean get() = currentSection == totalSections - 1
        val canSubmit: Boolean get() = form.name.isNotBlank() && form.traits.isNotEmpty()
        val sectionProgress: Float get() = (currentSection + 1).toFloat() / totalSections
    }

    enum class CreationStatus {
        IDLE,
        SUBMITTING,
        GENERATING,
        PROMPT_READY,
        READY,
        ERROR,
    }

    sealed interface Effect : MviContract.Effect {
        data class NavigateToAvatarViewer(val avatarId: String) : Effect
        data class ShowError(val message: String) : Effect
        data object ScrollToTop : Effect
    }
}
