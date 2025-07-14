package com.lifo.chat.data.repository

import com.lifo.chat.domain.model.ChatMessage
import com.lifo.chat.domain.model.ChatResult
import com.lifo.chat.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Session operations
    fun getAllSessions(): Flow<List<ChatSession>>
    suspend fun getSession(sessionId: String): ChatSession?
    suspend fun createSession(title: String? = null): ChatResult<ChatSession>
    suspend fun updateSession(session: ChatSession): ChatResult<Unit>
    suspend fun deleteSession(sessionId: String): ChatResult<Unit>

    // Message operations
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(sessionId: String, content: String): ChatResult<ChatMessage>
    suspend fun deleteMessage(messageId: String): ChatResult<Unit>
    suspend fun retryMessage(messageId: String): ChatResult<ChatMessage>

    // AI operations
    suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>
    ): Flow<ChatResult<String>>

    // NUOVO: Salva messaggio AI solo dopo streaming completo
    suspend fun saveAiMessage(sessionId: String, content: String): ChatResult<ChatMessage>

    // Export operations
    suspend fun exportSessionToDiary(sessionId: String): ChatResult<String>
}