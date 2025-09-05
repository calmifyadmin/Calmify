package com.lifo.chat.data.realtime

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client for Gemini Live API
 * Based on Google AI Developers Live API WebSocket reference
 * https://ai.google.dev/api/live
 *
 * Using Gemini 2.5 Flash Preview Native Audio Dialog model
 * for natural voice conversations with advanced features like
 * affective dialog and proactive audio
 */
@Singleton
class GeminiLiveWebSocketClient @Inject constructor() {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val LIVE_API_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        // Use the correct native audio model
        private const val MODEL_NAME = "gemini-2.5-flash-preview-native-audio-dialog"
        private const val AUDIO_FORMAT = "pcm16" // 16-bit PCM
        private const val SAMPLE_RATE = 24000 // 24 kHz
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Connection state
    private val _connectionState = MutableStateFlow<GeminiConnectionState>(GeminiConnectionState.Disconnected)
    val connectionState: StateFlow<GeminiConnectionState> = _connectionState.asStateFlow()

    // Audio input channel for sending mic data
    private val audioInputChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // Audio output for AI responses
    private val _audioOutput = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 10)
    val audioOutput: SharedFlow<ByteArray> = _audioOutput.asSharedFlow()

    // Text transcription output
    private val _transcriptOutput = MutableSharedFlow<String>(replay = 0)
    val transcriptOutput: SharedFlow<String> = _transcriptOutput.asSharedFlow()

    // Error events
    private val _errorEvents = MutableSharedFlow<String>(replay = 0)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var isSetupSent = false
    private val audioBuffer = ByteArrayOutputStream()

    /**
     * Connect to Gemini Live API with API key
     * Note: In production, use ephemeral tokens for better security
     */
    fun connect(apiKey: String) {
        if (_connectionState.value is GeminiConnectionState.Connecting ||
            _connectionState.value is GeminiConnectionState.Connected) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = GeminiConnectionState.Connecting

        // Build WebSocket URL with API key
        val url = "$LIVE_API_ENDPOINT?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())

        // Start audio processing coroutine
        startAudioProcessing()
    }

    /**
     * Connect using ephemeral token (more secure approach)
     */
    fun connectWithEphemeralToken(token: String) {
        if (_connectionState.value is GeminiConnectionState.Connecting ||
            _connectionState.value is GeminiConnectionState.Connected) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = GeminiConnectionState.Connecting

        // Use constrained endpoint with token
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?access_token=$token"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener())
        startAudioProcessing()
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "✅ WebSocket connected")
            _connectionState.value = GeminiConnectionState.Connected

            // Send setup message immediately after connection
            sendSetupMessage()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "📧 Received text message: ${text.take(100)}...")
            handleTextMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            Log.d(TAG, "📧 Received binary message: ${bytes.size} bytes")

            // Debug: verifica che sia audio 24kHz
            if (bytes.size > 0) {
                Log.v(TAG, "🔊 Emitting audio for playback (expecting 24kHz PCM)")
            }

            _audioOutput.tryEmit(bytes.toByteArray())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "🔌 WebSocket closing: $code - $reason")
            _connectionState.value = GeminiConnectionState.Disconnecting
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "🔌 WebSocket closed: $code - $reason")
            _connectionState.value = GeminiConnectionState.Disconnected
            isSetupSent = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "❌ WebSocket error", t)
            _connectionState.value = GeminiConnectionState.Error(t.message ?: "Connection failed")
            _errorEvents.tryEmit(t.message ?: "Connection failed")
            isSetupSent = false
        }
    }

    private fun sendSetupMessage() {
        if (isSetupSent) return

        // CORRECT setup message for native audio model
        val setupMessage = JSONObject().apply {
            put("setup", JSONObject().apply {
                // Model with "models/" prefix
                put("model", "models/$MODEL_NAME")

                // CRITICAL: Use snake_case, NOT camelCase!
                put("generation_config", JSONObject().apply {  // ✅ snake_case
                    // CRITICAL: Only AUDIO for native audio model!
                    put("response_modalities", org.json.JSONArray().apply {
                        put("AUDIO")  // ✅ ONLY AUDIO, no TEXT!
                    })

                    // Voice configuration - also snake_case
                    put("speech_config", JSONObject().apply {  // ✅ snake_case
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", "Aoede")
                            })
                        })
                    })
                })

                // System instruction
                put("system_instruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are Calmify, a calm and supportive AI assistant focused on mental health and wellness. Keep responses concise and empathetic. Speak in a warm, caring tone.")
                        })
                    })
                })
            })
        }

        Log.d(TAG, "📤 Sending setup message...")
        Log.d(TAG, "📤 Setup JSON: ${setupMessage.toString(2)}")

        val success = webSocket?.send(setupMessage.toString()) ?: false

        if (success) {
            isSetupSent = true
            Log.d(TAG, "✅ Setup message sent successfully")
        } else {
            Log.e(TAG, "❌ Failed to send setup message")
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)

            when {
                json.has("setupComplete") -> {
                    Log.d(TAG, "🎉 Setup completed successfully")
                    // You might want to emit a ready state here
                }

                json.has("serverContent") -> {
                    val serverContent = json.getJSONObject("serverContent")
                    if (serverContent.has("modelTurn")) {
                        val modelTurn = serverContent.getJSONObject("modelTurn")
                        if (modelTurn.has("parts")) {
                            val parts = modelTurn.getJSONArray("parts")
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("text")) {
                                    val transcript = part.getString("text")
                                    Log.d(TAG, "📝 AI transcript: $transcript")
                                    _transcriptOutput.tryEmit(transcript)
                                }
                            }
                        }
                    }
                }

                json.has("realtimeInput") -> {
                    // Handle user input transcription if needed
                    Log.d(TAG, "🎤 User input processed")
                }

                json.has("error") -> {
                    val error = json.getJSONObject("error")
                    val errorMessage = error.optString("message", "Unknown error")
                    val errorCode = error.optString("code", "")
                    Log.e(TAG, "❌ Server error: $errorCode - $errorMessage")
                    _errorEvents.tryEmit("Error: $errorMessage")
                }

                else -> {
                    Log.d(TAG, "🤷 Unknown message type: ${json.keys().asSequence().joinToString()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse message", e)
        }
    }

    /**
     * Send audio data to the API
     * Audio should be 16-bit PCM at 16kHz, mono
     */
    fun sendAudio(audioData: ByteArray) {
        coroutineScope.launch {
            audioInputChannel.send(audioData)
        }
    }

    /**
     * Send text message to AI
     */
    fun sendText(text: String) {
        val message = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }

        Log.d(TAG, "📤 Sending text message: $text")
        val success = webSocket?.send(message.toString()) ?: false
        Log.d(TAG, if (success) "📤 Text sent successfully" else "❌ Failed to send text")
    }

    private fun startAudioProcessing() {
        coroutineScope.launch {
            for (audioData in audioInputChannel) {
                try {
                    // Buffer audio data and send in chunks
                    audioBuffer.write(audioData)

                    // Send when we have enough data (e.g., 480 bytes for 10ms at 24kHz)
                    if (audioBuffer.size() >= 480) {
                        val bufferedData = audioBuffer.toByteArray()
                        audioBuffer.reset()

                        val message = JSONObject().apply {
                            put("realtimeInput", JSONObject().apply {
                                put("mediaChunks", org.json.JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=24000")
                                        put("data", android.util.Base64.encodeToString(bufferedData, android.util.Base64.NO_WRAP))
                                    })
                                })
                            })
                        }

                        webSocket?.send(message.toString())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to process audio", e)
                }
            }
        }
    }

    /**
     * Disconnect from the API
     */
    fun disconnect() {
        Log.d(TAG, "🔌 Disconnecting...")
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        audioBuffer.reset()
        isSetupSent = false
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
        coroutineScope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}

/**
 * Connection states for Gemini Live API
 */
sealed class GeminiConnectionState {
    object Disconnected : GeminiConnectionState()
    object Connecting : GeminiConnectionState()
    object Connected : GeminiConnectionState()
    object Disconnecting : GeminiConnectionState()
    data class Error(val message: String) : GeminiConnectionState()
}