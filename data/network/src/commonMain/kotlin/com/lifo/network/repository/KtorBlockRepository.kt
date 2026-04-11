package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.BlockProto
import com.lifo.util.currentTimeMillis
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.Block
import com.lifo.util.model.RequestState
import com.lifo.util.repository.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorBlockRepository(
    private val api: KtorApiClient,
) : BlockRepository {

    override suspend fun saveBlock(block: Block): Result<Unit> {
        val proto = block.toProto()
        val result = if (block.id.isEmpty()) {
            api.post<BlockProto>("/api/v1/wellness/block", proto)
        } else {
            api.put<BlockProto>("/api/v1/wellness/block/${block.id}", proto)
        }
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }

    override fun getActiveBlocks(userId: String): Flow<List<Block>> = flow {
        val result = api.get<WellnessListDto<BlockProto>>("/api/v1/wellness/block?limit=100")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }.filter { !it.isResolved }
            else -> emptyList()
        })
    }

    override fun getResolvedBlocks(userId: String, limit: Int): Flow<List<Block>> = flow {
        val result = api.get<WellnessListDto<BlockProto>>("/api/v1/wellness/block?limit=$limit")
        emit(when (result) {
            is RequestState.Success -> result.data.data.map { it.toDomain() }.filter { it.isResolved }
            else -> emptyList()
        })
    }

    override suspend fun resolveBlock(blockId: String, resolution: String, note: String): Result<Unit> {
        val existing = api.get<BlockProto>("/api/v1/wellness/block/$blockId")
        return when (existing) {
            is RequestState.Success -> {
                val resolved = existing.data.copy(
                    isResolved = true,
                    resolution = resolution,
                    resolutionNote = note,
                    resolvedAtMillis = currentTimeMillis(),
                )
                val updateResult = api.put<BlockProto>("/api/v1/wellness/block/$blockId", resolved)
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

    override suspend fun deleteBlock(blockId: String): Result<Unit> {
        val result = api.deleteNoBody("/api/v1/wellness/block/$blockId")
        return when (result) {
            is RequestState.Success -> Result.success(Unit)
            is RequestState.Error -> Result.failure(Exception(result.error.message))
            else -> Result.failure(Exception("Unexpected state"))
        }
    }
}
