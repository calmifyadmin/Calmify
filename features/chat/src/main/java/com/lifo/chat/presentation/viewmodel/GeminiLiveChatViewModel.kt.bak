package com.lifo.chat.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.chat.data.realtime.GeminiConnectionState
import com.lifo.chat.domain.GeminiLiveSessionManager
import com.lifo.chat.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel for Gemini Live API chat functionality
 * Uses the new Gemini Live WebSocket API instead of OpenAI Realtime API
 */
@HiltViewModel
class GeminiLiveChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiLiveSessionManager: GeminiLiveSessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "GeminiLiveChatVM"
    }

    // Main UI State
    private val _uiState = MutableStateFlow(
        LiveChatUiState(
            connectionStatus = ConnectionStatus.Disconnected,
            hasAudioPermission = checkAudioPermission()
        )
    )
    val uiState: StateFlow<LiveChatUiState> = _uiState.asStateFlow()

    // Current transcript from AI
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    init {
        Log.d(TAG, "🚀 Initializing GeminiLiveChatViewModel...")
        observeSessionStates()
        observeTranscripts()
        observeErrors()
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observeSessionStates() {
        viewModelScope.launch {
            // Observe Gemini Live session state
            geminiLiveSessionManager.sessionState.collectLatest { sessionState ->
                Log.d(TAG, "📱 Session state changed: connected=${sessionState.isConnected}, recording=${sessionState.isRecording}")
                
                val connectionStatus = when {
                    sessionState.error != null -> ConnectionStatus.Error
                    sessionState.isConnected -> ConnectionStatus.Connected
                    sessionState.isConnecting -> ConnectionStatus.Connecting
                    else -> ConnectionStatus.Disconnected
                }
                
                _uiState.update { currentState ->
                    currentState.copy(
                        connectionStatus = connectionStatus,
                        isRecording = sessionState.isRecording,
                        error = sessionState.error,
                        aiEmotion = if (sessionState.isAISpeaking) AIEmotion.Speaking else AIEmotion.Neutral,
                        pushToTalkState = when {
                            sessionState.isRecording -> PTTState.Listening
                            sessionState.isAISpeaking -> PTTState.Processing
                            else -> PTTState.Idle
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            // Observe audio level for visualization
            geminiLiveSessionManager.audioLevel.collectLatest { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }

        viewModelScope.launch {
            // Observe recording state
            geminiLiveSessionManager.recordingState.collectLatest { isRecording ->
                Log.d(TAG, "🎤 Recording state changed: $isRecording")
                _uiState.update { it.copy(isRecording = isRecording) }
            }
        }
    }

    private fun observeTranscripts() {
        viewModelScope.launch {
            geminiLiveSessionManager.transcriptOutput.collectLatest { transcript ->
                Log.d(TAG, "📝 Received transcript: $transcript")
                _currentTranscript.value = transcript
                
                // Update AI emotion when speaking
                _uiState.update { it.copy(aiEmotion = AIEmotion.Speaking) }
                
                // Reset after a delay
                delay(2000)
                _uiState.update { it.copy(aiEmotion = AIEmotion.Neutral) }
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            geminiLiveSessionManager.errorEvents.collectLatest { error ->
                Log.e(TAG, "❌ Session error: $error")
                _uiState.update { 
                    it.copy(
                        error = error,
                        connectionStatus = ConnectionStatus.Error
                    )
                }
            }
        }
    }

    /**
     * Called when audio permission is granted
     */
    fun onPermissionGranted() {
        Log.d(TAG, "🎤 Audio permission granted")
        _uiState.update { it.copy(hasAudioPermission = true) }
    }

    /**
     * Called when audio permission is denied
     */
    fun onPermissionDenied() {
        Log.d(TAG, "❌ Audio permission denied")
        _uiState.update { 
            it.copy(
                hasAudioPermission = false,
                error = "Audio permission required for live chat"
            )
        }
    }

    /**
     * Connect to Gemini Live API
     */
    fun connectToRealtime() {
        if (!_uiState.value.hasAudioPermission) {
            Log.w(TAG, "⚠️ Attempted to connect without audio permission")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Connecting to Gemini Live API...")
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Connecting,
                        error = null
                    )
                }

                val result = geminiLiveSessionManager.startSession()
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: IllegalStateException("Failed to start session")
                }
                
                Log.d(TAG, "✅ Successfully connected to Gemini Live API")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to connect to Gemini Live API", e)
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Error,
                        error = "Connection failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Disconnect from Gemini Live API
     */
    fun disconnectFromRealtime() {
        viewModelScope.launch {
            Log.d(TAG, "🔌 Disconnecting from Gemini Live API...")
            geminiLiveSessionManager.endSession()
            _uiState.update { 
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    pushToTalkState = PTTState.Idle,
                    isRecording = false,
                    audioLevel = 0f,
                    error = null
                )
            }
        }
    }

    /**
     * Start recording audio (push-to-talk pressed)
     */
    fun onPushToTalkPressed() {
        Log.d(TAG, "🎤 Push-to-talk PRESSED")
        
        if (_uiState.value.connectionStatus != ConnectionStatus.Connected) {
            Log.w(TAG, "⚠️ Push-to-talk pressed while not connected")
            return
        }

        try {
            val success = geminiLiveSessionManager.startRecording()
            if (!success) {
                Log.w(TAG, "⚠️ Failed to start recording")
                _uiState.update { it.copy(error = "Failed to start recording") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording", e)
            _uiState.update { it.copy(error = "Recording failed: ${e.message}") }
        }
    }

    /**
     * Stop recording audio (push-to-talk released)
     */
    fun onPushToTalkReleased() {
        Log.d(TAG, "🎤 Push-to-talk RELEASED")
        
        try {
            geminiLiveSessionManager.stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop recording", e)
            _uiState.update { it.copy(error = "Recording stop failed: ${e.message}") }
        }
    }

    /**
     * Cancel current push-to-talk session
     */
    fun cancelPushToTalk() {
        try {
            Log.d(TAG, "❌ Cancelling push-to-talk")
            geminiLiveSessionManager.stopRecording()
            _uiState.update { 
                it.copy(
                    pushToTalkState = PTTState.Idle,
                    aiEmotion = AIEmotion.Neutral,
                    isRecording = false,
                    audioLevel = 0f
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel recording", e)
            _uiState.update { it.copy(error = "Cancel failed: ${e.message}") }
        }
    }

    /**
     * Send text message to AI (for testing)
     */
    fun sendTextMessage(text: String) {
        Log.d(TAG, "📝 Sending text message: $text")
        
        if (_uiState.value.connectionStatus != ConnectionStatus.Connected) {
            Log.w(TAG, "⚠️ Cannot send text - not connected")
            return
        }
        
        geminiLiveSessionManager.sendText(text)
    }

    /**
     * Clear current error
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Retry connection
     */
    fun retryConnection() {
        clearError()
        connectToRealtime()
    }

    /**
     * Get audio configuration info for debugging
     */
    fun getAudioInfo(): String {
        return geminiLiveSessionManager.getAudioInfo()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            disconnectFromRealtime()
            geminiLiveSessionManager.cleanup()
        }
    }
}