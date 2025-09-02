package com.lifo.chat.domain

import android.util.Log
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.realtime.GeminiConnectionState
import com.lifo.chat.data.realtime.GeminiLiveWebSocketClient
import com.lifo.chat.domain.audio.GeminiLiveAudioManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session manager for Gemini Live API
 * Coordinates between WebSocket client and audio manager
 */
@Singleton
class GeminiLiveSessionManager @Inject constructor(
    private val webSocketClient: GeminiLiveWebSocketClient,
    private val audioManager: GeminiLiveAudioManager,
    private val apiConfigManager: ApiConfigManager
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Session state
    private val _sessionState = MutableStateFlow(GeminiLiveSessionState())
    val sessionState: StateFlow<GeminiLiveSessionState> = _sessionState.asStateFlow()

    // Connection status from WebSocket
    val connectionState = webSocketClient.connectionState

    // Audio flows
    val transcriptOutput = webSocketClient.transcriptOutput
    val errorEvents = webSocketClient.errorEvents
    val audioLevel = audioManager.audioLevel
    val recordingState = audioManager.recordingState

    init {
        observeConnectionChanges()
        observeAudioInput()
        observeAudioOutput()
    }

    private fun observeConnectionChanges() {
        coroutineScope.launch {
            webSocketClient.connectionState.collectLatest { connectionState ->
                Log.d(TAG, "🔄 Connection state changed: $connectionState")

                _sessionState.update { currentState ->
                    currentState.copy(
                        isConnected = connectionState is GeminiConnectionState.Connected,
                        isConnecting = connectionState is GeminiConnectionState.Connecting,
                        error = if (connectionState is GeminiConnectionState.Error) {
                            connectionState.message
                        } else null
                    )
                }
            }
        }
    }

    private fun observeAudioInput() {
        coroutineScope.launch {
            audioManager.audioInputStream.collectLatest { audioData ->
                // Forward audio data to WebSocket
                webSocketClient.sendAudio(audioData)
            }
        }
    }

    private fun observeAudioOutput() {
        // OPZIONE 1: Usa startAudioPlayback con il flow (PREFERITO)
        coroutineScope.launch {
            // Inizializza playback una volta
            if (audioManager.initializePlayback()) {
                Log.d(TAG, "🔊 Audio playback initialized, starting stream...")

                // Passa il flow direttamente all'audio manager
                audioManager.startAudioPlayback(webSocketClient.audioOutput)

                // Monitor audio state separately
                launch {
                    webSocketClient.audioOutput.collect {
                        _sessionState.update { it.copy(isAISpeaking = true) }
                        delay(500) // Keep speaking state for 500ms
                        _sessionState.update { it.copy(isAISpeaking = false) }
                    }
                }
            } else {
                Log.e(TAG, "❌ Failed to initialize audio playback")
            }
        }
    }

    /**
     * Start a new Live API session
     */
    suspend fun startSession(): Result<Unit> {
        return try {
            Log.d(TAG, "🚀 Starting Gemini Live session...")

            if (!audioManager.hasAudioPermission()) {
                throw IllegalStateException("Audio permission required")
            }

            _sessionState.update {
                it.copy(
                    isConnecting = true,
                    error = null
                )
            }

            // Get API key
            val apiKey = getGeminiApiKey()
            if (apiKey.isEmpty()) {
                throw IllegalStateException("Gemini API key not configured")
            }

            // Connect to WebSocket
            webSocketClient.connect(apiKey)

            // Wait for connection to be established
            val connected = withTimeoutOrNull(10000) {
                connectionState.first { it is GeminiConnectionState.Connected }
            }

            if (connected == null) {
                throw IllegalStateException("Connection timeout")
            }

            Log.d(TAG, "✅ Gemini Live session started successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start session", e)
            _sessionState.update {
                it.copy(
                    isConnecting = false,
                    error = e.message ?: "Failed to start session"
                )
            }
            Result.failure(e)
        }
    }

    /**
     * Start recording audio input
     */
    fun startRecording(): Boolean {
        Log.d(TAG, "🎤 Starting audio recording...")

        val currentState = _sessionState.value
        if (!currentState.isConnected) {
            Log.w(TAG, "⚠️ Cannot start recording - not connected")
            return false
        }

        val success = audioManager.startRecording()
        if (success) {
            _sessionState.update { it.copy(isRecording = true) }
            Log.d(TAG, "✅ Recording started")
        } else {
            Log.e(TAG, "❌ Failed to start recording")
        }

        return success
    }

    /**
     * Stop recording audio input
     */
    fun stopRecording() {
        Log.d(TAG, "🎤 Stopping audio recording...")

        audioManager.stopRecording()
        _sessionState.update { it.copy(isRecording = false) }

        Log.d(TAG, "✅ Recording stopped")
    }

    /**
     * Send text message to AI
     */
    fun sendText(text: String) {
        Log.d(TAG, "📝 Sending text: $text")
        webSocketClient.sendText(text)
    }

    /**
     * End the Live API session
     */
    fun endSession() {
        Log.d(TAG, "🛑 Ending Gemini Live session...")

        // Stop recording
        stopRecording()

        // Disconnect WebSocket
        webSocketClient.disconnect()

        // Stop audio playback
        audioManager.stopPlayback()

        // Reset state
        _sessionState.value = GeminiLiveSessionState()

        Log.d(TAG, "✅ Session ended")
    }

    /**
     * Get audio configuration info
     */
    fun getAudioInfo(): String {
        return audioManager.getAudioInfo()
    }

    private fun getGeminiApiKey(): String {
        return try {
            // Try to get from BuildConfig first (for direct API key approach)
            val buildConfigKey = try {
                val buildConfigClass = Class.forName("com.lifo.chat.BuildConfig")
                val field = buildConfigClass.getField("GEMINI_API_KEY")
                field.get(null) as? String ?: ""
            } catch (e: Exception) {
                ""
            }

            if (buildConfigKey.isNotEmpty()) {
                return buildConfigKey
            }

            // Fallback to config manager
            apiConfigManager.getGeminiApiKey()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get API key", e)
            ""
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up session manager...")

        endSession()
        webSocketClient.cleanup()
        audioManager.cleanup()
        coroutineScope.cancel()
    }
}

/**
 * State for Gemini Live session
 */
data class GeminiLiveSessionState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isRecording: Boolean = false,
    val isAISpeaking: Boolean = false,
    val error: String? = null
)