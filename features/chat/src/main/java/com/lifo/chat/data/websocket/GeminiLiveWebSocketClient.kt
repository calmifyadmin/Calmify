package com.lifo.chat.data.websocket

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
        // Modello supportato per Live API realtime
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
    var onPartialTranscript: ((String) -> Unit)? = null
    var onFinalTranscript: ((String) -> Unit)? = null
    var onTurnStarted: (() -> Unit)? = null
    var onTurnCompleted: (() -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null

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

        val url =
            "wss://$HOST/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
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
        Log.d(TAG, "📤 Sending initial setup message (v1beta, camelCase)")

        val setup = JSONObject().apply {
            // Modello
            put("model", MODEL)

            // Generation config: UNA sola modality per sessione (AUDIO oppure TEXT)
            val generationConfig = JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                // Config TTS opzionale corretta (camelCase)
                val speechConfig = JSONObject().apply {
                    put("languageCode", "it-IT")
                    // Opzionale: voce predefinita
                     val voiceConfig = JSONObject().apply {
                         put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Aoede"))
                     }
                     put("voiceConfig", voiceConfig)
                }
                put("speechConfig", speechConfig)
            }
            put("generationConfig", generationConfig)

            // System instruction (solo testo)
            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text",
                    "Parla in italiano con voce dolce, allegra e affettuosa. " +
                            "Mantieni un tono caldo, sorridente e carina. " +
                            "Usa frasi brevi e naturali.")
                ))
            }
            put("systemInstruction", systemInstruction)

            // Realtime input config: VAD server ON con nomi camelCase
            val realtimeInputConfig = JSONObject().apply {
                val aad = JSONObject().apply {
                    put("disabled", false)
                    put("prefixPaddingMs", 100)   // cattura attacchi
                    put("silenceDurationMs", 300) // chiusura turno reattiva
                }
                put("automaticActivityDetection", aad)
                // Gestione attività: barge-in interrompe la generazione
                put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS")
            }
            put("realtimeInputConfig", realtimeInputConfig)

            // Trascrizioni (abilitate)
            put("inputAudioTranscription", JSONObject())
            put("outputAudioTranscription", JSONObject())
        }

        val setupMessage = JSONObject().put("setup", setup)
        webSocket?.send(setupMessage.toString())
        Log.d(TAG, "📤 Setup sent")
    }

    /** Invia chunk audio PCM 16k mono 16-bit in base64 */
    fun sendAudioData(audioBase64: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send audio - not connected")
            return
        }
        try {
            val audioBlob = JSONObject().apply {
                put("mimeType", "audio/pcm;rate=16000")
                put("data", audioBase64)
            }
            val msg = JSONObject().put("realtimeInput", JSONObject().put("audio", audioBlob))
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
        }
    }

    /** Invia immagine (jpeg) in tempo reale */
    fun sendImageData(imageBase64: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send image - not connected")
            return
        }
        try {
            Log.d(TAG, "📸 Sending image data (${imageBase64.length} chars)")
            
            // Costruzione del messaggio con il formato corretto per Gemini Live API
            val msg = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", imageBase64)
                        })
                    })
                })
            }
            
            webSocket?.send(msg.toString())
            Log.d(TAG, "📤 Image sent successfully via mediaChunks")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image", e)
        }
    }

    /**
     * Fine turno: svuota buffer VAD (mute/unmute, pausa, fine frase)
     */
    fun sendEndOfStream() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        try {
            val msg = JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true))
            webSocket?.send(msg.toString())
            Log.d(TAG, "🔚 audioStreamEnd sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audioStreamEnd", e)
        }
    }

    private fun receiveMessage(message: String?) {
        if (message == null) return

        try {
            val messageData = JSONObject(message)
            Log.d(TAG, "📨 Message keys: ${messageData.keys().asSequence().joinToString()}")

            // Setup completo
            if (messageData.has("setupComplete")) {
                Log.d(TAG, "✅ Setup complete - VAD configured and ready")
                onTurnCompleted?.invoke()
            }

            // Trascrizioni top-level (possono arrivare indipendenti)
            if (messageData.has("inputTranscription")) {
                val t = messageData.getJSONObject("inputTranscription")
                val text = t.optString("text", "")
                if (text.isNotEmpty()) onPartialTranscript?.invoke(text)
            }
            if (messageData.has("outputTranscription")) {
                val t = messageData.getJSONObject("outputTranscription")
                val text = t.optString("text", "")
                if (text.isNotEmpty()) onTextReceived?.invoke(text)
            }

            // Contenuti dal server (turni, output, audio)
            if (messageData.has("serverContent")) {
                val serverContent = messageData.getJSONObject("serverContent")

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    Log.d(TAG, "✅ Turn complete")
                    onTurnCompleted?.invoke()
                }

                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    Log.d(TAG, "⚠️ Response interrupted by user (barge-in detected)")
                    onInterrupted?.invoke()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    Log.d(TAG, "🤖 AI turn started - user should stop speaking")
                    onTurnStarted?.invoke()

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
                                if (mimeType.startsWith("audio/pcm")) {
                                    val audioData = inlineData.getString("data")
                                    Log.d(TAG, "🔊 Audio received: ${audioData.length} chars ($mimeType)")
                                    onAudioReceived?.invoke(audioData)
                                }
                            }
                        }
                    }
                }

                // Output transcript dentro serverContent (se presente)
                if (serverContent.has("outputAudioTranscription")) {
                    val transcription = serverContent.getJSONObject("outputAudioTranscription")
                    val text = transcription.optString("text", "")
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "🔊 Output transcript: $text")
                        onTextReceived?.invoke(text)
                    }
                }
            }

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
