package com.lifo.util.repository

import com.lifo.util.model.MovementLog
import kotlinx.coroutines.flow.Flow

interface MovementRepository {
    suspend fun saveLog(log: MovementLog): Result<Unit>
    fun getTodayLog(userId: String, dayKey: String): Flow<MovementLog?>
    fun getRecentLogs(userId: String, limit: Int = 30): Flow<List<MovementLog>>
    fun getLogsInRange(userId: String, startDayKey: String, endDayKey: String): Flow<List<MovementLog>>
    suspend fun deleteLog(logId: String): Result<Unit>
}
