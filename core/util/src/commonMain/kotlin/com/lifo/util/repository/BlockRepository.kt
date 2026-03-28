package com.lifo.util.repository

import com.lifo.util.model.Block
import kotlinx.coroutines.flow.Flow

interface BlockRepository {
    suspend fun saveBlock(block: Block): Result<Unit>
    fun getActiveBlocks(userId: String): Flow<List<Block>>
    fun getResolvedBlocks(userId: String, limit: Int = 20): Flow<List<Block>>
    suspend fun resolveBlock(blockId: String, resolution: String, note: String): Result<Unit>
    suspend fun deleteBlock(blockId: String): Result<Unit>
}
