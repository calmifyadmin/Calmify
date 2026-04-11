package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.RecurringThoughtProto
import com.lifo.util.currentTimeMillis
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.RecurringThought
import com.lifo.util.model.RequestState
import com.lifo.util.repository.RecurringThoughtRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorRecurringThoughtRepository(
    private val api: KtorApiClient,
) : RecurringThoughtRepository {

    override fun getThoughts(userId: String): Flow<List<RecurringThought>> = flow {
        val result = api.get<WellnessListDto<RecurringThoughtProto>>("/api/v1/wellness/recurring?limit=100")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }
            else -> emptyList()
        })
    }

    override suspend fun saveThought(thought: RecurringThought): Result<Unit> {
        val proto = thought.toProto()
        val result = if (thought.id.isEmpty()) {
            api.post<RecurringThoughtProto>("/api/v1/wellness/recurring", proto)
        } else {
            api.put<RecurringThoughtProto>("/api/v1/wellness/recurring/${thought.id}", proto)
        }
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    override suspend fun incrementOccurrence(thoughtId: String): Result<Unit> {
        return updateThought(thoughtId) { thought ->
            thought.copy(
                occurrences = thought.occurrences + 1,
                lastSeenMillis = currentTimeMillis(),
            )
        }
    }

    override suspend fun markReframed(thoughtId: String, reframeId: String): Result<Unit> {
        return updateThought(thoughtId) { thought ->
            thought.copy(
                reframeId = reframeId,
                reframedAtMillis = currentTimeMillis(),
            )
        }
    }

    override suspend fun incrementPostReframe(thoughtId: String): Result<Unit> {
        return updateThought(thoughtId) { thought ->
            thought.copy(occurrencesPostReframe = thought.occurrencesPostReframe + 1)
        }
    }

    override suspend fun resolveThought(thoughtId: String): Result<Unit> {
        return updateThought(thoughtId) { thought ->
            thought.copy(isResolved = true)
        }
    }

    override suspend fun deleteThought(thoughtId: String): Result<Unit> {
        val result = api.deleteNoBody("/api/v1/wellness/recurring/$thoughtId")
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    private suspend fun updateThought(
        thoughtId: String,
        transform: (RecurringThoughtProto) -> RecurringThoughtProto,
    ): Result<Unit> {
        val existing = api.get<RecurringThoughtProto>("/api/v1/wellness/recurring/$thoughtId")
        return when (existing) {
            is RequestState.Success -> {
                val updated = transform(existing.data)
                val updateResult = api.put<RecurringThoughtProto>("/api/v1/wellness/recurring/$thoughtId", updated)
                when (updateResult) {
                    is RequestState.Success -> Result.success(Unit)
                    is RequestState.Error -> Result.failure(Exception(updateResult.error.message))
                    else -> Result.failure(Exception("Unexpected state"))
                }
            }
            is RequestState.Error -> Result.failure(Exception(existing.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }
}
