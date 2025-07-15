package com.lifo.chat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.MessageStatus
import com.lifo.util.model.RequestState
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
        private const val STREAMING_DEBOUNCE_MS = 100L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val sessionId: String? = savedStateHandle.get<String>(KEY_SESSION_ID)
    private var streamingJob: Job? = null
    private var messagesJob: Job? = null
    private val streamingBuffer = StringBuilder()
    private var lastStreamingUpdate = 0L

    init {
        Log.d(TAG, "ChatViewModel initialized with sessionId: $sessionId")
        loadSessions()
        if (sessionId != null) {
            loadSession(sessionId)
        } else {
            viewModelScope.launch {
                createNewSession()
            }
        }
    }

    fun onEvent(event: ChatEvent) {
        if (_uiState.value.isNavigating) {
            Log.d(TAG, "Event ignored during navigation: $event")
            return
        }

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
            repository.getAllSessions()
                .distinctUntilChanged()
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            _uiState.update { it.copy(sessions = result.data) }
                        }
                        is RequestState.Error -> {
                            _uiState.update {
                                it.copy(error = result.error.message ?: "Failed to load sessions")
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun loadSession(sessionId: String) {
        Log.d(TAG, "Loading session: $sessionId")
        messagesJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isNavigating = true) }

            when (val result = repository.getSession(sessionId)) {
                is RequestState.Success -> {
                    val session = result.data
                    Log.d(TAG, "Session loaded: ${session.id}")
                    _uiState.update { it.copy(currentSession = session) }

                    messagesJob = viewModelScope.launch {
                        repository.getMessagesForSession(sessionId)
                            .distinctUntilChanged()
                            .collect { messagesResult ->
                                when (messagesResult) {
                                    is RequestState.Success -> {
                                        Log.d(TAG, "Messages updated: ${messagesResult.data.size}")
                                        _uiState.update { currentState ->
                                            currentState.copy(
                                                messages = messagesResult.data,
                                                isLoading = false,
                                                isNavigating = false,
                                                sessionStarted = messagesResult.data.isNotEmpty()
                                            )
                                        }
                                    }
                                    is RequestState.Error -> {
                                        _uiState.update {
                                            it.copy(
                                                error = messagesResult.error.message ?: "Failed to load messages",
                                                isLoading = false,
                                                isNavigating = false
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                    }
                }
                is RequestState.Error -> {
                    Log.e(TAG, "Session not found: $sessionId")
                    _uiState.update {
                        it.copy(
                            error = result.error.message ?: "Session not found",
                            isLoading = false,
                            isNavigating = false
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun sendMessage(content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) {
            Log.d(TAG, "Empty message, not sending")
            return
        }

        _uiState.update { it.copy(sessionStarted = true) }

        Log.d(TAG, "Sending message: $trimmedContent")
        val currentSession = _uiState.value.currentSession

        if (currentSession == null) {
            Log.d(TAG, "No current session, creating new one")
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
            _uiState.update { it.copy(inputText = "") }

            when (val result = repository.sendMessage(sessionId, content)) {
                is RequestState.Success -> {
                    Log.d(TAG, "Message sent successfully")
                    generateAiResponseOptimized(sessionId, content)
                }
                is RequestState.Error -> {
                    Log.e(TAG, "Failed to send message", result.error)
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to send message")
                    }
                }
                else -> {}
            }
        }
    }

    private fun generateAiResponseOptimized(sessionId: String, userMessage: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                val streamingMessage = StreamingMessage()
                _uiState.update { it.copy(streamingMessage = streamingMessage) }

                streamingBuffer.clear()
                lastStreamingUpdate = System.currentTimeMillis()

                val context = _uiState.value.messages.takeLast(10)

                repository.generateAiResponse(sessionId, userMessage, context)
                    .collect { result ->
                        when (result) {
                            is RequestState.Success -> {
                                streamingBuffer.clear()
                                streamingBuffer.append(result.data)

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastStreamingUpdate > STREAMING_DEBOUNCE_MS ||
                                    result.data.endsWith(".") ||
                                    result.data.endsWith("!") ||
                                    result.data.endsWith("?")) {

                                    _uiState.update { state ->
                                        state.copy(
                                            streamingMessage = streamingMessage.copy(
                                                content = StringBuilder(streamingBuffer.toString())
                                            )
                                        )
                                    }
                                    lastStreamingUpdate = currentTime
                                }
                            }
                            is RequestState.Error -> {
                                _uiState.update {
                                    it.copy(
                                        streamingMessage = null,
                                        error = "Failed to generate response: ${result.error.message}"
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                val finalContent = streamingBuffer.toString()
                if (finalContent.isNotEmpty()) {
                    repository.saveAiMessage(sessionId, finalContent)
                    _uiState.update { it.copy(streamingMessage = null) }
                }

            } finally {
                _uiState.update { it.copy(streamingMessage = null) }
            }
        }
    }

    fun getUserPhotoUrl(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user photo", e)
            null
        }
    }

    fun getUserDisplayName(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.displayName?.toString()?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user name", e)
            null
        }
    }

    private fun createNewSession(title: String? = null, onComplete: ((com.lifo.mongo.repository.ChatSession) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isNavigating = true) }

            when (val result = repository.createSession(title)) {
                is RequestState.Success -> {
                    Log.d(TAG, "Session created: ${result.data.id}")
                    _uiState.update {
                        it.copy(
                            currentSession = result.data,
                            messages = emptyList(),
                            showNewSessionDialog = false,
                            sessionStarted = false,
                            streamingMessage = null,
                            isNavigating = false
                        )
                    }
                    loadSession(result.data.id)
                    onComplete?.invoke(result.data)
                }
                is RequestState.Error -> {
                    Log.e(TAG, "Failed to create session", result.error)
                    _uiState.update {
                        it.copy(
                            error = result.error.message ?: "Failed to create session",
                            isNavigating = false
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteSession(sessionId)) {
                is RequestState.Success -> {
                    if (_uiState.value.currentSession?.id == sessionId) {
                        _uiState.update {
                            it.copy(
                                currentSession = null,
                                messages = emptyList(),
                                sessionStarted = false,
                                streamingMessage = null
                            )
                        }
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to delete session")
                    }
                }
                else -> {}
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteMessage(messageId)) {
                is RequestState.Success -> {
                    if (_uiState.value.messages.size <= 1) {
                        _uiState.update { it.copy(sessionStarted = false) }
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to delete message")
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
                    is RequestState.Success -> {
                        generateAiResponseOptimized(message.sessionId, message.content)
                    }
                    is RequestState.Error -> {
                        _uiState.update {
                            it.copy(error = result.error.message ?: "Failed to retry message")
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
                is RequestState.Success -> {
                    // TODO: Navigate to write screen with exported content
                    Log.d(TAG, "Exported content: ${result.data}")
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to export session")
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