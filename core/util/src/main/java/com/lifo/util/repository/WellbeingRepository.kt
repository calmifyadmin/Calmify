package com.lifo.util.repository

import com.lifo.util.model.RequestState
import com.lifo.util.model.WellbeingSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * WellbeingRepository Interface
 *
 * Manages wellbeing snapshots in Firestore
 */
interface WellbeingRepository {
    suspend fun insertSnapshot(snapshot: WellbeingSnapshot): RequestState<String>
    fun getAllSnapshots(): Flow<RequestState<List<WellbeingSnapshot>>>
    suspend fun getLatestSnapshot(): RequestState<WellbeingSnapshot?>
    fun getSnapshotsInRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<RequestState<List<WellbeingSnapshot>>>
    suspend fun deleteSnapshot(id: String): RequestState<Boolean>
}
