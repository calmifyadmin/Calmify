package com.lifo.util.repository

import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * InsightRepository Interface
 *
 * Manages AI-generated diary insights in Firestore
 */
interface InsightRepository {
    fun getInsightByDiaryId(diaryId: String): Flow<RequestState<DiaryInsight?>>
    fun getAllInsights(): Flow<RequestState<List<DiaryInsight>>>
    suspend fun insertInsight(insight: DiaryInsight): RequestState<String>
    suspend fun submitFeedback(
        insightId: String,
        rating: Int,
        correction: String = ""
    ): RequestState<Boolean>
    suspend fun deleteInsight(insightId: String): RequestState<Boolean>
}
