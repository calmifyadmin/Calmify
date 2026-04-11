package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.MeditationSessionProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.MeditationSession
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MeditationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorMeditationRepository(
    private val api: KtorApiClient,
) : MeditationRepository {

    override suspend fun saveSession(session: MeditationSession): Result<Unit> {
        val proto = session.toProto()
        val result = if (session.id.isEmpty()) {
            api.post<MeditationSessionProto>("/api/v1/wellness/meditation", proto)
        } else {
            api.put<MeditationSessionProto>("/api/v1/wellness/meditation/${session.id}", proto)
        }
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    override fun getRecentSessions(userId: String, limit: Int): Flow<List<MeditationSession>> = flow {
        val result = api.get<WellnessListDto<MeditationSessionProto>>("/api/v1/wellness/meditation?limit=$limit")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }
            else -> emptyList()
        })
    }

    override suspend fun getTotalMinutes(userId: String): Int {
        val result = api.get<WellnessListDto<MeditationSessionProto>>("/api/v1/wellness/meditation?limit=1000")
        return when (result) {
            is RequestState.Success -> result.data.data.sumOf { it.completedSeconds / 60 }
            else -> 0
        }
    }

    override suspend fun getSessionCount(userId: String): Int {
        val result = api.get<WellnessListDto<MeditationSessionProto>>("/api/v1/wellness/meditation?limit=1000")
        return when (result) {
            is RequestState.Success -> result.data.data.size
            else -> 0
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        val result = api.deleteNoBody("/api/v1/wellness/meditation/$sessionId")
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }
}
