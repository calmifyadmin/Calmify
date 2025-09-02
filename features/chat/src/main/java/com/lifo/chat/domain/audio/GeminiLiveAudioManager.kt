package com.lifo.chat.domain.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audio manager specifically for Gemini Live API
 * Handles 16kHz PCM recording and 24kHz audio playback
 */
@Singleton
class GeminiLiveAudioManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "GeminiLiveAudio"

        // Recording settings - Gemini Live expects 16kHz PCM
        private const val RECORDING_SAMPLE_RATE = 16000  // ✅ CORRETTO
        private const val RECORDING_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val RECORDING_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Playback settings - Gemini Live outputs 24kHz!
        private const val PLAYBACK_SAMPLE_RATE = 24000  // ⚠️ DEVE ESSERE 24000, NON 16000!
        private const val PLAYBACK_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val PLAYBACK_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val BUFFER_SIZE_FACTOR = 4
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Recording components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    // Playback components
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isPlaybackInitialized = false

    // Audio data streams
    private val _audioInputStream = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 50)
    val audioInputStream: SharedFlow<ByteArray> = _audioInputStream.asSharedFlow()

    private val _recordingState = MutableStateFlow(false)
    val recordingState: StateFlow<Boolean> = _recordingState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Buffer for audio processing
    private var recordingBuffer: ByteArray? = null

    /**
     * Check if audio permission is granted
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Initialize audio playback for AI responses
     * MUST be called before receiving any audio from Gemini
     */
    fun initializePlayback(): Boolean {
        // Run on Main thread to ensure thread safety
        if (isPlaybackInitialized && audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
            Log.d(TAG, "🔊 Playback already initialized")
            return true
        }

        try {
            // Stop any existing playback first
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
            audioTrack = null

            val minBufferSize = AudioTrack.getMinBufferSize(
                PLAYBACK_SAMPLE_RATE,
                PLAYBACK_CHANNEL_CONFIG,
                PLAYBACK_AUDIO_FORMAT
            )

            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Invalid playback buffer size: $minBufferSize")
                return false
            }

            // Log per confermare il sample rate
            Log.d(TAG, "✅ Playback initialized - Sample Rate: ${PLAYBACK_SAMPLE_RATE}Hz (should be 24000Hz)")

            // Verifica che sia 24kHz
            if (PLAYBACK_SAMPLE_RATE != 24000) {
                Log.e(TAG, "⚠️ WARNING: Playback sample rate should be 24000Hz for Gemini Live!")
            }

            // Use a larger buffer to prevent underruns
            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            // Create AudioTrack with proper attributes
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(PLAYBACK_SAMPLE_RATE)
                .setChannelMask(PLAYBACK_CHANNEL_CONFIG)
                .setEncoding(PLAYBACK_AUDIO_FORMAT)
                .build()

            val newTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioTrack failed to initialize")
                newTrack.release()
                return false
            }

            // Start playback immediately
            newTrack.play()

            // Only assign after successful initialization
            audioTrack = newTrack
            isPlaybackInitialized = true

            Log.d(TAG, "✅ Playback initialized - Sample Rate: ${PLAYBACK_SAMPLE_RATE}Hz, Buffer: $bufferSize bytes")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize playback", e)
            audioTrack?.release()
            audioTrack = null
            isPlaybackInitialized = false
            return false
        }
    }

    /**
     * Start playing audio stream from Gemini
     * This should be called with the audio flow from WebSocket
     */
    fun startAudioPlayback(audioFlow: SharedFlow<ByteArray>) {
        // Cancel any existing playback
        playbackJob?.cancel()

        // Ensure playback is initialized
        if (!initializePlayback()) {
            Log.e(TAG, "❌ Cannot start playback - initialization failed")
            return
        }

        playbackJob = coroutineScope.launch {
            Log.d(TAG, "🎵 Starting audio playback stream...")

            audioFlow.collect { audioData ->
                if (audioData.isNotEmpty()) {
                    playAudioChunk(audioData)
                }
            }
        }
    }

    /**
     * Play a single audio chunk
     * Audio from Gemini is raw PCM 16-bit at 24kHz
     */
    private suspend fun playAudioChunk(audioData: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                val track = audioTrack

                // Check if track is valid
                if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioTrack not initialized, attempting to reinitialize...")
                    if (!initializePlayback()) {
                        return@withContext
                    }
                }

                // Ensure track is playing
                if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track?.play()
                }

                // Write audio data directly - no conversion needed!
                val bytesWritten = track?.write(audioData, 0, audioData.size) ?: -1

                when {
                    bytesWritten < 0 -> {
                        Log.e(TAG, "❌ AudioTrack write error: $bytesWritten")
                        // Try to recover
                        track?.flush()
                        track?.play()
                    }
                    bytesWritten < audioData.size -> {
                        Log.w(TAG, "⚠️ Partial write: $bytesWritten of ${audioData.size} bytes")
                    }
                    else -> {
                        // Success - only log for larger chunks to avoid spam
                        if (audioData.size > 1000) {
                            Log.v(TAG, "🔊 Playing ${audioData.size} bytes")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error playing audio chunk", e)
            }
        }
    }

    /**
     * Alternative: Play audio data directly (for single chunks)
     * Use this if you're not using a flow
     */
    fun playAudioDirect(audioData: ByteArray) {
        coroutineScope.launch {
            if (!isPlaybackInitialized) {
                initializePlayback()
            }
            playAudioChunk(audioData)
        }
    }
    /**
     * Play audio data from Gemini Live API
     * Data should be 24kHz PCM
     * This is a simplified version that handles initialization automatically
     */
    fun playAudio(audioData: ByteArray) {
        // Skip empty data
        if (audioData.isEmpty()) {
            return
        }

        // Ensure we're on the right thread
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Initialize if needed (thread-safe)
                if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    synchronized(this@GeminiLiveAudioManager) {
                        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                            Log.d(TAG, "🔊 Initializing AudioTrack for playback...")
                            if (!initializePlayback()) {
                                Log.e(TAG, "❌ Failed to initialize playback")
                                return@launch
                            }
                        }
                    }
                }

                // Get track reference safely
                val track = audioTrack
                if (track == null) {
                    Log.e(TAG, "❌ AudioTrack is null after initialization")
                    return@launch
                }

                // Ensure track is playing
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }

                // Write audio data
                val written = track.write(audioData, 0, audioData.size)

                when {
                    written < 0 -> {
                        Log.e(TAG, "❌ Failed to write audio data: $written")
                        // Try to recover
                        track.flush()
                        track.play()
                    }
                    written < audioData.size -> {
                        Log.w(TAG, "⚠️ Partial write: $written of ${audioData.size} bytes")
                    }
                    else -> {
                        // Success - only log larger chunks
                        if (audioData.size > 1000) {
                            Log.v(TAG, "🔊 Played ${audioData.size} bytes")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error playing audio", e)
            }
        }
    }
    /**
     * Start recording audio for Gemini Live API
     * Returns true if recording started successfully
     */
    fun startRecording(): Boolean {
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ Audio permission not granted")
            return false
        }

        if (isRecording) {
            Log.w(TAG, "⚠️ Already recording")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                RECORDING_SAMPLE_RATE,
                RECORDING_CHANNEL_CONFIG,
                RECORDING_AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Invalid buffer size: $minBufferSize")
                return false
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
            recordingBuffer = ByteArray(bufferSize)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDING_SAMPLE_RATE,
                RECORDING_CHANNEL_CONFIG,
                RECORDING_AUDIO_FORMAT,
                bufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ AudioRecord not initialized properly")
                    record.release()
                    return false
                }

                record.startRecording()
                isRecording = true
                _recordingState.value = true

                Log.d(TAG, "🎤 Recording started - Sample Rate: ${RECORDING_SAMPLE_RATE}Hz, Buffer: $bufferSize bytes")

                startRecordingLoop(record)
            }

            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording", e)
            cleanup()
            return false
        }
    }

    private fun startRecordingLoop(record: AudioRecord) {
        recordingJob = coroutineScope.launch {
            val buffer = recordingBuffer ?: return@launch

            while (isRecording && isActive) {
                try {
                    val bytesRead = record.read(buffer, 0, buffer.size)

                    if (bytesRead > 0) {
                        // Calculate audio level for visualization
                        val audioLevel = calculateAudioLevel(buffer, bytesRead)
                        _audioLevel.value = audioLevel

                        // Send raw PCM data to Gemini Live API
                        val audioData = buffer.copyOf(bytesRead)
                        _audioInputStream.tryEmit(audioData)

                        // Small delay to prevent overwhelming the API
                        delay(10) // 10ms chunks
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "❌ AudioRecord read error: $bytesRead")
                        break
                    }

                } catch (e: CancellationException) {
                    // Normal cancellation
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error reading audio data", e)
                    break
                }
            }

            Log.d(TAG, "🎤 Recording loop ended")
        }
    }

    private fun calculateAudioLevel(buffer: ByteArray, length: Int): Float {
        var sum = 0.0

        // Convert bytes to shorts (16-bit PCM)
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (buffer[i].toInt() and 0xFF) or ((buffer[i + 1].toInt() and 0xFF) shl 8)
                val shortSample = if (sample > 32767) sample - 65536 else sample
                sum += shortSample * shortSample.toDouble()
            }
        }

        val rms = kotlin.math.sqrt(sum / (length / 2)).toFloat()
        return (rms / 32768.0f).coerceIn(0f, 1f)
    }

    /**
     * Stop recording audio
     */
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "⚠️ Not recording")
            return
        }

        Log.d(TAG, "🎤 Stopping recording...")

        isRecording = false
        _recordingState.value = false
        _audioLevel.value = 0f

        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping AudioRecord", e)
            }
        }
        audioRecord = null
        recordingBuffer = null
    }

    /**
     * Stop all playback
     */
    fun stopPlayback() {
        Log.d(TAG, "🔇 Stopping playback...")

        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping AudioTrack", e)
        }
        // Don't release audioTrack here - keep it for reuse
    }

    /**
     * Cleanup all audio resources
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up audio resources...")
        stopRecording()
        stopPlayback()

        audioTrack?.release()
        audioTrack = null
        isPlaybackInitialized = false

        coroutineScope.cancel()
    }

    /**
     * Get optimal audio settings info for debugging
     */
    fun getAudioInfo(): String {
        val recordBufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE,
            RECORDING_CHANNEL_CONFIG,
            RECORDING_AUDIO_FORMAT
        )

        val playbackBufferSize = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            PLAYBACK_CHANNEL_CONFIG,
            PLAYBACK_AUDIO_FORMAT
        )

        return """Audio Configuration:
            |Recording: ${RECORDING_SAMPLE_RATE}Hz, Buffer: $recordBufferSize bytes
            |Playback: ${PLAYBACK_SAMPLE_RATE}Hz, Buffer: $playbackBufferSize bytes
            |Has Permission: ${hasAudioPermission()}
            |Recording: $isRecording
            |Playback Initialized: $isPlaybackInitialized
        """.trimMargin()
    }
}