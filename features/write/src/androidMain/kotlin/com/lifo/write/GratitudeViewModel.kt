package com.lifo.write

import com.lifo.util.model.GratitudeCategory
import com.lifo.util.model.GratitudeEntry
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.GratitudeRepository
import com.lifo.util.auth.AuthProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────
// Contract
// ──────────────────────────────────────────────────────────

object GratitudeContract {

    sealed interface Intent : MviContract.Intent {
        data class SetItem(val index: Int, val text: String) : Intent
        data class SetCategory(val index: Int, val category: GratitudeCategory) : Intent
        data object Save : Intent
        data object LoadToday : Intent
    }

    data class State(
        val item1: String = "",
        val item2: String = "",
        val item3: String = "",
        val category1: GratitudeCategory = GratitudeCategory.ALTRO,
        val category2: GratitudeCategory = GratitudeCategory.ALTRO,
        val category3: GratitudeCategory = GratitudeCategory.ALTRO,
        val isSaving: Boolean = false,
        val isLoading: Boolean = true,
        val savedToday: Boolean = false,
        val existingEntryId: String? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object SavedSuccessfully : Effect
        data class Error(val message: String) : Effect
    }
}

// ──────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────

class GratitudeViewModel(
    private val repository: GratitudeRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<GratitudeContract.Intent, GratitudeContract.State, GratitudeContract.Effect>(
    GratitudeContract.State()
) {

    init {
        onIntent(GratitudeContract.Intent.LoadToday)
    }

    override fun handleIntent(intent: GratitudeContract.Intent) {
        when (intent) {
            is GratitudeContract.Intent.SetItem -> handleSetItem(intent.index, intent.text)
            is GratitudeContract.Intent.SetCategory -> handleSetCategory(intent.index, intent.category)
            is GratitudeContract.Intent.Save -> handleSave()
            is GratitudeContract.Intent.LoadToday -> handleLoadToday()
        }
    }

    private fun handleSetItem(index: Int, text: String) {
        updateState {
            when (index) {
                0 -> copy(item1 = text)
                1 -> copy(item2 = text)
                2 -> copy(item3 = text)
                else -> this
            }
        }
    }

    private fun handleSetCategory(index: Int, category: GratitudeCategory) {
        updateState {
            when (index) {
                0 -> copy(category1 = category)
                1 -> copy(category2 = category)
                2 -> copy(category3 = category)
                else -> this
            }
        }
    }

    private fun handleLoadToday() {
        scope.launch {
            repository.getTodayEntry().collectLatest { result ->
                when (result) {
                    is RequestState.Loading -> updateState { copy(isLoading = true) }
                    is RequestState.Success -> {
                        val entry = result.data
                        if (entry != null) {
                            updateState {
                                copy(
                                    item1 = entry.item1,
                                    item2 = entry.item2,
                                    item3 = entry.item3,
                                    category1 = entry.category1,
                                    category2 = entry.category2,
                                    category3 = entry.category3,
                                    savedToday = true,
                                    existingEntryId = entry.id,
                                    isLoading = false,
                                )
                            }
                        } else {
                            updateState { copy(isLoading = false, savedToday = false) }
                        }
                    }
                    is RequestState.Error -> {
                        updateState { copy(isLoading = false) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun handleSave() {
        val state = currentState
        if (state.item1.isBlank() && state.item2.isBlank() && state.item3.isBlank()) {
            sendEffect(GratitudeContract.Effect.Error("Scrivi almeno una cosa bella!"))
            return
        }

        val userId = authProvider.currentUserId ?: return
        updateState { copy(isSaving = true) }

        scope.launch {
            val entry = GratitudeEntry.create(
                ownerId = userId
            ).copy(
                item1 = state.item1.trim(),
                item2 = state.item2.trim(),
                item3 = state.item3.trim(),
                category1 = state.category1,
                category2 = state.category2,
                category3 = state.category3,
            )

            when (val result = repository.upsertEntry(entry)) {
                is RequestState.Success -> {
                    updateState { copy(isSaving = false, savedToday = true) }
                    sendEffect(GratitudeContract.Effect.SavedSuccessfully)
                }
                is RequestState.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(GratitudeContract.Effect.Error("Errore nel salvataggio"))
                }
                else -> {}
            }
        }
    }
}
