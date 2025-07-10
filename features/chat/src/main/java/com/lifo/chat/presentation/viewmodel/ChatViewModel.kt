package com.lifo.chat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.chat.data.repository.ChatRepository
import com.lifo.chat.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val KEY_SESSION_ID = "sessionId"
    }

    // UI State
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Current session ID from navigation
    private val sessionId: String? = savedStateHandle.get<String>(KEY_SESSION_ID)

    // Job for AI response streaming
    private var streamingJob: Job? = null

    // Job for observing messages
    private var messagesJob: Job? = null

    init {
        Log.d(TAG, "ChatViewModel initialized with sessionId: $sessionId")
        loadSessions()
        if (sessionId != null) {
            loadSession(sessionId)
        } else {
            // Create a default session if none provided
            viewModelScope.launch {
                createNewSession()
            }
        }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.content)
            is ChatEvent.LoadSession -> loadSession(event.sessionId)
            is ChatEvent.CreateNewSession -> createNewSession(event.title)
            is ChatEvent.DeleteSession -> deleteSession(event.sessionId)
            is ChatEvent.DeleteMessage -> deleteMessage(event.messageId)
            is ChatEvent.RetryMessage -> retryMessage(event.messageId)
            is ChatEvent.UpdateInputText -> updateInputText(event.text)
            is ChatEvent.ExportToDiary -> exportToDiary(event.sessionId)
            is ChatEvent.ClearError -> clearError()
            is ChatEvent.ShowNewSessionDialog -> showNewSessionDialog()
            is ChatEvent.HideNewSessionDialog -> hideNewSessionDialog()
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            repository.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }

    private fun loadSession(sessionId: String) {
        Log.d(TAG, "Loading session: $sessionId")

        // Cancel previous messages observation
        messagesJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Load session details
                val session = repository.getSession(sessionId)
                if (session != null) {
                    Log.d(TAG, "Session loaded: ${session.id}")
                    _uiState.update { it.copy(currentSession = session) }

                    // Start observing messages for this session
                    messagesJob = viewModelScope.launch {
                        repository.getMessagesForSession(sessionId).collect { messages ->
                            Log.d(TAG, "Messages updated for session $sessionId: ${messages.size} messages")
                            _uiState.update { currentState ->
                                currentState.copy(
                                    messages = messages,
                                    isLoading = false,
                                    sessionStarted = messages.isNotEmpty() // Set sessionStarted based on messages
                                )
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Session not found: $sessionId")
                    _uiState.update {
                        it.copy(
                            error = "Session not found",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load session",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun sendMessage(content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) {
            Log.d(TAG, "Empty message, not sending")
            return
        }

        // Mark session as started
        _uiState.update { it.copy(sessionStarted = true) }

        Log.d(TAG, "Sending message: $trimmedContent")
        val currentSession = _uiState.value.currentSession

        if (currentSession == null) {
            Log.d(TAG, "No current session, creating new one")
            // Create new session if none exists
            createNewSession { session ->
                Log.d(TAG, "New session created: ${session.id}")
                sendMessageToSession(session.id, trimmedContent)
            }
        } else {
            Log.d(TAG, "Using existing session: ${currentSession.id}")
            sendMessageToSession(currentSession.id, trimmedContent)
        }
    }

    private fun sendMessageToSession(sessionId: String, content: String) {
        viewModelScope.launch {
            // Clear input immediately for better UX
            _uiState.update { it.copy(inputText = "") }

            // Send user message
            when (val result = repository.sendMessage(sessionId, content)) {
                is ChatResult.Success -> {
                    Log.d(TAG, "Message sent successfully")
                    // Generate AI response
                    generateAiResponse(sessionId, content)
                }
                is ChatResult.Error -> {
                    Log.e(TAG, "Failed to send message", result.exception)
                    _uiState.update {
                        it.copy(error = result.exception.message ?: "Failed to send message")
                    }
                }
                else -> {}
            }
        }
    }

    private fun generateAiResponse(sessionId: String, userMessage: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _uiState.update { it.copy(isStreamingResponse = true) }

            try {
                val context = _uiState.value.messages.takeLast(10)

                repository.generateAiResponse(sessionId, userMessage, context)
                    .collect { result ->
                        when (result) {
                            is ChatResult.Success -> {
                                // Response is being streamed and updated in real-time
                                Log.d(TAG, "AI Response received in ViewModel: ${result.data.take(100)}")
                            }
                            is ChatResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        error = "Failed to generate response: ${result.exception.message}",
                                        isStreamingResponse = false
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
            } finally {
                _uiState.update { it.copy(isStreamingResponse = false) }
            }
        }
    }

    private fun createNewSession(title: String? = null, onComplete: ((ChatSession) -> Unit)? = null) {
        viewModelScope.launch {
            when (val result = repository.createSession(title)) {
                is ChatResult.Success -> {
                    Log.d(TAG, "Session created: ${result.data.id}")
                    _uiState.update {
                        it.copy(
                            currentSession = result.data,
                            messages = emptyList(),
                            showNewSessionDialog = false,
                            sessionStarted = false // Reset session started flag
                        )
                    }
                    // Important: Start observing messages for the new session
                    loadSession(result.data.id)
                    onComplete?.invoke(result.data)
                }
                is ChatResult.Error -> {
                    Log.e(TAG, "Failed to create session", result.exception)
                    _uiState.update {
                        it.copy(error = result.exception.message ?: "Failed to create session")
                    }
                }
                else -> {}
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteSession(sessionId)) {
                is ChatResult.Success -> {
                    // If current session was deleted, clear it
                    if (_uiState.value.currentSession?.id == sessionId) {
                        _uiState.update {
                            it.copy(
                                currentSession = null,
                                messages = emptyList(),
                                sessionStarted = false
                            )
                        }
                    }
                }
                is ChatResult.Error -> {
                    _uiState.update {
                        it.copy(error = result.exception.message ?: "Failed to delete session")
                    }
                }
                else -> {}
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteMessage(messageId)) {
                is ChatResult.Success -> {
                    // Check if all messages are deleted
                    if (_uiState.value.messages.size <= 1) {
                        _uiState.update { it.copy(sessionStarted = false) }
                    }
                }
                is ChatResult.Error -> {
                    _uiState.update {
                        it.copy(error = result.exception.message ?: "Failed to delete message")
                    }
                }
                else -> {}
            }
        }
    }

    private fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.id == messageId }
            if (message != null && message.isUser) {
                when (val result = repository.retryMessage(messageId)) {
                    is ChatResult.Success -> {
                        // Re-generate AI response
                        generateAiResponse(message.sessionId, message.content)
                    }
                    is ChatResult.Error -> {
                        _uiState.update {
                            it.copy(error = result.exception.message ?: "Failed to retry message")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun exportToDiary(sessionId: String) {
        viewModelScope.launch {
            when (val result = repository.exportSessionToDiary(sessionId)) {
                is ChatResult.Success -> {
                    // TODO: Navigate to write screen with exported content
                    Log.d(TAG, "Exported content: ${result.data}")
                }
                is ChatResult.Error -> {
                    _uiState.update {
                        it.copy(error = result.exception.message ?: "Failed to export session")
                    }
                }
                else -> {}
            }
        }
    }

    private fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun showNewSessionDialog() {
        _uiState.update { it.copy(showNewSessionDialog = true) }
    }

    private fun hideNewSessionDialog() {
        _uiState.update { it.copy(showNewSessionDialog = false) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        messagesJob?.cancel()
    }
}