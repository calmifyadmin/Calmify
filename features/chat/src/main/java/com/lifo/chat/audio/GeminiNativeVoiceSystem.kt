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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIXED V6:
 * - NO endian conversion (data is already Little Endian)
 * - Create fresh AudioTrack just before playback to avoid underrun
 */
@Singleton
open class GeminiNativeVoiceSystem @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "GeminiNativeVoice"

        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val MODEL_ID = "gemini-2.5-flash-preview-tts"
        private const val GENERATE_CONTENT_API = "streamGenerateContent"

        // Audio: PCM 16-bit, 24kHz, mono (Little Endian for Android)
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private val VOICE_MAP = mapOf(
            Emotion.HAPPY to "Puck",
            Emotion.EXCITED to "Fenrir",
            Emotion.THOUGHTFUL to "Charon",
            Emotion.SAD to "Enceladus",
            Emotion.EMPATHETIC to "Aoede",
            Emotion.NEUTRAL to "Kore",
            Emotion.CURIOUS to "Zephyr"
        )
    }

    private var audioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    protected var okHttpClient: OkHttpClient

    protected val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var currentStreamJob: Job? = null
    private var apiKey: String = ""
    protected val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    enum class PlaybackState {
        IDLE,           // No audio playing
        STARTED,        // AudioTrack.play() called, playback beginning
        PLAYING,        // Actively writing/playing audio data
        FINISHING,      // All data written, waiting for buffer to drain
        ENDED           // Playback complete
    }

    data class VoiceState(
        val isInitialized: Boolean = false,
        val isSpeaking: Boolean = false,
        val currentMessageId: String? = null,
        val currentSpeakingMessageId: String? = null,
        val currentVoice: String = "Kore",
        val latencyMs: Long = 0,
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val totalBytesPlayed: Int = 0,
        val estimatedDurationMs: Long = 0,
        val emotion: Emotion = Emotion.NEUTRAL,
        val naturalness: Float = 1.0f,
        val streamProgress: Float = 0f,
        val error: String? = null
    )

    enum class Emotion {
        NEUTRAL, HAPPY, SAD, EXCITED, THOUGHTFUL, EMPATHETIC, CURIOUS
    }

    open suspend fun initialize(apiKey: String = "") = withContext(Dispatchers.Main) {
        Log.d(TAG, "🎙️ Initializing Gemini Native Voice System V6")

        this@GeminiNativeVoiceSystem.apiKey = apiKey

        if (apiKey.isEmpty()) {
            Log.e(TAG, "❌ API key is empty!")
            _voiceState.update { it.copy(error = "API key not configured") }
            return@withContext
        }

        try {
            requestAudioFocus()
            checkDeviceVolume()

            _voiceState.update {
                it.copy(isInitialized = true, error = null)
            }
            Log.d(TAG, "✅ Gemini Voice System V6 initialized")

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
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        Log.d(TAG, "🔊 Audio focus: $result (1=GRANTED)")
    }

    /**
     * Create a FRESH AudioTrack right before playback
     */
    private fun createFreshAudioTrack(): AudioTrack {
        // Release old track if exists
        audioTrack?.release()

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        Log.d(TAG, "🔊 Creating fresh AudioTrack, minBuffer=$minBufferSize")

        val bufferSize = maxOf(minBufferSize * 4, SAMPLE_RATE * 2) // At least 1 sec buffer

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

        Log.d(TAG, "🔊 AudioTrack created, state=${track.state}")

        audioTrack = track
        return track
    }

    private fun checkDeviceVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        Log.d(TAG, "🔊 Device volume: $currentVolume/$maxVolume")

        if (currentVolume == 0) {
            Log.w(TAG, "⚠️ VOLUME IS ZERO!")
        }
    }

    open fun speakWithEmotion(
        text: String,
        emotion: Emotion = Emotion.NEUTRAL,
        messageId: String
    ) {
        Log.d(TAG, "🎤 Speaking: ${text.take(50)}... [Emotion: $emotion]")

        if (!_voiceState.value.isInitialized) {
            Log.e(TAG, "❌ Not initialized!")
            return
        }

        currentStreamJob?.cancel()

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
                streamAudioFromGemini(text, emotion)

                val latency = System.currentTimeMillis() - startTime
                _voiceState.update { it.copy(latencyMs = latency, streamProgress = 1f) }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error", e)
                _voiceState.update { it.copy(error = e.message) }
            } finally {
                _voiceState.update {
                    it.copy(
                        isSpeaking = false,
                        currentSpeakingMessageId = null
                    )
                }
            }
        }
    }

    private suspend fun streamAudioFromGemini(
        text: String,
        emotion: Emotion
    ) = withContext(Dispatchers.IO) {
        val voiceName = VOICE_MAP[emotion] ?: "Kore"

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
                put("temperature", 1)
                put("responseModalities", JSONArray().apply {
                    put("audio")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })
        }

        val url = "$GEMINI_API_URL$MODEL_ID:$GENERATE_CONTENT_API?alt=sse&key=$apiKey"
        Log.d(TAG, "🌐 Calling API...")

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        val startTime = System.currentTimeMillis()

        okHttpClient.newCall(request).execute().use { response ->
            val networkTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "📡 Response: ${response.code} (${networkTime}ms)")

            if (!response.isSuccessful) {
                val error = response.body?.string() ?: "Unknown"
                throw IOException("API Error ${response.code}: $error")
            }

            response.body?.let { body ->
                processStreamingResponseSSE(body)
            } ?: throw IOException("Empty response")
        }
    }

    private fun processStreamingResponseSSE(body: ResponseBody) {
        var chunkCount = 0
        var totalBytesPlayed = 0
        var audioTrackCreated = false

        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue

            if (currentLine.startsWith("data: ")) {
                val jsonString = currentLine.removePrefix("data: ").trim()

                if (jsonString.isEmpty() || jsonString == "[DONE]") continue

                try {
                    val json = JSONObject(jsonString)
                    chunkCount++

                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")

                        if (parts != null && parts.length() > 0) {
                            val part = parts.getJSONObject(0)
                            val inlineData = part.optJSONObject("inlineData")

                            if (inlineData != null) {
                                val mimeType = inlineData.optString("mimeType", "")
                                val data = inlineData.optString("data", "")

                                Log.d(TAG, "🎵 Chunk #$chunkCount: ${data.length} base64 chars")

                                if (data.isNotEmpty()) {
                                    // ✅ Create AudioTrack JUST before first audio chunk
                                    if (!audioTrackCreated) {
                                        val track = createFreshAudioTrack()
                                        track.play()
                                        Log.d(TAG, "🔊 AudioTrack started, playState=${track.playState}")
                                        audioTrackCreated = true

                                        // 🎬 PLAYBACK STARTED EVENT
                                        _voiceState.update {
                                            it.copy(
                                                playbackState = PlaybackState.STARTED,
                                                totalBytesPlayed = 0
                                            )
                                        }
                                    }

                                    val bytesPlayed = playAudioData(data)
                                    totalBytesPlayed += bytesPlayed

                                    // 🎵 PLAYBACK PROGRESS EVENT
                                    _voiceState.update {
                                        it.copy(
                                            playbackState = PlaybackState.PLAYING,
                                            totalBytesPlayed = totalBytesPlayed
                                        )
                                    }
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Parse error: ${e.message}")
                }
            }
        }

        reader.close()

        // Wait for AudioTrack to finish playing buffered data
        if (audioTrackCreated && totalBytesPlayed > 0) {
            val durationMs = (totalBytesPlayed / (SAMPLE_RATE * 2)) * 1000 // 2 bytes per sample
            Log.d(TAG, "⏳ Waiting ${durationMs}ms for playback to complete...")

            // 🎬 PLAYBACK FINISHING EVENT (all data written, draining buffer)
            _voiceState.update {
                it.copy(
                    playbackState = PlaybackState.FINISHING,
                    estimatedDurationMs = durationMs.toLong()
                )
            }

            Thread.sleep(durationMs.toLong() + 500) // Extra buffer

            // 🎬 PLAYBACK ENDED EVENT
            _voiceState.update {
                it.copy(
                    playbackState = PlaybackState.ENDED
                )
            }
        }

        Log.d(TAG, "✅ Complete. Chunks: $chunkCount, Bytes: $totalBytesPlayed")
    }

    /**
     * Play audio data - NO endian conversion needed!
     * Gemini returns Little Endian PCM data despite the L16 name
     */
    private fun playAudioData(base64Data: String): Int {
        try {
            // Decode base64 - data is already Little Endian PCM
            val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
            Log.d(TAG, "🎵 Decoded: ${audioBytes.size} bytes")

            // Debug: show first samples (Little Endian: low byte first)
            if (audioBytes.size >= 10) {
                val samples = StringBuilder("Samples: ")
                for (i in 0 until minOf(5, audioBytes.size / 2)) {
                    // Little Endian: [LOW][HIGH]
                    val low = audioBytes[i * 2].toInt() and 0xFF
                    val high = audioBytes[i * 2 + 1].toInt()
                    val sample = (high shl 8) or low
                    samples.append("$sample ")
                }
                Log.d(TAG, "🔊 $samples")
            }

            val track = audioTrack
            if (track == null || track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.e(TAG, "❌ AudioTrack not ready!")
                return 0
            }

            // Write all data
            var offset = 0
            while (offset < audioBytes.size) {
                val remaining = audioBytes.size - offset
                val written = track.write(audioBytes, offset, remaining)

                if (written > 0) {
                    offset += written
                } else if (written < 0) {
                    Log.e(TAG, "❌ Write error: $written")
                    break
                }
            }

            Log.d(TAG, "🔊 Wrote $offset/${audioBytes.size} bytes")
            return offset

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error", e)
            return 0
        }
    }

    open fun stop() {
        Log.d(TAG, "🛑 Stopping")
        currentStreamJob?.cancel()
        audioTrack?.pause()
        audioTrack?.flush()

        _voiceState.update {
            it.copy(
                isSpeaking = false,
                currentMessageId = null,
                currentSpeakingMessageId = null,
                streamProgress = 0f,
                playbackState = PlaybackState.IDLE,
                totalBytesPlayed = 0,
                estimatedDurationMs = 0
            )
        }
    }

    open fun cleanup() {
        Log.d(TAG, "🧹 Cleanup")
        stop()
        audioTrack?.release()
        audioTrack = null

        audioFocusRequest?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }

        scope.cancel()
    }
}