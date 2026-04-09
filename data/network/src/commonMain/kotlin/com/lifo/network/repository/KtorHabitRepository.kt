package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.HabitProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCompletion
import com.lifo.util.model.RequestState
import com.lifo.util.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

class KtorHabitRepository(
    private val api: KtorApiClient,
) : HabitRepository {

    override suspend fun upsertHabit(habit: Habit): RequestState<String> {
        val proto = habit.toProto()
        val result = if (habit.id.isEmpty()) {
            api.post<HabitProto>("/api/v1/wellness/habits", proto)
        } else {
            api.put<HabitProto>("/api/v1/wellness/habits/${habit.id}", proto)
        }
        return result.map { it.id }
    }

    override fun getActiveHabits(): Flow<RequestState<List<Habit>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<HabitProto>>("/api/v1/wellness/habits")
        emit(result.map { it.data.map { h -> h.toDomain() } })
    }

    override suspend fun deleteHabit(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/wellness/habits/$id")
    }

    override suspend fun toggleCompletion(habitId: String, dayKey: String): RequestState<Boolean> {
        // Completions may need their own sub-endpoint — for now use the habit's day query
        return api.postNoBody("/api/v1/wellness/habits/$habitId/toggle") {
            url { parameters.append("dayKey", dayKey) }
        }
    }

    override fun getCompletionsForDay(dayKey: String): Flow<RequestState<List<HabitCompletion>>> = flow {
        emit(RequestState.Loading)
        // Would need a completions sub-endpoint
        emit(RequestState.Success(emptyList()))
    }

    override fun getCompletionsForHabit(habitId: String, limit: Int): Flow<RequestState<List<HabitCompletion>>> = flow {
        emit(RequestState.Loading)
        emit(RequestState.Success(emptyList()))
    }
}

@Serializable
data class WellnessListDto<T>(val data: List<T> = emptyList())
