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
import com.lifo.chat.data.camera.GeminiLiveCameraManager
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
    private val geminiCameraManager: GeminiLiveCameraManager,
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
            isMuted = false,
            turnState = TurnState.WaitingForUser,
            audioLevel = 0f,
            transcript = "",
            partialTranscript = "",
            error = null,
            aiEmotion = AIEmotion.Neutral,
            sessionId = null,
            isChannelOpen = false
        )
    )
    val uiState: StateFlow<LiveChatUiState> = _uiState.asStateFlow()

    // Current transcript from AI
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()
    
    // Track if audio channel is open
    private var isAudioChannelOpen = false

    init {
        Log.d(TAG, "🎙️ Initializing LiveChatViewModel...")
        observeGeminiStates()
        setupGeminiCallbacks()
        setupCameraIntegration()
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

        // Observe recording state (channel always open)
        viewModelScope.launch {
            geminiAudioManager.recordingState.collectLatest { isRecording ->
                _uiState.update {
                    it.copy(
                        isChannelOpen = isRecording,
                        aiEmotion = if (!it.isMuted && isRecording) AIEmotion.Thinking else AIEmotion.Neutral
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

        // Observe camera state
        viewModelScope.launch {
            geminiCameraManager.isCameraActive.collectLatest { isActive ->
                _uiState.update {
                    it.copy(isCameraActive = isActive)
                }
            }
        }
    }

    // In LiveChatViewModel.kt

    private fun setupGeminiCallbacks() {
        // Handle partial transcript from user speech
        geminiWebSocketClient.onPartialTranscript = { partial ->
            Log.d(TAG, "🎤 Partial transcript: $partial")
            _uiState.update { it.copy(partialTranscript = partial) }
        }

        // Handle final transcript from user speech
        geminiWebSocketClient.onFinalTranscript = { final ->
            Log.d(TAG, "🎤 Final transcript: $final")
            _uiState.update { it.copy(transcript = final, partialTranscript = "") }
        }

        // Handle turn started (AI is responding)
        geminiWebSocketClient.onTurnStarted = {
            Log.d(TAG, "🤖 AI turn started")
            _uiState.update { it.copy(turnState = TurnState.AgentTurn, aiEmotion = AIEmotion.Speaking) }
        }

        // Handle turn completed
        geminiWebSocketClient.onTurnCompleted = {
            Log.d(TAG, "✅ Turn completed")
            _uiState.update { it.copy(turnState = TurnState.WaitingForUser, aiEmotion = AIEmotion.Neutral) }
        }

        // Handle interruption - AGGIUNGI QUI
        geminiWebSocketClient.onInterrupted = {
            Log.d(TAG, "⚠️ AI interrupted by user")
            geminiAudioManager.handleInterruption() // Pulisci la coda audio
            _uiState.update { it.copy(turnState = TurnState.UserTurn) }
        }

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

    private fun setupCameraIntegration() {
        // Setup camera callback to send images to Gemini
        geminiCameraManager.onImageCaptured = { imageBase64 ->
            Log.d(TAG, "📸 Sending image to Gemini Live (${imageBase64.length} chars)")
            viewModelScope.launch {
                try {
                    geminiWebSocketClient.sendImageData(imageBase64)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send image to Gemini", e)
                    _uiState.update {
                        it.copy(error = "Failed to send image: ${e.message}")
                    }
                }
            }
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
        Log.d(TAG, "📸 Updated UI state - hasCameraPermission: true")
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
        Log.d(TAG, "🔍 startCameraPreview() called in LiveChatViewModel")
        Log.d(TAG, "🔍 hasCameraPermission: ${_uiState.value.hasCameraPermission}")
        Log.d(TAG, "🔍 isCameraActive: ${_uiState.value.isCameraActive}")
        Log.d(TAG, "🔍 surfaceTexture: $surfaceTexture")
        
        if (!_uiState.value.hasCameraPermission) {
            Log.w(TAG, "❌ Cannot start camera without permission")
            return
        }
        
        Log.d(TAG, "📸 Starting camera preview - launching coroutine...")
        viewModelScope.launch {
            try {
                Log.d(TAG, "📸 Calling geminiCameraManager.startCameraPreview()...")
                geminiCameraManager.startCameraPreview(surfaceTexture)
                Log.d(TAG, "📸 geminiCameraManager.startCameraPreview() completed")
                // State will be updated automatically through the observer
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start camera preview", e)
                _uiState.update {
                    it.copy(error = "Failed to start camera: ${e.message}")
                }
            }
        }
    }

    fun stopCameraPreview() {
        Log.d(TAG, "📸 Stopping camera preview")
        viewModelScope.launch {
            try {
                geminiCameraManager.stopCameraPreview()
                // State will be updated automatically through the observer
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to stop camera preview", e)
            }
        }
    }

    fun connectToRealtime() {
        if (!_uiState.value.hasAudioPermission) {
            Log.w(TAG, "Cannot connect without audio permission")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Connecting to Gemini Live with VAD...")
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
                
                // Wait for connection then start audio channel
                delay(500)
                if (_uiState.value.connectionStatus == ConnectionStatus.Connected) {
                    startAudioChannel()
                }

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

            isAudioChannelOpen = false
            geminiAudioManager.stopRecording()
            geminiAudioManager.stopPlayback()
            geminiCameraManager.stopCameraPreview()
            geminiWebSocketClient.disconnect()

            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    turnState = TurnState.WaitingForUser,
                    isMuted = false,
                    isChannelOpen = false,
                    audioLevel = 0f,
                    transcript = "",
                    partialTranscript = "",
                    error = null,
                    aiEmotion = AIEmotion.Neutral,
                    isCameraActive = false
                )
            }

            _currentTranscript.value = ""
        }
    }

    /**
     * Start the audio channel (always open with VAD)
     */
    private fun startAudioChannel() {
        if (!isAudioChannelOpen && _uiState.value.connectionStatus == ConnectionStatus.Connected) {
            Log.d(TAG, "🎤 Starting always-open audio channel with VAD")
            try {
                geminiAudioManager.startRecording()
                isAudioChannelOpen = true
                _uiState.update { it.copy(isChannelOpen = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio channel", e)
                _uiState.update { it.copy(error = "Failed to start audio: ${e.message}") }
            }
        }
    }
    
    /**
     * Toggle mute/unmute state
     */
    fun toggleMute() {
        val newMuteState = !_uiState.value.isMuted
        Log.d(TAG, if (newMuteState) "🔇 Muting microphone" else "🎤 Unmuting microphone")
        
        _uiState.update { it.copy(isMuted = newMuteState) }
        
        if (newMuteState) {
            // When muting, send audioStreamEnd to flush VAD buffer
            geminiWebSocketClient.sendEndOfStream()
        }
        // Note: We don't stop recording, channel stays open
    }
    
    /**
     * Get current mute state
     */
    fun isMuted(): Boolean = _uiState.value.isMuted

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
            geminiCameraManager.release()
        }
    }
}