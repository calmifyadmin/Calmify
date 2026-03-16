package com.lifo.insight.domain

import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.RequestState
import com.lifo.util.repository.InsightRepository
import com.lifo.util.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow
/**
 * Fetches AI-generated insight for a specific diary entry.
 */
class GetInsightByDiaryIdUseCase(
    private val insightRepository: InsightRepository
) : FlowUseCase<String, RequestState<DiaryInsight?>> {

    override fun invoke(params: String): Flow<RequestState<DiaryInsight?>> {
        return insightRepository.getInsightByDiaryId(params)
    }
}
