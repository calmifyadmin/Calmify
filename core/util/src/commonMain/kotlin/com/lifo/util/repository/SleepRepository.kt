package com.lifo.util.repository

import com.lifo.util.model.RequestState
import com.lifo.util.model.SleepLog
import kotlinx.coroutines.flow.Flow

interface SleepRepository {
    suspend fun upsertSleepLog(log: SleepLog): RequestState<String>
    fun getTodayLog(): Flow<RequestState<SleepLog?>>
    fun getRecentLogs(limit: Int = 30): Flow<RequestState<List<SleepLog>>>
    suspend fun deleteLog(id: String): RequestState<Boolean>
}
