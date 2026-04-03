package com.lifo.habits

import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCompletion
import com.lifo.util.mvi.MviContract

object HabitContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadHabits : Intent
        data class ToggleCompletion(val habitId: String) : Intent
        data class DeleteHabit(val habitId: String) : Intent
        data object ShowAddDialog : Intent
        data object DismissAddDialog : Intent
        data class SaveNewHabit(val habit: Habit) : Intent
        data object RetryLoad : Intent
    }

    data class State(
        val habits: List<Habit> = emptyList(),
        val todayCompletions: Set<String> = emptySet(), // habitIds completed today
        val streaks: Map<String, Int> = emptyMap(),     // habitId -> current streak
        val completionHistory: List<HabitCompletion> = emptyList(), // last 90 days, all habits
        val isLoading: Boolean = true,
        val showAddDialog: Boolean = false,
        val errorMessage: String? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class Error(val message: String) : Effect
        data object HabitSaved : Effect
        data object HabitDeleted : Effect
        data object StreakMilestone : Effect  // fired when a habit is marked complete
    }
}
