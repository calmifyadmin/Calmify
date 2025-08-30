package com.lifo.chat.domain.audio

import android.util.Log
import com.lifo.chat.data.realtime.PushToTalkState
import com.lifo.chat.data.realtime.RealtimeSessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushToTalkController @Inject constructor(
    private val audioCapture: RealtimeAudioCapture,
    private val sessionManager: RealtimeSessionManager
) {
    companion object {
        private const val TAG = "PushToTalkController"
        private const val AUDIO_LEVEL_SMOOTHING = 0.7f // Smoothing factor for audio level
        private const val MAX_RECORDING_DURATION_MS = 30000L // 30 seconds max
    }

    private val _state = MutableStateFlow(PushToTalkState())
    val state = _state.asStateFlow()

    private var recordingJob: Job? = null
    private var recordingStartTime = 0L
    private var durationUpdateJob: Job? = null
    private var smoothedAudioLevel = 0f

    /**
     * Start recording audio for push-to-talk
     */
    suspend fun startRecording() {
        if (_state.value.isRecording) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        Log.d(TAG, "Starting push-to-talk recording")
        
        try {
            recordingStartTime = System.currentTimeMillis()
            _state.update { 
                it.copy(
                    isRecording = true, 
                    error = null,
                    recordingDuration = 0L,
                    audioLevel = 0f
                ) 
            }

            // Start duration tracking
            startDurationTracking()

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    audioCapture.startCapture().collect { result ->
                        when (result) {
                            is AudioCaptureResult.Started -> {
                                Log.d(TAG, "Audio capture started successfully")
                            }
                            
                            is AudioCaptureResult.AudioData -> {
                                // Send audio data to OpenAI Realtime API
                                sessionManager.sendAudioData(result.data)
                                
                                // Update audio level with smoothing
                                smoothedAudioLevel = (AUDIO_LEVEL_SMOOTHING * smoothedAudioLevel) + 
                                                   ((1 - AUDIO_LEVEL_SMOOTHING) * result.level)
                                
                                _state.update { 
                                    it.copy(audioLevel = smoothedAudioLevel) 
                                }
                            }
                            
                            is AudioCaptureResult.Error -> {
                                Log.e(TAG, "Audio capture error: ${result.message}")
                                _state.update { 
                                    it.copy(
                                        isRecording = false, 
                                        error = result.message
                                    ) 
                                }
                                stopRecordingInternal()
                            }
                            
                            is AudioCaptureResult.Stopped -> {
                                Log.d(TAG, "Audio capture stopped")
                                _state.update { 
                                    it.copy(
                                        isRecording = false,
                                        audioLevel = 0f
                                    ) 
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in recording coroutine", e)
                    _state.update { 
                        it.copy(
                            isRecording = false, 
                            error = "Recording error: ${e.message}"
                        ) 
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _state.update { 
                it.copy(
                    isRecording = false, 
                    error = "Failed to start recording: ${e.message}"
                ) 
            }
        }
    }

    /**
     * Stop recording and commit audio buffer
     */
    suspend fun stopRecording() {
        if (!_state.value.isRecording) {
            Log.w(TAG, "No recording in progress")
            return
        }

        Log.d(TAG, "Stopping push-to-talk recording")
        
        stopRecordingInternal()
        
        // Commit the audio buffer to signal end of user speech
        try {
            sessionManager.commitAudioBuffer()
            Log.d(TAG, "Audio buffer committed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error committing audio buffer", e)
            _state.update { 
                it.copy(error = "Failed to commit audio: ${e.message}") 
            }
        }
    }

    /**
     * Cancel recording without committing
     */
    suspend fun cancelRecording() {
        if (!_state.value.isRecording) {
            Log.w(TAG, "No recording in progress to cancel")
            return
        }

        Log.d(TAG, "Cancelling push-to-talk recording")
        
        stopRecordingInternal()
        
        // Clear the audio buffer instead of committing
        try {
            sessionManager.clearAudioBuffer()
            Log.d(TAG, "Audio buffer cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio buffer", e)
        }
    }

    /**
     * Clear any error state
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Get current recording duration
     */
    fun getRecordingDuration(): Long {
        return if (_state.value.isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            _state.value.recordingDuration
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = _state.value.isRecording

    /**
     * Get current audio level (0.0 to 1.0)
     */
    fun getCurrentAudioLevel(): Float = _state.value.audioLevel

    private fun stopRecordingInternal() {
        recordingJob?.cancel()
        durationUpdateJob?.cancel()
        
        audioCapture.stopCapture()
        smoothedAudioLevel = 0f
        
        _state.update { 
            it.copy(
                isRecording = false,
                audioLevel = 0f,
                recordingDuration = if (recordingStartTime > 0) {
                    System.currentTimeMillis() - recordingStartTime
                } else {
                    it.recordingDuration
                }
            ) 
        }
    }

    private fun startDurationTracking() {
        durationUpdateJob?.cancel()
        durationUpdateJob = CoroutineScope(Dispatchers.IO).launch {
            while (_state.value.isRecording && isActive) {
                val duration = System.currentTimeMillis() - recordingStartTime
                
                _state.update { 
                    it.copy(recordingDuration = duration) 
                }

                // Auto-stop if max duration reached
                if (duration >= MAX_RECORDING_DURATION_MS) {
                    Log.w(TAG, "Maximum recording duration reached, stopping automatically")
                    stopRecording()
                    break
                }

                delay(100) // Update every 100ms
            }
        }
    }
}

/**
 * Extension to convert float array to ByteArray for visualization
 */
fun Float.toFloatArray(): FloatArray = floatArrayOf(this)

/**
 * Utility functions for audio level processing
 */
object AudioLevelUtils {
    /**
     * Apply smoothing to audio level changes
     */
    fun smoothAudioLevel(current: Float, new: Float, smoothingFactor: Float = 0.7f): Float {
        return (smoothingFactor * current) + ((1 - smoothingFactor) * new)
    }

    /**
     * Convert audio level to decibels
     */
    fun levelToDecibels(level: Float): Float {
        if (level <= 0f) return -60f // Minimum dB
        return 20f * kotlin.math.log10(level)
    }

    /**
     * Map audio level to a discrete range (e.g., for bar visualizers)
     */
    fun mapLevelToRange(level: Float, minRange: Int, maxRange: Int): Int {
        val clampedLevel = level.coerceIn(0f, 1f)
        return (minRange + (clampedLevel * (maxRange - minRange))).toInt()
    }
}