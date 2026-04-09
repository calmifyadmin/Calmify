package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.ValuesDiscovery
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ValuesRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ValuesViewModel(
    private val repository: ValuesRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<ValuesContract.Intent, ValuesContract.State, ValuesContract.Effect>(
    ValuesContract.State()
) {

    init {
        onIntent(ValuesContract.Intent.Load)
    }

    override fun handleIntent(intent: ValuesContract.Intent) {
        when (intent) {
            is ValuesContract.Intent.Load -> load()
            is ValuesContract.Intent.SetStep -> updateState { copy(currentStep = intent.step, currentInput = "") }
            is ValuesContract.Intent.UpdateInput -> updateState { copy(currentInput = intent.text) }
            is ValuesContract.Intent.AddCurrentInput -> addCurrentInput()
            is ValuesContract.Intent.RemoveItem -> removeItem(intent.index)
            is ValuesContract.Intent.SetFinalReflection -> setFinalReflection(intent.text)
            is ValuesContract.Intent.ConfirmValues -> confirmValues(intent.values)
            is ValuesContract.Intent.Save -> save()
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getDiscovery(userId).collect { discovery ->
                val step = discovery?.completedSteps ?: 0
                updateState { copy(discovery = discovery, isLoading = false, currentStep = step.coerceAtMost(3)) }
            }
        }
    }

    private fun addCurrentInput() {
        val input = state.value.currentInput.trim()
        if (input.isBlank()) return
        val userId = authProvider.currentUserId ?: return
        val current = state.value.discovery ?: ValuesDiscovery(
            ownerId = userId,
            createdAtMillis = Clock.System.now().toEpochMilliseconds(),
        )

        val updated = when (state.value.currentStep) {
            0 -> current.copy(aliveMoments = current.aliveMoments + input)
            1 -> current.copy(indignationTopics = current.indignationTopics + input)
            3 -> current.copy(discoveredValues = current.discoveredValues + input)
            else -> current
        }

        updateState { copy(discovery = updated, currentInput = "") }
        autoSave(updated)
    }

    private fun removeItem(index: Int) {
        val current = state.value.discovery ?: return
        val updated = when (state.value.currentStep) {
            0 -> current.copy(aliveMoments = current.aliveMoments.filterIndexed { i, _ -> i != index })
            1 -> current.copy(indignationTopics = current.indignationTopics.filterIndexed { i, _ -> i != index })
            3 -> current.copy(discoveredValues = current.discoveredValues.filterIndexed { i, _ -> i != index })
            else -> current
        }
        updateState { copy(discovery = updated) }
        autoSave(updated)
    }

    private fun setFinalReflection(text: String) {
        val userId = authProvider.currentUserId ?: return
        val current = state.value.discovery ?: ValuesDiscovery(
            ownerId = userId,
            createdAtMillis = Clock.System.now().toEpochMilliseconds(),
        )
        val updated = current.copy(finalReflection = text)
        updateState { copy(discovery = updated) }
    }

    private fun confirmValues(values: List<String>) {
        val current = state.value.discovery ?: return
        val updated = current.copy(
            confirmedValues = values,
            completedSteps = 4,
            lastReviewMillis = Clock.System.now().toEpochMilliseconds(),
            nextReviewMillis = Clock.System.now().toEpochMilliseconds() + (180L * 24 * 60 * 60 * 1000), // 6 months
        )
        updateState { copy(discovery = updated) }
        autoSave(updated)
    }

    private fun save() {
        val current = state.value.discovery ?: return
        val step = state.value.currentStep
        val updated = current.copy(completedSteps = (step + 1).coerceAtMost(4))
        scope.launch {
            updateState { copy(isSaving = true) }
            repository.saveDiscovery(updated)
                .onSuccess {
                    updateState { copy(discovery = updated, isSaving = false, currentStep = (step + 1).coerceAtMost(3)) }
                    sendEffect(ValuesContract.Effect.Saved)
                }
                .onFailure {
                    updateState { copy(isSaving = false) }
                    sendEffect(ValuesContract.Effect.Error(it.message ?: "Errore"))
                }
        }
    }

    private fun autoSave(discovery: ValuesDiscovery) {
        scope.launch { try { repository.saveDiscovery(discovery) } catch (_: Exception) { } }
    }
}
