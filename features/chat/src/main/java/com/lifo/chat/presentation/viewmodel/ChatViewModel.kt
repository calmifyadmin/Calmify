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
import com.lifo.chat.data.realtime.*
import com.lifo.chat.domain.audio.PushToTalkController
import com.lifo.chat.domain.audio.RealtimeAudioPlayer
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
    private val realtimeSessionManager: RealtimeSessionManager,
    private val pushToTalkController: PushToTalkController,
    private val realtimeAudioPlayer: RealtimeAudioPlayer,
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

    // OpenAI Realtime Live Chat State
    private val _liveChatState = MutableStateFlow<LiveChatState>(LiveChatState.Idle)
    val liveChatState = _liveChatState.asStateFlow()
    
    // Push-to-talk state
    val pushToTalkState = pushToTalkController.state
    
    // Realtime session state
    val realtimeSessionState = realtimeSessionManager.sessionState
    val realtimeConversationState = realtimeSessionManager.conversationState

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

    // ============= OPENAI REALTIME LIVE CHAT FUNCTIONS =============
    
    /**
     * Start a live voice chat session using OpenAI Realtime API
     */
    fun startLiveChat() {
        viewModelScope.launch {
            Log.d(TAG, "Starting live chat session...")
            
            try {
                _liveChatState.value = LiveChatState.Connecting
                
                val sessionConfig = SessionConfig(
                    instructions = """
                        You are a compassionate mental health companion in the Calmify app.
                        Provide supportive, empathetic responses in Italian when speaking to Italian users.
                        Keep responses conversational, warm, and concise (30-60 seconds of speech max).
                        Focus on emotional wellness, active listening, and positive coping strategies.
                        Use a gentle, caring tone that makes users feel heard and understood.
                    """.trimIndent(),
                    voice = "shimmer",
                    temperature = 0.8f,
                    modalities = listOf("text", "audio")
                )
                
                val result = realtimeSessionManager.startSession(sessionConfig)
                
                if (result.isSuccess) {
                    Log.d(TAG, "Live chat session started successfully")
                    _liveChatState.value = LiveChatState.Connected
                    
                    // Start audio player for response playback
                    realtimeAudioPlayer.startPlayback()
                    
                    // Start observing realtime events for audio playback
                    observeRealtimeAudioEvents()
                    
                } else {
                    Log.e(TAG, "Failed to start live chat: ${result.exceptionOrNull()?.message}")
                    _liveChatState.value = LiveChatState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to start live chat",
                        result.exceptionOrNull()
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting live chat", e)
                _liveChatState.value = LiveChatState.Error("Live chat error: ${e.message}", e)
            }
        }
    }
    
    /**
     * End the current live chat session
     */
    fun endLiveChat() {
        viewModelScope.launch {
            Log.d(TAG, "Ending live chat session...")
            
            try {
                // Stop any ongoing recording
                if (pushToTalkState.value.isRecording) {
                    pushToTalkController.cancelRecording()
                }
                
                // Stop audio playback
                realtimeAudioPlayer.stopPlayback()
                
                // End the realtime session
                realtimeSessionManager.endSession()
                
                _liveChatState.value = LiveChatState.Idle
                
                Log.d(TAG, "Live chat session ended")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error ending live chat", e)
                _liveChatState.value = LiveChatState.Error("Error ending session: ${e.message}", e)
            }
        }
    }
    
    /**
     * Start push-to-talk recording
     */
    fun onPushToTalkPressed() {
        viewModelScope.launch {
            if (!realtimeSessionState.value.isConnected) {
                Log.w(TAG, "Cannot start recording: not connected to realtime session")
                return@launch
            }
            
            Log.d(TAG, "Push-to-talk pressed - starting recording")
            
            try {
                // Stop any ongoing AI response playback
                realtimeAudioPlayer.clearQueue()
                
                // Start recording
                pushToTalkController.startRecording()
                
                _liveChatState.value = LiveChatState.Recording(
                    duration = 0L,
                    audioLevel = 0f
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting push-to-talk", e)
                _liveChatState.value = LiveChatState.Error("Recording error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Stop push-to-talk recording and request AI response
     */
    fun onPushToTalkReleased() {
        viewModelScope.launch {
            if (!pushToTalkState.value.isRecording) {
                Log.w(TAG, "Push-to-talk released but not recording")
                return@launch
            }
            
            Log.d(TAG, "Push-to-talk released - stopping recording and requesting response")
            
            try {
                // Stop recording and commit audio buffer
                pushToTalkController.stopRecording()
                
                // Request AI response
                val responseConfig = ResponseConfig(
                    modalities = listOf("audio", "text"),
                    voice = "shimmer",
                    outputAudioFormat = "pcm16"
                )
                
                realtimeSessionManager.generateResponse(responseConfig)
                
                _liveChatState.value = LiveChatState.Processing("Elaborando la tua richiesta...")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping push-to-talk", e)
                _liveChatState.value = LiveChatState.Error("Processing error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Cancel current push-to-talk recording
     */
    fun cancelPushToTalk() {
        viewModelScope.launch {
            if (pushToTalkState.value.isRecording) {
                pushToTalkController.cancelRecording()
                _liveChatState.value = LiveChatState.Connected
            }
        }
    }
    
    /**
     * Clear live chat error state
     */
    fun clearLiveChatError() {
        if (_liveChatState.value is LiveChatState.Error) {
            _liveChatState.value = if (realtimeSessionState.value.isConnected) {
                LiveChatState.Connected
            } else {
                LiveChatState.Idle
            }
        }
    }
    
    private fun observeRealtimeAudioEvents() {
        viewModelScope.launch {
            realtimeSessionManager.conversationState.collect { conversationState ->
                
                // Update live chat state based on conversation state
                when {
                    conversationState.isGeneratingResponse -> {
                        _liveChatState.value = LiveChatState.Processing("L'AI sta rispondendo...")
                    }
                    conversationState.currentTranscript.isNotEmpty() -> {
                        _liveChatState.value = LiveChatState.Speaking(
                            transcript = conversationState.currentTranscript,
                            audioLevel = 0.5f // You can get this from audio player if needed
                        )
                    }
                    else -> {
                        if (_liveChatState.value is LiveChatState.Processing || 
                            _liveChatState.value is LiveChatState.Speaking) {
                            _liveChatState.value = LiveChatState.Connected
                        }
                    }
                }
            }
        }
        
        // Also observe session state for connection changes
        viewModelScope.launch {
            realtimeSessionState.collect { sessionState ->
                if (!sessionState.isConnected && sessionState.error != null) {
                    _liveChatState.value = LiveChatState.Error(
                        sessionState.error,
                        null
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceSystem.cleanup()
        
        // Cleanup live chat resources
        viewModelScope.launch {
            try {
                if (realtimeSessionState.value.isConnected) {
                    realtimeSessionManager.endSession()
                }
                realtimeAudioPlayer.stopPlayback()
                if (pushToTalkState.value.isRecording) {
                    pushToTalkController.cancelRecording()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
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