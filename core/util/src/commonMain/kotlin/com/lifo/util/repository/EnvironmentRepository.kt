package com.lifo.util.repository

import com.lifo.util.model.EnvironmentChecklist
import kotlinx.coroutines.flow.Flow

interface EnvironmentRepository {
    fun getChecklist(userId: String): Flow<EnvironmentChecklist?>
    suspend fun saveChecklist(checklist: EnvironmentChecklist): Result<Unit>
}
