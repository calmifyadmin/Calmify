package com.lifo.chat.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.*
import android.os.Process
import android.util.Base64
import android.util.Log
import com.lifo.chat.audio.vad.SileroVadEngine
import com.lifo.chat.domain.audio.AdaptiveBargeinDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val adaptiveBargeinDetector: AdaptiveBargeinDetector,
    private val sileroVadEngine: SileroVadEngine
) {
    companion object {
        private const val TAG = "GeminiAudioManager"
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val AUDIO_CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // VAD-optimized chunk size: 512 samples = 32ms @ 16kHz (Silero VAD requirement)
        private const val INPUT_CHUNK_SIZE_SAMPLES = 512
        private const val SEND_INTERVAL_MS = 32L

        // Increased buffer multiplier for better audio quality
        private const val BUFFER_SIZE_MULTIPLIER = 8

        // Queue limits to prevent overflow
        private const val MAX_QUEUE_SIZE = 500 // Increased for smoother playback
        private const val MAX_QUEUE_BYTES = 5_000_000 // ~1MB max

        // Legacy threshold (now handled by SileroVadEngine)
        private const val SPEECH_THRESHOLD = 0.08f
        private const val HOT_FRAMES_TO_BARGE = 3 // ~96ms with 32ms frames

        // Use Silero VAD instead of simple threshold
        private const val USE_SILERO_VAD = true
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

    // NUOVO: Real-time audio levels for liquid visualizer
    private val _userAudioLevel = MutableStateFlow(0f)
    val userAudioLevel: StateFlow<Float> = _userAudioLevel

    private val _aiAudioLevel = MutableStateFlow(0f)
    val aiAudioLevel: StateFlow<Float> = _aiAudioLevel

    // SMOOTHING: Audio level smoothing for fluid transitions
    private var userLevelSmoothing = 0f
    private var aiLevelSmoothing = 0f
    private val smoothingFactor = 0.25f // BALANCED: Good responsiveness with elegant smoothness

    // NUOVO: Barge-in detection
    private var hotFrameCount = 0
    private var aiCurrentlySpeaking = false

    // MIGLIORATO: Thread-safe queue con limite di dimensione
    private val audioQueue = Collections.synchronizedList(mutableListOf<ByteArray>())
    private var totalQueueBytes = AtomicInteger(0)

    private var isPlaying = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onAudioChunkReady: ((String) -> Unit)? = null
    var onBargeInDetected: (() -> Unit)? = null

    // NUOVO: Funzione per notificare quando l'AI sta parlando
    // Abilita/disabilita dinamicamente la modalità barge-in del VAD
    fun setAiSpeaking(speaking: Boolean) {
        val wasAiSpeaking = aiCurrentlySpeaking
        aiCurrentlySpeaking = speaking

        if (speaking && !wasAiSpeaking) {
            // AI started speaking - enable barge-in detection
            Log.d(TAG, "🎙️ AI started speaking - enabling VAD barge-in mode")
            if (USE_SILERO_VAD) {
                sileroVadEngine.enableBargeInMode()
            }
        } else if (!speaking && wasAiSpeaking) {
            // AI stopped speaking - disable barge-in detection
            Log.d(TAG, "🔇 AI stopped speaking - disabling VAD barge-in mode")
            if (USE_SILERO_VAD) {
                sileroVadEngine.disableBargeInMode()
            }
            hotFrameCount = 0 // Reset legacy barge-in counter
        }
    }

    // NUOVO: Start voice learning per calibrazione iniziale
    fun startVoiceLearning() {
        Log.d(TAG, "🎓 Starting adaptive voice learning")
        adaptiveBargeinDetector.startVoiceLearning()

        // Initialize Silero VAD
        if (USE_SILERO_VAD && sileroVadEngine.initialize()) {
            Log.d(TAG, "✅ Silero VAD initialized for voice learning")
        }
    }

    // NUOVO: Get detection stats per analytics
    fun getAdaptiveDetectionStats(): Map<String, Any> {
        return adaptiveBargeinDetector.getDetectionStats()
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // NUOVO: Configura audio per comunicazione full-duplex
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
            // Use MIC instead of VOICE_COMMUNICATION to avoid phone call mode
            // VOICE_COMMUNICATION activates telephony routing (earpiece + AEC)
            // MIC provides standard microphone input with speaker output
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

            // NUOVO: Abilita effetti audio se disponibili
            enableAudioEffects(audioRecord)

            audioRecord?.startRecording()
            isRecording = true
            _recordingState.value = true
            Log.d(TAG, "🎤 Recording started - buffer size: $bufferSize")

            // Recording thread with high priority for real-time audio
            recordingJob = scope.launch(Dispatchers.Default) {
                // Set high priority for audio thread
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                val buffer = ShortArray(INPUT_CHUNK_SIZE_SAMPLES)

                // Note: Barge-in mode is now dynamically enabled/disabled via setAiSpeaking()
                // This ensures proper tracking even when AI starts speaking during recording
                Log.d(TAG, "🎤 Recording loop started - VAD ready for barge-in detection")

                while (isRecording && isActive) {
                    try {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                        when {
                            readSize > 0 -> {
                                // Barge-in detection during AI speech
                                if (aiCurrentlySpeaking) {
                                    if (USE_SILERO_VAD) {
                                        // Use Silero VAD for accurate voice detection
                                        val vadResult = sileroVadEngine.processFrame(buffer, readSize)

                                        // Debug logging for VAD during barge-in mode
                                        if (vadResult.isSpeech) {
                                            Log.d(TAG, "🎤 VAD speech detected during AI talk: prob=${vadResult.probability}, reason=${vadResult.reason}")
                                        }

                                        // Check for barge-in event
                                        val bargeInEvent = sileroVadEngine.bargeInEvent.value
                                        if (bargeInEvent != null) {
                                            Log.d(TAG, "🧠 Silero VAD barge-in detected! Confidence: ${bargeInEvent.confidence}, Duration: ${bargeInEvent.durationMs}ms")
                                            onBargeInDetected?.invoke()
                                            sileroVadEngine.reset() // Reset after triggering
                                        }
                                    } else {
                                        // Fallback to adaptive detector
                                        val result = adaptiveBargeinDetector.processAudioFrame(
                                            buffer, readSize, INPUT_SAMPLE_RATE
                                        )
                                        if (result.shouldTrigger) {
                                            Log.d(TAG, "🧠 Adaptive barge-in triggered! Confidence: ${result.confidence}, Reason: ${result.reason}")
                                            onBargeInDetected?.invoke()
                                        }
                                    }
                                } else {
                                    // When AI not speaking: process VAD for user voice detection
                                    if (USE_SILERO_VAD) {
                                        sileroVadEngine.processFrame(buffer, readSize)
                                    }
                                    // Continue learning voice profile
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

                // Note: Barge-in mode is managed via setAiSpeaking(), not here
                Log.d(TAG, "🎤 Recording loop ended")
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

    private fun sendAccumulatedData() {
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
                    it.forEach { value -> buffer.putShort(value) }
                    val byteArray = buffer.array()
                    val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT or Base64.NO_WRAP)
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

    fun queueAudioForPlayback(audioBase64: String) {
        scope.launch {
            try {
                val arrayBuffer = Base64.decode(audioBase64, Base64.DEFAULT)

                // IMPORTANTE: Controllo overflow della coda
                val currentSize = totalQueueBytes.get()
                if (audioQueue.size >= MAX_QUEUE_SIZE || currentSize >= MAX_QUEUE_BYTES) {
                    Log.w(TAG, "⚠️ Audio queue full, dropping oldest chunks")
                    // Rimuovi i chunks più vecchi
                    while (audioQueue.size > MAX_QUEUE_SIZE / 2 && audioQueue.isNotEmpty()) {
                        val removed = audioQueue.removeAt(0)
                        totalQueueBytes.addAndGet(-removed.size)
                    }
                }

                audioQueue.add(arrayBuffer)
                totalQueueBytes.addAndGet(arrayBuffer.size)

                if (!isPlaying.get()) {
                    playNextAudioChunk()
                }

                Log.v(TAG, "✅ Queued audio: ${arrayBuffer.size} bytes (queue: ${audioQueue.size} chunks)")
            } catch (e: Exception) {
                Log.e(TAG, "Error queueing audio", e)
            }
        }
    }

    private fun playNextAudioChunk() {
        if (playbackJob?.isActive == true) {
            return // Già in riproduzione
        }

        playbackJob = scope.launch {
            while (audioQueue.isNotEmpty() && isActive) {
                isPlaying.set(true)
                _playbackState.value = true

                try {
                    // Prendi il prossimo chunk
                    val chunk = audioQueue.removeAt(0)
                    totalQueueBytes.addAndGet(-chunk.size)

                    playAudio(chunk)

                    // NUOVO: Calculate real-time AI audio level for visualizer with smoothing
                    val rawAiLevel = calculateAudioLevelFromBytes(chunk)
                    aiLevelSmoothing = aiLevelSmoothing * (1f - smoothingFactor) + rawAiLevel * smoothingFactor
                    _aiAudioLevel.value = aiLevelSmoothing

                } catch (e: Exception) {
                    Log.e(TAG, "Error playing audio chunk", e)
                }
            }

            isPlaying.set(false)
            _playbackState.value = false

            // SMOOTHING: Gradual fade-out for AI audio level
            scope.launch {
                while (aiLevelSmoothing > 0.01f) {
                    aiLevelSmoothing *= 0.85f // Exponential decay
                    _aiAudioLevel.value = aiLevelSmoothing
                    delay(20) // 50fps smooth fade
                }
                aiLevelSmoothing = 0f
                _aiAudioLevel.value = 0f
            }
        }
    }

    private suspend fun playAudio(byteArray: ByteArray) = withContext(Dispatchers.IO) {
        synchronized(audioTrackLock) {
            try {
                // Verifica/inizializza AudioTrack
                if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    initializeAudioTrack()
                }

                // IMPORTANTE: Ricontrolla dopo l'inizializzazione
                val track = audioTrack
                if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack not initialized properly")
                    return@withContext
                }

                // Scrivi audio
                var offset = 0
                val chunkSize = 1920 // 40ms a 24kHz

                while (offset < byteArray.size && isActive) {
                    val bytesToWrite = minOf(chunkSize, byteArray.size - offset)
                    val bytesWritten = track.write(byteArray, offset, bytesToWrite)

                    if (bytesWritten < 0) {
                        Log.e(TAG, "AudioTrack write error: $bytesWritten")
                        break
                    }

                    offset += bytesWritten
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in playAudio", e)
                // Reset AudioTrack in caso di errore
                releaseAudioTrack()
            }
        }
    }

    private fun initializeAudioTrack() {
        synchronized(audioTrackLock) {
            try {
                // Rilascia track esistente
                releaseAudioTrack()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    OUTPUT_SAMPLE_RATE,
                    AUDIO_CHANNEL_OUT,
                    AUDIO_ENCODING
                )

                if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid audio playback parameters")
                    return
                }

                // Buffer aumentato per audio più fluido: almeno 8x min o 1 secondo
                val bufferSize = maxOf(
                    minBufferSize * BUFFER_SIZE_MULTIPLIER,
                    OUTPUT_SAMPLE_RATE * 2 // Almeno 1 secondo di buffer
                )

                // USAGE_MEDIA with CONTENT_TYPE_SPEECH for speaker output
                // Note: USAGE_ASSISTANT routes to earpiece on many devices
                // USAGE_MEDIA ensures output through main speakers
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
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
                Log.d(TAG, "🔊 AudioTrack initialized - buffer: $bufferSize")

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

    // NUOVO: Metodo per gestire interruzioni da VAD
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

    // NUOVO: Configurazione audio per comunicazione full-duplex
    private fun configureAudioForCommunication() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true // Default speaker

            // NUOVO: Configurazione Bluetooth SCO se disponibile
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

    // NUOVO: Abilita effetti audio se disponibili
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

    // NUOVO: Calcola livello audio per barge-in
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

        // Release Silero VAD engine
        if (USE_SILERO_VAD) {
            sileroVadEngine.release()
            Log.d(TAG, "✅ Silero VAD engine released")
        }

        scope.cancel()
    }

    /**
     * Get Silero VAD metrics for debugging/analytics
     */
    fun getVadMetrics(): Map<String, Any> {
        return if (USE_SILERO_VAD) {
            sileroVadEngine.getStatistics()
        } else {
            emptyMap()
        }
    }

    /**
     * Get current speech probability from Silero VAD
     */
    fun getSpeechProbability(): Float {
        return if (USE_SILERO_VAD) {
            sileroVadEngine.speechProbability.value
        } else {
            0f
        }
    }

    /**
     * Check if user is currently speaking (VAD-based)
     */
    fun isUserSpeaking(): Boolean {
        return if (USE_SILERO_VAD) {
            sileroVadEngine.isSpeechDetected.value
        } else {
            _userAudioLevel.value > SPEECH_THRESHOLD
        }
    }
}