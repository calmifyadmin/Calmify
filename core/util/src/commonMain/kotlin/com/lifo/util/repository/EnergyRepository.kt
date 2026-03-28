package com.lifo.util.repository

import com.lifo.util.model.EnergyCheckIn
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * EnergyRepository — manages daily "Come Sta il Tuo Corpo?" check-ins.
 */
interface EnergyRepository {
    suspend fun upsertCheckIn(checkIn: EnergyCheckIn): RequestState<String>
    fun getTodayCheckIn(): Flow<RequestState<EnergyCheckIn?>>
    fun getRecentCheckIns(limit: Int = 30): Flow<RequestState<List<EnergyCheckIn>>>
    fun getCheckInsInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<EnergyCheckIn>>>
    suspend fun deleteCheckIn(id: String): RequestState<Boolean>
}
