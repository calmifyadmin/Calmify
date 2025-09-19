package com.lifo.chat.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.*
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresPermission
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

        // NUOVO: Limiti per evitare overflow
        private const val MAX_QUEUE_SIZE = 50 // Max chunks in coda
        private const val MAX_QUEUE_BYTES = 500_000 // ~500KB max

        // NUOVO: Barge-in detection
        private const val SPEECH_THRESHOLD = 0.08f // Soglia per rilevare voce
        private const val HOT_FRAMES_TO_BARGE = 4 // Frame consecutivi per barge-in (~80ms)
    }

    // ===== NUOVO: Echo gate playback-aware fields =====
    // Manteniamo ~500 ms di TTS riprodotto (a 24 kHz = 12k campioni mono)
    private val playbackMirror = ShortRingBuffer(capacitySamples = 12_000)
    private val tmpShortBuf = ThreadLocal.withInitial { ShortArray(12_000) }

    // Attenuazione TTS (~ -6 dB) via volume dell'AudioTrack
    private var ttsVolume = 0.6f
    // ===== Fine campi echo gate =====

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
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    // ===== NUOVO: Ring Buffer class =====
    private class ShortRingBuffer(private val capacitySamples: Int) {
        private val data = ShortArray(capacitySamples)
        private var writePos = 0
        private var filled = 0
        fun write(src: ShortArray, n: Int) {
            var i = 0
            while (i < n) {
                data[writePos] = src[i]
                writePos = (writePos + 1) % capacitySamples
                if (filled < capacitySamples) filled++
                i++
            }
        }
        fun copyRecent(dst: ShortArray, n: Int): Int {
            val take = minOf(n, filled)
            if (take == 0) return 0
            var start = (writePos - take + capacitySamples) % capacitySamples
            var i = 0
            while (i < take) {
                dst[i] = data[start]
                start = (start + 1) % capacitySamples
                i++
            }
            return take
        }
    }

    // ===== NUOVO: Helper functions per echo gate =====
    // Converte PCM16LE (byte) in short[] e scrive nel mirror
    private fun mirrorPlaybackPcm16Le(pcm: ByteArray, length: Int) {
        val nShorts = length / 2
        val buf = tmpShortBuf.get()
        if (buf!!.size < nShorts) return
        var i = 0; var j = 0
        while (i + 1 < length && j < nShorts) {
            val s = (((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)) and 0xFFFF).toShort()
            buf[j++] = s
            i += 2
        }
        playbackMirror.write(buf, nShorts)
    }

    private fun downsample24kTo16k(src: ShortArray, n: Int, dst: ShortArray): Int {
        // 24k/16k = 1.5 → dstLen ≈ n * 2 / 3, pick src[(i*3)/2]
        val dstLen = (n * 2) / 3
        var i = 0
        while (i < dstLen) {
            val srcIndex = (i * 3) / 2
            dst[i] = src[srcIndex]
            i++
        }
        return dstLen
    }

    private fun cosineSimilarity(a: ShortArray, na: Int, b: ShortArray, nb: Int): Float {
        val n = minOf(na, nb)
        if (n == 0) return 0f
        var dot = 0.0
        var na2 = 0.0
        var nb2 = 0.0
        var i = 0
        while (i < n) {
            val ax = a[i].toInt()
            val bx = b[i].toInt()
            dot += ax * bx
            na2 += ax * ax
            nb2 += bx * bx
            i++
        }
        if (na2 == 0.0 || nb2 == 0.0) return 0f
        return (dot / (Math.sqrt(na2) * Math.sqrt(nb2))).toFloat()
    }

    private fun shouldUploadFrameIfAiSpeaking(micPcm16: ShortArray, n: Int): Boolean {
        if (!aiCurrentlySpeaking) return true

        // Prendi ~200 ms dal mirror: 200 ms @ 24k = 4800 samples
        val scratch = tmpShortBuf.get()
        val got24k = playbackMirror.copyRecent(scratch!!, 4_800)
        if (got24k <= 0) {
            // prudenziale: se manca ref, NON inviare
            return false
        }

        // Downsample 24k→16k per matchare la cattura
        val ds = ShortArray((got24k * 2) / 3)
        val got16k = downsample24kTo16k(scratch, got24k, ds)

        val take = minOf(n, got16k)
        val sim = cosineSimilarity(micPcm16, take, ds, take)

        val isEcho = sim >= 0.60f
        if (isEcho) Log.v(TAG, "🛑 Echo gate: similarity=${"%.2f".format(sim)} → DROP")
        return !isEcho
    }

    // NUOVO: Funzione per notificare quando l'AI sta parlando
    fun setAiSpeaking(speaking: Boolean) {
        aiCurrentlySpeaking = speaking
        if (!speaking) {
            hotFrameCount = 0 // Reset barge-in counter
        }
    }

    // NUOVO: Start voice learning per calibrazione iniziale
    fun startVoiceLearning() {
        Log.d(TAG, "🎓 Starting adaptive voice learning")
        adaptiveBargeinDetector.startVoiceLearning()
    }

    private fun applyInputAudioEffects(sessionId: Int,
                                       enableAec: Boolean = true,
                                       enableNs: Boolean = false,
                                       enableAgc: Boolean = false) {
        // Pulisci eventuali effetti precedenti
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() };  ns  = null
        runCatching { agc?.release() }; agc = null

        // AEC consigliato (se disponibile)
        aec = if (enableAec && AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        } else null

        // NS/AGC sconsigliati in questo scenario (li lasciamo OFF)
        ns = if (enableNs && NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        } else null

        agc = if (enableAgc && AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(sessionId)?.apply { enabled = true }
        } else null

        android.util.Log.d(TAG, "🎛️ Effects: AEC=${aec?.enabled==true} NS=${ns?.enabled==true} AGC=${agc?.enabled==true} (session=$sessionId)")
    }

    private fun releaseInputAudioEffects() {
        runCatching { aec?.release() }; aec = null
        runCatching { ns?.release() };  ns  = null
        runCatching { agc?.release() }; agc = null
    }

    // NUOVO: Get detection stats per analytics
    fun getAdaptiveDetectionStats(): Map<String, Any> {
        return adaptiveBargeinDetector.getDetectionStats()
    }

    // ===== MODIFICATO: sendAccumulatedData con echo gate =====
    private fun sendAccumulatedData() {
        val dataCopy: List<Short>? = synchronized(pcmData) {
            if (pcmData.size >= INPUT_CHUNK_SIZE_SAMPLES) {
                val copy = pcmData.take(INPUT_CHUNK_SIZE_SAMPLES).toList()
                repeat(INPUT_CHUNK_SIZE_SAMPLES.coerceAtMost(pcmData.size)) { pcmData.removeAt(0) }
                copy
            } else null
        }

        dataCopy?.let { shortList ->
            scope.launch {
                try {
                    // (1) Converte in byte (PCM16LE) e short[] per il gate
                    val n = shortList.size
                    val micShorts = ShortArray(n)
                    for (i in 0 until n) micShorts[i] = shortList[i]

                    // ⛔ Echo gate: se AI parla e il frame è simile al TTS → DROP
                    if (aiCurrentlySpeaking && !shouldUploadFrameIfAiSpeaking(micShorts, n)) {
                        Log.v(TAG, "⏸️ Upload blocked by echo gate ($n samples)")
                        return@launch
                    }

                    // (2) OK: serializza e invia
                    val buffer = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until n) buffer.putShort(micShorts[i])
                    val byteArray = buffer.array()
                    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                    onAudioChunkReady?.invoke(base64)
                    Log.v(TAG, "📤 Uploaded ${byteArray.size} bytes")

                } catch (e: Exception) {
                    Log.e(TAG, "Error sending audio", e)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // Config: full-duplex assistant (mode NORMAL, niente profilo chiamata)
        configureAudioForCommunication()

        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AUDIO_CHANNEL_IN,
            AUDIO_ENCODING
        )

        if (minBufferSize <= 0 || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid audio recording parameters (minBufferSize=$minBufferSize)")
            return
        }

        val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

        try {
            val rec = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION) // consigliato per assistant
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_ENCODING)
                        .setSampleRate(INPUT_SAMPLE_RATE)
                        .setChannelMask(AUDIO_CHANNEL_IN)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed (state=${rec.state})")
                rec.release()
                return
            }

            // Effetti input: AEC ON, NS/AGC OFF (evita auto-ascolto; VAD lato server)
            applyInputAudioEffects(
                sessionId = rec.audioSessionId,
                enableAec = true,
                enableNs = false,
                enableAgc = false
            )

            rec.startRecording()
            audioRecord = rec
            isRecording = true
            _recordingState.value = true

            Log.i(
                TAG,
                "🎤 Recording started | sr=$INPUT_SAMPLE_RATE ch=mono enc=$AUDIO_ENCODING " +
                        "buf=$bufferSize session=${rec.audioSessionId}"
            )

            // Thread di cattura
            recordingJob = scope.launch {
                val buffer = ShortArray(INPUT_CHUNK_SIZE_SAMPLES)

                while (isRecording && isActive) {
                    try {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readSize > 0) {
                            // Adaptive barge-in: apprendimento continuo + trigger se AI parla
                            if (aiCurrentlySpeaking) {
                                val result = adaptiveBargeinDetector.processAudioFrame(
                                    buffer, readSize, INPUT_SAMPLE_RATE
                                )
                                if (result.shouldTrigger) {
                                    Log.d(
                                        TAG,
                                        "🧠 Adaptive barge-in TRIGGER | conf=${result.confidence} reason=${result.reason}"
                                    )
                                    onBargeInDetected?.invoke()
                                }
                            } else {
                                adaptiveBargeinDetector.processAudioFrame(
                                    buffer, readSize, INPUT_SAMPLE_RATE
                                )
                            }

                            // Meter con smoothing
                            val rawLevel = calculateAudioLevel(buffer, readSize)
                            userLevelSmoothing =
                                userLevelSmoothing * (1f - smoothingFactor) + rawLevel * smoothingFactor
                            _userAudioLevel.value = userLevelSmoothing

                            // Accumulo PCM per invio
                            synchronized(pcmData) {
                                if (pcmData.size < 10_000) {
                                    // evita allocazioni inutili
                                    for (i in 0 until readSize) pcmData.add(buffer[i])
                                }
                            }
                        } else if (readSize == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "AudioRecord ERROR_INVALID_OPERATION")
                            break
                        } else if (readSize == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord ERROR_BAD_VALUE")
                            break
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in recording loop", t)
                        break
                    }
                }
            }

            // Thread di invio (sempre-on; VAD lato server decide cosa usare)
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

    fun stopRecording() {
        Log.d(TAG, "Stopping recording...")

        isRecording = false
        _recordingState.value = false

        // Fade-out del livello utente (solo UI)
        scope.launch {
            while (userLevelSmoothing > 0.01f) {
                userLevelSmoothing *= 0.85f
                _userAudioLevel.value = userLevelSmoothing
                delay(20)
            }
            userLevelSmoothing = 0f
            _userAudioLevel.value = 0f
        }

        // Chiudi job di cattura
        try {
            recordingJob?.cancel()
            recordingJob = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling recording job", e)
        }

        // Ferma e rilascia AudioRecord + effetti
        try {
            audioRecord?.let { rec ->
                runCatching { rec.stop() }
                runCatching { rec.release() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            // importantissimo: rilascia gli effetti legati alla sessione
            releaseInputAudioEffects()
        }

        synchronized(pcmData) {
            pcmData.clear()
        }

        Log.i(TAG, "🎤 Recording stopped")
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

    // ===== MODIFICATO: playAudio con mirror del TTS =====
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

                // ===== NUOVO: Mirror del TTS prima di scrivere =====
                mirrorPlaybackPcm16Le(byteArray, byteArray.size)

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

    // ───────────────────────────────────────────────────────────────────────────────
    // 1) Playback: AudioTrack con USAGE_ASSISTANT (no profilo chiamata)
    // ───────────────────────────────────────────────────────────────────────────────
    // ===== MODIFICATO: initializeAudioTrack con volume attenuato =====
    private fun initializeAudioTrack() {
        synchronized(audioTrackLock) {
            try {
                // Rilascia un'eventuale track esistente
                releaseAudioTrack()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    OUTPUT_SAMPLE_RATE,
                    AUDIO_CHANNEL_OUT,
                    AUDIO_ENCODING
                )
                if (minBufferSize == AudioTrack.ERROR_BAD_VALUE || minBufferSize <= 0) {
                    Log.e(TAG, "Invalid audio playback parameters (minBufferSize=$minBufferSize)")
                    return
                }

                val bufferSize = (minBufferSize * BUFFER_SIZE_MULTIPLIER).coerceAtLeast(minBufferSize)

                // Uscita Assistant: l'OS gestisce policy/ducking corretti per un assistente
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

                // ===== NUOVO: Attenuazione TTS di ~-6dB =====
                audioTrack?.setVolume(ttsVolume)

                Log.d(TAG, "🔊 AudioTrack initialized (usage=ASSISTANT) - buffer: $bufferSize, volume: $ttsVolume")

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
    // ───────────────────────────────────────────────────────────────────────────────
    // 2) Config di sistema: niente profilo chiamata, niente speaker forzato
    // ───────────────────────────────────────────────────────────────────────────────
    private fun configureAudioForCommunication() {
        try {
            // Evita la pipeline telefonica: lasciamo la policy lavorare con USAGE_ASSISTANT
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false

            // Non forziamo SCO; se l'utente lo richiede esplicitamente, chiameremo configureBluetooth(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                configureBluetooth() // default preferSco = false
            }

            Log.d(TAG, "🔊 Audio configured (mode=NORMAL, speaker=false, usage=ASSISTANT)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure audio", e)
        }
    }

    // ───────────────────────────────────────────────────────────────────────────────
    // 3) Bluetooth: selezione SCO solo su richiesta esplicita (preferSco = true)
    // ───────────────────────────────────────────────────────────────────────────────
    @Suppress("NewApi")
    private fun configureBluetooth(preferSco: Boolean = false) {
        try {
            if (!preferSco) {
                Log.d(TAG, "🎧 Bluetooth not forced (preferSco=false)")
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val scoDevice = audioManager
                    .availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

                if (scoDevice != null) {
                    audioManager.setCommunicationDevice(scoDevice)
                    Log.d(TAG, "🎧 SCO forced by user preference")
                } else {
                    Log.d(TAG, "⚠️ No BLUETOOTH_SCO device available")
                }
            } else {
                // Su versioni < S evitiamo startBluetoothSco()/MODE_IN_COMMUNICATION (pipeline telefonica)
                Log.d(TAG, "ℹ️ Pre-Android S: SCO not forced to avoid telephony pipeline")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure Bluetooth", e)
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
        scope.cancel()
    }
}