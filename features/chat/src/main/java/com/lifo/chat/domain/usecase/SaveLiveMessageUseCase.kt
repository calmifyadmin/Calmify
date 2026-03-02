package com.lifo.chat.domain.usecase

import com.lifo.util.model.ChatMessage
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ChatRepository
import com.lifo.util.usecase.UseCase
/**
 * Saves a live chat message to the chat database for unification with text chat.
 */
class SaveLiveMessageUseCase constructor(
    private val chatRepository: ChatRepository
) : UseCase<SaveLiveMessageUseCase.Params, RequestState<ChatMessage>> {

    data class Params(
        val sessionId: String,
        val content: String,
        val isUser: Boolean
    )

    override suspend fun invoke(params: Params): RequestState<ChatMessage> {
        return chatRepository.saveLiveMessage(
            sessionId = params.sessionId,
            content = params.content,
            isUser = params.isUser
        )
    }
}
