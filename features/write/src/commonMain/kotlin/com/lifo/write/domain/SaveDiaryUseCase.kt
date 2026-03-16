package com.lifo.write.domain

import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.usecase.UseCase
/**
 * Saves or updates a diary entry in Firestore.
 */
class SaveDiaryUseCase(
    private val diaryRepository: MongoRepository
) : UseCase<Diary, RequestState<Diary>> {

    override suspend fun invoke(params: Diary): RequestState<Diary> {
        return diaryRepository.insertDiary(params)
    }
}
