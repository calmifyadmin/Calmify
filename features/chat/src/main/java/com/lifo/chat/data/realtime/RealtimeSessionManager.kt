package com.lifo.chat.data.realtime

import android.util.Log
import com.lifo.chat.config.ApiConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeSessionManager @Inject constructor(
    private val realtimeClient: OpenAIRealtimeClient,
    private val apiConfigManager: ApiConfigManager
) {
    companion object {
        private const val TAG = "RealtimeSessionManager"
    }

    data class SessionState(
        val sessionId: String? = null,
        val isConnected: Boolean = false,
        val conversationId: String? = null,
        val currentConfig: SessionConfig = SessionConfig(),
        val isInitialized: Boolean = false,
        val error: String? = null
    )

    data class ConversationState(
        val items: List<ConversationItem> = emptyList(),
        val isGeneratingResponse: Boolean = false,
        val activeResponseId: String? = null,
        val currentTranscript: String = "",
        val lastAudioData: ByteArray? = null
    )

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState = _sessionState.asStateFlow()

    private val _conversationState = MutableStateFlow(ConversationState())
    val conversationState = _conversationState.asStateFlow()

    private var sessionJob: Job? = null
    private var currentResponseBuilder = StringBuilder()

    /**
     * Start a new OpenAI Realtime session
     */
    suspend fun startSession(config: SessionConfig = SessionConfig()): Result<String> {
        return try {
            Log.d(TAG, "Starting realtime session...")
            
            // Get API key
            val apiKey = apiConfigManager.getOpenAIApiKey() // Fixed: Use OpenAI key for OpenAI Realtime API
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("OpenAI API key not configured"))
            }

            _sessionState.update { 
                it.copy(
                    isConnected = false, 
                    currentConfig = config,
                    error = null
                ) 
            }

            // Start connection and observe events
            sessionJob?.cancel()
            sessionJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Connect to WebSocket
                    realtimeClient.connect(apiKey).collect { connectionState ->
                        when (connectionState) {
                            is RealtimeConnectionState.Connected -> {
                                Log.d(TAG, "Connected to OpenAI Realtime API")
                                _sessionState.update { 
                                    it.copy(isConnected = true) 
                                }
                                
                                // Initialize session
                                initializeSession(config)
                            }
                            is RealtimeConnectionState.Connecting -> {
                                Log.d(TAG, "Connecting to OpenAI Realtime API...")
                            }
                            is RealtimeConnectionState.Disconnected -> {
                                Log.d(TAG, "Disconnected from OpenAI Realtime API")
                                _sessionState.update { 
                                    it.copy(
                                        isConnected = false, 
                                        sessionId = null,
                                        isInitialized = false
                                    ) 
                                }
                            }
                            is RealtimeConnectionState.Error -> {
                                Log.e(TAG, "Connection error: ${connectionState.message}")
                                _sessionState.update { 
                                    it.copy(
                                        isConnected = false, 
                                        error = connectionState.message
                                    ) 
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in session job", e)
                    _sessionState.update { 
                        it.copy(error = "Session error: ${e.message}") 
                    }
                }
            }

            // Start listening to realtime events
            observeRealtimeEvents()

            // Wait a bit for connection
            delay(2000)
            
            val currentState = _sessionState.value
            if (currentState.isConnected) {
                Result.success(currentState.sessionId ?: "session_started")
            } else {
                Result.failure(Exception(currentState.error ?: "Failed to connect"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting session", e)
            Result.failure(e)
        }
    }

    /**
     * End the current session
     */
    suspend fun endSession() {
        Log.d(TAG, "Ending realtime session...")
        sessionJob?.cancel()
        realtimeClient.disconnect()
        _sessionState.value = SessionState()
        _conversationState.value = ConversationState()
    }

    /**
     * Update session configuration
     */
    suspend fun updateSessionConfig(config: SessionConfig) {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "Cannot update session: not connected")
            return
        }

        val success = realtimeClient.createSession(config)
        if (success) {
            _sessionState.update { 
                it.copy(currentConfig = config) 
            }
            Log.d(TAG, "Session configuration updated")
        } else {
            Log.e(TAG, "Failed to update session configuration")
        }
    }

    /**
     * Add a conversation item
     */
    suspend fun addConversationItem(item: ConversationItem) {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "Cannot add conversation item: not connected")
            return
        }

        val success = realtimeClient.addConversationItem(item)
        if (success) {
            _conversationState.update { state ->
                state.copy(items = state.items + item)
            }
            Log.d(TAG, "Added conversation item: ${item.type}")
        } else {
            Log.e(TAG, "Failed to add conversation item")
        }
    }

    /**
     * Generate AI response
     */
    suspend fun generateResponse(config: ResponseConfig = ResponseConfig.default()) {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "Cannot generate response: not connected")
            return
        }

        _conversationState.update { 
            it.copy(isGeneratingResponse = true) 
        }
        currentResponseBuilder.clear()

        val success = realtimeClient.generateResponse(config)
        if (!success) {
            Log.e(TAG, "Failed to request response generation")
            _conversationState.update { 
                it.copy(isGeneratingResponse = false) 
            }
        }
    }

    /**
     * Cancel ongoing response generation
     */
    suspend fun cancelResponse() {
        val success = realtimeClient.cancelResponse()
        if (success) {
            _conversationState.update { 
                it.copy(
                    isGeneratingResponse = false,
                    activeResponseId = null
                ) 
            }
            Log.d(TAG, "Response generation cancelled")
        }
    }

    /**
     * Send audio data to OpenAI
     */
    suspend fun sendAudioData(audioData: ByteArray) {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "Cannot send audio: not connected")
            return
        }

        realtimeClient.sendAudioData(audioData)
    }

    /**
     * Commit audio buffer (end of user speech)
     */
    suspend fun commitAudioBuffer() {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "Cannot commit audio: not connected")
            return
        }

        realtimeClient.commitAudioBuffer()
    }

    /**
     * Clear audio buffer
     */
    suspend fun clearAudioBuffer() {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "Cannot clear audio: not connected")
            return
        }

        realtimeClient.clearAudioBuffer()
    }

    private suspend fun initializeSession(config: SessionConfig) {
        try {
            Log.d(TAG, "Initializing session with config...")
            val success = realtimeClient.createSession(config)
            if (success) {
                Log.d(TAG, "Session initialization successful")
            } else {
                Log.e(TAG, "Session initialization failed")
                _sessionState.update { 
                    it.copy(error = "Failed to initialize session") 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing session", e)
            _sessionState.update { 
                it.copy(error = "Session initialization error: ${e.message}") 
            }
        }
    }

    private fun observeRealtimeEvents() {
        CoroutineScope(Dispatchers.IO).launch {
            realtimeClient.realtimeEvents.collect { event ->
                handleRealtimeEvent(event)
            }
        }
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        Log.d(TAG, "Handling event: ${event.type}")

        when (event) {
            is RealtimeEvent.Server.SessionCreated -> {
                _sessionState.update { 
                    it.copy(
                        sessionId = event.eventId,
                        isInitialized = true
                    ) 
                }
                Log.d(TAG, "Session created with ID: ${event.eventId}")
            }
            
            is RealtimeEvent.Server.SessionUpdated -> {
                _sessionState.update { 
                    it.copy(isInitialized = true) 
                }
                Log.d(TAG, "Session updated")
            }

            is RealtimeEvent.Server.InputAudioBufferSpeechStarted -> {
                Log.d(TAG, "User started speaking")
                _conversationState.update { 
                    it.copy(currentTranscript = "") 
                }
            }

            is RealtimeEvent.Server.InputAudioBufferSpeechStopped -> {
                Log.d(TAG, "User stopped speaking")
            }

            is RealtimeEvent.Server.InputAudioBufferCommitted -> {
                Log.d(TAG, "Audio buffer committed")
            }

            is RealtimeEvent.Server.ResponseCreated -> {
                Log.d(TAG, "Response created")
                _conversationState.update { 
                    it.copy(activeResponseId = event.eventId) 
                }
            }

            is RealtimeEvent.Server.ResponseTextDelta -> {
                // Accumulate text response
                currentResponseBuilder.append(event.delta)
                _conversationState.update { 
                    it.copy(currentTranscript = currentResponseBuilder.toString()) 
                }
                Log.v(TAG, "Text delta: ${event.delta}")
            }

            is RealtimeEvent.Server.ResponseTextDone -> {
                Log.d(TAG, "Text response completed: ${event.text}")
                val assistantMessage = ConversationItem.assistantMessage(event.text)
                _conversationState.update { state ->
                    state.copy(
                        items = state.items + assistantMessage,
                        currentTranscript = event.text
                    )
                }
            }

            is RealtimeEvent.Server.ResponseAudioDelta -> {
                // Handle audio data
                Log.v(TAG, "Audio delta received")
                // You can decode and play the audio here
            }

            is RealtimeEvent.Server.ResponseAudioDone -> {
                Log.d(TAG, "Audio response completed")
            }

            is RealtimeEvent.Server.ResponseAudioTranscriptDelta -> {
                // Handle audio transcript
                currentResponseBuilder.append(event.delta)
                _conversationState.update { 
                    it.copy(currentTranscript = currentResponseBuilder.toString()) 
                }
                Log.v(TAG, "Audio transcript delta: ${event.delta}")
            }

            is RealtimeEvent.Server.ResponseAudioTranscriptDone -> {
                Log.d(TAG, "Audio transcript completed: ${event.transcript}")
                _conversationState.update { 
                    it.copy(currentTranscript = event.transcript) 
                }
            }

            is RealtimeEvent.Server.ResponseDone -> {
                Log.d(TAG, "Response generation completed")
                _conversationState.update { 
                    it.copy(
                        isGeneratingResponse = false,
                        activeResponseId = null
                    ) 
                }
                currentResponseBuilder.clear()
            }

            is RealtimeEvent.Server.Error -> {
                Log.e(TAG, "Server error: ${event.error.message}")
                _sessionState.update { 
                    it.copy(error = event.error.message) 
                }
                _conversationState.update { 
                    it.copy(isGeneratingResponse = false) 
                }
            }

            else -> {
                Log.d(TAG, "Unhandled event type: ${event.type}")
            }
        }
    }
}