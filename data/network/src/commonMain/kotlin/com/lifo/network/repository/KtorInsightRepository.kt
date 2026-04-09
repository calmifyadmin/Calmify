package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.DiaryInsightListResponse
import com.lifo.shared.api.DiaryInsightResponse
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.RequestState
import com.lifo.util.repository.InsightRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorInsightRepository(
    private val api: KtorApiClient,
) : InsightRepository {

    override fun getInsightByDiaryId(diaryId: String): Flow<RequestState<DiaryInsight?>> = flow {
        emit(RequestState.Loading)
        val result = api.get<DiaryInsightResponse>("/api/v1/insights/diary/$diaryId")
        emit(result.map { it.data?.toDomain() })
    }

    override fun getAllInsights(): Flow<RequestState<List<DiaryInsight>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<DiaryInsightListResponse>("/api/v1/insights?limit=100")
        emit(result.map { it.data.map { p -> p.toDomain() } })
    }

    override suspend fun insertInsight(insight: DiaryInsight): RequestState<String> {
        val result = api.post<DiaryInsightResponse>("/api/v1/insights", insight.toProto())
        return result.map { it.data?.id ?: "" }
    }

    override suspend fun submitFeedback(
        insightId: String,
        rating: Int,
        correction: String,
    ): RequestState<Boolean> {
        val body = mapOf("rating" to rating, "correction" to correction)
        return api.postNoBody("/api/v1/insights/$insightId/feedback") {
            url { parameters.append("rating", rating.toString()); parameters.append("correction", correction) }
        }
    }

    override suspend fun deleteInsight(insightId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/insights/$insightId")
    }
}
