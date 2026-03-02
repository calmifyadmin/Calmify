package com.lifo.home.domain.usecase

import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
/**
 * Retrieves diary entries filtered by a specific date (dayKey string "YYYY-MM-DD").
 */
class GetFilteredDiariesUseCase(
    private val diaryRepository: MongoRepository
) : FlowUseCase<String, RequestState<Map<LocalDate, List<Diary>>>> {

    override fun invoke(params: String): Flow<RequestState<Map<LocalDate, List<Diary>>>> {
        return diaryRepository.getFilteredDiaries(params)
    }
}
