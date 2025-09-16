package com.lifo.chat.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.*
import android.util.Base64
import android.util.Log
import com.lifo.chat.domain.audio.AdaptiveBargeinDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLiveAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adaptiveBargeinDetector: AdaptiveBargeinDetector
) {
    companion object {
        private const val TAG = "GeminiAudioManager"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val INPUT_CHUNK_SIZE_SAMPLES = 320 // 20ms chunks
        private const val SEND_INTERVAL_MS = 20L
        private const val BUFFER_SIZE_MULTIPLIER = 4

        // Limiti per evitare overflow
        private const val MAX_QUEUE_SIZE = 50 // Max chunks in coda
        private const val MAX_QUEUE_BYTES = 500_000 // ~500KB max

        // Barge-in detection
        private const val SPEECH_THRESHOLD = 0.08f // Soglia per rilevare voce
        private const val HOT_FRAMES_TO_BARGE = 4 // Frame consecutivi per barge-in (~80ms)
    }

    private var audioRecord: AudioRecord? = null

    // IMPORTANTE: Sincronizzazione thread-safe per AudioTrack
    private val audioTrackLock = Any()
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var isRecording = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pcmData = Collections.synchronizedList(mutableListOf<Short>())

    private val _recordingState = MutableStateFlow(false)
    val recordingState: StateFlow<Boolean> = _recordingState

    private val _playbackState = MutableStateFlow(false)
    val playbackState: StateFlow<Boolean> = _playbackState

    // Real-time audio levels for liquid visualizer
    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel

    private val _aiAudioLevel = MutableStateFlow(0f)
    val aiAudioLevel: StateFlow<Float> = _aiAudioLevel

    // SMOOTHING: Audio level smoothing for fluid transitions
    private var userLevelSmoothing = 0f
    private var aiLevelSmoothing = 0f
    private val smoothingFactor = 0.25f // BALANCED: Good responsiveness with elegant smoothness

    // Barge-in detection
    private var hotFrameCount = 0
    private var aiCurrentlySpeaking = false

    // Thread-safe queue con limite di dimensione
    private val audioQueue = Collections.synchronizedList(mutableListOf<ByteArray>())
    private var totalQueueBytes = AtomicInteger(0)

    // Buffer health monitoring
    private var bufferUnderrunCount = AtomicInteger(0)
    private var bufferOptimizationCount = AtomicInteger(0)

    private var isPlaying = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onAudioChunkReady: ((String) -> Unit)? = null
    var onBargeInDetected: (() -> Unit)? = null

    // Funzione per notificare quando l'AI sta parlando
    fun setAiSpeaking(speaking: Boolean) {
        aiCurrentlySpeaking = speaking
        if (!speaking) {
            hotFrameCount = 0 // Reset barge-in counter
        }
    }

    // Start voice learning per calibrazione iniziale
    fun startVoiceLearning() {
        Log.d(TAG, "🎓 Starting adaptive voice learning")
        adaptiveBargeinDetector.startVoiceLearning()
    }

    // Get detection stats per analytics
    fun getAdaptiveDetectionStats(): Map<String, Any> {
        return adaptiveBargeinDetector.getDetectionStats()
    }

    // Get buffer health stats per diagnostica
    fun getBufferHealthStats(): Map<String, Any> {
        return mapOf(
            "currentQueueSize" to audioQueue.size,
            "totalQueueBytes" to totalQueueBytes.get(),
            "bufferOptimizations" to bufferOptimizationCount.get(),
            "bufferUnderruns" to bufferUnderrunCount.get(),
            "isPlaying" to isPlaying.get(),
            "maxQueueSize" to MAX_QUEUE_SIZE,
            "maxQueueBytes" to MAX_QUEUE_BYTES
        )
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // Configura audio per comunicazione full-duplex
        configureAudioForCommunication()

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

        try {
            val audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_ENCODING)
                        .setSampleRate(INPUT_SAMPLE_RATE)
                        .setChannelMask(AUDIO_CHANNEL_IN)
                        .build()
                ).setBufferSizeInBytes(bufferSize)
                .build()

            this.audioRecord = audioRecord

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                return
            }

            // Abilita effetti audio se disponibili
            enableAudioEffects(audioRecord)

            audioRecord?.startRecording()
            isRecording = true
            _recordingState.value = true
            Log.d(TAG, "🎤 Recording started - buffer size: $bufferSize")

            // Recording thread
            recordingJob = scope.launch {
                val buffer = ShortArray(INPUT_CHUNK_SIZE_SAMPLES)

                while (isRecording && isActive) {
                    try {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                        when {
                            readSize > 0 -> {
                                // Adaptive Barge-in detection durante TTS
                                if (aiCurrentlySpeaking) {
                                    val result = adaptiveBargeinDetector.processAudioFrame(
                                        buffer, readSize, INPUT_SAMPLE_RATE
                                    )

                                    if (result.shouldTrigger) {
                                        Log.d(TAG, "🧠 Adaptive barge-in triggered! Confidence: ${result.confidence}, Reason: ${result.reason}")
                                        onBargeInDetected?.invoke()
                                    }
                                } else {
                                    // Continue learning voice profile when user speaks normally
                                    adaptiveBargeinDetector.processAudioFrame(
                                        buffer, readSize, INPUT_SAMPLE_RATE
                                    )
                                }

                                // Calculate real-time audio level for visualizer with smoothing
                                val rawLevel = calculateAudioLevel(buffer, readSize)
                                userLevelSmoothing = userLevelSmoothing * (1f - smoothingFactor) + rawLevel * smoothingFactor
                                _userAudioLevel.value = userLevelSmoothing

                                synchronized(pcmData) {
                                    // Limita la dimensione del buffer
                                    if (pcmData.size < 10000) {
                                        pcmData.addAll(buffer.take(readSize).toList())
                                    }
                                }
                            }
                            readSize == AudioRecord.ERROR_INVALID_OPERATION -> {
                                Log.e(TAG, "AudioRecord invalid operation")
                                break
                            }
                            readSize == AudioRecord.ERROR_BAD_VALUE -> {
                                Log.e(TAG, "AudioRecord bad value")
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in recording loop", e)
                        break
                    }
                }
            }

            // Send thread
            scope.launch {
                while (isRecording && isActive) {
                    delay(SEND_INTERVAL_MS)
                    sendAccumulatedData()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopRecording()
        }
    }

    // PATCH #1: Hard half-duplex - NON inviare audio mentre l'AI parla
    private fun sendAccumulatedData() {
        // 🚫 Non inviare input mentre l'AI parla: evita ducking e glitch
        if (aiCurrentlySpeaking) return

        val dataCopy = synchronized(pcmData) {
            if (pcmData.size >= INPUT_CHUNK_SIZE_SAMPLES) {
                val copy = pcmData.take(INPUT_CHUNK_SIZE_SAMPLES).toList()
                repeat(INPUT_CHUNK_SIZE_SAMPLES.coerceAtMost(pcmData.size)) {
                    pcmData.removeAt(0)
                }
                copy
            } else null
        }

        dataCopy?.let {
            scope.launch {
                try {
                    val buffer = ByteBuffer.allocate(it.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    it.forEach { v -> buffer.putShort(v) }
                    val base64 = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
                    Log.v(TAG, "🎤 Sending ${it.size} samples")
                    onAudioChunkReady?.invoke(base64)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending audio", e)
                }
            }
        }
    }

    fun stopRecording() {
        Log.d(TAG, "Stopping recording...")

        isRecording = false
        _recordingState.value = false

        // SMOOTHING: Gradual fade-out for user audio level
        scope.launch {
            while (userLevelSmoothing > 0.01f) {
                userLevelSmoothing *= 0.85f // Exponential decay
                _userAudioLevel.value = userLevelSmoothing
                delay(20) // 50fps smooth fade
            }
            userLevelSmoothing = 0f
            _userAudioLevel.value = 0f
        }

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        synchronized(pcmData) {
            pcmData.clear()
        }
    }

    // PATCH #4: Smart buffer management - evita skip intelligentemente
    fun queueAudioForPlayback(audioBase64: String) {
        scope.launch {
            try {
                val arrayBuffer = Base64.decode(audioBase64, Base64.DEFAULT)

                // Smart overflow handling: non scartare mai, ma ottimizza la coda
                val currentSize = totalQueueBytes.get()
                if (audioQueue.size >= MAX_QUEUE_SIZE || currentSize >= MAX_QUEUE_BYTES) {
                    // STRATEGIA 1: Rimuovi chunk più vecchi per fare spazio
                    while (audioQueue.size >= MAX_QUEUE_SIZE - 2 && audioQueue.isNotEmpty()) {
                        val removedChunk = audioQueue.removeAt(0)
                        totalQueueBytes.addAndGet(-removedChunk.size)
                    }
                    bufferOptimizationCount.incrementAndGet()
                    Log.w(TAG, "🔄 Buffer optimized: made space for new chunk (queue: ${audioQueue.size}, optimizations: ${bufferOptimizationCount.get()})")
                }

                audioQueue.add(arrayBuffer)
                totalQueueBytes.addAndGet(arrayBuffer.size)

                // Avvia playback immediato se non già in corso
                if (!isPlaying.get()) {
                    playNextAudioChunk()
                }

                Log.v(TAG, "✅ Queued audio: ${arrayBuffer.size} bytes (queue: ${audioQueue.size} chunks)")
            } catch (e: Exception) {
                Log.e(TAG, "Error queueing audio", e)
            }
        }
    }

    // PATCH #5: Smart chunking con continuità garantita
    private fun playNextAudioChunk() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch {
            while (audioQueue.isNotEmpty() && isActive) {
                // Check for underrun condition
                if (audioQueue.size == 1) {
                    bufferUnderrunCount.incrementAndGet()
                    Log.w(TAG, "⚠️ Buffer underrun detected (underruns: ${bufferUnderrunCount.get()})")
                }
                isPlaying.set(true)
                _playbackState.value = true

                try {
                    // STRATEGIA SMART: Accorpa dinamicamente per evitare gap
                    val packet = ByteArrayOutputStream()
                    var packets = 0
                    val targetChunks = if (audioQueue.size > 10) 5 else 3 // Più aggressive quando molti chunk

                    while (packets < targetChunks && audioQueue.isNotEmpty()) {
                        val chunk = audioQueue.removeAt(0)
                        totalQueueBytes.addAndGet(-chunk.size)
                        packet.write(chunk)
                        packets++
                    }
                    val merged = packet.toByteArray()

                    // Pre-check: se la coda è troppo piena, riduci latenza
                    if (audioQueue.size > 20) {
                        Log.d(TAG, "🚀 High queue detected, reducing chunking delay")
                        playAudio(merged, fastMode = true)
                    } else {
                        playAudio(merged)
                    }

                    val rawAiLevel = calculateAudioLevelFromBytes(merged)
                    aiLevelSmoothing = aiLevelSmoothing * (1f - smoothingFactor) + rawAiLevel * smoothingFactor
                    _aiAudioLevel.value = aiLevelSmoothing

                } catch (e: Exception) {
                    Log.e(TAG, "Error in playback loop", e)
                    // Recovery: continua il playback anche in caso di errore singolo
                    delay(10) // Breve pausa per recovery
                }
            }

            isPlaying.set(false)
            _playbackState.value = false

            // SMOOTHING: Gradual fade-out for AI audio level
            scope.launch {
                while (aiLevelSmoothing > 0.01f) {
                    aiLevelSmoothing *= 0.85f
                    _aiAudioLevel.value = aiLevelSmoothing
                    delay(20)
                }
                aiLevelSmoothing = 0f
                _aiAudioLevel.value = 0f
            }
        }
    }

    private suspend fun playAudio(byteArray: ByteArray, fastMode: Boolean = false) = withContext(Dispatchers.IO) {
        synchronized(audioTrackLock) {
            try {
                // Verifica/inizializza AudioTrack
                if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initializeAudioTrack()
                }

                // Ricontrolla dopo l'inizializzazione
                val track = audioTrack
                if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack not initialized properly")
                    return@withContext
                }

                // Scrivi audio con ottimizzazioni per fast mode
                var offset = 0
                val chunkSize = if (fastMode) 3840 else 1920 // Fast: 80ms, Normal: 40ms

                while (offset < byteArray.size && isActive) {
                    val bytesToWrite = minOf(chunkSize, byteArray.size - offset)
                    val bytesWritten = track.write(byteArray, offset, bytesToWrite)

                    if (bytesWritten < 0) {
                        Log.e(TAG, "AudioTrack write error: $bytesWritten")
                        break
                    }

                    offset += bytesWritten

                    // Fast mode: scrivi tutto subito senza pause
                    if (fastMode) continue
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in playAudio", e)
                // Reset AudioTrack in caso di errore
                releaseAudioTrack()
            }
        }
    }

    // PATCH #3: Togli VOICE_COMMUNICATION durante il playback (evita ducking)
    private fun initializeAudioTrack() {
        synchronized(audioTrackLock) {
            try {
                // 🔁 Passa a NORMAL per evitare ducking/processing invasivo
                try {
                    audioManager.mode = AudioManager.MODE_NORMAL
                } catch (_: Exception) {}

                releaseAudioTrack()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    OUTPUT_SAMPLE_RATE,
                    AUDIO_CHANNEL_OUT,
                    AUDIO_ENCODING
                )

                val bufferSize = (minBufferSize * 4).coerceAtLeast(OUTPUT_SAMPLE_RATE / 5) // ~200ms

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT) // era VOICE_COMMUNICATION
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setEncoding(AUDIO_ENCODING)
                    .setChannelMask(AUDIO_CHANNEL_OUT)
                    .build()

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                Log.d(TAG, "🔊 AudioTrack init (NORMAL) - buffer: $bufferSize")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AudioTrack", e)
                audioTrack = null
            }
        }
    }

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

    fun stopPlayback() {
        Log.d(TAG, "Stopping playback...")

        isPlaying.set(false)
        _playbackState.value = false

        playbackJob?.cancel()
        playbackJob = null

        // Pulisci la coda
        audioQueue.clear()
        totalQueueBytes.set(0)

        // Rilascia AudioTrack
        releaseAudioTrack()
        resetAudioConfiguration()
    }

    // Metodo per gestire interruzioni da VAD
    fun handleInterruption() {
        Log.d(TAG, "⚠️ Handling VAD interruption - clearing audio queue")

        // Ferma playback corrente
        playbackJob?.cancel()

        // Pulisci coda audio
        audioQueue.clear()
        totalQueueBytes.set(0)

        // Reset stato
        isPlaying.set(false)
        _playbackState.value = false
    }

    // Configurazione audio per comunicazione full-duplex
    private fun configureAudioForCommunication() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true // Default speaker

            // Configurazione Bluetooth SCO se disponibile
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                configureBluetooth()
            }

            Log.d(TAG, "🔊 Audio configured for full-duplex communication")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure audio", e)
        }
    }

    @Suppress("NewApi")
    private fun configureBluetooth() {
        try {
            val communicationDevices = audioManager.availableCommunicationDevices
            val scoDevice = communicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            scoDevice?.let {
                audioManager.setCommunicationDevice(it)
                Log.d(TAG, "🎧 Bluetooth SCO configured")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure Bluetooth", e)
        }
    }

    // Abilita effetti audio se disponibili
    private fun enableAudioEffects(audioRecord: AudioRecord) {
        try {
            val sessionId = audioRecord.audioSessionId

            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(sessionId)
                aec?.enabled = true
                Log.d(TAG, "✅ AEC enabled")
            }

            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
                Log.d(TAG, "✅ Noise suppressor enabled")
            }

            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(sessionId)
                agc?.enabled = true
                Log.d(TAG, "✅ AGC enabled")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable audio effects", e)
        }
    }

    // Calcola livello audio per barge-in
    private fun calculatePcmLevel(buffer: ShortArray, length: Int): Float {
        if (length == 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            sum += kotlin.math.abs(buffer[i].toInt())
        }
        val avg = sum / length
        return (avg / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Calculate real-time audio level from PCM samples for visualizer
     * Enhanced with higher sensitivity and better dynamic range
     */
    private fun calculateAudioLevel(buffer: ShortArray, length: Int): Float {
        if (length == 0) return 0f

        // Calculate RMS (Root Mean Square) for better audio level representation
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }

        val rms = kotlin.math.sqrt(sum / length)

        // BALANCED: Good sensitivity with controlled amplification
        val normalized = (rms / 20000.0).toFloat() // Moderate sensitivity

        // BALANCED: Apply curve for natural visual response
        val curved = Math.pow(normalized.toDouble(), 0.7).toFloat() // Moderate power curve

        // BALANCED: Controlled amplification for elegant response
        val amplified = curved * 2.0f // 2.0x amplification

        return amplified.coerceIn(0f, 1f)
    }

    /**
     * Calculate real-time audio level from AI audio bytes for visualizer
     */
    private fun calculateAudioLevelFromBytes(audioBytes: ByteArray): Float {
        if (audioBytes.isEmpty()) return 0f

        // Convert bytes to shorts (16-bit PCM)
        val buffer = ShortArray(audioBytes.size / 2)
        val byteBuffer = java.nio.ByteBuffer.wrap(audioBytes)
        byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

        for (i in buffer.indices) {
            if (byteBuffer.remaining() >= 2) {
                buffer[i] = byteBuffer.short
            }
        }

        return calculateAudioLevel(buffer, buffer.size)
    }

    private fun resetAudioConfiguration() {
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Could not reset audio", e)
        }
    }

    fun release() {
        Log.i(TAG, "Releasing audio manager...")
        stopRecording()
        stopPlayback()
        scope.cancel()
    }
}