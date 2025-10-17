package com.lifo.chat.presentation.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatMessage
import com.lifo.mongo.repository.ChatRepository
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @ApplicationContext private val context: Context,
    private val voiceSystem: GeminiNativeVoiceSystem,
    private val apiConfigManager: ApiConfigManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Current session tracking
    private var currentSessionId: String? = null

    // Voice state exposed to UI (existing Gemini system)
    val voiceState = voiceSystem.voiceState
    val isVoiceActive = voiceState
        .map { it.isSpeaking }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )
    val voiceEmotion = voiceState
        .map { it.emotion }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = GeminiNativeVoiceSystem.Emotion.NEUTRAL
        )
    val voiceLatency = voiceState
        .map { it.latencyMs }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = 0L
        )

    private val emotionDetector = SimpleEmotionDetector()

    // SEMPLIFICATO: niente più chunking, solo messaggi completi
    private var pendingVoiceMessage: String? = null
    private var pendingVoiceMessageId: String? = null

    init {
        viewModelScope.launch {
            Log.d(TAG, "🎙️ Initializing ChatViewModel...")
            initializeVoiceSystem()
            // Only create new session if no existing session is loaded
            if (currentSessionId == null) {
                Log.d(TAG, "📝 Creating new session (no existing session loaded)")
                createNewSession()
            } else {
                Log.d(TAG, "♻️ Skipping new session creation - existing session loaded: $currentSessionId")
            }
        }
    }

    private suspend fun initializeVoiceSystem() {
        try {
            Log.d(TAG, "🔧 Starting voice system initialization...")

            val apiKey = apiConfigManager.getGeminiApiKey()

            if (apiKey.isEmpty()) {
                Log.e(TAG, "❌ API key is empty")
                _uiState.update {
                    it.copy(error = "API key not configured")
                }
                return
            }

            Log.d(TAG, "🔑 Initializing voice system with API key")
            voiceSystem.initialize(apiKey)

            delay(500)

            val isInitialized = voiceSystem.voiceState.value.isInitialized
            Log.d(TAG, "🎙️ Voice system initialized: $isInitialized")

            if (!isInitialized) {
                Log.e(TAG, "❌ Voice system failed to initialize")
                _uiState.update {
                    it.copy(error = "Voice system initialization failed")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during voice initialization", e)
            _uiState.update {
                it.copy(error = "Voice error: ${e.message}")
            }
        }
    }

    fun loadExistingSession(sessionId: String) {
        Log.d(TAG, "Loading existing session: $sessionId")
        viewModelScope.launch {
            try {
                // Load the session from database
                val sessionResult = repository.getSession(sessionId)
                val session = when (sessionResult) {
                    is RequestState.Success -> sessionResult.data
                    else -> null
                }
                session?.let {
                    // Update current session
                    currentSessionId = sessionId
                    
                    // Update UI state with session info
                    _uiState.update { state ->
                        state.copy(
                            currentSession = it,
                            sessionStarted = true
                        )
                    }
                    
                    // Load messages for this session
                    loadMessages(sessionId)
                    
                    Log.d(TAG, "Loaded session $sessionId with ${it.messageCount} messages")
                } ?: run {
                    Log.w(TAG, "Session $sessionId not found")
                    _uiState.update { state ->
                        state.copy(
                            error = "Session not found"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading session $sessionId", e)
                _uiState.update { state ->
                    state.copy(
                        error = e.message ?: "Failed to load session"
                    )
                }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // Stop any ongoing speech
        voiceSystem.stop()
        pendingVoiceMessage = null
        pendingVoiceMessageId = null

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "") }

            val sessionId = _uiState.value.currentSession?.id ?: return@launch

            when (val result = repository.sendMessage(sessionId, content)) {
                is RequestState.Success -> {
                    generateAiResponseWithVoice(sessionId, content)
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message)
                    }
                }
                else -> {}
            }
        }
    }

    private fun generateAiResponseWithVoice(sessionId: String, userMessage: String) {
        viewModelScope.launch {
            val streamingMessage = StreamingMessage()
            _uiState.update { it.copy(streamingMessage = streamingMessage) }

            var fullContent = ""
            val currentMessageId = "streaming_${System.currentTimeMillis()}"

            repository.generateAiResponse(sessionId, userMessage, emptyList())
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            fullContent = result.data

                            // Update UI with streaming text
                            _uiState.update { state ->
                                state.copy(
                                    streamingMessage = streamingMessage.copy(
                                        content = StringBuilder(fullContent)
                                    )
                                )
                            }

                            // Store for voice but DON'T speak yet - wait for complete message
                            pendingVoiceMessage = fullContent
                            pendingVoiceMessageId = currentMessageId
                        }
                        is RequestState.Error -> {
                            _uiState.update {
                                it.copy(
                                    streamingMessage = null,
                                    error = result.error.message
                                )
                            }
                        }
                        else -> {}
                    }
                }

            // STREAMING COMPLETE - Now speak the ENTIRE message at once
            if (fullContent.isNotEmpty()) {
                // Save the message first
                repository.saveAiMessage(sessionId, fullContent)
                _uiState.update { it.copy(streamingMessage = null) }

                // Then speak the ENTIRE message if voice is initialized
                if (voiceSystem.voiceState.value.isInitialized && pendingVoiceMessage != null) {
                    speakCompleteMessage(pendingVoiceMessage!!, currentMessageId)
                }
            }
        }
    }

    private fun speakCompleteMessage(text: String, messageId: String) {
        viewModelScope.launch {
            try {
                // Clean the text
                val cleanText = cleanTextForSpeech(text)

                // Detect overall emotion for the entire message
                val emotion = emotionDetector.detectEmotion(cleanText)

                Log.d(TAG, "🎤 Speaking complete message: ${cleanText.take(50)}...")

                // Speak the ENTIRE message in one go
                voiceSystem.speakWithEmotion(
                    text = cleanText,
                    emotion = emotion,
                    messageId = messageId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking message", e)
            }
        }
    }

    fun speakMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId }
        message?.let {
            if (!it.isUser) {
                voiceSystem.stop()

                viewModelScope.launch {
                    delay(100) // Small delay to ensure stop is processed

                    val cleanText = cleanTextForSpeech(it.content)
                    val emotion = emotionDetector.detectEmotion(cleanText)

                    // Speak the ENTIRE message at once
                    voiceSystem.speakWithEmotion(
                        text = cleanText,
                        emotion = emotion,
                        messageId = messageId
                    )
                }
            }
        }
    }

    fun stopSpeaking() {
        voiceSystem.stop()
        pendingVoiceMessage = null
        pendingVoiceMessageId = null
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("**", "")
            .replace("*", "")
            .replace("_", "")
            .replace("`", "")
            .replace("```", "")
            .replace("#", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deleteMessage(messageId: String) {
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

    private fun createNewSession() {
        viewModelScope.launch {
            when (val result = repository.createSession(null)) {
                is RequestState.Success -> {
                    _uiState.update {
                        it.copy(
                            currentSession = result.data,
                            messages = emptyList()
                        )
                    }
                    loadMessages(result.data.id)
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message)
                    }
                }
                else -> {}
            }
        }
    }

    private fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            repository.getMessagesForSession(sessionId)
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            _uiState.update {
                                it.copy(messages = result.data)
                            }
                        }
                        is RequestState.Error -> {
                            _uiState.update {
                                it.copy(error = result.error.message)
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceSystem.cleanup()
    }

    // Simplified emotion detector
    inner class SimpleEmotionDetector {
        fun detectEmotion(text: String): GeminiNativeVoiceSystem.Emotion {
            val lowercaseText = text.lowercase()

            return when {
                lowercaseText.contains(Regex("fantastico|incredibile|wow|meraviglioso|stupendo")) ->
                    GeminiNativeVoiceSystem.Emotion.EXCITED

                lowercaseText.contains(Regex("felice|contento|bene|ottimo|bravo|perfetto")) ->
                    GeminiNativeVoiceSystem.Emotion.HAPPY

                lowercaseText.contains(Regex("triste|dispiaciuto|purtroppo|male|difficile")) ->
                    GeminiNativeVoiceSystem.Emotion.SAD

                lowercaseText.contains(Regex("penso|credo|forse|probabilmente|consideriamo")) ->
                    GeminiNativeVoiceSystem.Emotion.THOUGHTFUL

                lowercaseText.contains(Regex("capisco|comprendo|mi dispiace|sono qui")) ->
                    GeminiNativeVoiceSystem.Emotion.EMPATHETIC

                text.contains("?") || lowercaseText.contains(Regex("interessante|curioso|dimmi")) ->
                    GeminiNativeVoiceSystem.Emotion.CURIOUS

                else -> GeminiNativeVoiceSystem.Emotion.NEUTRAL
            }
        }
    }
}