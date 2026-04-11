package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.MovementLogProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.MovementLog
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MovementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorMovementRepository(
    private val api: KtorApiClient,
) : MovementRepository {

    override suspend fun saveLog(log: MovementLog): Result<Unit> {
        val proto = log.toProto()
        val result = if (log.id.isEmpty()) {
            api.post<MovementLogProto>("/api/v1/wellness/movement", proto)
        } else {
            api.put<MovementLogProto>("/api/v1/wellness/movement/${log.id}", proto)
        }
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    override fun getTodayLog(userId: String, dayKey: String): Flow<MovementLog?> = flow {
        val result = api.get<WellnessListDto<MovementLogProto>>("/api/v1/wellness/movement/day/$dayKey")
        emit(when (result) {
            is RequestState.Success -> result.data.data.firstOrNull()?.toDomain()
            else -> null
        })
    }

    override fun getRecentLogs(userId: String, limit: Int): Flow<List<MovementLog>> = flow {
        val result = api.get<WellnessListDto<MovementLogProto>>("/api/v1/wellness/movement?limit=$limit")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }
            else -> emptyList()
        })
    }

    override fun getLogsInRange(userId: String, startDayKey: String, endDayKey: String): Flow<List<MovementLog>> = flow {
        val result = api.get<WellnessListDto<MovementLogProto>>("/api/v1/wellness/movement?limit=100")
        emit(when (result) {
            is RequestState.Success -> result.data.data
                .filter { it.dayKey >= startDayKey && it.dayKey <= endDayKey }
                .map { it.toDomain() }
            else -> emptyList()
        })
    }

    override suspend fun deleteLog(logId: String): Result<Unit> {
        val result = api.deleteNoBody("/api/v1/wellness/movement/$logId")
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }
}
