package com.lifo.chat.data.audio

import android.annotation.SuppressLint
import android.media.*
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLiveAudioManager @Inject constructor() {

    companion object {
        private const val TAG = "GeminiAudioManager"
        private const val AUDIO_SAMPLE_RATE = 24000
        private const val RECEIVE_SAMPLE_RATE = 24000
        private const val AUDIO_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // OTTIMIZZAZIONE: Chunk più grandi per ridurre frammentazione
        private const val CHUNK_SIZE_SAMPLES = 4800 // 200ms di audio invece di 26ms
        private const val SEND_INTERVAL_MS = 100L // Invia ogni 100ms invece che immediatamente
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val pcmData = Collections.synchronizedList(mutableListOf<Short>())

    private val _recordingState = MutableStateFlow(false)
    val recordingState: StateFlow<Boolean> = _recordingState

    private val _playbackState = MutableStateFlow(false)
    val playbackState: StateFlow<Boolean> = _playbackState

    private val audioQueue = Collections.synchronizedList(mutableListOf<ByteArray>())
    private var isPlaying = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onAudioChunkReady: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // OTTIMIZZAZIONE: Buffer più grande
        val minBufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_CONFIG,
            AUDIO_ENCODING
        )
        val bufferSize = minBufferSize * 4 // Buffer 4x più grande

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            AUDIO_SAMPLE_RATE,
            AUDIO_CHANNEL_CONFIG,
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

        // Thread di registrazione
        recordingJob = scope.launch {
            val buffer = ShortArray(CHUNK_SIZE_SAMPLES)

            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readSize > 0) {
                    synchronized(pcmData) {
                        pcmData.addAll(buffer.take(readSize).toList())
                    }
                }
            }
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
            if (pcmData.size >= CHUNK_SIZE_SAMPLES) {
                val copy = pcmData.take(CHUNK_SIZE_SAMPLES).toList()
                repeat(CHUNK_SIZE_SAMPLES.coerceAtMost(pcmData.size)) {
                    pcmData.removeAt(0)
                }
                copy
            } else if (pcmData.isNotEmpty()) {
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

    fun queueAudioForPlayback(audioBase64: String) {
        scope.launch {
            try {
                val arrayBuffer = Base64.decode(audioBase64, Base64.DEFAULT)
                audioQueue.add(arrayBuffer)

                if (!isPlaying) {
                    playNextAudioChunk()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing chunk", e)
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
            val bufferSize = AudioTrack.getMinBufferSize(
                RECEIVE_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2 // Buffer 2x

            audioTrack?.release()

            // OTTIMIZZAZIONE: Usa AudioAttributes per migliore qualità
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(RECEIVE_SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        }

        // Scrivi in chunk per evitare underrun
        var offset = 0
        val chunkSize = 2048
        while (offset < byteArray.size) {
            val bytesToWrite = minOf(chunkSize, byteArray.size - offset)
            audioTrack?.write(byteArray, offset, bytesToWrite)
            offset += bytesToWrite
        }
    }

    fun stopPlayback() {
        isPlaying = false
        _playbackState.value = false

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        audioQueue.clear()
    }

    fun release() {
        stopRecording()
        stopPlayback()
        scope.cancel()
    }
}