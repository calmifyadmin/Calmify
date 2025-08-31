package com.lifo.chat.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.realtime.*
import com.lifo.chat.domain.audio.PushToTalkManager
import com.lifo.chat.domain.audio.AudioLevelExtractor
import com.lifo.chat.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LiveChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiConfigManager: ApiConfigManager,
    private val webRTCClient: RealtimeWebRTCClient,
    private val pushToTalkManager: PushToTalkManager,
    private val audioLevelExtractor: AudioLevelExtractor,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "LiveChatViewModel"
    }

    // Main UI State
    private val _uiState = MutableStateFlow(
        LiveChatUiState(
            connectionStatus = ConnectionStatus.Disconnected,
            hasAudioPermission = checkAudioPermission()
        )
    )
    val uiState: StateFlow<LiveChatUiState> = _uiState.asStateFlow()

    // Push-to-talk state from manager
    val pushToTalkState = pushToTalkManager.state

    // WebRTC session state
    val webRTCSessionState = webRTCClient.sessionState

    init {
        Log.d(TAG, "🎙️ Initializing LiveChatViewModel...")
        observePermissions()
        observeRealtimeStates()
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observePermissions() {
        // Update permission status when needed
        viewModelScope.launch {
            // This would be called from the UI when permissions are granted/denied
        }
    }

    private fun observeRealtimeStates() {
        viewModelScope.launch {
            // Observe WebRTC session state changes
            webRTCSessionState.collectLatest { sessionState ->
                Log.d(TAG, "🎯 WebRTC session state changed: connectionState=${sessionState.connectionState}, sessionId=${sessionState.sessionId}")
                val connectionStatus = when (sessionState.connectionState) {
                    WebRTCConnectionState.Connected -> {
                        Log.d(TAG, "✅ WebRTC Connected - updating UI to Connected")
                        ConnectionStatus.Connected
                    }
                    WebRTCConnectionState.Connecting -> {
                        Log.d(TAG, "🔄 WebRTC Connecting - updating UI to Connecting")
                        ConnectionStatus.Connecting
                    }
                    WebRTCConnectionState.Failed -> {
                        Log.d(TAG, "❌ WebRTC Failed - updating UI to Error")
                        ConnectionStatus.Error
                    }
                    WebRTCConnectionState.Disconnected, WebRTCConnectionState.Closed -> {
                        Log.d(TAG, "🔌 WebRTC Disconnected/Closed - updating UI to Disconnected")
                        ConnectionStatus.Disconnected
                    }
                    WebRTCConnectionState.New -> {
                        Log.d(TAG, "🆕 WebRTC New - updating UI to Disconnected")
                        ConnectionStatus.Disconnected
                    }
                }
                
                Log.d(TAG, "🎯 Updating UI state to: $connectionStatus")
                _uiState.update { currentState ->
                    currentState.copy(
                        connectionStatus = connectionStatus,
                        sessionId = sessionState.sessionId,
                        error = sessionState.error,
                        audioLevel = sessionState.audioLevel
                    )
                }
            }
        }

        viewModelScope.launch {
            // Observe push-to-talk state for UI updates
            pushToTalkState.collectLatest { pttState ->
                val currentPTTState = when {
                    pttState.isRecording -> PTTState.Listening
                    pttState.recordingDuration > 0 && !pttState.isRecording -> PTTState.Processing
                    else -> PTTState.Idle
                }
                
                _uiState.update { currentState ->
                    currentState.copy(
                        isRecording = pttState.isRecording,
                        recordingDuration = pttState.recordingDuration,
                        pushToTalkState = currentPTTState,
                        error = pttState.error
                    )
                }
            }
        }
    }

    fun onPermissionGranted() {
        Log.d(TAG, "🎤 Audio permission granted")
        _uiState.update { it.copy(hasAudioPermission = true) }
        // Auto-start connection when permission is granted
        connectToRealtime()
    }

    fun onPermissionDenied() {
        Log.d(TAG, "❌ Audio permission denied")
        _uiState.update { 
            it.copy(
                hasAudioPermission = false,
                error = "Audio permission required for live chat"
            )
        }
    }

    fun connectToRealtime() {
        if (!_uiState.value.hasAudioPermission) {
            Log.w(TAG, "⚠️ Attempted to connect without audio permission")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Connecting to OpenAI Realtime via WebRTC...")
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Connecting,
                        error = null
                    )
                }

                val apiKey = apiConfigManager.getOpenAIApiKey()
                if (apiKey.isEmpty()) {
                    throw IllegalStateException("OpenAI API key not configured")
                }

                val result = webRTCClient.startSession()
                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: IllegalStateException("Failed to start WebRTC session")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to connect to realtime", e)
                _uiState.update { 
                    it.copy(
                        connectionStatus = ConnectionStatus.Error,
                        error = "Connection failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnectFromRealtime() {
        viewModelScope.launch {
            Log.d(TAG, "🔌 Disconnecting from realtime...")
            webRTCClient.endSession()
            _uiState.update { 
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    pushToTalkState = PTTState.Idle,
                    isRecording = false,
                    audioLevel = 0f,
                    transcript = "",
                    error = null
                )
            }
        }
    }

    fun onPushToTalkPressed() {
        Log.d(TAG, "🎤 Push-to-talk PRESSED by user")
        
        if (_uiState.value.connectionStatus != ConnectionStatus.Connected) {
            Log.w(TAG, "⚠️ Push-to-talk pressed while not connected")
            return
        }

        try {
            Log.d(TAG, "🎤 Starting push-to-talk recording...")
            val success = pushToTalkManager.startRecording()
            if (success) {
                _uiState.update { 
                    it.copy(
                        pushToTalkState = PTTState.Listening,
                        aiEmotion = AIEmotion.Thinking
                    )
                }
            } else {
                Log.w(TAG, "⚠️ Failed to start push-to-talk recording")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording", e)
            _uiState.update { it.copy(error = "Recording failed: ${e.message}") }
        }
    }

    fun onPushToTalkReleased() {
        Log.d(TAG, "🎤 Push-to-talk RELEASED by user")
        
        try {
            Log.d(TAG, "🎤 Stopping push-to-talk recording...")
            val success = pushToTalkManager.stopRecording()
            if (success) {
                _uiState.update { 
                    it.copy(
                        pushToTalkState = PTTState.Processing,
                        aiEmotion = AIEmotion.Thinking
                    )
                }
            } else {
                Log.w(TAG, "⚠️ Failed to stop push-to-talk recording")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop recording", e)
            _uiState.update { it.copy(error = "Recording stop failed: ${e.message}") }
        }
    }

    fun cancelPushToTalk() {
        try {
            Log.d(TAG, "❌ Cancelling push-to-talk")
            pushToTalkManager.cancelRecording()
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retryConnection() {
        clearError()
        connectToRealtime()
    }

    /**
     * Update audio level for liquid globe visualization
     * This is called by WebRTC client when audio levels change
     */
    fun updateAudioLevel(level: Float) {
        _uiState.update { it.copy(audioLevel = level.coerceIn(0f, 1f)) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            disconnectFromRealtime()
            webRTCClient.cleanup()
            pushToTalkManager.cleanup()
            audioLevelExtractor.reset()
        }
    }
}