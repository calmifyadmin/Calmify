package com.lifo.chat.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a chat session
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Instant = Instant.now(),
    val lastMessageAt: Instant = Instant.now(),
    val aiModel: String = "gemini-2.0-flash",
    val messageCount: Int = 0
)

/**
 * Domain model for a chat message
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Instant = Instant.now(),
    val status: MessageStatus = MessageStatus.SENT,
    val error: String? = null,
    val isStreaming: Boolean = false  // NUOVO: flag per messaggi in streaming
)

/**
 * Message status states
 */
enum class MessageStatus {
    SENDING,    // Message is being sent
    SENT,       // Message successfully sent
    FAILED,     // Message failed to send
    STREAMING   // AI response is streaming
}

/**
 * Streaming message temporaneo (non salvato in DB)
 */
data class StreamingMessage(
    val id: String = "streaming_${System.currentTimeMillis()}",
    val content: StringBuilder = StringBuilder(),
    val isComplete: Boolean = false
)

/**
 * Chat UI state
 */
data class ChatUiState(
    // Stati esistenti
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val showNewSessionDialog: Boolean = false,
    val sessionStarted: Boolean = false,

    // NUOVO: stato streaming separato
    val streamingMessage: StreamingMessage? = null,
    val isNavigating: Boolean = false  // NUOVO: previene navigazione durante operazioni
)

/**
 * Chat events for user interactions
 */
sealed class ChatEvent {
    data class SendMessage(val content: String) : ChatEvent()
    data class LoadSession(val sessionId: String) : ChatEvent()
    data class CreateNewSession(val title: String? = null) : ChatEvent()
    data class DeleteSession(val sessionId: String) : ChatEvent()
    data class DeleteMessage(val messageId: String) : ChatEvent()
    data class RetryMessage(val messageId: String) : ChatEvent()
    data class UpdateInputText(val text: String) : ChatEvent()
    data class ExportToDiary(val sessionId: String) : ChatEvent()
    object ClearError : ChatEvent()
    object ShowNewSessionDialog : ChatEvent()
    object HideNewSessionDialog : ChatEvent()
}

/**
 * Result wrapper for chat operations
 */
sealed class ChatResult<out T> {
    data class Success<T>(val data: T) : ChatResult<T>()
    data class Error(val exception: Throwable) : ChatResult<Nothing>()
    object Loading : ChatResult<Nothing>()
}