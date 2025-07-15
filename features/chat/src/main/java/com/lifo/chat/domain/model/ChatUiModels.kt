package com.lifo.chat.domain.model

import com.lifo.mongo.repository.ChatSession
import com.lifo.mongo.repository.ChatMessage

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val showNewSessionDialog: Boolean = false,
    val sessionStarted: Boolean = false,
    val streamingMessage: StreamingMessage? = null,
    val isNavigating: Boolean = false
)

data class StreamingMessage(
    val id: String = "streaming_${System.currentTimeMillis()}",
    val content: StringBuilder = StringBuilder(),
    val isComplete: Boolean = false
)

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