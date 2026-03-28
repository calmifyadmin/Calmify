package com.lifo.habits

import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCompletion
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.HabitRepository
import com.lifo.util.auth.AuthProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class HabitViewModel(
    private val repository: HabitRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<HabitContract.Intent, HabitContract.State, HabitContract.Effect>(
    HabitContract.State()
) {

    private val todayKey: String
        get() = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toString()

    init {
        onIntent(HabitContract.Intent.LoadHabits)
    }

    override fun handleIntent(intent: HabitContract.Intent) {
        when (intent) {
            is HabitContract.Intent.LoadHabits -> handleLoadHabits()
            is HabitContract.Intent.ToggleCompletion -> handleToggle(intent.habitId)
            is HabitContract.Intent.DeleteHabit -> handleDelete(intent.habitId)
            is HabitContract.Intent.ShowAddDialog -> updateState { copy(showAddDialog = true) }
            is HabitContract.Intent.DismissAddDialog -> updateState { copy(showAddDialog = false) }
            is HabitContract.Intent.SaveNewHabit -> handleSaveHabit(intent.habit)
        }
    }

    private fun handleLoadHabits() {
        scope.launch {
            combine(
                repository.getActiveHabits(),
                repository.getCompletionsForDay(todayKey)
            ) { habitsResult, completionsResult ->
                Pair(habitsResult, completionsResult)
            }.collectLatest { (habitsResult, completionsResult) ->
                val habits = when (habitsResult) {
                    is RequestState.Success -> habitsResult.data
                    else -> emptyList()
                }
                val completedIds = when (completionsResult) {
                    is RequestState.Success -> completionsResult.data.map { it.habitId }.toSet()
                    else -> emptySet()
                }

                // Calculate streaks for each habit
                val streaks = habits.associate { it.id to calculateStreak(it.id, completedIds) }

                // Load 90-day completion history across all habits (best-effort, non-blocking)
                val history = habits.flatMap { habit ->
                    when (val r = repository.getCompletionsForHabit(habit.id, 90).first()) {
                        is RequestState.Success -> r.data
                        else -> emptyList()
                    }
                }

                updateState {
                    copy(
                        habits = habits,
                        todayCompletions = completedIds,
                        streaks = streaks,
                        completionHistory = history,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun calculateStreak(habitId: String, todayCompletedIds: Set<String>): Int {
        // Simple streak: count from today backwards
        // For now, just return 1 if completed today, 0 otherwise
        // Full streak calculation requires loading historical completions
        return if (habitId in todayCompletedIds) 1 else 0
    }

    private fun handleToggle(habitId: String) {
        // Capture whether we're completing (not un-completing) before toggling
        val wasCompleted = habitId in currentState.todayCompletions
        scope.launch {
            when (val result = repository.toggleCompletion(habitId, todayKey)) {
                is RequestState.Success -> {
                    // Celebrate when marking a habit as complete
                    if (!wasCompleted) {
                        sendEffect(HabitContract.Effect.StreakMilestone)
                    }
                    // State updates via Firestore snapshot listener
                }
                is RequestState.Error -> {
                    sendEffect(HabitContract.Effect.Error("Errore nell'aggiornamento"))
                }
                else -> {}
            }
        }
    }

    private fun handleDelete(habitId: String) {
        scope.launch {
            when (repository.deleteHabit(habitId)) {
                is RequestState.Success -> sendEffect(HabitContract.Effect.HabitDeleted)
                is RequestState.Error -> sendEffect(HabitContract.Effect.Error("Errore nella cancellazione"))
                else -> {}
            }
        }
    }

    private fun handleSaveHabit(habit: Habit) {
        val userId = authProvider.currentUserId ?: return
        val withOwner = habit.copy(ownerId = userId)
        scope.launch {
            when (repository.upsertHabit(withOwner)) {
                is RequestState.Success -> {
                    updateState { copy(showAddDialog = false) }
                    sendEffect(HabitContract.Effect.HabitSaved)
                }
                is RequestState.Error -> {
                    sendEffect(HabitContract.Effect.Error("Errore nel salvataggio"))
                }
                else -> {}
            }
        }
    }
}
