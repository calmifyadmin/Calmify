package com.lifo.util.repository

import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCompletion
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * HabitRepository — manages habits and their daily completions.
 */
interface HabitRepository {
    // Habits CRUD
    suspend fun upsertHabit(habit: Habit): RequestState<String>
    fun getActiveHabits(): Flow<RequestState<List<Habit>>>
    suspend fun deleteHabit(id: String): RequestState<Boolean>

    // Completions
    suspend fun toggleCompletion(habitId: String, dayKey: String): RequestState<Boolean>
    fun getCompletionsForDay(dayKey: String): Flow<RequestState<List<HabitCompletion>>>
    fun getCompletionsForHabit(habitId: String, limit: Int = 90): Flow<RequestState<List<HabitCompletion>>>
}
