package com.lifo.chat.domain.audio

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Extracts audio levels (RMS) from audio frames for real-time visualization
 */
class AudioLevelExtractor {
    
    companion object {
        private const val TAG = "AudioLevelExtractor"
        private const val SMOOTHING_FACTOR = 0.3f // For smoothing level changes
        private const val MIN_RMS_DB = -60f // Minimum RMS in dB
        private const val MAX_RMS_DB = 0f // Maximum RMS in dB
        private const val LEVEL_UPDATE_INTERVAL_MS = 50L // 20 FPS updates
    }
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private var previousLevel = 0f
    private var updateJob: Job? = null
    private val levelQueue = ArrayDeque<Float>()
    private val maxQueueSize = 5 // Average over last 5 samples
    
    /**
     * Start extracting audio levels from the provided audio data flow
     */
    fun startExtracting(audioDataFlow: Flow<ByteArray>, sampleRate: Int = 48000, channels: Int = 1) {
        stopExtracting()
        
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            audioDataFlow
                .map { audioData ->
                    calculateRMS(audioData, sampleRate, channels)
                }
                .sample(LEVEL_UPDATE_INTERVAL_MS) // Limit updates to reasonable rate
                .collect { rmsLevel ->
                    val normalizedLevel = normalizeRMSLevel(rmsLevel)
                    val smoothedLevel = smoothLevel(normalizedLevel)
                    _audioLevel.value = smoothedLevel
                }
        }
        
        Log.d(TAG, "🎵 Started audio level extraction")
    }
    
    /**
     * Process a single audio frame and update level
     */
    fun processAudioFrame(audioData: ByteArray, sampleRate: Int = 48000, channels: Int = 1) {
        val rmsLevel = calculateRMS(audioData, sampleRate, channels)
        val normalizedLevel = normalizeRMSLevel(rmsLevel)
        val smoothedLevel = smoothLevel(normalizedLevel)
        _audioLevel.value = smoothedLevel
    }
    
    /**
     * Stop extracting audio levels
     */
    fun stopExtracting() {
        updateJob?.cancel()
        updateJob = null
        _audioLevel.value = 0f
        previousLevel = 0f
        levelQueue.clear()
        Log.d(TAG, "🔇 Stopped audio level extraction")
    }
    
    /**
     * Calculate RMS (Root Mean Square) from audio data
     */
    private fun calculateRMS(audioData: ByteArray, sampleRate: Int, channels: Int): Float {
        if (audioData.isEmpty()) return 0f
        
        return when {
            audioData.size % 2 == 0 -> calculateRMS16Bit(audioData)
            else -> calculateRMS8Bit(audioData)
        }
    }
    
    /**
     * Calculate RMS for 16-bit audio samples
     */
    private fun calculateRMS16Bit(audioData: ByteArray): Float {
        val buffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        var sampleCount = 0
        
        while (buffer.hasRemaining()) {
            if (buffer.remaining() >= 2) {
                val sample = buffer.short.toFloat() / Short.MAX_VALUE.toFloat()
                sum += sample * sample
                sampleCount++
            } else {
                break
            }
        }
        
        return if (sampleCount > 0) {
            sqrt(sum / sampleCount).toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Calculate RMS for 8-bit audio samples
     */
    private fun calculateRMS8Bit(audioData: ByteArray): Float {
        var sum = 0.0
        
        audioData.forEach { sample ->
            val normalizedSample = (sample.toInt() and 0xFF - 128) / 128.0f
            sum += normalizedSample * normalizedSample
        }
        
        return sqrt(sum / audioData.size).toFloat()
    }
    
    /**
     * Normalize RMS level to 0.0 - 1.0 range using decibel scale
     */
    private fun normalizeRMSLevel(rms: Float): Float {
        if (rms <= 0f) return 0f
        
        val dbLevel = 20 * log10(rms)
        val normalizedDb = (dbLevel - MIN_RMS_DB) / (MAX_RMS_DB - MIN_RMS_DB)
        
        return normalizedDb.coerceIn(0f, 1f)
    }
    
    /**
     * Apply smoothing to level changes to reduce jitter
     */
    private fun smoothLevel(newLevel: Float): Float {
        // Add to queue and maintain size
        levelQueue.addLast(newLevel)
        if (levelQueue.size > maxQueueSize) {
            levelQueue.removeFirst()
        }
        
        // Calculate moving average
        val averageLevel = levelQueue.average().toFloat()
        
        // Apply exponential smoothing
        val smoothedLevel = (SMOOTHING_FACTOR * averageLevel) + 
                           ((1 - SMOOTHING_FACTOR) * previousLevel)
        
        previousLevel = smoothedLevel
        return smoothedLevel
    }
    
    /**
     * Get current audio level without subscribing to flow
     */
    fun getCurrentLevel(): Float = _audioLevel.value
    
    /**
     * Manually set audio level (useful for testing)
     */
    fun setLevel(level: Float) {
        _audioLevel.value = level.coerceIn(0f, 1f)
    }
    
    /**
     * Reset the extractor to initial state
     */
    fun reset() {
        stopExtracting()
        _audioLevel.value = 0f
        previousLevel = 0f
        levelQueue.clear()
    }
}