package com.lifo.settings

import com.lifo.util.model.ChecklistCategory
import com.lifo.util.model.ChecklistItem
import com.lifo.util.model.EnvironmentChecklist
import com.lifo.util.model.RoutineStep
import com.lifo.util.mvi.MviContract

object EnvironmentContract {

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class ToggleChecklistItem(val itemId: String) : Intent
        data class AddChecklistItem(val text: String, val category: ChecklistCategory) : Intent
        data class RemoveChecklistItem(val itemId: String) : Intent
        data class ToggleRoutineStep(val stepId: String, val isMorning: Boolean) : Intent
        data class SetDetoxMinutes(val minutes: Int) : Intent
        data object StartDetoxTimer : Intent
        data object StopDetoxTimer : Intent
        data class SelectTab(val index: Int) : Intent
        data object Save : Intent
    }

    data class State(
        val checklist: EnvironmentChecklist? = null,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        // Detox timer
        val detoxActive: Boolean = false,
        val detoxRemainingSeconds: Int = 0,
        // Tab
        val selectedTab: Int = 0,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object Saved : Effect
        data class Error(val message: String) : Effect
        data object DetoxCompleted : Effect
    }
}
