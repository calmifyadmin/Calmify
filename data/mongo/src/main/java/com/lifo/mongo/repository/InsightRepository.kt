package com.lifo.mongo.repository

import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * InsightRepository Interface
 *
 * Manages AI-generated diary insights in Firestore
 * Week 5 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
interface InsightRepository {
    /**
     * Get insight for a specific diary entry
     */
    fun getInsightByDiaryId(diaryId: String): Flow<RequestState<DiaryInsight?>>

    /**
     * Get all insights for current user
     */
    fun getAllInsights(): Flow<RequestState<List<DiaryInsight>>>

    /**
     * Save a new insight (typically called by backend/cloud function)
     */
    suspend fun insertInsight(insight: DiaryInsight): RequestState<String>

    /**
     * Update user feedback on an insight
     */
    suspend fun submitFeedback(
        insightId: String,
        rating: Int,
        correction: String = ""
    ): RequestState<Boolean>

    /**
     * Delete an insight
     */
    suspend fun deleteInsight(insightId: String): RequestState<Boolean>
}
