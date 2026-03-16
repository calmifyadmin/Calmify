package com.lifo.home.domain.usecase

import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.util.usecase.NoParamUseCase
/**
 * Deletes all user data (diaries, insights, profiles, chat sessions, etc.).
 */
class DeleteAllUserDataUseCase(
    private val diaryRepository: MongoRepository
) : NoParamUseCase<RequestState<Boolean>> {

    override suspend fun invoke(): RequestState<Boolean> {
        return diaryRepository.deleteAllUserData()
    }
}
