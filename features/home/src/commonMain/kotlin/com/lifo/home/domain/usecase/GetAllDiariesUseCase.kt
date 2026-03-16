package com.lifo.home.domain.usecase

import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.usecase.NoParamFlowUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
/**
 * Retrieves all diary entries grouped by date.
 */
class GetAllDiariesUseCase(
    private val diaryRepository: MongoRepository
) : NoParamFlowUseCase<RequestState<Map<LocalDate, List<Diary>>>> {

    override fun invoke(): Flow<RequestState<Map<LocalDate, List<Diary>>>> {
        return diaryRepository.getAllDiaries()
    }
}
