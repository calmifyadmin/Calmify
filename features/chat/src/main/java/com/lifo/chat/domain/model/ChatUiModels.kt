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
    val isNavigating: Boolean = false,
    val exportedContent: String? = null
)

data class StreamingMessage(
    val id: String = "streaming_${System.currentTimeMillis()}",
    val content: StringBuilder = StringBuilder(),
    val isComplete: Boolean = false
)

// Voice support state
data class VoiceState(
    val isTTSReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val currentSpeakingMessageId: String? = null,
    val autoSpeak: Boolean = false
)

// Smart suggestion model
data class SmartSuggestion(
    val id: String,
    val text: String,
    val category: SuggestionCategory,
    val icon: String = ""
)

enum class SuggestionCategory {
    MOOD,
    PLANNING,
    CHECK_IN,
    WELLNESS,
    REFLECTION,
    SUPPORT,
    LIFESTYLE
}

// Events
sealed class ChatEvent {
    // Message events
    data class SendMessage(val content: String) : ChatEvent()
    data class DeleteMessage(val messageId: String) : ChatEvent()
    data class RetryMessage(val messageId: String) : ChatEvent()
    data class UpdateInputText(val text: String) : ChatEvent()

    // Session events
    data class LoadSession(val sessionId: String) : ChatEvent()
    data class CreateNewSession(val title: String? = null) : ChatEvent()
    data class DeleteSession(val sessionId: String) : ChatEvent()
    data class ExportToDiary(val sessionId: String) : ChatEvent()

    // Voice events
    data class SpeakMessage(val messageId: String) : ChatEvent()
    object StopSpeaking : ChatEvent()

    // Smart features
    data class UseSuggestion(val suggestion: SmartSuggestion) : ChatEvent()

    // UI events
    object ClearError : ChatEvent()
    object ShowNewSessionDialog : ChatEvent()
    object HideNewSessionDialog : ChatEvent()
}