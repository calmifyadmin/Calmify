package com.lifo.chat.domain.usecase

import com.lifo.util.model.ChatMessage
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ChatRepository
import com.lifo.util.usecase.UseCase
/**
 * Sends a chat message and returns the AI response.
 */
class SendMessageUseCase constructor(
    private val chatRepository: ChatRepository
) : UseCase<SendMessageUseCase.Params, RequestState<ChatMessage>> {

    data class Params(
        val sessionId: String,
        val content: String
    )

    override suspend fun invoke(params: Params): RequestState<ChatMessage> {
        return chatRepository.sendMessage(
            sessionId = params.sessionId,
            content = params.content
        )
    }
}
