package com.lifo.chat.audio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AAA Audio Engine - Orchestratore Principale
 *
 * Engine audio di livello professionale che integra tutti i componenti:
 * - Lock-free Ring Buffer per producer/consumer decoupling
 * - Adaptive Jitter Buffer stile WebRTC NetEQ
 * - Packet Loss Concealment per mascherare gap
 * - High Priority Thread per playback real-time
 * - Metriche complete per monitoring
 *
 * Architettura ispirata a:
 * - Discord Voice Engine (4B+ voice minutes/day)
 * - Google Oboe / AAudio
 * - WebRTC audio pipeline
 * - Signal adaptive jitter buffer
 *
 * Target Performance:
 * - Latenza E2E < 150ms
 * - Jitter tolerance < 20ms
 * - Packet loss recovery > 99%
 * - Underrun rate < 0.01%
 *
 * @author Jarvis AI Assistant - AAA Audio Engine
 */
@Singleton
class AAAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AAAudioEngine"

        // Audio configuration
        const val SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2

        // Buffer sizes
        private const val RING_BUFFER_SECONDS = 3 // 3 secondi di buffer
        private const val RING_BUFFER_SIZE = SAMPLE_RATE * BYTES_PER_SAMPLE * RING_BUFFER_SECONDS

        // Playback chunk: 20ms @ 24kHz 16-bit mono = 960 bytes
        private const val PLAYBACK_CHUNK_SIZE = 960

        // Pre-buffering target
        private const val PRE_BUFFER_TARGET_MS = 100

        // Monitoring interval
        private const val METRICS_UPDATE_INTERVAL_MS = 100L
    }

    /**
     * Stato dell'engine
     */
    enum class EngineState {
        UNINITIALIZED,  // Non inizializzato
        INITIALIZED,     // Inizializzato, pronto
        BUFFERING,       // Accumulando buffer iniziale
        PLAYING,         // Riproduzione attiva
        PAUSED,          // In pausa
        RECOVERING,      // Recovery da problema
        ERROR,           // Errore
        RELEASED         // Rilasciato
    }

    /**
     * Configurazione engine
     */
    data class EngineConfig(
        val sampleRate: Int = SAMPLE_RATE,
        val preBufferTargetMs: Int = PRE_BUFFER_TARGET_MS,
        val minJitterBufferMs: Int = 50,
        val maxJitterBufferMs: Int = 300,
        val enablePLC: Boolean = true,
        val enableAdaptiveJitter: Boolean = true
    )

    /**
     * Callback per eventi engine
     */
    interface EngineCallback {
        fun onStateChanged(state: EngineState)
        fun onPlaybackStarted()
        fun onPlaybackStopped()
        fun onBufferingProgress(percent: Int)
        fun onUnderrun()
        fun onError(error: String)
        fun onAudioLevelChanged(level: Float)
    }

    // Componenti core
    private lateinit var ringBuffer: LockFreeAudioRingBuffer
    private lateinit var jitterBuffer: AdaptiveJitterBuffer
    private lateinit var plcEngine: PacketLossConcealmentEngine
    private var playbackThread: HighPriorityAudioThread? = null
    private var audioTrack: AudioTrack? = null

    // Metriche
    val metrics = AudioEngineMetrics()

    // Stato
    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Config
    private var config = EngineConfig()
    private var callback: EngineCallback? = null

    // Coroutine scope
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var metricsJob: Job? = null
    private var jitterToRingJob: Job? = null

    // Sequence tracking per jitter buffer
    private val packetSequence = AtomicLong(0)

    // Flags
    private val isInitialized = AtomicBoolean(false)
    private val preBufferingComplete = AtomicBoolean(false)

    /**
     * Inizializza l'engine con configurazione opzionale
     */
    fun initialize(config: EngineConfig = EngineConfig()): Boolean {
        if (isInitialized.get()) {
            Log.w(TAG, "⚠️ Engine già inizializzato")
            return true
        }

        Log.d(TAG, "🚀 Inizializzazione AAA Audio Engine...")
        this.config = config

        try {
            // 1. Crea Ring Buffer
            ringBuffer = LockFreeAudioRingBuffer(RING_BUFFER_SIZE)
            Log.d(TAG, "✅ Ring Buffer creato: ${RING_BUFFER_SIZE / 1024}KB")

            // 2. Crea Jitter Buffer
            jitterBuffer = AdaptiveJitterBuffer(
                sampleRate = config.sampleRate,
                minBufferMs = config.minJitterBufferMs,
                maxBufferMs = config.maxJitterBufferMs,
                targetBufferMs = config.preBufferTargetMs
            )
            Log.d(TAG, "✅ Jitter Buffer creato: ${config.minJitterBufferMs}-${config.maxJitterBufferMs}ms")

            // 3. Crea PLC Engine
            plcEngine = PacketLossConcealmentEngine(
                sampleRate = config.sampleRate,
                frameSize = PLAYBACK_CHUNK_SIZE / BYTES_PER_SAMPLE
            )
            Log.d(TAG, "✅ PLC Engine creato")

            // 4. Crea AudioTrack
            audioTrack = createAudioTrack()
            if (audioTrack == null) {
                throw IllegalStateException("Failed to create AudioTrack")
            }
            Log.d(TAG, "✅ AudioTrack creato")

            // 5. Crea Playback Thread
            playbackThread = HighPriorityAudioThread(
                ringBuffer = ringBuffer,
                audioTrack = audioTrack!!,
                plcEngine = plcEngine,
                chunkSizeBytes = PLAYBACK_CHUNK_SIZE
            ).apply {
                setCallback(createPlaybackCallback())
            }
            Log.d(TAG, "✅ Playback Thread creato")

            // 6. Avvia job trasferimento Jitter -> Ring
            startJitterToRingTransfer()

            // 7. Avvia metriche monitoring
            startMetricsMonitoring()

            isInitialized.set(true)
            _state.value = EngineState.INITIALIZED
            callback?.onStateChanged(EngineState.INITIALIZED)

            Log.d(TAG, "✅ AAA Audio Engine inizializzato con successo")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore inizializzazione", e)
            _state.value = EngineState.ERROR
            callback?.onError("Initialization failed: ${e.message}")
            return false
        }
    }

    /**
     * Crea AudioTrack con configurazione ottimale
     */
    private fun createAudioTrack(): AudioTrack? {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_ENCODING
        )

        if (minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            Log.e(TAG, "❌ Invalid AudioTrack parameters")
            return null
        }

        // Buffer size: 4x minimo per sicurezza
        val bufferSize = minBufferSize * 4

        // USAGE_MEDIA with CONTENT_TYPE_SPEECH for speaker output
        // Note: USAGE_ASSISTANT routes to earpiece on many devices
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AUDIO_ENCODING)
            .setChannelMask(CHANNEL_CONFIG)
            .build()

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also {
                    Log.d(TAG, "🔊 AudioTrack buffer: ${bufferSize / 1024}KB")
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create AudioTrack", e)
            null
        }
    }

    /**
     * Crea callback per playback thread
     */
    private fun createPlaybackCallback() = object : HighPriorityAudioThread.Callback {
        override fun onPlaybackStarted() {
            Log.d(TAG, "▶️ Playback avviato")
            _isPlaying.value = true
            _state.value = EngineState.PLAYING
            callback?.onPlaybackStarted()
            callback?.onStateChanged(EngineState.PLAYING)
        }

        override fun onPlaybackStopped() {
            Log.d(TAG, "⏹️ Playback fermato")
            _isPlaying.value = false
            callback?.onPlaybackStopped()
        }

        override fun onUnderrun(consecutiveCount: Int) {
            Log.w(TAG, "⚠️ Underrun #$consecutiveCount")
            if (consecutiveCount == 1) {
                callback?.onUnderrun()
            }
            if (consecutiveCount > 3) {
                _state.value = EngineState.RECOVERING
                callback?.onStateChanged(EngineState.RECOVERING)
            }
        }

        override fun onRecovery() {
            Log.d(TAG, "✅ Recovery da underrun")
            _state.value = EngineState.PLAYING
            callback?.onStateChanged(EngineState.PLAYING)
        }

        override fun onBufferLevelChanged(levelMs: Float) {
            // Aggiorna audio level per visualizzazione
            val normalizedLevel = (levelMs / 200f).coerceIn(0f, 1f)
            _audioLevel.value = normalizedLevel
            callback?.onAudioLevelChanged(normalizedLevel)
        }

        override fun onError(error: String) {
            Log.e(TAG, "❌ Playback error: $error")
            _state.value = EngineState.ERROR
            callback?.onError(error)
            callback?.onStateChanged(EngineState.ERROR)
        }
    }

    /**
     * Avvia trasferimento Jitter Buffer -> Ring Buffer
     *
     * IMPORTANTE: NON trasferire durante il pre-buffering!
     * Il pre-buffering deve accumulare dati nel jitter buffer.
     * Una volta completato, questo job inizia a svuotare il jitter buffer
     * verso il ring buffer, che viene consumato dal playback thread.
     *
     * Flow:
     * 1. WebSocket riceve audio → queueAudioBytes() → Jitter Buffer
     * 2. Pre-buffering: accumula nel Jitter Buffer fino a 100ms
     * 3. Pre-buffering completato → startPlayback() + questo job inizia
     * 4. Questo job trasferisce: Jitter Buffer → Ring Buffer
     * 5. Playback Thread consuma: Ring Buffer → AudioTrack
     */
    private fun startJitterToRingTransfer() {
        jitterToRingJob = engineScope.launch {
            // ASPETTA che il pre-buffering sia completato!
            // Questo è critico: se iniziamo a popolare dal jitter buffer subito,
            // lo svuotiamo prima che raggiunga il target di pre-buffering.
            Log.d(TAG, "⏳ Job trasferimento in attesa del pre-buffering...")

            while (isActive && !preBufferingComplete.get()) {
                delay(20)
            }

            if (!isActive) return@launch

            Log.d(TAG, "🚀 Pre-buffering completato, avvio trasferimento Jitter -> Ring")

            while (isActive) {
                try {
                    // Estrai pacchetti dal jitter buffer e scrivi nel ring buffer
                    val packet = jitterBuffer.popPacket()

                    if (packet != null) {
                        val written = ringBuffer.write(packet.data)
                        if (written < packet.data.size) {
                            // Overflow - il playback thread non sta consumando abbastanza veloce
                            if (written == 0) {
                                Log.w(TAG, "⚠️ Ring buffer pieno, packet dropped")
                                // Aspetta che il consumer recuperi
                                delay(10)
                            }
                        }

                        // Aggiorna PLC con dati buoni - processa frame per frame
                        // Il PLC si aspetta frame di 480 campioni (20ms @ 24kHz)
                        updatePLCWithPacket(packet.data)

                    } else {
                        // Nessun pacchetto disponibile nel jitter buffer
                        // Questo può succedere se la rete è lenta
                        delay(5)
                    }

                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "❌ Errore trasferimento jitter->ring", e)
                    }
                }
            }
        }
    }

    /**
     * Aggiorna PLC engine con dati buoni, processando frame per frame
     * Il PLC si aspetta frame di 480 campioni (20ms @ 24kHz)
     */
    private fun updatePLCWithPacket(data: ByteArray) {
        val samples = byteArrayToShortArray(data)
        val frameSize = 480 // 20ms @ 24kHz

        var offset = 0
        while (offset + frameSize <= samples.size) {
            val frame = ShortArray(frameSize)
            System.arraycopy(samples, offset, frame, 0, frameSize)
            try {
                plcEngine.processGoodFrame(frame)
            } catch (e: Exception) {
                // Ignora errori PLC, non sono critici per il playback
                Log.v(TAG, "PLC frame update skipped: ${e.message}")
            }
            offset += frameSize
        }
    }

    /**
     * Avvia monitoring metriche
     */
    private fun startMetricsMonitoring() {
        metricsJob = engineScope.launch {
            while (isActive) {
                try {
                    // Aggiorna metriche da tutti i componenti
                    metrics.updateRingBufferMetrics(ringBuffer.getStatistics())
                    metrics.updateJitterBufferMetrics(jitterBuffer.getStatistics())
                    metrics.updatePLCMetrics(plcEngine.getStatistics())
                    playbackThread?.let {
                        metrics.updatePlaybackMetrics(it.getStatistics())
                    }

                    delay(METRICS_UPDATE_INTERVAL_MS)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "❌ Errore aggiornamento metriche", e)
                    }
                }
            }
        }
    }

    /**
     * Accoda audio per riproduzione (da WebSocket)
     *
     * @param audioBase64 Audio PCM codificato in Base64
     * @return true se audio accettato
     */
    fun queueAudio(audioBase64: String): Boolean {
        if (!isInitialized.get()) {
            Log.w(TAG, "⚠️ Engine non inizializzato")
            return false
        }

        return try {
            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            queueAudioBytes(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore decodifica Base64", e)
            false
        }
    }

    /**
     * Accoda audio bytes per riproduzione
     *
     * @param audioBytes Raw PCM audio bytes
     * @return true se audio accettato
     */
    fun queueAudioBytes(audioBytes: ByteArray): Boolean {
        if (!isInitialized.get()) {
            Log.w(TAG, "⚠️ Engine non inizializzato")
            return false
        }

        try {
            // Aggiungi al jitter buffer con sequence number
            val seq = packetSequence.incrementAndGet()
            val accepted = jitterBuffer.pushPacket(
                data = audioBytes,
                sequenceNumber = seq,
                durationMs = calculateDurationMs(audioBytes.size)
            )

            // Verifica se buffering iniziale completato
            if (!preBufferingComplete.get()) {
                val bufferMs = jitterBuffer.currentBufferLevelMs()
                val progress = ((bufferMs / config.preBufferTargetMs) * 100).toInt().coerceIn(0, 100)
                callback?.onBufferingProgress(progress)

                if (bufferMs >= config.preBufferTargetMs) {
                    preBufferingComplete.set(true)
                    Log.d(TAG, "✅ Pre-buffering completato: ${bufferMs}ms")

                    // Avvia playback thread
                    startPlayback()
                } else {
                    _state.value = EngineState.BUFFERING
                }
            }

            return accepted
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore queue audio", e)
            return false
        }
    }

    /**
     * Calcola durata audio in ms
     */
    private fun calculateDurationMs(bytes: Int): Int {
        val samples = bytes / BYTES_PER_SAMPLE
        return (samples * 1000) / SAMPLE_RATE
    }

    /**
     * Avvia riproduzione
     */
    fun startPlayback() {
        if (!isInitialized.get()) {
            Log.w(TAG, "⚠️ Engine non inizializzato")
            return
        }

        if (playbackThread?.isAlive == true) {
            Log.d(TAG, "▶️ Playback già attivo")
            return
        }

        Log.d(TAG, "▶️ Avvio playback...")

        try {
            // Assicurati che AudioTrack sia pronto
            audioTrack?.play()

            // Avvia playback thread
            if (playbackThread?.isAlive != true) {
                // Ricrea thread se necessario
                playbackThread = HighPriorityAudioThread(
                    ringBuffer = ringBuffer,
                    audioTrack = audioTrack!!,
                    plcEngine = plcEngine,
                    chunkSizeBytes = PLAYBACK_CHUNK_SIZE
                ).apply {
                    setCallback(createPlaybackCallback())
                }
            }

            playbackThread?.start()
            _isPlaying.value = true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore avvio playback", e)
            callback?.onError("Start playback failed: ${e.message}")
        }
    }

    /**
     * Ferma riproduzione
     */
    fun stopPlayback() {
        Log.d(TAG, "⏹️ Stop playback...")

        playbackThread?.stopPlayback()
        audioTrack?.pause()
        audioTrack?.flush()

        _isPlaying.value = false
        _state.value = EngineState.PAUSED
        callback?.onStateChanged(EngineState.PAUSED)
    }

    /**
     * Mette in pausa
     */
    fun pause() {
        playbackThread?.pause()
        audioTrack?.pause()
        _isPlaying.value = false
        _state.value = EngineState.PAUSED
        callback?.onStateChanged(EngineState.PAUSED)
    }

    /**
     * Riprende da pausa
     */
    fun resume() {
        audioTrack?.play()
        playbackThread?.resumePlayback()
        _isPlaying.value = true
        _state.value = EngineState.PLAYING
        callback?.onStateChanged(EngineState.PLAYING)
    }

    /**
     * Flush tutti i buffer (per interruzione/barge-in)
     */
    fun flush() {
        Log.d(TAG, "🚿 Flush buffer...")

        jitterBuffer.flush()
        ringBuffer.flush()
        plcEngine.reset()

        preBufferingComplete.set(false)
        packetSequence.set(0)

        _state.value = EngineState.BUFFERING
        callback?.onStateChanged(EngineState.BUFFERING)
    }

    /**
     * Gestisce interruzione (barge-in)
     */
    fun handleInterruption() {
        Log.d(TAG, "⚠️ Handling interruption (barge-in)")

        // Stop playback
        playbackThread?.stopPlayback()

        // Flush buffers
        flush()

        // Pausa AudioTrack
        audioTrack?.pause()
        audioTrack?.flush()

        _isPlaying.value = false
    }

    /**
     * Imposta callback
     */
    fun setCallback(callback: EngineCallback) {
        this.callback = callback
    }

    /**
     * Ottiene report metriche
     */
    fun getMetricsReport(): String = metrics.generateReport()

    /**
     * Verifica se engine è pronto
     */
    fun isReady(): Boolean = isInitialized.get() && _state.value != EngineState.ERROR

    /**
     * Verifica se in riproduzione
     */
    fun isCurrentlyPlaying(): Boolean = _isPlaying.value

    /**
     * Rilascia risorse
     */
    fun release() {
        Log.d(TAG, "🧹 Rilascio AAA Audio Engine...")

        _state.value = EngineState.RELEASED

        // Stop jobs
        metricsJob?.cancel()
        jitterToRingJob?.cancel()

        // Stop playback
        playbackThread?.stopPlayback()
        playbackThread = null

        // Release AudioTrack
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Errore rilascio AudioTrack", e)
        }
        audioTrack = null

        // Reset buffers
        if (::ringBuffer.isInitialized) ringBuffer.reset()
        if (::jitterBuffer.isInitialized) jitterBuffer.reset()
        if (::plcEngine.isInitialized) plcEngine.reset()

        // Reset flags
        isInitialized.set(false)
        preBufferingComplete.set(false)

        // Cancel scope
        engineScope.cancel()

        callback?.onStateChanged(EngineState.RELEASED)

        Log.d(TAG, "✅ AAA Audio Engine rilasciato")
    }

    // ==================== Helper Methods ====================

    private fun byteArrayToShortArray(bytes: ByteArray): ShortArray {
        val samples = ShortArray(bytes.size / 2)
        for (i in samples.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt()
            samples[i] = ((high shl 8) or low).toShort()
        }
        return samples
    }
}
