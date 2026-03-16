package com.lifo.write.domain

import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.usecase.UseCase
/**
 * Deletes a diary entry from Firestore.
 */
class DeleteDiaryUseCase(
    private val diaryRepository: MongoRepository
) : UseCase<String, RequestState<Boolean>> {

    /**
     * @param params diary ID to delete
     */
    override suspend fun invoke(params: String): RequestState<Boolean> {
        return diaryRepository.deleteDiary(params)
    }
}
