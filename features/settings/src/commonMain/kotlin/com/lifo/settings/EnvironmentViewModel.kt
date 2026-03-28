package com.lifo.settings

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.ChecklistItem
import com.lifo.util.model.EnvironmentChecklist
import com.lifo.util.model.defaultChecklist
import com.lifo.util.model.defaultEveningRoutine
import com.lifo.util.model.defaultMorningRoutine
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.EnvironmentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class EnvironmentViewModel(
    private val repository: EnvironmentRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<EnvironmentContract.Intent, EnvironmentContract.State, EnvironmentContract.Effect>(
    EnvironmentContract.State()
) {

    private var detoxJob: Job? = null

    init {
        onIntent(EnvironmentContract.Intent.Load)
    }

    override fun handleIntent(intent: EnvironmentContract.Intent) {
        when (intent) {
            is EnvironmentContract.Intent.Load -> load()
            is EnvironmentContract.Intent.ToggleChecklistItem -> toggleItem(intent.itemId)
            is EnvironmentContract.Intent.AddChecklistItem -> addItem(intent.text, intent.category)
            is EnvironmentContract.Intent.RemoveChecklistItem -> removeItem(intent.itemId)
            is EnvironmentContract.Intent.ToggleRoutineStep -> toggleRoutineStep(intent.stepId, intent.isMorning)
            is EnvironmentContract.Intent.SetDetoxMinutes -> setDetoxMinutes(intent.minutes)
            is EnvironmentContract.Intent.StartDetoxTimer -> startDetox()
            is EnvironmentContract.Intent.StopDetoxTimer -> stopDetox()
            is EnvironmentContract.Intent.SelectTab -> updateState { copy(selectedTab = intent.index) }
            is EnvironmentContract.Intent.Save -> save()
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getChecklist(userId).collect { checklist ->
                val actual = checklist ?: EnvironmentChecklist(
                    ownerId = userId,
                    items = defaultChecklist(),
                    morningRoutine = defaultMorningRoutine(),
                    eveningRoutine = defaultEveningRoutine(),
                )
                updateState { copy(checklist = actual, isLoading = false) }
            }
        }
    }

    private fun toggleItem(itemId: String) {
        val current = state.value.checklist ?: return
        val updated = current.copy(
            items = current.items.map { if (it.id == itemId) it.copy(isCompleted = !it.isCompleted) else it }
        )
        updateState { copy(checklist = updated) }
        autoSave(updated)
    }

    private fun addItem(text: String, category: com.lifo.util.model.ChecklistCategory) {
        val current = state.value.checklist ?: return
        val newItem = ChecklistItem(
            id = Random.nextLong().toString(36),
            text = text,
            isCompleted = false,
            category = category,
        )
        val updated = current.copy(items = current.items + newItem)
        updateState { copy(checklist = updated) }
        autoSave(updated)
    }

    private fun removeItem(itemId: String) {
        val current = state.value.checklist ?: return
        val updated = current.copy(items = current.items.filter { it.id != itemId })
        updateState { copy(checklist = updated) }
        autoSave(updated)
    }

    private fun toggleRoutineStep(stepId: String, isMorning: Boolean) {
        val current = state.value.checklist ?: return
        val updated = if (isMorning) {
            current.copy(morningRoutine = current.morningRoutine.map { if (it.id == stepId) it.copy(isCompleted = !it.isCompleted) else it })
        } else {
            current.copy(eveningRoutine = current.eveningRoutine.map { if (it.id == stepId) it.copy(isCompleted = !it.isCompleted) else it })
        }
        updateState { copy(checklist = updated) }
        autoSave(updated)
    }

    private fun setDetoxMinutes(minutes: Int) {
        val current = state.value.checklist ?: return
        val updated = current.copy(detoxTimerMinutes = minutes)
        updateState { copy(checklist = updated) }
    }

    private fun startDetox() {
        val minutes = state.value.checklist?.detoxTimerMinutes ?: 60
        updateState { copy(detoxActive = true, detoxRemainingSeconds = minutes * 60) }
        detoxJob?.cancel()
        detoxJob = scope.launch {
            while (state.value.detoxRemainingSeconds > 0 && state.value.detoxActive) {
                delay(1000L)
                updateState { copy(detoxRemainingSeconds = detoxRemainingSeconds - 1) }
            }
            if (state.value.detoxActive) {
                updateState { copy(detoxActive = false) }
                sendEffect(EnvironmentContract.Effect.DetoxCompleted)
            }
        }
    }

    private fun stopDetox() {
        detoxJob?.cancel()
        updateState { copy(detoxActive = false, detoxRemainingSeconds = 0) }
    }

    private fun autoSave(checklist: EnvironmentChecklist) {
        scope.launch { repository.saveChecklist(checklist) }
    }

    private fun save() {
        val current = state.value.checklist ?: return
        scope.launch {
            updateState { copy(isSaving = true) }
            repository.saveChecklist(current)
                .onSuccess { sendEffect(EnvironmentContract.Effect.Saved) }
                .onFailure { sendEffect(EnvironmentContract.Effect.Error(it.message ?: "Errore")) }
            updateState { copy(isSaving = false) }
        }
    }

    override fun onCleared() {
        detoxJob?.cancel()
        super.onCleared()
    }
}
