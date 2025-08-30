package com.lifo.chat.domain.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class RealtimeAudioPlayer @Inject constructor() {
    companion object {
        private const val TAG = "RealtimeAudioPlayer"
        
        // OpenAI Realtime API provides 24kHz, 16-bit, mono PCM
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 4 // Larger buffer for smoother playback
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<AudioChunk>()
    private var playbackJob: Job? = null
    private var isPlaying = false
    private var bufferSize = 0

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    /**
     * Start audio playback
     */
    suspend fun startPlayback(): Boolean = withContext(Dispatchers.IO) {
        if (isPlaying) {
            Log.w(TAG, "Playback already active")
            return@withContext true
        }

        try {
            initializeAudioTrack()
            isPlaying = true
            _playbackState.value = PlaybackState.Playing(0f)

            // Start playback coroutine
            playbackJob = launch {
                playAudioQueue()
            }

            Log.d(TAG, "Audio playback started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio playback", e)
            _playbackState.value = PlaybackState.Error(e.message ?: "Playback initialization failed")
            false
        }
    }

    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        Log.d(TAG, "Stopping audio playback")
        
        isPlaying = false
        playbackJob?.cancel()
        
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                flush()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        } finally {
            audioTrack = null
            audioQueue.clear()
            _playbackState.value = PlaybackState.Idle
        }
    }

    /**
     * Queue audio data for playback
     * @param base64Audio Base64 encoded PCM16 audio data from OpenAI
     */
    fun queueAudio(base64Audio: String) {
        if (!isPlaying) {
            Log.w(TAG, "Cannot queue audio: playback not started")
            return
        }

        try {
            val audioData = Base64.decode(base64Audio, Base64.DEFAULT)
            val audioLevel = calculateAudioLevel(audioData)
            val chunk = AudioChunk(audioData, audioLevel)
            
            if (!audioQueue.offer(chunk)) {
                Log.w(TAG, "Audio queue is full, dropping audio chunk")
            } else {
                Log.v(TAG, "Queued audio chunk: ${audioData.size} bytes, level: $audioLevel")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing audio", e)
        }
    }

    /**
     * Queue raw PCM audio data
     */
    fun queueRawAudio(audioData: ByteArray) {
        if (!isPlaying) {
            Log.w(TAG, "Cannot queue audio: playback not started")
            return
        }

        val audioLevel = calculateAudioLevel(audioData)
        val chunk = AudioChunk(audioData, audioLevel)
        
        if (!audioQueue.offer(chunk)) {
            Log.w(TAG, "Audio queue is full, dropping audio chunk")
        } else {
            Log.v(TAG, "Queued raw audio chunk: ${audioData.size} bytes, level: $audioLevel")
        }
    }

    /**
     * Clear all queued audio
     */
    fun clearQueue() {
        audioQueue.clear()
        Log.d(TAG, "Audio queue cleared")
    }

    /**
     * Get current queue size
     */
    fun getQueueSize(): Int = audioQueue.size

    /**
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * Get audio configuration
     */
    fun getAudioConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = SAMPLE_RATE,
            channelConfig = CHANNEL_CONFIG,
            audioFormat = AUDIO_FORMAT,
            bufferSize = bufferSize
        )
    }

    private fun initializeAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalArgumentException("Invalid audio playback configuration")
        }

        bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

        Log.d(TAG, "Initializing AudioTrack - Sample Rate: $SAMPLE_RATE, Buffer Size: $bufferSize")

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        ).also { track ->
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                throw IllegalStateException("AudioTrack initialization failed")
            }

            track.play()

            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
                track.release()
                throw IllegalStateException("AudioTrack failed to start playing")
            }
        }

        Log.d(TAG, "AudioTrack initialized successfully")
    }

    private suspend fun playAudioQueue() {
        while (isPlaying && currentCoroutineContext().isActive) {
            try {
                // Wait for audio data with timeout
                val chunk = withTimeoutOrNull(100) {
                    withContext(Dispatchers.IO) {
                        audioQueue.take()
                    }
                }

                chunk?.let { audioChunk ->
                    val track = audioTrack
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val bytesWritten = track.write(
                            audioChunk.data, 
                            0, 
                            audioChunk.data.size
                        )

                        if (bytesWritten < 0) {
                            Log.e(TAG, "AudioTrack write error: $bytesWritten")
                        } else {
                            Log.v(TAG, "Played audio chunk: $bytesWritten bytes")
                            _playbackState.value = PlaybackState.Playing(audioChunk.level)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isPlaying) { // Only log if we're still supposed to be playing
                    Log.e(TAG, "Error in playback loop", e)
                }
            }
        }

        Log.d(TAG, "Playback loop ended")
    }

    private fun calculateAudioLevel(audioData: ByteArray): Float {
        if (audioData.isEmpty()) return 0f

        var sum = 0.0
        val sampleCount = audioData.size / 2 // 16-bit samples

        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                // Convert little-endian bytes to 16-bit sample
                val sample = (audioData[i].toInt() and 0xFF) or 
                           ((audioData[i + 1].toInt() and 0xFF) shl 8)
                
                // Convert to signed value
                val signedSample = if (sample > 32767) sample - 65536 else sample
                sum += kotlin.math.abs(signedSample)
            }
        }

        val average = sum / sampleCount
        return (average / 32767.0).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * Audio chunk data class
 */
private data class AudioChunk(
    val data: ByteArray,
    val level: Float
)

/**
 * Playback state sealed class
 */
sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Playing(val audioLevel: Float = 0f) : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}