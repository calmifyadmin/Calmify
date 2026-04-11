package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.ValuesDiscoveryProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.RequestState
import com.lifo.util.model.ValuesDiscovery
import com.lifo.util.repository.ValuesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorValuesRepository(
    private val api: KtorApiClient,
) : ValuesRepository {

    override fun getDiscovery(userId: String): Flow<ValuesDiscovery?> = flow {
        val result = api.get<WellnessListDto<ValuesDiscoveryProto>>("/api/v1/wellness/values?limit=1")
        emit(when (result) {
            is RequestState.Success -> result.data.data.firstOrNull()?.toDomain()
            else -> null
        })
    }

    override suspend fun saveDiscovery(discovery: ValuesDiscovery): Result<Unit> {
        val proto = discovery.toProto()
        val result = if (discovery.id.isEmpty()) {
            api.post<ValuesDiscoveryProto>("/api/v1/wellness/values", proto)
        } else {
            api.put<ValuesDiscoveryProto>("/api/v1/wellness/values/${discovery.id}", proto)
        }
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    override suspend fun deleteDiscovery(discoveryId: String): Result<Unit> {
        val result = api.deleteNoBody("/api/v1/wellness/values/$discoveryId")
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }
}
