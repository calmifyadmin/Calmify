package com.lifo.write

import com.lifo.util.model.RequestState
import com.lifo.util.model.ThoughtCategory
import com.lifo.util.model.ThoughtReframe
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ReframeRepository
import com.lifo.util.auth.AuthProvider
import kotlinx.coroutines.launch

object ReframeContract {

    sealed interface Intent : MviContract.Intent {
        data class SetOriginalThought(val text: String) : Intent
        data class SetEvidenceFor(val text: String) : Intent
        data class SetEvidenceAgainst(val text: String) : Intent
        data class SetFriendPerspective(val text: String) : Intent
        data class SetReframedThought(val text: String) : Intent
        data class SetCategory(val category: ThoughtCategory) : Intent
        data object NextStep : Intent
        data object PrevStep : Intent
        data object Save : Intent
    }

    data class State(
        val currentStep: Int = 0, // 0=capture, 1=question, 2=reframe
        val originalThought: String = "",
        val evidenceFor: String = "",
        val evidenceAgainst: String = "",
        val friendPerspective: String = "",
        val reframedThought: String = "",
        val category: ThoughtCategory = ThoughtCategory.ALTRO,
        val isSaving: Boolean = false,
    ) : MviContract.State {
        val canProceed: Boolean get() = when (currentStep) {
            0 -> originalThought.isNotBlank()
            1 -> true // questions are optional
            2 -> reframedThought.isNotBlank()
            else -> false
        }
    }

    sealed interface Effect : MviContract.Effect {
        data object SavedSuccessfully : Effect
        data class Error(val message: String) : Effect
    }
}

class ReframeViewModel(
    private val repository: ReframeRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<ReframeContract.Intent, ReframeContract.State, ReframeContract.Effect>(
    ReframeContract.State()
) {
    override fun handleIntent(intent: ReframeContract.Intent) {
        when (intent) {
            is ReframeContract.Intent.SetOriginalThought -> updateState { copy(originalThought = intent.text) }
            is ReframeContract.Intent.SetEvidenceFor -> updateState { copy(evidenceFor = intent.text) }
            is ReframeContract.Intent.SetEvidenceAgainst -> updateState { copy(evidenceAgainst = intent.text) }
            is ReframeContract.Intent.SetFriendPerspective -> updateState { copy(friendPerspective = intent.text) }
            is ReframeContract.Intent.SetReframedThought -> updateState { copy(reframedThought = intent.text) }
            is ReframeContract.Intent.SetCategory -> updateState { copy(category = intent.category) }
            is ReframeContract.Intent.NextStep -> updateState { copy(currentStep = (currentStep + 1).coerceAtMost(2)) }
            is ReframeContract.Intent.PrevStep -> updateState { copy(currentStep = (currentStep - 1).coerceAtLeast(0)) }
            is ReframeContract.Intent.Save -> handleSave()
        }
    }

    private fun handleSave() {
        val s = currentState
        if (s.originalThought.isBlank() || s.reframedThought.isBlank()) {
            sendEffect(ReframeContract.Effect.Error("Scrivi il pensiero originale e quello riformulato"))
            return
        }
        val userId = authProvider.currentUserId ?: return
        updateState { copy(isSaving = true) }
        scope.launch {
            val reframe = ThoughtReframe(
                ownerId = userId,
                originalThought = s.originalThought.trim(),
                evidenceFor = s.evidenceFor.trim(),
                evidenceAgainst = s.evidenceAgainst.trim(),
                friendPerspective = s.friendPerspective.trim(),
                reframedThought = s.reframedThought.trim(),
                category = s.category,
            )
            when (repository.saveReframe(reframe)) {
                is RequestState.Success -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(ReframeContract.Effect.SavedSuccessfully)
                }
                is RequestState.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(ReframeContract.Effect.Error("Errore nel salvataggio"))
                }
                else -> {}
            }
        }
    }
}
