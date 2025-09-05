package com.lifo.chat.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLiveAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "GeminiAudioManager"
        
        // GEMINI LIVE API SPECIFICATIONS:
        // Input: 16kHz, 16-bit PCM, mono (as required by Gemini API)
        // Output: 24kHz for high quality playback
        private const val INPUT_SAMPLE_RATE = 16000  // Gemini API requirement
        private const val OUTPUT_SAMPLE_RATE = 24000 // High quality output
        private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // OPTIMIZED CHUNK SIZES:
        // Input: 320 samples = 20ms at 16kHz (optimal for real-time processing)
        // Buffer management for smoother audio flow
        private const val INPUT_CHUNK_SIZE_SAMPLES = 320 // 20ms at 16kHz
        private const val SEND_INTERVAL_MS = 20L // Send every 20ms for lower latency
        private const val BUFFER_SIZE_MULTIPLIER = 4 // 4x min buffer for stability
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    // Audio system management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val pcmData = Collections.synchronizedList(mutableListOf<Short>())

    private val _recordingState = MutableStateFlow(false)
    val recordingState: StateFlow<Boolean> = _recordingState

    private val _playbackState = MutableStateFlow(false)
    val playbackState: StateFlow<Boolean> = _playbackState

    private val audioQueue = Collections.synchronizedList(mutableListOf<ByteArray>())
    private var isPlaying = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onAudioChunkReady: ((String) -> Unit)? = null

    /**
     * Configure audio routing to ensure high-quality speaker output
     */
    private fun configureAudioForSpeakerOutput() {
        try {
            // Ensure audio is routed to speakers, not earpiece
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = AudioManager.MODE_NORMAL // Normal media mode, not call mode
            
            // Set volume to appropriate level for media
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            // If volume is too low, set it to 70% of max for good audibility
            if (currentVolume < maxVolume * 0.3) {
                val recommendedVolume = (maxVolume * 0.7).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, recommendedVolume, 0)
                Log.d(TAG, "🔊 Audio volume set to $recommendedVolume/$maxVolume for better playback")
            }
            
            Log.d(TAG, "🔊 Audio configured for high-quality speaker output")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure audio routing", e)
        }
    }

    /**
     * Reset audio configuration to normal state
     */
    private fun resetAudioConfiguration() {
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "🔊 Audio configuration reset to normal")
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset audio configuration", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // OPTIMIZED AUDIO RECORDING CONFIGURATION
        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AUDIO_CHANNEL_IN,
            AUDIO_ENCODING
        )
        
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid audio recording parameters")
            return
        }
        
        val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Echo cancellation + noise suppression
            INPUT_SAMPLE_RATE,
            AUDIO_CHANNEL_IN,
            AUDIO_ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        _recordingState.value = true
        Log.d(TAG, "🎤 Start Recording - buffer size: $bufferSize")

        // RECORDING THREAD - Optimized for 16kHz input
        recordingJob = scope.launch {
            val buffer = ShortArray(INPUT_CHUNK_SIZE_SAMPLES)
            Log.d(TAG, "🎤 Recording thread started - chunk size: $INPUT_CHUNK_SIZE_SAMPLES samples")

            while (isRecording) {
                try {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    when {
                        readSize > 0 -> {
                            synchronized(pcmData) {
                                pcmData.addAll(buffer.take(readSize).toList())
                            }
                        }
                        readSize == AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "AudioRecord invalid operation")
                            break
                        }
                        readSize == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "AudioRecord bad value error")
                            break
                        }
                        else -> {
                            // Continue reading
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in recording loop", e)
                    break
                }
            }
            
            Log.d(TAG, "🎤 Recording thread ended")
        }

        // Thread separato per l'invio periodico
        scope.launch {
            while (isRecording) {
                delay(SEND_INTERVAL_MS)
                sendAccumulatedData()
            }
        }
    }

    private fun sendAccumulatedData() {
        val dataCopy = synchronized(pcmData) {
            if (pcmData.size >= INPUT_CHUNK_SIZE_SAMPLES) {
                val copy = pcmData.take(INPUT_CHUNK_SIZE_SAMPLES).toList()
                repeat(INPUT_CHUNK_SIZE_SAMPLES.coerceAtMost(pcmData.size)) {
                    pcmData.removeAt(0)
                }
                copy
            } else if (pcmData.isNotEmpty()) {
                // Send partial data to reduce latency
                val copy = pcmData.toList()
                pcmData.clear()
                copy
            } else {
                null
            }
        }

        dataCopy?.let {
            scope.launch {
                val buffer = ByteBuffer.allocate(it.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                it.forEach { value ->
                    buffer.putShort(value)
                }

                val byteArray = buffer.array()
                val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT or Base64.NO_WRAP)

                Log.d(TAG, "🎤 Send Audio Chunk (${it.size} samples)")
                onAudioChunkReady?.invoke(base64)
            }
        }
    }

    fun stopRecording() {
        Log.d(TAG, "Stop Recording")

        // Invia gli ultimi dati rimasti
        sendAccumulatedData()

        isRecording = false
        _recordingState.value = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        synchronized(pcmData) {
            pcmData.clear()
        }
    }

    fun queueAudioForPlayback(audioBase64: String, inputSampleRate: Int = OUTPUT_SAMPLE_RATE) {
        scope.launch {
            try {
                val arrayBuffer = Base64.decode(audioBase64, Base64.DEFAULT)
                
                // SAMPLE RATE CONVERSION if needed
                val processedBuffer = if (inputSampleRate != OUTPUT_SAMPLE_RATE) {
                    Log.d(TAG, "🔄 Converting audio from ${inputSampleRate}Hz to ${OUTPUT_SAMPLE_RATE}Hz")
                    resampleAudio(arrayBuffer, inputSampleRate, OUTPUT_SAMPLE_RATE)
                } else {
                    arrayBuffer
                }
                
                audioQueue.add(processedBuffer)

                if (!isPlaying) {
                    playNextAudioChunk()
                }
                
                Log.v(TAG, "✅ Queued audio: ${processedBuffer.size} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing audio chunk", e)
            }
        }
    }

    private fun playNextAudioChunk() {
        scope.launch {
            while (audioQueue.isNotEmpty()) {
                isPlaying = true
                _playbackState.value = true

                // Accumula alcuni chunk prima di riprodurre per smoother playback
                val chunksToPlay = mutableListOf<ByteArray>()
                repeat(3.coerceAtMost(audioQueue.size)) {
                    if (audioQueue.isNotEmpty()) {
                        chunksToPlay.add(audioQueue.removeAt(0))
                    }
                }

                if (chunksToPlay.isNotEmpty()) {
                    val combined = ByteArray(chunksToPlay.sumOf { it.size })
                    var offset = 0
                    chunksToPlay.forEach { chunk ->
                        chunk.copyInto(combined, offset)
                        offset += chunk.size
                    }
                    playAudio(combined)
                }
            }

            isPlaying = false
            _playbackState.value = false
        }
    }

    private suspend fun playAudio(byteArray: ByteArray) = withContext(Dispatchers.IO) {
        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            // Configure audio system for high-quality speaker output
            configureAudioForSpeakerOutput()
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AUDIO_CHANNEL_OUT,
                AUDIO_ENCODING
            )
            
            if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid audio playback parameters")
                return@withContext
            }
            
            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

            audioTrack?.release()

            // HIGH-QUALITY AUDIO ATTRIBUTES FOR MEDIA CONTENT
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) // High-quality media output to speakers
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // High-quality audio processing
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY) // Low latency for real-time
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(OUTPUT_SAMPLE_RATE) // 24kHz for high quality output
                .setEncoding(AUDIO_ENCODING)
                .setChannelMask(AUDIO_CHANNEL_OUT)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) // Low latency mode
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed")
                return@withContext
            }

            audioTrack?.play()
            Log.d(TAG, "🔊 AudioTrack initialized - Sample Rate: $OUTPUT_SAMPLE_RATE, Buffer: $bufferSize")
        }

        // OPTIMIZED CHUNK WRITING - Prevent underruns and optimize for 24kHz playback
        var offset = 0
        val chunkSize = 1920 // 960 samples at 24kHz = 40ms chunks for smooth playback
        
        while (offset < byteArray.size) {
            val bytesToWrite = minOf(chunkSize, byteArray.size - offset)
            val bytesWritten = audioTrack?.write(byteArray, offset, bytesToWrite) ?: 0
            
            if (bytesWritten < 0) {
                Log.e(TAG, "AudioTrack write error: $bytesWritten")
                break
            } else if (bytesWritten != bytesToWrite) {
                Log.w(TAG, "Partial audio write: $bytesWritten/$bytesToWrite bytes")
            }
            
            offset += bytesWritten
        }
    }

    fun stopPlayback() {
        isPlaying = false
        _playbackState.value = false

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        audioQueue.clear()
        
        // Reset audio configuration when playback stops
        resetAudioConfiguration()
    }

    /**
     * Get current audio configuration information
     */
    fun getAudioConfig(): String {
        return """
            ═══ GEMINI LIVE AUDIO CONFIG ═══
            📡 Input:  ${INPUT_SAMPLE_RATE}Hz, 16-bit PCM, Mono
            🔊 Output: ${OUTPUT_SAMPLE_RATE}Hz, 16-bit PCM, Mono  
            🎤 Source: VOICE_COMMUNICATION (Echo Cancellation ON)
            🔊 Playback: USAGE_MEDIA + CONTENT_TYPE_MUSIC (High-Quality Speakers)
            📦 Chunk Size: $INPUT_CHUNK_SIZE_SAMPLES samples (${INPUT_CHUNK_SIZE_SAMPLES * 1000 / INPUT_SAMPLE_RATE}ms)
            🔄 Send Interval: ${SEND_INTERVAL_MS}ms
            💾 Buffer Multiplier: ${BUFFER_SIZE_MULTIPLIER}x
            🎯 Performance: LOW_LATENCY mode
            🔊 Volume: Auto-configured for optimal media playback
        """.trimIndent()
    }
    
    /**
     * Get current system status
     */
    fun getSystemStatus(): String {
        return """
            ═══ SYSTEM STATUS ═══
            🎤 Recording: ${if (isRecording) "ACTIVE" else "INACTIVE"}
            🔊 Playing: ${if (isPlaying) "ACTIVE" else "INACTIVE"}
            📊 Queue Size: ${audioQueue.size} chunks
            ⚡ PCM Buffer: ${pcmData.size} samples
        """.trimIndent()
    }

    fun release() {
        Log.i(TAG, "🔄 Releasing GeminiLiveAudioManager resources...")
        stopRecording()
        stopPlayback()
        resetAudioConfiguration()
        scope.cancel()
        Log.i(TAG, "✅ GeminiLiveAudioManager released")
    }
    
    /**
     * HIGH-QUALITY SAMPLE RATE CONVERSION using linear interpolation
     * Converts 16-bit PCM audio from input sample rate to output sample rate
     */
    private fun resampleAudio(inputBuffer: ByteArray, inputSampleRate: Int, outputSampleRate: Int): ByteArray {
        if (inputSampleRate == outputSampleRate) return inputBuffer
        
        // Convert bytes to 16-bit samples
        val inputSamples = ShortArray(inputBuffer.size / 2)
        val inputByteBuffer = ByteBuffer.wrap(inputBuffer).order(ByteOrder.LITTLE_ENDIAN)
        for (i in inputSamples.indices) {
            inputSamples[i] = inputByteBuffer.getShort()
        }
        
        // Calculate conversion ratio and output size
        val ratio = outputSampleRate.toDouble() / inputSampleRate.toDouble()
        val outputLength = (inputSamples.size * ratio).toInt()
        val outputSamples = ShortArray(outputLength)
        
        // LINEAR INTERPOLATION RESAMPLING - Better quality than nearest neighbor
        for (i in outputSamples.indices) {
            val inputIndex = i / ratio
            val inputIndexFloor = inputIndex.toInt()
            val fraction = inputIndex - inputIndexFloor
            
            if (inputIndexFloor >= inputSamples.size - 1) {
                // Handle edge case
                outputSamples[i] = inputSamples[inputSamples.size - 1]
            } else {
                // Linear interpolation between two adjacent samples
                val sample1 = inputSamples[inputIndexFloor].toDouble()
                val sample2 = inputSamples[inputIndexFloor + 1].toDouble()
                val interpolated = sample1 + fraction * (sample2 - sample1)
                outputSamples[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        
        // Convert back to byte array
        val outputBuffer = ByteBuffer.allocate(outputSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in outputSamples) {
            outputBuffer.putShort(sample)
        }
        
        Log.d(TAG, "🔄 Resampled: ${inputSamples.size} -> ${outputSamples.size} samples (${inputSampleRate}Hz -> ${outputSampleRate}Hz)")
        return outputBuffer.array()
    }
}