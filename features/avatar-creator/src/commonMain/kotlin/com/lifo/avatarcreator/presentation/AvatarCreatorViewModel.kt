package com.lifo.avatarcreator.presentation

import com.lifo.avatarcreator.presentation.AvatarCreatorContract.CreationStatus
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Effect
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.Intent
import com.lifo.avatarcreator.presentation.AvatarCreatorContract.State
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.*
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.AvatarRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AvatarCreatorViewModel(
    private val avatarRepository: AvatarRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<Intent, State, Effect>(State()) {

    override fun handleIntent(intent: Intent) {
        when (intent) {
            // ── Navigation ──
            is Intent.NextSection -> navigateSection(1)
            is Intent.PreviousSection -> navigateSection(-1)
            is Intent.GoToSection -> updateState { copy(currentSection = intent.index.coerceIn(0, totalSections - 1)) }

            // ── Section 1: Identity ──
            is Intent.UpdateName -> updateForm { copy(name = intent.name) }
            is Intent.UpdateAge -> updateForm { copy(perceivedAge = intent.age) }
            is Intent.UpdateGender -> updateForm { copy(gender = intent.gender) }
            is Intent.ToggleLanguage -> updateForm {
                val updated = if (intent.language in languages) languages - intent.language else languages + intent.language
                copy(languages = updated)
            }

            // ── Section 2: Appearance ──
            is Intent.UpdateBodyType -> updateForm { copy(appearance = appearance.copy(bodyType = intent.type)) }
            is Intent.UpdateHeight -> updateForm { copy(appearance = appearance.copy(height = intent.height)) }
            is Intent.UpdateSkinTone -> updateForm { copy(appearance = appearance.copy(skinTone = intent.tone)) }
            is Intent.UpdateHairStyle -> updateForm { copy(appearance = appearance.copy(hairStyle = intent.style)) }
            is Intent.UpdateHairColor -> updateForm { copy(appearance = appearance.copy(hairColor = intent.color)) }
            is Intent.UpdateOutfit -> updateForm { copy(appearance = appearance.copy(outfitType = intent.outfit)) }
            is Intent.ToggleExtra -> updateForm {
                val updated = if (intent.extra in appearance.extras) appearance.extras - intent.extra else appearance.extras + intent.extra
                copy(appearance = appearance.copy(extras = updated))
            }

            // ── Section 3: Personality ──
            is Intent.ToggleTrait -> updateForm {
                val updated = if (intent.trait in traits) {
                    traits - intent.trait
                } else if (traits.size < 3) {
                    traits + intent.trait
                } else traits
                copy(traits = updated)
            }
            is Intent.UpdateStressResponse -> updateForm { copy(stressResponse = intent.response) }
            is Intent.UpdateDirectness -> updateForm { copy(directness = intent.value) }
            is Intent.UpdateHumor -> updateForm { copy(humor = intent.value) }
            is Intent.UpdateDecisionStyle -> updateForm { copy(decisionStyle = intent.style) }
            is Intent.UpdateAuthorityRelation -> updateForm { copy(authorityRelation = intent.value) }

            // ── Section 4: Values & Bias ──
            is Intent.UpdateValues -> updateForm { copy(values = intent.values) }
            is Intent.ToggleBias -> updateForm {
                val updated = if (intent.bias in biases) biases - intent.bias else biases + intent.bias
                copy(biases = updated)
            }
            is Intent.UpdateCoreWound -> updateForm { copy(coreWound = intent.wound) }
            is Intent.UpdateCoreStrength -> updateForm { copy(coreStrength = intent.strength) }
            is Intent.UpdateCulturalBackground -> updateForm { copy(culturalBackground = intent.bg) }
            is Intent.UpdateCulturalReference -> updateForm { copy(culturalReference = intent.ref) }

            // ── Section 5: Needs ──
            is Intent.UpdatePrimaryNeed -> updateForm { copy(primaryNeed = intent.need) }
            is Intent.UpdateGoals -> updateForm { copy(goals = intent.goals) }
            is Intent.UpdateAvoidTopics -> updateForm { copy(avoidTopics = intent.topics) }
            is Intent.UpdateEngagement -> updateForm { copy(engagementFrequency = intent.freq) }

            // ── Section 6: Emotional ──
            is Intent.UpdateAttachmentStyle -> updateForm { copy(attachmentStyle = intent.style) }
            is Intent.UpdateJealousy -> updateForm { copy(jealousyLevel = intent.level) }
            is Intent.UpdateAffectionStyle -> updateForm { copy(affectionStyle = intent.style) }
            is Intent.UpdateVulnerabilityTriggers -> updateForm { copy(vulnerabilityTriggers = intent.triggers) }
            is Intent.UpdateEmotionalBarrier -> updateForm { copy(emotionalBarrier = intent.barrier) }

            // ── Section 7: Voice ──
            is Intent.UpdateVoiceId -> updateForm { copy(voiceId = intent.voiceId) }
            is Intent.UpdateSpeakingRate -> updateForm { copy(speakingRate = intent.rate) }
            is Intent.UpdateVoiceTone -> updateForm { copy(voiceTone = intent.tone) }
            is Intent.TogglePauseStyle -> updateForm { copy(pauseStyle = intent.enabled) }
            is Intent.UpdateVolumeGain -> updateForm { copy(volumeGain = intent.gain) }

            // ── Submit ──
            is Intent.SubmitForm -> submitForm()
            is Intent.RetrySubmit -> submitForm()
        }
    }

    private fun navigateSection(delta: Int) {
        updateState {
            val next = (currentSection + delta).coerceIn(0, totalSections - 1)
            copy(currentSection = next)
        }
        sendEffect(Effect.ScrollToTop)
    }

    private fun updateForm(reducer: AvatarCreationForm.() -> AvatarCreationForm) {
        updateState { copy(form = form.reducer()) }
    }

    private fun submitForm() {
        val userId = authProvider.currentUserId ?: run {
            sendEffect(Effect.ShowError("Devi essere autenticato per creare un avatar"))
            return
        }

        updateState { copy(creationStatus = CreationStatus.SUBMITTING, errorMessage = null) }

        scope.launch {
            try {
                val avatarId = avatarRepository.createAvatar(userId, currentState.form)
                updateState { copy(creationStatus = CreationStatus.GENERATING, avatarId = avatarId) }

                // Observe realtime status from Firestore
                avatarRepository.observeAvatar(userId, avatarId)
                    .catch { e ->
                        updateState {
                            copy(
                                creationStatus = CreationStatus.ERROR,
                                errorMessage = e.message ?: "Errore durante la generazione"
                            )
                        }
                    }
                    .collectLatest { avatar ->
                        if (avatar == null) return@collectLatest
                        when (avatar.status) {
                            AvatarStatus.GENERATING -> updateState {
                                copy(creationStatus = CreationStatus.GENERATING)
                            }
                            AvatarStatus.PROMPT_READY -> updateState {
                                copy(creationStatus = CreationStatus.PROMPT_READY, creationProgress = 50)
                            }
                            AvatarStatus.READY -> {
                                updateState {
                                    copy(creationStatus = CreationStatus.READY, creationProgress = 100)
                                }
                                sendEffect(Effect.NavigateToAvatarViewer(avatarId))
                            }
                            AvatarStatus.ERROR -> updateState {
                                copy(
                                    creationStatus = CreationStatus.ERROR,
                                    errorMessage = "Errore nella generazione dell'avatar"
                                )
                            }
                            AvatarStatus.PENDING -> { /* waiting */ }
                        }
                    }
            } catch (e: Exception) {
                updateState {
                    copy(
                        creationStatus = CreationStatus.ERROR,
                        errorMessage = e.message ?: "Errore imprevisto"
                    )
                }
                sendEffect(Effect.ShowError(e.message ?: "Errore imprevisto"))
            }
        }
    }
}
