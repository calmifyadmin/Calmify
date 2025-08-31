package com.lifo.chat.domain.audio

import android.util.Log
import com.lifo.chat.data.realtime.RealtimeWebRTCClient
import com.lifo.chat.data.realtime.WebRTCConnectionState
import com.lifo.chat.domain.model.PTTState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Push-to-Talk functionality for WebRTC audio streaming
 */
@Singleton
class PushToTalkManager @Inject constructor(
    private val webRTCClient: RealtimeWebRTCClient,
    private val audioLevelExtractor: AudioLevelExtractor
) {
    
    companion object {
        private const val TAG = "PushToTalkManager"
        private const val MAX_RECORDING_DURATION_MS = 30_000L // 30 seconds max
        private const val MIN_RECORDING_DURATION_MS = 500L // 500ms minimum
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // PTT State
    private val _state = MutableStateFlow(PushToTalkState())
    val state: StateFlow<PushToTalkState> = _state.asStateFlow()
    
    // Recording timer
    private var recordingJob: Job? = null
    private var recordingStartTime: Long = 0
    
    init {
        observeWebRTCState()
        observeAudioLevels()
    }
    
    /**
     * Observe WebRTC connection state
     */
    private fun observeWebRTCState() {
        coroutineScope.launch {
            webRTCClient.sessionState.collect { webRTCState ->
                val canRecord = webRTCState.connectionState == WebRTCConnectionState.Connected
                _state.update { it.copy(canRecord = canRecord) }
                
                // If connection lost while recording, stop recording
                if (!canRecord && _state.value.isRecording) {
                    stopRecordingInternal("Connection lost")
                }
            }
        }
    }
    
    /**
     * Observe audio levels from extractor
     */
    private fun observeAudioLevels() {
        coroutineScope.launch {
            audioLevelExtractor.audioLevel.collect { level ->
                if (_state.value.isRecording) {
                    _state.update { it.copy(audioLevel = level) }
                }
            }
        }
    }
    
    /**
     * Start recording (Push-to-Talk pressed)
     */
    fun startRecording(): Boolean {
        return try {
            Log.d(TAG, "🎤 Starting push-to-talk recording...")
            
            val currentState = _state.value
            
            // Check if we can record
            if (!currentState.canRecord) {
                Log.w(TAG, "⚠️ Cannot start recording - WebRTC not connected")
                return false
            }
            
            if (currentState.isRecording) {
                Log.w(TAG, "⚠️ Already recording")
                return false
            }
            
            // Update state
            recordingStartTime = System.currentTimeMillis()
            _state.update { 
                it.copy(
                    isRecording = true,
                    recordingDuration = 0L,
                    error = null
                )
            }
            
            // Enable audio transmission
            webRTCClient.setAudioEnabled(true)
            
            // Start recording duration timer
            startRecordingTimer()
            
            Log.d(TAG, "✅ Push-to-talk recording started")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording", e)
            _state.update { 
                it.copy(
                    isRecording = false,
                    error = "Failed to start recording: ${e.message}"
                )
            }
            false
        }
    }
    
    /**
     * Stop recording (Push-to-Talk released)
     */
    fun stopRecording(): Boolean {
        return try {
            Log.d(TAG, "🎤 Stopping push-to-talk recording...")
            
            val currentState = _state.value
            
            if (!currentState.isRecording) {
                Log.w(TAG, "⚠️ Not currently recording")
                return false
            }
            
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            
            // Check minimum duration
            if (recordingDuration < MIN_RECORDING_DURATION_MS) {
                Log.w(TAG, "⚠️ Recording too short (${recordingDuration}ms), minimum is ${MIN_RECORDING_DURATION_MS}ms")
                cancelRecording()
                return false
            }
            
            stopRecordingInternal("User released PTT")
            Log.d(TAG, "✅ Push-to-talk recording stopped after ${recordingDuration}ms")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop recording", e)
            _state.update { 
                it.copy(
                    isRecording = false,
                    error = "Failed to stop recording: ${e.message}"
                )
            }
            false
        }
    }
    
    /**
     * Cancel recording (dismiss/cancel action)
     */
    fun cancelRecording() {
        try {
            Log.d(TAG, "❌ Cancelling push-to-talk recording...")
            
            if (!_state.value.isRecording) {
                Log.w(TAG, "⚠️ Not currently recording")
                return
            }
            
            stopRecordingInternal("User cancelled")
            Log.d(TAG, "✅ Push-to-talk recording cancelled")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel recording", e)
            _state.update { 
                it.copy(
                    isRecording = false,
                    error = "Failed to cancel recording: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Internal method to stop recording
     */
    private fun stopRecordingInternal(reason: String) {
        Log.d(TAG, "🛑 Stopping recording internally: $reason")
        
        // Cancel recording timer
        recordingJob?.cancel()
        recordingJob = null
        
        // Disable audio transmission 
        // Note: In real implementation, you might want to keep audio enabled
        // for bidirectional conversation. For PTT, we disable when not actively talking.
        webRTCClient.setAudioEnabled(false)
        
        // Update state
        val finalDuration = if (recordingStartTime > 0) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0L
        }
        
        _state.update { 
            it.copy(
                isRecording = false,
                audioLevel = 0f,
                recordingDuration = finalDuration
            )
        }
        
        recordingStartTime = 0
    }
    
    /**
     * Start the recording duration timer
     */
    private fun startRecordingTimer() {
        recordingJob?.cancel()
        
        recordingJob = coroutineScope.launch {
            while (_state.value.isRecording) {
                delay(100) // Update every 100ms
                
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - recordingStartTime
                
                // Check maximum duration
                if (duration >= MAX_RECORDING_DURATION_MS) {
                    Log.w(TAG, "⚠️ Maximum recording duration reached (${MAX_RECORDING_DURATION_MS}ms)")
                    stopRecordingInternal("Maximum duration reached")
                    break
                }
                
                // Update duration
                _state.update { it.copy(recordingDuration = duration) }
            }
        }
    }
    
    /**
     * Get current PTT state for UI
     */
    fun getCurrentPTTState(): PTTState {
        return when {
            _state.value.isRecording -> PTTState.Listening
            _state.value.recordingDuration > 0 -> PTTState.Processing
            else -> PTTState.Idle
        }
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
    
    /**
     * Get recording duration in a human-readable format
     */
    fun getFormattedDuration(): String {
        val duration = _state.value.recordingDuration
        val seconds = (duration / 1000).toInt()
        val milliseconds = (duration % 1000).toInt()
        return String.format("%d.%03ds", seconds, milliseconds)
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = _state.value.isRecording
    
    /**
     * Check if can record (WebRTC connected)
     */
    fun canRecord(): Boolean = _state.value.canRecord
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        recordingJob?.cancel()
        coroutineScope.cancel()
        _state.value = PushToTalkState()
    }
}

/**
 * State for Push-to-Talk functionality
 */
data class PushToTalkState(
    val isRecording: Boolean = false,
    val canRecord: Boolean = false,
    val recordingDuration: Long = 0L,
    val audioLevel: Float = 0f, // 0.0 to 1.0
    val error: String? = null
)