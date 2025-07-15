package com.lifo.mongo.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Session operations
    fun getAllSessions(): Flow<RequestState<List<ChatSession>>>
    suspend fun getSession(sessionId: String): RequestState<ChatSession>
    suspend fun createSession(title: String? = null): RequestState<ChatSession>
    suspend fun updateSession(session: ChatSession): RequestState<Unit>
    suspend fun deleteSession(sessionId: String): RequestState<Boolean>

    // Message operations
    fun getMessagesForSession(sessionId: String): Flow<RequestState<List<ChatMessage>>>
    suspend fun sendMessage(sessionId: String, content: String): RequestState<ChatMessage>
    suspend fun deleteMessage(messageId: String): RequestState<Boolean>
    suspend fun retryMessage(messageId: String): RequestState<ChatMessage>

    // AI operations
    suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>
    ): Flow<RequestState<String>>

    suspend fun saveAiMessage(sessionId: String, content: String): RequestState<ChatMessage>

    // Export operations
    suspend fun exportSessionToDiary(sessionId: String): RequestState<String>
}