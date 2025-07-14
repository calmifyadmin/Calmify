package com.lifo.chat.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
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
        private const val STREAMING_DEBOUNCE_MS = 100L
    }

    // UI State
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Current session ID from navigation
    private val sessionId: String? = savedStateHandle.get<String>(KEY_SESSION_ID)

    // Job per AI response streaming
    private var streamingJob: Job? = null

    // Job per observing messages - IMPORTANTE: uno solo per evitare duplicati
    private var messagesJob: Job? = null

    // Buffer per ottimizzare streaming
    private val streamingBuffer = StringBuilder()
    private var lastStreamingUpdate = 0L

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
        // Previeni eventi durante navigazione
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
                .distinctUntilChanged() // Evita update duplicati
                .collect { sessions ->
                    _uiState.update { it.copy(sessions = sessions) }
                }
        }
    }

    private fun loadSession(sessionId: String) {
        Log.d(TAG, "Loading session: $sessionId")

        // IMPORTANTE: Cancella job precedente per evitare duplicati
        messagesJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isNavigating = true) }

            try {
                val session = repository.getSession(sessionId)
                if (session != null) {
                    Log.d(TAG, "Session loaded: ${session.id}")
                    _uiState.update { it.copy(currentSession = session) }

                    // UN SOLO JOB per osservare messaggi
                    messagesJob = viewModelScope.launch {
                        repository.getMessagesForSession(sessionId)
                            .distinctUntilChanged() // CRITICO: evita re-emit degli stessi messaggi
                            .collect { messages ->
                                Log.d(TAG, "Messages updated: ${messages.size}")
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        messages = messages.filter { !it.isStreaming }, // Filtra messaggi streaming
                                        isLoading = false,
                                        isNavigating = false,
                                        sessionStarted = messages.isNotEmpty()
                                    )
                                }
                            }
                    }
                } else {
                    Log.e(TAG, "Session not found: $sessionId")
                    _uiState.update {
                        it.copy(
                            error = "Session not found",
                            isLoading = false,
                            isNavigating = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to load session",
                        isLoading = false,
                        isNavigating = false
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
            // Clear input immediately
            _uiState.update { it.copy(inputText = "") }

            // Send user message
            when (val result = repository.sendMessage(sessionId, content)) {
                is ChatResult.Success -> {
                    Log.d(TAG, "Message sent successfully")
                    // Generate AI response con streaming ottimizzato
                    generateAiResponseOptimized(sessionId, content)
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

    // NUOVO: Generazione AI ottimizzata senza salvare in DB durante streaming
    private fun generateAiResponseOptimized(sessionId: String, userMessage: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                // Crea messaggio streaming temporaneo
                val streamingMessage = StreamingMessage()
                _uiState.update { it.copy(streamingMessage = streamingMessage) }

                // Reset buffer
                streamingBuffer.clear()
                lastStreamingUpdate = System.currentTimeMillis()

                val context = _uiState.value.messages.takeLast(10)

                repository.generateAiResponse(sessionId, userMessage, context)
                    .collect { result ->
                        when (result) {
                            is ChatResult.Success -> {
                                streamingBuffer.clear()
                                streamingBuffer.append(result.data)

                                // Aggiorna UI con debouncing
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
                            is ChatResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        streamingMessage = null,
                                        error = "Failed to generate response: ${result.exception.message}"
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                // Salva messaggio finale una sola volta
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
            Log.e(com.lifo.chat.presentation.viewmodel.ChatViewModel.TAG, "Error getting user photo", e)
            null
        }
    }

    fun getUserDisplayName(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.displayName?.toString()?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            Log.e(com.lifo.chat.presentation.viewmodel.ChatViewModel.TAG, "Error getting user photo", e)
            null
        }
    }

    private fun createNewSession(title: String? = null, onComplete: ((ChatSession) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isNavigating = true) }

            when (val result = repository.createSession(title)) {
                is ChatResult.Success -> {
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
                    // Load session properly
                    loadSession(result.data.id)
                    onComplete?.invoke(result.data)
                }
                is ChatResult.Error -> {
                    Log.e(TAG, "Failed to create session", result.exception)
                    _uiState.update {
                        it.copy(
                            error = result.exception.message ?: "Failed to create session",
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
                is ChatResult.Success -> {
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
                        generateAiResponseOptimized(message.sessionId, message.content)
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