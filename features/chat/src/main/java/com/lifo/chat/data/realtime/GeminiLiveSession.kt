package com.lifo.chat.domain.realtime

import android.util.Log
import com.lifo.chat.data.realtime.GeminiConnectionState
import com.lifo.chat.data.realtime.GeminiLiveWebSocketClient
import com.lifo.chat.domain.audio.GeminiLiveAudioManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main session manager for Gemini Live API
 * Coordinates WebSocket connection and audio management
 */
@Singleton
class GeminiLiveSession @Inject constructor(
    private val webSocketClient: GeminiLiveWebSocketClient,
    private val audioManager: GeminiLiveAudioManager
) {
    companion object {
        private const val TAG = "GeminiLiveSession"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Session state
    data class SessionState(
        val isConnected: Boolean = false,
        val isRecording: Boolean = false,
        val errorMessage: String? = null
    )

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    // Expose connection state from WebSocket
    val connectionState: StateFlow<GeminiConnectionState> = webSocketClient.connectionState

    // Expose transcripts
    val transcripts: SharedFlow<String> = webSocketClient.transcriptOutput

    // Expose recording state
    val isRecording: StateFlow<Boolean> = audioManager.recordingState

    // Expose audio level for UI visualization
    val audioLevel: StateFlow<Float> = audioManager.audioLevel

    private var audioPlaybackJob: Job? = null
    private var audioRecordingJob: Job? = null
    private var connectionMonitorJob: Job? = null

    /**
     * Start a new Gemini Live session
     */
    fun startSession(apiKey: String) {
        Log.d(TAG, "🚀 Starting Gemini Live session...")

        // Connect to WebSocket
        webSocketClient.connect(apiKey)

        // Monitor connection state
        monitorConnectionState()
    }

    /**
     * Monitor WebSocket connection state and handle audio accordingly
     */
    private fun monitorConnectionState() {
        connectionMonitorJob?.cancel()

        connectionMonitorJob = coroutineScope.launch {
            webSocketClient.connectionState.collect { state ->
                Log.d(TAG, "🔄 Connection state changed: $state")

                when (state) {
                    is GeminiConnectionState.Connected -> {
                        Log.d(TAG, "✅ Connected - Initializing audio...")
                        handleConnected()
                    }

                    is GeminiConnectionState.Disconnected -> {
                        Log.d(TAG, "🔌 Disconnected")
                        handleDisconnected()
                    }

                    is GeminiConnectionState.Error -> {
                        Log.e(TAG, "❌ Connection error: ${state.message}")
                        handleError(state.message)
                    }

                    is GeminiConnectionState.Connecting -> {
                        Log.d(TAG, "🔄 Connecting...")
                        _sessionState.update {
                            it.copy(isConnected = false, errorMessage = null)
                        }
                    }

                    is GeminiConnectionState.Disconnecting -> {
                        Log.d(TAG, "🔄 Disconnecting...")
                    }
                }
            }
        }
    }

    /**
     * Handle successful connection
     */
    private fun handleConnected() {
        _sessionState.update {
            it.copy(isConnected = true, errorMessage = null)
        }

        // Initialize audio playback IMMEDIATELY after connection
        coroutineScope.launch(Dispatchers.IO) {
            delay(100) // Small delay to ensure setup is complete

            withContext(Dispatchers.Main) {
                if (audioManager.initializePlayback()) {
                    Log.d(TAG, "✅ Audio playback initialized")
                    startAudioPlayback()
                } else {
                    Log.e(TAG, "❌ Failed to initialize audio playback")
                }
            }
        }

        // Start monitoring for transcripts
        monitorTranscripts()

        Log.d(TAG, "✅ Gemini Live session started successfully")
    }

    /**
     * Handle disconnection
     */
    private fun handleDisconnected() {
        _sessionState.update {
            it.copy(isConnected = false, isRecording = false)
        }

        // Stop all audio processing
        stopAudioPlayback()
        stopRecording()

        Log.d(TAG, "🔌 Session disconnected")
    }

    /**
     * Handle connection error
     */
    private fun handleError(message: String) {
        _sessionState.update {
            it.copy(isConnected = false, isRecording = false, errorMessage = message)
        }

        // Stop all audio processing
        stopAudioPlayback()
        stopRecording()
    }

    /**
     * Start audio playback from Gemini
     */
    private fun startAudioPlayback() {
        audioPlaybackJob?.cancel()

        audioPlaybackJob = coroutineScope.launch {
            Log.d(TAG, "🎵 Starting audio playback stream...")

            // Connect the WebSocket audio output directly to the AudioManager
            audioManager.startAudioPlayback(webSocketClient.audioOutput)
        }
    }

    /**
     * Stop audio playback
     */
    private fun stopAudioPlayback() {
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        audioManager.stopPlayback()
    }

    /**
     * Monitor transcripts from Gemini
     */
    private fun monitorTranscripts() {
        coroutineScope.launch {
            webSocketClient.transcriptOutput.collect { transcript ->
                Log.d(TAG, "📝 Transcript: $transcript")
                // Transcripts are already exposed via the transcripts flow
                // Additional handling can be added here if needed
            }
        }
    }

    /**
     * Start recording audio from microphone
     */
    fun startRecording() {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "⚠️ Cannot start recording - not connected")
            return
        }

        if (_sessionState.value.isRecording) {
            Log.w(TAG, "⚠️ Already recording")
            return
        }

        Log.d(TAG, "🎤 Starting audio recording...")

        // Start recording
        if (audioManager.startRecording()) {
            _sessionState.update { it.copy(isRecording = true) }

            // Forward audio to WebSocket
            audioRecordingJob = coroutineScope.launch {
                audioManager.audioInputStream.collect { audioData ->
                    webSocketClient.sendAudio(audioData)
                }
            }

            Log.d(TAG, "✅ Recording started")
        } else {
            Log.e(TAG, "❌ Failed to start recording")
        }
    }

    /**
     * Stop recording audio
     */
    fun stopRecording() {
        if (!_sessionState.value.isRecording) {
            Log.w(TAG, "⚠️ Not recording")
            return
        }

        Log.d(TAG, "🎤 Stopping audio recording...")

        audioRecordingJob?.cancel()
        audioRecordingJob = null

        audioManager.stopRecording()
        _sessionState.update { it.copy(isRecording = false) }

        Log.d(TAG, "✅ Recording stopped")
    }

    /**
     * Send text message to Gemini
     */
    fun sendText(text: String) {
        if (!_sessionState.value.isConnected) {
            Log.w(TAG, "⚠️ Cannot send text - not connected")
            return
        }

        webSocketClient.sendText(text)
    }

    /**
     * End the session and cleanup resources
     */
    fun endSession() {
        Log.d(TAG, "🔚 Ending Gemini Live session...")

        // Cancel all jobs
        connectionMonitorJob?.cancel()
        audioPlaybackJob?.cancel()
        audioRecordingJob?.cancel()

        // Stop audio
        audioManager.cleanup()

        // Disconnect WebSocket
        webSocketClient.disconnect()

        // Reset state
        _sessionState.value = SessionState()

        Log.d(TAG, "✅ Session ended")
    }

    /**
     * Cleanup all resources
     */
    fun cleanup() {
        endSession()
        coroutineScope.cancel()
        webSocketClient.cleanup()
    }

    /**
     * Get session info for debugging
     */
    fun getSessionInfo(): String {
        return """
            |Session Info:
            |Connected: ${_sessionState.value.isConnected}
            |Recording: ${_sessionState.value.isRecording}
            |Connection State: ${connectionState.value}
            |Error: ${_sessionState.value.errorMessage}
            |
            |${audioManager.getAudioInfo()}
        """.trimMargin()
    }
}