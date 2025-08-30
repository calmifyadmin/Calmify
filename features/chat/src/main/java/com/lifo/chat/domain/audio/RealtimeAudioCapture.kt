package com.lifo.chat.domain.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class RealtimeAudioCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "RealtimeAudioCapture"
        
        // OpenAI Realtime API requires 24kHz, 16-bit, mono PCM
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2 // Multiple of min buffer size for smoother capture
        
        // Audio levels for visualization
        private const val MAX_AMPLITUDE = 32767.0 // For 16-bit audio
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var bufferSize = 0

    /**
     * Start capturing audio and return a flow of audio data chunks
     */
    fun startCapture(): Flow<AudioCaptureResult> = flow {
        if (!checkAudioPermission()) {
            emit(AudioCaptureResult.Error("Audio recording permission not granted"))
            return@flow
        }

        try {
            initializeAudioRecord()
            isRecording = true
            
            emit(AudioCaptureResult.Started)
            
            val buffer = ByteArray(bufferSize)
            
            while (isRecording && currentCoroutineContext().isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                when {
                    bytesRead > 0 -> {
                        val audioData = buffer.copyOf(bytesRead)
                        val audioLevel = calculateRMSLevel(audioData)
                        emit(AudioCaptureResult.AudioData(audioData, audioLevel))
                    }
                    bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                        emit(AudioCaptureResult.Error("AudioRecord not initialized properly"))
                        break
                    }
                    bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                        emit(AudioCaptureResult.Error("AudioRecord bad value error"))
                        break
                    }
                    else -> {
                        // Continue reading
                    }
                }
            }
            
        } catch (e: SecurityException) {
            emit(AudioCaptureResult.Error("Security exception: ${e.message}"))
        } catch (e: Exception) {
            emit(AudioCaptureResult.Error("Audio capture error: ${e.message}"))
            Log.e(TAG, "Audio capture error", e)
        } finally {
            stopCapture()
            emit(AudioCaptureResult.Stopped)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stop audio capture
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping audio capture")
        isRecording = false
        
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio capture", e)
        } finally {
            audioRecord = null
        }
    }

    /**
     * Check if audio recording is currently active
     */
    fun isCapturing(): Boolean = isRecording

    /**
     * Get the current audio configuration info
     */
    fun getAudioConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = SAMPLE_RATE,
            channelConfig = CHANNEL_CONFIG,
            audioFormat = AUDIO_FORMAT,
            bufferSize = bufferSize
        )
    }

    private fun checkAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun initializeAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalArgumentException("Invalid audio recording configuration")
        }
        
        // Use a larger buffer size for more reliable capture
        bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
        
        Log.d(TAG, "Initializing AudioRecord - Sample Rate: $SAMPLE_RATE, Buffer Size: $bufferSize")
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).also { record ->
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("AudioRecord initialization failed")
            }
            
            record.startRecording()
            
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
                record.release()
                throw IllegalStateException("AudioRecord failed to start recording")
            }
        }
        
        Log.d(TAG, "AudioRecord initialized successfully")
    }

    /**
     * Calculate RMS (Root Mean Square) level for audio visualization
     * Returns a value between 0.0 and 1.0
     */
    private fun calculateRMSLevel(audioData: ByteArray): Float {
        if (audioData.isEmpty()) return 0f
        
        var sum = 0.0
        
        // Convert byte array to 16-bit samples and calculate RMS
        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                // Combine two bytes to form a 16-bit sample (little-endian)
                val sample = (audioData[i].toInt() and 0xFF) or 
                           ((audioData[i + 1].toInt() and 0xFF) shl 8)
                
                // Convert to signed 16-bit value
                val signedSample = if (sample > 32767) sample - 65536 else sample
                sum += signedSample * signedSample
            }
        }
        
        val rms = sqrt(sum / (audioData.size / 2))
        return (rms / MAX_AMPLITUDE).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Result types for audio capture operations
 */
sealed class AudioCaptureResult {
    object Started : AudioCaptureResult()
    object Stopped : AudioCaptureResult()
    data class AudioData(val data: ByteArray, val level: Float) : AudioCaptureResult()
    data class Error(val message: String) : AudioCaptureResult()
}

/**
 * Audio configuration data class
 */
data class AudioConfig(
    val sampleRate: Int,
    val channelConfig: Int,
    val audioFormat: Int,
    val bufferSize: Int
) {
    fun isCompatibleWith(other: AudioConfig): Boolean {
        return sampleRate == other.sampleRate &&
               channelConfig == other.channelConfig &&
               audioFormat == other.audioFormat
    }
    
    override fun toString(): String {
        return "AudioConfig(sampleRate=$sampleRate, channels=${getChannelCount()}, format=${getFormatString()}, bufferSize=$bufferSize)"
    }
    
    private fun getChannelCount(): Int {
        return when (channelConfig) {
            AudioFormat.CHANNEL_IN_MONO -> 1
            AudioFormat.CHANNEL_IN_STEREO -> 2
            else -> 1
        }
    }
    
    private fun getFormatString(): String {
        return when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit PCM"
            AudioFormat.ENCODING_PCM_8BIT -> "8-bit PCM"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit PCM"
            else -> "Unknown"
        }
    }
}