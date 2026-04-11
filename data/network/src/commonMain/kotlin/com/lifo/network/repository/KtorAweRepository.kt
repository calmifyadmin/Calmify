package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.AweEntryProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.AweEntry
import com.lifo.util.model.RequestState
import com.lifo.util.repository.AweRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorAweRepository(
    private val api: KtorApiClient,
) : AweRepository {

    override fun getEntries(userId: String): Flow<List<AweEntry>> = flow {
        val result = api.get<WellnessListDto<AweEntryProto>>("/api/v1/wellness/awe?limit=100")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }
            else -> emptyList()
        })
    }

    override suspend fun saveEntry(entry: AweEntry): Result<Unit> {
        val proto = entry.toProto()
        val result = if (entry.id.isEmpty()) {
            api.post<AweEntryProto>("/api/v1/wellness/awe", proto)
        } else {
            api.put<AweEntryProto>("/api/v1/wellness/awe/${entry.id}", proto)
        }
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    override suspend fun deleteEntry(entryId: String): Result<Unit> {
        val result = api.deleteNoBody("/api/v1/wellness/awe/$entryId")
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }
}
