package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.api.AiChatRequest
import com.lifo.shared.api.AiChatResponse
import com.lifo.shared.api.ChatMessageListResponse
import com.lifo.shared.api.ChatSessionListResponse
import com.lifo.shared.api.ChatSessionResponse
import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.ChatMessage
import com.lifo.util.model.ChatSession
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorChatRepository(
    private val api: KtorApiClient,
) : ChatRepository {

    override fun getAllSessions(): Flow<RequestState<List<ChatSession>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ChatSessionListResponse>("/api/v1/chat/sessions")
        emit(result.map { it.data.map { s -> s.toDomain() } })
    }

    override suspend fun getSession(sessionId: String): RequestState<ChatSession> {
        return api.get<ChatSessionResponse>("/api/v1/chat/sessions/$sessionId")
            .map { it.data.toDomain() }
    }

    override suspend fun createSession(title: String?): RequestState<ChatSession> {
        val body = ChatSessionProto(title = title ?: "Nuova conversazione")
        return api.post<ChatSessionResponse>("/api/v1/chat/sessions", body)
            .map { it.data.toDomain() }
    }

    override suspend fun updateSession(session: ChatSession): RequestState<Unit> {
        return api.put("/api/v1/chat/sessions/${session.id}", session.toProto())
    }

    override suspend fun deleteSession(sessionId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/chat/sessions/$sessionId")
    }

    override fun getMessagesForSession(sessionId: String): Flow<RequestState<List<ChatMessage>>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ChatMessageListResponse>("/api/v1/chat/sessions/$sessionId/messages")
        emit(result.map { it.data.map { m -> m.toDomain() } })
    }

    override suspend fun sendMessage(sessionId: String, content: String): RequestState<ChatMessage> {
        val body = ChatMessageProto(sessionId = sessionId, content = content, isUser = true)
        return api.post<ChatMessageProto>("/api/v1/chat/sessions/$sessionId/messages", body)
            .map { it.toDomain() }
    }

    override suspend fun deleteMessage(messageId: String): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/chat/messages/$messageId")
    }

    override suspend fun retryMessage(messageId: String): RequestState<ChatMessage> {
        return api.post<ChatMessageProto>("/api/v1/chat/messages/$messageId/retry")
            .map { it.toDomain() }
    }

    override suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>,
    ): Flow<RequestState<String>> = flow {
        emit(RequestState.Loading)
        val request = AiChatRequest(sessionId = sessionId, message = userMessage)
        val result = api.post<AiChatResponse>("/api/v1/ai/chat", request)
        emit(result.map { it.message })
    }

    override suspend fun saveAiMessage(sessionId: String, content: String): RequestState<ChatMessage> {
        val body = ChatMessageProto(sessionId = sessionId, content = content, isUser = false)
        return api.post<ChatMessageProto>("/api/v1/chat/sessions/$sessionId/messages", body)
            .map { it.toDomain() }
    }

    override suspend fun exportSessionToDiary(sessionId: String): RequestState<String> {
        val result = api.post<ExportResponse>("/api/v1/chat/sessions/$sessionId/export")
        return result.map { it.diaryId }
    }

    @kotlinx.serialization.Serializable
    private data class ExportResponse(val diaryId: String = "")

    override suspend fun deleteAllSessions(): RequestState<Boolean> {
        return api.deleteNoBody("/api/v1/chat/sessions")
    }

    override suspend fun saveLiveMessage(
        sessionId: String,
        content: String,
        isUser: Boolean,
    ): RequestState<ChatMessage> {
        val body = ChatMessageProto(sessionId = sessionId, content = content, isUser = isUser)
        return api.post<ChatMessageProto>("/api/v1/chat/sessions/$sessionId/messages", body)
            .map { it.toDomain() }
    }

    override suspend fun getCrossSessionContext(
        currentSessionId: String,
        fromLiveSessions: Boolean,
        maxMessages: Int,
    ): String {
        // Cross-session context requires local DB access — REST can't efficiently do this
        return ""
    }
}
