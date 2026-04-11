package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.SleepLogProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.RequestState
import com.lifo.util.model.SleepLog
import com.lifo.util.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.*

class KtorSleepRepository(
    private val api: KtorApiClient,
) : SleepRepository {

    override suspend fun upsertSleepLog(log: SleepLog): RequestState<String> {
        val proto = log.toProto()
        val result = if (log.id.isEmpty()) {
            api.post<SleepLogProto>("/api/v1/wellness/sleep", proto)
        } else {
            api.put<SleepLogProto>("/api/v1/wellness/sleep/${log.id}", proto)
        }
        return result.map { it.id }
    }

    override fun getTodayLog(): Flow<RequestState<SleepLog?>> = flow {
        emit(RequestState.Loading)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dayKey = "${today.year}-${today.monthNumber.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"
        val result = api.get<WellnessListDto<SleepLogProto>>("/api/v1/wellness/sleep/day/$dayKey")
        emit(result.map { it.data.firstOrNull()?.toDomain() })
    }

    override fun getRecentLogs(limit: Int): Flow<RequestState<List<SleepLog>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<WellnessListDto<SleepLogProto>>("/api/v1/wellness/sleep?limit=$limit")
        emit(result.map { it.data.map { e -> e.toDomain() } })
    }

    override suspend fun deleteLog(id: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/wellness/sleep/$id")
    }
}
