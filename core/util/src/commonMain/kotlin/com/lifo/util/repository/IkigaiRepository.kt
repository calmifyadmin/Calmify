package com.lifo.util.repository

import com.lifo.util.model.IkigaiExploration
import kotlinx.coroutines.flow.Flow

interface IkigaiRepository {
    fun getExploration(userId: String): Flow<IkigaiExploration?>
    suspend fun saveExploration(exploration: IkigaiExploration): Result<Unit>
    suspend fun deleteExploration(explorationId: String): Result<Unit>
}
