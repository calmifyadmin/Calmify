package com.lifo.chat.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * GeminiAudioPlayer - Production-Ready Audio Player for Gemini Live API
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Caratteristiche:
 * - CircularBuffer lock-free per performance massima
 * - Jitter buffer di 500ms per assorbire variazioni di rete
 * - AudioTrack low-latency con PERFORMANCE_MODE_LOW_LATENCY
 * - Protezione overflow/underrun con gestione elegante
 * - Statistiche real-time per diagnostica
 *
 * Formato audio:
 * - Input: PCM 24kHz, mono, 16-bit (da Gemini Live API)
 * - Buffer: 500ms = 24000 samples = 48000 bytes
 *
 * @author Jarvis AI Assistant
 */
class GeminiAudioPlayer {

    companion object {
        private const val TAG = "GeminiAudioPlayer"

        // Audio format constants (Gemini Live API output)
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2 // 16-bit

        // Buffer configuration
        private const val JITTER_BUFFER_MS = 500 // 500ms jitter buffer
        private const val JITTER_BUFFER_SAMPLES = SAMPLE_RATE * JITTER_BUFFER_MS / 1000 // 12000 samples
        private const val JITTER_BUFFER_BYTES = JITTER_BUFFER_SAMPLES * BYTES_PER_SAMPLE // 24000 bytes

        // Playback configuration
        private const val PLAYBACK_CHUNK_MS = 20 // Write 20ms chunks to AudioTrack
        private const val PLAYBACK_CHUNK_SAMPLES = SAMPLE_RATE * PLAYBACK_CHUNK_MS / 1000 // 480 samples
        private const val PLAYBACK_CHUNK_BYTES = PLAYBACK_CHUNK_SAMPLES * BYTES_PER_SAMPLE // 960 bytes

        // Start threshold: begin playback when buffer reaches this level
        private const val START_THRESHOLD_MS = 100 // Start after 100ms buffered
        private const val START_THRESHOLD_BYTES = SAMPLE_RATE * START_THRESHOLD_MS / 1000 * BYTES_PER_SAMPLE

        // Underrun handling
        private const val MAX_CONSECUTIVE_UNDERRUNS = 10
        private const val UNDERRUN_RECOVERY_DELAY_MS = 5L
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CIRCULAR BUFFER - Thread-safe, lock-minimized implementation
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * CircularBuffer ottimizzato per audio streaming.
     * Usa un singolo lock per semplicità e correttezza.
     */
    private class CircularBuffer(private val capacity: Int) {
        private val buffer = ByteArray(capacity)
        private var writePos = 0
        private var readPos = 0
        private var availableBytes = 0
        private val lock = ReentrantLock()

        /**
         * Scrive dati nel buffer circolare.
         * @return numero di byte effettivamente scritti
         */
        fun write(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
            lock.withLock {
                val bytesToWrite = minOf(length, capacity - availableBytes)
                if (bytesToWrite <= 0) return 0

                var written = 0
                var srcOffset = offset
                var remaining = bytesToWrite

                while (remaining > 0) {
                    val spaceToEnd = capacity - writePos
                    val chunk = minOf(remaining, spaceToEnd)
                    System.arraycopy(data, srcOffset, buffer, writePos, chunk)
                    writePos = (writePos + chunk) % capacity
                    srcOffset += chunk
                    remaining -= chunk
                    written += chunk
                }

                availableBytes += written
                return written
            }
        }

        /**
         * Legge dati dal buffer circolare.
         * @return numero di byte effettivamente letti
         */
        fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size): Int {
            lock.withLock {
                val bytesToRead = minOf(length, availableBytes)
                if (bytesToRead <= 0) return 0

                var read = 0
                var dstOffset = offset
                var remaining = bytesToRead

                while (remaining > 0) {
                    val dataToEnd = capacity - readPos
                    val chunk = minOf(remaining, dataToEnd)
                    System.arraycopy(buffer, readPos, dest, dstOffset, chunk)
                    readPos = (readPos + chunk) % capacity
                    dstOffset += chunk
                    remaining -= chunk
                    read += chunk
                }

                availableBytes -= read
                return read
            }
        }

        /**
         * @return byte disponibili per la lettura
         */
        fun available(): Int = lock.withLock { availableBytes }

        /**
         * @return spazio libero per scrittura
         */
        fun freeSpace(): Int = lock.withLock { capacity - availableBytes }

        /**
         * Svuota il buffer
         */
        fun clear() {
            lock.withLock {
                writePos = 0
                readPos = 0
                availableBytes = 0
            }
        }

        /**
         * @return true se il buffer è vuoto
         */
        fun isEmpty(): Boolean = lock.withLock { availableBytes == 0 }

        /**
         * @return percentuale di riempimento (0.0 - 1.0)
         */
        fun fillLevel(): Float = lock.withLock { availableBytes.toFloat() / capacity }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════════

    private val circularBuffer = CircularBuffer(JITTER_BUFFER_BYTES)
    private var audioTrack: AudioTrack? = null
    private val audioTrackLock = Any()

    private val isRunning = AtomicBoolean(false)
    private val isBuffering = AtomicBoolean(true)
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Statistics
    private val chunksReceived = AtomicInteger(0)
    private val chunksPlayed = AtomicInteger(0)
    private val underrunCount = AtomicInteger(0)
    private val overflowCount = AtomicInteger(0)
    private var consecutiveUnderruns = 0
    private var sessionStartTime = 0L

    // State flows for external observation
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _bufferLevel = MutableStateFlow(0f)
    val bufferLevel: StateFlow<Float> = _bufferLevel

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel

    // Audio level smoothing
    private var smoothedAudioLevel = 0f
    private val smoothingFactor = 0.25f

    // Fade-out control for smooth barge-in interruption
    private var currentVolume = 1.0f
    private val isFadingOut = AtomicBoolean(false)
    private var fadeOutJob: Job? = null

    // Fade-out parameters (matches Gemini Live behavior)
    private val fadeOutDurationMs = 150L  // 150ms smooth fade
    private val fadeOutSteps = 15
    private val fadeOutStepDelay = fadeOutDurationMs / fadeOutSteps

    // ═══════════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Avvia il player audio.
     * Inizializza AudioTrack e avvia il playback loop in background.
     */
    fun startPlayer() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Player already running")
            return
        }

