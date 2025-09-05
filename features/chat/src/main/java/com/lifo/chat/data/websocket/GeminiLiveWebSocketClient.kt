package com.lifo.chat.data.websocket

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.drafts.Draft_6455
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLiveWebSocketClient @Inject constructor() {

    companion object {
        private const val TAG = "GeminiLiveWebSocket"
        private const val MODEL = "models/gemini-2.0-flash-exp"
        private const val HOST = "generativelanguage.googleapis.com"
    }

    private var webSocket: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    var onTextReceived: ((String) -> Unit)? = null
    var onAudioReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    fun connect(apiKey: String) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        val url = "wss://$HOST/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        Log.d(TAG, "🔌 Connecting to: $url")

        _connectionState.value = ConnectionState.CONNECTING

        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"

        webSocket = object : WebSocketClient(URI(url), Draft_6455(), headers) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d(TAG, "✅ Connected. Server handshake: ${handshakedata?.httpStatus}")
                _connectionState.value = ConnectionState.CONNECTED
                sendInitialSetupMessage()
            }

            override fun onMessage(message: String?) {
                Log.d(TAG, "📥 Message Received: ${message?.take(200)}")
                receiveMessage(message)
            }

            override fun onMessage(bytes: ByteBuffer?) {
                bytes?.let {
                    val message = String(it.array(), Charsets.UTF_8)
                    receiveMessage(message)
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.d(TAG, "🔌 Connection Closed: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onError(ex: Exception?) {
                Log.e(TAG, "❌ Error: ${ex?.message}")
                _connectionState.value = ConnectionState.ERROR
                onError?.invoke(ex?.message ?: "Unknown error")
            }
        }

        webSocket?.connect()
    }

    private fun sendInitialSetupMessage() {
        Log.d(TAG, "📤 Sending initial setup message")

        val setupMessage = JSONObject()
        val setup = JSONObject()
        val generationConfig = JSONObject()
        val responseModalities = JSONArray().put("AUDIO")

        // 1) Modalità di risposta (una sola: AUDIO)
        generationConfig.put("response_modalities", responseModalities)

        // 2) Voce femminile + lingua italiana (half-cascade supporta language_code)
        val prebuiltVoice = JSONObject().put("voice_name", "Kore") // oppure "Kore"
        val voiceConfig = JSONObject().put("prebuilt_voice_config", prebuiltVoice)
        val speechConfig = JSONObject()
            .put("voice_config", voiceConfig)
            .put("language_code", "it-IT") // opzionale ma utile con modelli non native-audio
        generationConfig.put("speech_config", speechConfig)

        // 3) Modello
        setup.put("model", MODEL) // "models/gemini-2.0-flash-exp" nel tuo companion object

        // 4) System instructions come Content (NON stringa)
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text",
                "Rispondi sempre in italiano. Sei un'assistente che parla solo italiano.")))
        // .put("role", "system") // opzionale: il ruolo viene ignorato
        setup.put("system_instruction", systemInstruction)

        // 5) (Opz.) abilita trascrizione output o input se ti serve
        // generationConfig.put("output_audio_transcription", JSONObject()) // abilita trascrizione output
        // generationConfig.put("input_audio_transcription", JSONObject())  // abilita ASR sull'input

        setup.put("generation_config", generationConfig)
        setupMessage.put("setup", setup)

        Log.d(TAG, "📤 Sending config payload: $setupMessage")
        webSocket?.send(setupMessage.toString())
    }


    fun sendAudioData(audioBase64: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send audio - not connected")
            return
        }

        try {
            // CRITICAL FIX: Use "audio/pcm" NOT "audio/pcm;rate=24000"
            val msg = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()

            val audioChunk = JSONObject()
            audioChunk.put("mime_type", "audio/pcm")  // <-- FIX HERE! No rate!
            audioChunk.put("data", audioBase64)
            mediaChunks.put(audioChunk)

            realtimeInput.put("media_chunks", mediaChunks)
            msg.put("realtime_input", realtimeInput)

            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
        }
    }

    fun sendImageData(imageBase64: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send image - not connected")
            return
        }

        try {
            Log.d(TAG, "📸 Sending image data (${imageBase64.length} chars)")
            val msg = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()

            val imageChunk = JSONObject()
            imageChunk.put("mime_type", "image/jpeg")
            imageChunk.put("data", imageBase64)
            mediaChunks.put(imageChunk)

            realtimeInput.put("media_chunks", mediaChunks)
            msg.put("realtime_input", realtimeInput)

            webSocket?.send(msg.toString())
            Log.d(TAG, "📤 Image sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image", e)
        }
    }

    fun sendEndOfStream() {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        try {
            val msg = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()

            realtimeInput.put("media_chunks", mediaChunks)
            msg.put("realtime_input", realtimeInput)

            webSocket?.send(msg.toString())
            Log.d(TAG, "🔚 End of stream sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending end of stream", e)
        }
    }

    private fun receiveMessage(message: String?) {
        if (message == null) return

        try {
            val messageData = JSONObject(message)

            // Log all message types for debugging
            Log.d(TAG, "📨 Message type: ${messageData.keys().asSequence().joinToString()}")

            if (messageData.has("serverContent")) {
                val serverContent = messageData.getJSONObject("serverContent")

                // Check for turn complete
                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    Log.d(TAG, "✅ Turn complete")
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            if (part.has("text")) {
                                val text = part.getString("text")
                                Log.d(TAG, "📝 GEMINI: $text")
                                onTextReceived?.invoke(text)
                            }

                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")

                                if (mimeType == "audio/pcm;rate=24000") {
                                    val audioData = inlineData.getString("data")
                                    Log.d(TAG, "🔊 Audio received: ${audioData.length} chars")
                                    onAudioReceived?.invoke(audioData)
                                }
                            }
                        }
                    }
                }
            }

            // Log other message types for debugging
            if (messageData.has("toolCall")) {
                Log.d(TAG, "🔧 Tool call received")
            }

            if (messageData.has("error")) {
                val error = messageData.optJSONObject("error")
                val errorMsg = error?.optString("message", "Unknown error") ?: "Unknown error"
                Log.e(TAG, "❌ Server error: $errorMsg")
                onError?.invoke(errorMsg)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        webSocket?.close()
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
}