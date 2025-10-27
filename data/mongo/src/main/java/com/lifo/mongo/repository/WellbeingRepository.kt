package com.lifo.mongo.repository

import com.lifo.util.model.RequestState
import com.lifo.util.model.WellbeingSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * WellbeingRepository Interface
 *
 * Manages wellbeing snapshots in Firestore
 * Week 2 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
interface WellbeingRepository {
    /**
     * Save a new wellbeing snapshot
     */
    suspend fun insertSnapshot(snapshot: WellbeingSnapshot): RequestState<String>

    /**
     * Get all snapshots for current user, ordered by timestamp descending
     */
    fun getAllSnapshots(): Flow<RequestState<List<WellbeingSnapshot>>>

    /**
     * Get the most recent snapshot
     */
    suspend fun getLatestSnapshot(): RequestState<WellbeingSnapshot?>

    /**
     * Get snapshots within a date range
     */
    fun getSnapshotsInRange(
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<RequestState<List<WellbeingSnapshot>>>

    /**
     * Delete a snapshot by ID
     */
    suspend fun deleteSnapshot(id: String): RequestState<Boolean>
}
