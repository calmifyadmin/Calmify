package com.lifo.util.repository

import com.lifo.util.model.GratitudeEntry
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * GratitudeRepository — manages "3 Cose Belle" gratitude entries.
 */
interface GratitudeRepository {
    suspend fun upsertEntry(entry: GratitudeEntry): RequestState<String>
    fun getTodayEntry(): Flow<RequestState<GratitudeEntry?>>
    fun getRecentEntries(limit: Int = 30): Flow<RequestState<List<GratitudeEntry>>>
    fun getEntriesInRange(startMillis: Long, endMillis: Long): Flow<RequestState<List<GratitudeEntry>>>
    suspend fun deleteEntry(id: String): RequestState<Boolean>
}
