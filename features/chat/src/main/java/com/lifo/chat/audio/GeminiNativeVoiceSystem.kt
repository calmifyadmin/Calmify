package com.lifo.chat.audio

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sistema di voce naturale utilizzando l'audio nativo di Gemini 2.5 Pro TTS
 * Basato sulla documentazione ufficiale Google
 */
@Singleton
open class GeminiNativeVoiceSystem @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "GeminiNativeVoice"

        // ✅ CORRECT: Configuration from official documentation
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MODEL_ID = "gemini-2.5-flash-preview-tts"
        private const val GENERATE_CONTENT_API = "streamGenerateContent"

        // Audio configuration for Android
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Voice names - only using documented voices
        private val VOICE_MAP = mapOf(
            Emotion.HAPPY to "Zephyr",
            Emotion.EXCITED to "Zephyr",
            Emotion.THOUGHTFUL to "Zephyr",
            Emotion.SAD to "Zephyr",
            Emotion.EMPATHETIC to "Zephyr",
            Emotion.NEUTRAL to "Zephyr",
            Emotion.CURIOUS to "Zephyr"
        )
    }

    // Core components
    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    protected var okHttpClient: OkHttpClient

    // State management
    protected val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Audio pipeline
    private val audioQueue = ConcurrentLinkedQueue<AudioChunk>()
    private var playbackJob: Job? = null
    private var currentStreamJob: Job? = null

    // API key
    private var apiKey: String = ""

    // Coroutine scope
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class VoiceState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val currentMessageId: String? = null,
        val currentSpeakingMessageId: String? = null,
        val currentVoice: String = "Zephyr",
        val latencyMs: Long = 0,
        val emotion: Emotion = Emotion.NEUTRAL,
        val naturalness: Float = 1.0f,
        val streamProgress: Float = 0f,
        val error: String? = null
    )

    enum class Emotion {
        NEUTRAL, HAPPY, SAD, EXCITED, THOUGHTFUL, EMPATHETIC, CURIOUS
    }

    data class AudioChunk(
        val data: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    )

    open suspend fun initialize(apiKey: String = "") = withContext(Dispatchers.Main) {
        Log.d(TAG, "🎙️ Initializing Gemini Native Voice System")
        Log.d(TAG, "🔑 API Key length: ${apiKey.length}")

        this@GeminiNativeVoiceSystem.apiKey = apiKey

        if (apiKey.isEmpty()) {
            Log.e(TAG, "❌ API key is empty!")
            _voiceState.update { it.copy(error = "API key not configured") }
            return@withContext
        }

        try {
            // Request audio focus
            requestAudioFocus()

            // Initialize AudioTrack
            initializeAudioTrack()

            // Start audio playback pipeline
            startAudioPlayback()

            _voiceState.update {
                it.copy(isInitialized = true, error = null)
            }
            Log.d(TAG, "✅ Gemini Voice System initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed", e)
            _voiceState.update {
                it.copy(error = "Initialization failed: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> audioTrack?.play()
                    AudioManager.AUDIOFOCUS_LOSS -> audioTrack?.pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> audioTrack?.pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioTrack?.setVolume(0.3f)
                }
            }
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        Log.d(TAG, "🔊 Audio focus request result: $result")
    }

    private fun initializeAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                throw IllegalStateException("AudioTrack configuration not supported")
            }

            val bufferSize = minBufferSize * 4
            Log.d(TAG, "🔊 AudioTrack buffer size: $bufferSize")

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            audioTrack?.play()
            Log.d(TAG, "✅ AudioTrack initialized and playing")

            // Check device volume
            checkDeviceVolume()

        } catch (e: Exception) {
            Log.e(TAG, "❌ AudioTrack initialization error", e)
            throw e
        }
    }

    private fun checkDeviceVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        Log.d(TAG, "🔊 Device volume: $currentVolume/$maxVolume")

        if (currentVolume == 0) {
            Log.w(TAG, "⚠️ Device volume is muted!")
        }
    }

    open fun speakWithEmotion(
        text: String,
        emotion: Emotion = Emotion.NEUTRAL,
        messageId: String
    ) {
        Log.d(TAG, "🎤 Speaking: ${text.take(50)}... [Emotion: $emotion]")

        if (!_voiceState.value.isInitialized) {
            Log.e(TAG, "❌ Voice system not initialized")
            return
        }

        // Cancel any existing stream
        currentStreamJob?.cancel()
        audioQueue.clear()

        currentStreamJob = scope.launch {
            val startTime = System.currentTimeMillis()

            _voiceState.update {
                it.copy(
                    isSpeaking = true,
                    currentMessageId = messageId,
                    currentSpeakingMessageId = messageId,
                    emotion = emotion,
                    streamProgress = 0f,
                    error = null
                )
            }

            try {
                // Create and execute request
                streamAudioFromGemini(text, emotion)

                // Calculate latency
                val latency = System.currentTimeMillis() - startTime
                _voiceState.update { it.copy(latencyMs = latency) }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in speech generation", e)
                _voiceState.update {
                    it.copy(
                        error = e.message,
                        isSpeaking = false
                    )
                }
            }
        }
    }

    private suspend fun streamAudioFromGemini(
        text: String,
        emotion: Emotion
    ) = withContext(Dispatchers.IO) {
        // Reduce latency by using lower temperature for shorter texts
        val temperature = when {
            text.length < 50 -> when (emotion) {
                Emotion.EXCITED -> 0.8
                Emotion.THOUGHTFUL -> 0.3
                else -> 0.5
            }
            else -> when (emotion) {
                Emotion.EXCITED -> 1.2
                Emotion.HAPPY -> 1.0
                Emotion.THOUGHTFUL -> 0.7
                Emotion.SAD -> 0.6
                else -> 1.0
            }
        }

        // ✅ EXACT structure from documentation
        val payload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", text)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("audio")
                })
                put("temperature", temperature)
                put("speech_config", JSONObject().apply {
                    put("voice_config", JSONObject().apply {
                        put("prebuilt_voice_config", JSONObject().apply {
                            put("voice_name", "Zephyr")
                        })
                    })
                })
            })
        }

        val url = "$GEMINI_API_URL$MODEL_ID:$GENERATE_CONTENT_API?key=$apiKey"
        Log.d(TAG, "🌐 Request URL: $url")
        Log.d(TAG, "📦 Request payload: ${payload.toString(2)}")

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            val startTime = System.currentTimeMillis()

            okHttpClient.newCall(request).execute().use { response ->
                val networkTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "📡 Response code: ${response.code} (Network time: ${networkTime}ms)")

                if (!response.isSuccessful) {
                    val error = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "❌ API Error: ${response.code} - $error")
                    throw IOException("API Error ${response.code}: $error")
                }

                response.body?.let { body ->
                    processStreamingResponse(body)
                } ?: throw IOException("Empty response")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Streaming error", e)
            throw e
        }
    }

    private suspend fun processStreamingResponse(body: ResponseBody) {
        try {
            // Read the entire response as text first
            val responseText = body.string()
            Log.d(TAG, "📄 Full response length: ${responseText.length}")

            // Log first 500 chars for debugging
            Log.d(TAG, "📄 Response preview: ${responseText.take(500)}...")

            var audioDataBuffer = StringBuilder()
            var totalAudioReceived = 0

            try {
                // Parse as JSON array
                val jsonArray = JSONArray(responseText)
                Log.d(TAG, "📦 Parsing JSON array with ${jsonArray.length()} elements")

                // Process each element in the array
                for (idx in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(idx)

                    if (json.has("candidates")) {
                        val candidates = json.getJSONArray("candidates")

                        for (i in 0 until candidates.length()) {
                            val candidate = candidates.getJSONObject(i)

                            if (candidate.has("content")) {
                                val content = candidate.getJSONObject("content")

                                if (content.has("parts")) {
                                    val parts = content.getJSONArray("parts")

                                    for (j in 0 until parts.length()) {
                                        val part = parts.getJSONObject(j)

                                        // Check for inline audio data
                                        if (part.has("inlineData")) {
                                            val inlineData = part.getJSONObject("inlineData")
                                            val mimeType = inlineData.optString("mimeType", "")
                                            val data = inlineData.optString("data", "")

                                            Log.d(TAG, "🎵 Found audio data: MIME=$mimeType, Size=${data.length}")

                                            if (mimeType.contains("audio") && data.isNotEmpty()) {
                                                // Process audio data immediately
                                                processAudioData(data)
                                                totalAudioReceived += data.length

                                                // Update progress
                                                val progress = (idx + 1).toFloat() / jsonArray.length()
                                                _voiceState.update {
                                                    it.copy(streamProgress = progress)
                                                }
                                            }
                                        }

                                        // Also check for text response
                                        if (part.has("text")) {
                                            val text = part.getString("text")
                                            Log.d(TAG, "📝 Text response: ${text.take(100)}")
                                        }
                                    }
                                }
                            }

                            // Check finish reason
                            if (candidate.has("finishReason")) {
                                val finishReason = candidate.getString("finishReason")
                                Log.d(TAG, "✅ Finish reason: $finishReason")
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error parsing JSON array", e)

                // Try parsing as single object if array parsing fails
                try {
                    val json = JSONObject(responseText)
                    Log.d(TAG, "📦 Parsing as single JSON object")
                    processSingleJsonResponse(json)
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Error parsing as single JSON object", e2)
                    throw e
                }
            }

            Log.d(TAG, "✅ Stream complete. Total audio data received: $totalAudioReceived bytes")
            _voiceState.update { it.copy(streamProgress = 1f) }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing stream", e)
            throw e
        }
    }

    private fun processSingleJsonResponse(json: JSONObject) {
        if (json.has("candidates")) {
            val candidates = json.getJSONArray("candidates")

            for (i in 0 until candidates.length()) {
                val candidate = candidates.getJSONObject(i)

                if (candidate.has("content")) {
                    val content = candidate.getJSONObject("content")

                    if (content.has("parts")) {
                        val parts = content.getJSONArray("parts")

                        for (j in 0 until parts.length()) {
                            val part = parts.getJSONObject(j)

                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                val data = inlineData.optString("data", "")

                                Log.d(TAG, "🎵 Found audio data in single response: MIME=$mimeType, Size=${data.length}")

                                if (mimeType.contains("audio") && data.isNotEmpty()) {
                                    processAudioData(data)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processAudioData(base64Data: String) {
        try {
            // Decode base64 to raw audio bytes
            val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
            Log.d(TAG, "🎵 Decoded audio: ${audioBytes.size} bytes")

            // Gemini returns 16-bit PCM at 24kHz which matches our AudioTrack config
            // Add directly to playback queue
            audioQueue.offer(AudioChunk(audioBytes))

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error decoding audio data", e)
        }
    }

    private fun startAudioPlayback() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            Log.d(TAG, "🔊 Audio playback pipeline started")

            var totalBytesPlayed = 0
            var consecutiveEmptyChecks = 0

            while (isActive) {
                val chunk = audioQueue.poll()

                if (chunk != null) {
                    consecutiveEmptyChecks = 0
                    try {
                        val written = audioTrack?.write(chunk.data, 0, chunk.data.size) ?: 0

                        if (written > 0) {
                            totalBytesPlayed += written
                            Log.d(TAG, "🔊 Played $written bytes (total: $totalBytesPlayed)")
                        } else if (written < 0) {
                            Log.e(TAG, "❌ AudioTrack write error: $written")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Playback error", e)
                    }
                } else {
                    // Only mark as complete if stream is done AND queue has been empty for a bit
                    if (_voiceState.value.streamProgress >= 1f) {
                        consecutiveEmptyChecks++

                        // Wait for 100ms of empty queue before marking complete
                        if (consecutiveEmptyChecks > 10) {
                            if (_voiceState.value.isSpeaking) {
                                Log.d(TAG, "🔇 Playback complete")
                                _voiceState.update {
                                    it.copy(
                                        isSpeaking = false,
                                        currentSpeakingMessageId = null
                                    )
                                }
                            }
                        }
                    }
                    delay(10)
                }
            }

            Log.d(TAG, "🔊 Audio playback pipeline stopped")
        }
    }

    open fun stop() {
        Log.d(TAG, "🛑 Stopping voice")
        currentStreamJob?.cancel()
        audioQueue.clear()
        audioTrack?.flush()

        _voiceState.update {
            it.copy(
                isSpeaking = false,
                currentMessageId = null,
                currentSpeakingMessageId = null,
                streamProgress = 0f
            )
        }
    }

    open fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up")
        stop()
        playbackJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()

        audioFocusRequest?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }

        scope.cancel()
    }
}