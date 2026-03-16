package com.lifo.write.domain

import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.usecase.FlowUseCase
import kotlinx.coroutines.flow.Flow
/**
 * Fetches a specific diary entry by ID.
 */
class GetDiaryByIdUseCase(
    private val diaryRepository: MongoRepository
) : FlowUseCase<String, RequestState<Diary>> {

    override fun invoke(params: String): Flow<RequestState<Diary>> {
        return diaryRepository.getSelectedDiary(params)
    }
}