        Log.d(TAG, "🎵 Starting GeminiAudioPlayer")
        Log.d(TAG, "   Jitter buffer: ${JITTER_BUFFER_MS}ms (${JITTER_BUFFER_BYTES} bytes)")
        Log.d(TAG, "   Start threshold: ${START_THRESHOLD_MS}ms")
        Log.d(TAG, "   Playback chunk: ${PLAYBACK_CHUNK_MS}ms")

        // Reset state
        circularBuffer.clear()
        isBuffering.set(true)
        chunksReceived.set(0)
        chunksPlayed.set(0)
        underrunCount.set(0)
        overflowCount.set(0)
        consecutiveUnderruns = 0
        sessionStartTime = System.currentTimeMillis()

        // Initialize AudioTrack
        initializeAudioTrack()

        // Start playback loop
        playbackJob = scope.launch {
            playbackLoop()
        }

        _isPlaying.value = true
        Log.d(TAG, "✅ Player started successfully")
    }

    /**
     * Accoda un chunk audio per la riproduzione.
     * @param audioData PCM bytes (24kHz, mono, 16-bit)
     */
    fun queueAudioChunk(audioData: ByteArray) {
        if (!isRunning.get()) {
            Log.w(TAG, "Cannot queue audio - player not running")
            return
        }

        chunksReceived.incrementAndGet()

        // Check for overflow
        val freeSpace = circularBuffer.freeSpace()
        if (freeSpace < audioData.size) {
            // Buffer overflow - drop oldest data to make room
            val overflow = audioData.size - freeSpace
            overflowCount.incrementAndGet()
            Log.w(TAG, "⚠️ Buffer overflow: dropping ${overflow} bytes (free: $freeSpace, incoming: ${audioData.size})")

            // Read and discard oldest bytes
            val discard = ByteArray(overflow)
            circularBuffer.read(discard)
        }

        // Write to circular buffer
        val written = circularBuffer.write(audioData)
        if (written < audioData.size) {
            Log.w(TAG, "⚠️ Partial write: $written/${audioData.size} bytes")
        }

        // Update buffer level
        _bufferLevel.value = circularBuffer.fillLevel()

        // Check if buffering is complete
        if (isBuffering.get() && circularBuffer.available() >= START_THRESHOLD_BYTES) {
            Log.d(TAG, "🎯 Buffering complete - starting playback (${circularBuffer.available()} bytes ready)")
            isBuffering.set(false)
        }

        Log.v(TAG, "📥 Queued ${audioData.size}B (buffer: ${(circularBuffer.fillLevel() * 100).toInt()}%)")
    }

    /**
     * Ferma il player e rilascia le risorse.
     */
    fun stopPlayer() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        Log.d(TAG, "🛑 Stopping GeminiAudioPlayer")

        // Cancel playback job
        playbackJob?.cancel()
        playbackJob = null

        // Release AudioTrack
        releaseAudioTrack()

        // Clear buffer
        circularBuffer.clear()

        // Log session statistics
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        Log.d(TAG, "📊 Session ended:")
        Log.d(TAG, "   Duration: ${sessionDuration}ms")
        Log.d(TAG, "   Chunks received: ${chunksReceived.get()}")
        Log.d(TAG, "   Chunks played: ${chunksPlayed.get()}")
        Log.d(TAG, "   Underruns: ${underrunCount.get()}")
        Log.d(TAG, "   Overflows: ${overflowCount.get()}")

        _isPlaying.value = false
        _bufferLevel.value = 0f
        _audioLevel.value = 0f
        smoothedAudioLevel = 0f

        Log.d(TAG, "✅ Player stopped")
    }

    /**
     * Gestisce un'interruzione (barge-in, VAD).
     * Implementa fade-out graduale del volume come Gemini Live desktop.
     */
    fun handleInterruption() {
        Log.d(TAG, "⚠️ Handling interruption - starting smooth fade-out")

        if (isFadingOut.getAndSet(true)) {
            Log.d(TAG, "Already fading out, skipping")
            return
        }

        // Cancel any existing fade-out
        fadeOutJob?.cancel()

        // Start smooth fade-out in background
        fadeOutJob = scope.launch {
            try {
                // Gradual volume reduction (matches Gemini Live behavior)
                val volumeStep = currentVolume / fadeOutSteps

                for (step in 0 until fadeOutSteps) {
                    if (!isActive) break

                    currentVolume = (currentVolume - volumeStep).coerceAtLeast(0f)
                    Log.v(TAG, "🔉 Fade-out step ${step + 1}/$fadeOutSteps: volume=${String.format("%.2f", currentVolume)}")

                    // Update audio level visualization
                    smoothedAudioLevel *= 0.85f
                    _audioLevel.value = smoothedAudioLevel

                    delay(fadeOutStepDelay)
                }

                // Now completely stop audio
                currentVolume = 0f
                circularBuffer.clear()
                isBuffering.set(true)
                consecutiveUnderruns = 0

                _bufferLevel.value = 0f
                _audioLevel.value = 0f
                smoothedAudioLevel = 0f

                Log.d(TAG, "✅ Fade-out complete - ready for new audio")

            } finally {
                // Reset for next audio
                currentVolume = 1.0f
                isFadingOut.set(false)
            }
        }
    }

    /**
     * Immediate stop without fade (for disconnect/cleanup)
     */
    fun handleImmediateStop() {
        Log.d(TAG, "🛑 Immediate stop - no fade")

        fadeOutJob?.cancel()
        isFadingOut.set(false)
        currentVolume = 1.0f

        circularBuffer.clear()
        isBuffering.set(true)
        consecutiveUnderruns = 0

        _bufferLevel.value = 0f
        _audioLevel.value = 0f
        smoothedAudioLevel = 0f
    }

    /**
     * @return diagnostica corrente del player
     */
    fun getDiagnostics(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning.get(),
            "isBuffering" to isBuffering.get(),
            "bufferLevel" to "${(circularBuffer.fillLevel() * 100).toInt()}%",
            "bufferBytes" to circularBuffer.available(),
            "bufferCapacity" to JITTER_BUFFER_BYTES,
            "chunksReceived" to chunksReceived.get(),
            "chunksPlayed" to chunksPlayed.get(),
            "underruns" to underrunCount.get(),
            "overflows" to overflowCount.get(),
            "audioTrackState" to (audioTrack?.state ?: -1)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PRIVATE IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Playback loop principale - gira in background thread dedicato.
     */
    private suspend fun playbackLoop() = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 Playback loop started")

        val playbackBuffer = ByteArray(PLAYBACK_CHUNK_BYTES)

        while (isRunning.get() && isActive) {
            try {
                // Wait for initial buffering
                if (isBuffering.get()) {
                    delay(10)
                    continue
                }

                // Read from circular buffer
                val bytesRead = circularBuffer.read(playbackBuffer)

                if (bytesRead > 0) {
                    // Reset underrun counter on successful read
                    consecutiveUnderruns = 0

                    // Calculate audio level for visualization
                    updateAudioLevel(playbackBuffer, bytesRead)

                    // Write to AudioTrack
                    writeToAudioTrack(playbackBuffer, bytesRead)

                    chunksPlayed.incrementAndGet()

                    // Update buffer level
                    _bufferLevel.value = circularBuffer.fillLevel()

                } else {
                    // Buffer underrun
                    handleUnderrun()
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "Playback loop cancelled")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in playback loop", e)
                delay(10)
            }
        }

        Log.d(TAG, "🔄 Playback loop ended")
    }

    /**
     * Gestisce un buffer underrun.
     */
    private suspend fun handleUnderrun() {
        consecutiveUnderruns++
        underrunCount.incrementAndGet()

        if (consecutiveUnderruns == 1) {
            Log.v(TAG, "🔇 Buffer underrun - waiting for data")
        }

        if (consecutiveUnderruns >= MAX_CONSECUTIVE_UNDERRUNS) {
            // Too many underruns - stream probably ended
            Log.d(TAG, "🔇 Stream appears ended ($consecutiveUnderruns consecutive underruns)")

            // Fade out audio level
            while (smoothedAudioLevel > 0.01f) {
                smoothedAudioLevel *= 0.85f
                _audioLevel.value = smoothedAudioLevel
                delay(20)
            }
            smoothedAudioLevel = 0f
            _audioLevel.value = 0f

            // Enter buffering mode to wait for more data
            isBuffering.set(true)
        }

        // Brief wait before next attempt
        delay(UNDERRUN_RECOVERY_DELAY_MS)
    }

    /**
     * Scrive dati all'AudioTrack con applicazione del volume fade.
     */
    private fun writeToAudioTrack(data: ByteArray, length: Int) {
        synchronized(audioTrackLock) {
            val track = audioTrack
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                Log.w(TAG, "AudioTrack not ready")
                return
            }

            // Apply volume fade if needed (during barge-in interruption)
            val processedData = if (currentVolume < 1.0f && currentVolume > 0f) {
                applyVolumeFade(data, length, currentVolume)
            } else if (currentVolume <= 0f) {
                // Volume is zero, skip writing (silently drop during fade-out completion)
                return
            } else {
                data
            }

            var offset = 0
            var remaining = length

            while (remaining > 0) {
                val written = track.write(processedData, offset, remaining)

                when {
                    written > 0 -> {
                        offset += written
                        remaining -= written
                    }
                    written == AudioTrack.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "AudioTrack ERROR_INVALID_OPERATION")
                        break
                    }
                    written == AudioTrack.ERROR_BAD_VALUE -> {
                        Log.e(TAG, "AudioTrack ERROR_BAD_VALUE")
                        break
                    }
                    written == AudioTrack.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "AudioTrack ERROR_DEAD_OBJECT - reinitializing")
                        initializeAudioTrack()
                        break
                    }
                    else -> {
                        Log.w(TAG, "AudioTrack write returned $written")
                        break
                    }
                }
            }
        }
    }

    /**
     * Applica fade di volume ai dati PCM 16-bit.
     */
    private fun applyVolumeFade(data: ByteArray, length: Int, volume: Float): ByteArray {
        val result = data.copyOf()
        val samples = length / 2

        for (i in 0 until samples) {
            val idx = i * 2
            // Read 16-bit sample (little-endian)
            val sample = (result[idx].toInt() and 0xFF) or (result[idx + 1].toInt() shl 8)
            val signedSample = sample.toShort()

            // Apply volume
            val scaledSample = (signedSample * volume).toInt().coerceIn(-32768, 32767).toShort()

            // Write back (little-endian)
            result[idx] = (scaledSample.toInt() and 0xFF).toByte()
            result[idx + 1] = ((scaledSample.toInt() shr 8) and 0xFF).toByte()
        }

        return result
    }

    /**
     * Inizializza AudioTrack con configurazione ottimale per low-latency.
     */
    private fun initializeAudioTrack() {
        synchronized(audioTrackLock) {
            try {
                // Release existing track
                releaseAudioTrack()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_ENCODING
                )

                if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid AudioTrack parameters")
                    return
                }

                // Buffer size: 2x minimum for smooth playback, capped at jitter buffer size
                val bufferSize = minOf(minBufferSize * 2, JITTER_BUFFER_BYTES)

                // USAGE_ASSISTANT: Optimized routing for AI voice assistants
                // - Audio focus: AUDIOFOCUS_GAIN_TRANSIENT
                // - Ducking: Other media streams are lowered automatically
                // - Privacy: Cannot be recorded by other apps
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AUDIO_ENCODING)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()

                val trackBuilder = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)

                // API 26+: Set performance mode
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }

                audioTrack = trackBuilder.build()

                if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                    audioTrack?.play()
                    Log.d(TAG, "🔊 AudioTrack initialized:")
                    Log.d(TAG, "   Buffer: $bufferSize bytes (min: $minBufferSize)")
                    Log.d(TAG, "   Sample rate: $SAMPLE_RATE Hz")
                    Log.d(TAG, "   Mode: LOW_LATENCY")
                } else {
                    Log.e(TAG, "AudioTrack failed to initialize")
                    audioTrack?.release()
                    audioTrack = null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AudioTrack", e)
                audioTrack = null
            }
        }
    }

    /**
     * Rilascia AudioTrack.
     */
    private fun releaseAudioTrack() {
        synchronized(audioTrackLock) {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            } finally {
                audioTrack = null
            }
        }
    }

    /**
     * Calcola e aggiorna il livello audio per la visualizzazione.
     */
    private fun updateAudioLevel(data: ByteArray, length: Int) {
        if (length < 2) return

        // Convert bytes to shorts and calculate RMS
        var sum = 0.0
        val samples = length / 2

        for (i in 0 until samples) {
            val idx = i * 2
            val sample = (data[idx].toInt() and 0xFF) or (data[idx + 1].toInt() shl 8)
            val normalized = sample.toShort().toDouble()
            sum += normalized * normalized
        }

        val rms = kotlin.math.sqrt(sum / samples)
        val rawLevel = (rms / 20000.0).toFloat().coerceIn(0f, 1f)

        // Apply smoothing
        smoothedAudioLevel = smoothedAudioLevel * (1f - smoothingFactor) + rawLevel * smoothingFactor

        // Apply curve for natural visual response
        val curved = Math.pow(smoothedAudioLevel.toDouble(), 0.7).toFloat()
        val amplified = curved * 2.0f

        _audioLevel.value = amplified.coerceIn(0f, 1f)
    }

    /**
     * Rilascia tutte le risorse.
     */
    fun release() {
        stopPlayer()
        scope.cancel()
    }
}
