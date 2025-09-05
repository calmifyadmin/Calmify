package com.lifo.chat.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
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
    private val geminiWebSocketClient: GeminiLiveWebSocketClient,
    private val geminiAudioManager: GeminiLiveAudioManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "LiveChatViewModel"
    }

    // Main UI State
    private val _uiState = MutableStateFlow(
        LiveChatUiState(
            connectionStatus = ConnectionStatus.Disconnected,
            hasAudioPermission = checkAudioPermission(),
            hasCameraPermission = checkCameraPermission(),
            isCameraActive = false,
            pushToTalkState = PTTState.Idle,
            isRecording = false,
            audioLevel = 0f,
            transcript = "",
            error = null,
            aiEmotion = AIEmotion.Neutral,
            sessionId = null,
            recordingDuration = 0L
        )
    )
    val uiState: StateFlow<LiveChatUiState> = _uiState.asStateFlow()

    // Current transcript from AI
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    init {
        Log.d(TAG, "🎙️ Initializing LiveChatViewModel...")
        observeGeminiStates()
        setupGeminiCallbacks()
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observeGeminiStates() {
        // Observe WebSocket connection state
        viewModelScope.launch {
            geminiWebSocketClient.connectionState.collectLatest { state ->
                Log.d(TAG, "🎯 Connection state: $state")

                val uiConnectionStatus = when (state) {
                    GeminiLiveWebSocketClient.ConnectionState.CONNECTED -> ConnectionStatus.Connected
                    GeminiLiveWebSocketClient.ConnectionState.CONNECTING -> ConnectionStatus.Connecting
                    GeminiLiveWebSocketClient.ConnectionState.ERROR -> ConnectionStatus.Error
                    GeminiLiveWebSocketClient.ConnectionState.DISCONNECTED -> ConnectionStatus.Disconnected
                }

                _uiState.update { it.copy(connectionStatus = uiConnectionStatus) }
            }
        }

        // Observe recording state
        viewModelScope.launch {
            geminiAudioManager.recordingState.collectLatest { isRecording ->
                _uiState.update {
                    it.copy(
                        isRecording = isRecording,
                        pushToTalkState = if (isRecording) PTTState.Listening else PTTState.Idle,
                        aiEmotion = if (isRecording) AIEmotion.Thinking else AIEmotion.Neutral
                    )
                }
            }
        }

        // Observe playback state
        viewModelScope.launch {
            geminiAudioManager.playbackState.collectLatest { isPlaying ->
                _uiState.update {
                    it.copy(aiEmotion = if (isPlaying) AIEmotion.Speaking else AIEmotion.Neutral)
                }
            }
        }
    }

    private fun setupGeminiCallbacks() {
        // Handle text from Gemini
        geminiWebSocketClient.onTextReceived = { text ->
            Log.d(TAG, "📝 Text from Gemini: $text")
            _currentTranscript.value = text
            _uiState.update { it.copy(transcript = text) }
        }

        // Handle audio from Gemini
        geminiWebSocketClient.onAudioReceived = { audioBase64 ->
            Log.d(TAG, "🔊 Audio from Gemini (${audioBase64.length} chars)")
            geminiAudioManager.queueAudioForPlayback(audioBase64)

            // Update audio level for visualization (simulated)
            _uiState.update { it.copy(audioLevel = 0.7f) }

            // Clear audio level after a delay
            viewModelScope.launch {
                delay(2000)
                _uiState.update { it.copy(audioLevel = 0f) }
            }
        }

        // Handle errors
        geminiWebSocketClient.onError = { error ->
            Log.e(TAG, "❌ Error: $error")
            _uiState.update {
                it.copy(
                    error = error,
                    connectionStatus = ConnectionStatus.Error
                )
            }
        }

        // Send audio chunks to Gemini
        geminiAudioManager.onAudioChunkReady = { audioBase64 ->
            geminiWebSocketClient.sendAudioData(audioBase64)
        }
    }

    fun onAudioPermissionGranted() {
        Log.d(TAG, "🎤 Audio permission granted")
        _uiState.update { it.copy(hasAudioPermission = true) }
        connectToRealtime()
    }

    fun onAudioPermissionDenied() {
        Log.d(TAG, "❌ Audio permission denied")
        _uiState.update {
            it.copy(
                hasAudioPermission = false,
                error = "Audio permission required for voice chat"
            )
        }
    }

    fun onCameraPermissionGranted() {
        Log.d(TAG, "📸 Camera permission granted")
        _uiState.update { it.copy(hasCameraPermission = true) }
    }

    fun onCameraPermissionDenied() {
        Log.d(TAG, "❌ Camera permission denied")
        _uiState.update {
            it.copy(
                hasCameraPermission = false,
                error = "Camera permission is optional but enhances the experience"
            )
        }
    }

    fun startCameraPreview(surfaceTexture: SurfaceTexture) {
        // Camera preview implementation would go here
        // For now, just update the state
        Log.d(TAG, "📸 Starting camera preview")
        _uiState.update { it.copy(isCameraActive = true) }
    }

    fun stopCameraPreview() {
        Log.d(TAG, "📸 Stopping camera preview")
        _uiState.update { it.copy(isCameraActive = false) }
    }

    fun connectToRealtime() {
        if (!_uiState.value.hasAudioPermission) {
            Log.w(TAG, "Cannot connect without audio permission")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Connecting to Gemini Live...")
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Connecting,
                        error = null
                    )
                }

                val apiKey = apiConfigManager.getGeminiApiKey()
                if (apiKey.isEmpty()) {
                    throw IllegalStateException("Gemini API key not configured")
                }

                Log.d(TAG, "🔑 Using API key: ${apiKey.take(10)}...")
                geminiWebSocketClient.connect(apiKey)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Connection failed", e)
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
            Log.d(TAG, "🔌 Disconnecting...")

            geminiAudioManager.stopRecording()
            geminiAudioManager.stopPlayback()
            geminiWebSocketClient.disconnect()

            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    pushToTalkState = PTTState.Idle,
                    isRecording = false,
                    audioLevel = 0f,
                    transcript = "",
                    error = null,
                    aiEmotion = AIEmotion.Neutral
                )
            }

            _currentTranscript.value = ""
        }
    }

    fun onPushToTalkPressed() {
        Log.d(TAG, "🎤 Push-to-talk PRESSED")

        if (_uiState.value.connectionStatus != ConnectionStatus.Connected) {
            Log.w(TAG, "Not connected - current status: ${_uiState.value.connectionStatus}")
            return
        }

        try {
            geminiAudioManager.startRecording()
            _uiState.update {
                it.copy(
                    pushToTalkState = PTTState.Listening,
                    aiEmotion = AIEmotion.Thinking,
                    error = null
                )
            }

            // Start tracking recording duration
            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                while (_uiState.value.isRecording) {
                    _uiState.update {
                        it.copy(recordingDuration = System.currentTimeMillis() - startTime)
                    }
                    delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _uiState.update { it.copy(error = "Recording failed: ${e.message}") }
        }
    }

    fun onPushToTalkReleased() {
        Log.d(TAG, "🎤 Push-to-talk RELEASED")

        try {
            geminiAudioManager.stopRecording()
            geminiWebSocketClient.sendEndOfStream()

            _uiState.update {
                it.copy(
                    pushToTalkState = PTTState.Processing,
                    aiEmotion = AIEmotion.Thinking,
                    recordingDuration = 0L
                )
            }

            // Reset to idle after processing
            viewModelScope.launch {
                delay(1500)
                if (_uiState.value.pushToTalkState == PTTState.Processing) {
                    _uiState.update {
                        it.copy(pushToTalkState = PTTState.Idle)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            _uiState.update { it.copy(error = "Stop failed: ${e.message}") }
        }
    }

    fun cancelPushToTalk() {
        Log.d(TAG, "❌ Cancelling push-to-talk")
        try {
            geminiAudioManager.stopRecording()
            _uiState.update {
                it.copy(
                    pushToTalkState = PTTState.Idle,
                    aiEmotion = AIEmotion.Neutral,
                    isRecording = false,
                    recordingDuration = 0L
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel recording", e)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retryConnection() {
        clearError()
        connectToRealtime()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            disconnectFromRealtime()
            geminiAudioManager.release()
        }
    }
}