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
        Log.d(TAG, "📤 Sending initial setup message with optimized VAD")

        val setupMessage = JSONObject()
        val setup = JSONObject()
        val generationConfig = JSONObject()
        val responseModalities = JSONArray().put("AUDIO")

        // 1) Modalità di risposta AUDIO
        generationConfig.put("response_modalities", responseModalities)

        // 2) Configurazione voce italiana
        val prebuiltVoice = JSONObject().put("voice_name", "Kore")
        val voiceConfig = JSONObject().put("prebuilt_voice_config", prebuiltVoice)
        val speechConfig = JSONObject()
            .put("voice_config", voiceConfig)
            .put("language_code", "it-IT")
        generationConfig.put("speech_config", speechConfig)

        // 3) Aggiungi generation_config a setup
        setup.put("generation_config", generationConfig)

        // 4) Modello
        setup.put("model", MODEL)

        // 5) System instructions
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text",
                "Rispondi sempre in italiano. Sei un'assistente che parla solo italiano.")))
        setup.put("system_instruction", systemInstruction)

        // ========================================================================
        // 6) CONFIGURAZIONE VAD OTTIMALE - BEST PRACTICE
        // ========================================================================
        val realtimeInputConfig = JSONObject()
        val automaticActivityDetection = JSONObject()
            .put("disabled", false)  // VAD automatico attivo

            // SENSIBILITÀ OTTIMALE PER RILEVARE ANCHE FRASI BREVI
            .put("start_of_speech_sensitivity", 1)  // LOW (1) - Massima sensibilità all'inizio
            // Nella documentazione ufficiale questo corrisponde a START_SENSITIVITY_LOW

            .put("end_of_speech_sensitivity", 2)    // MEDIUM (2) - Bilanciato per fine parlato
            // Evita che tagli troppo presto ma non aspetta troppo

            // PADDING E TIMING OTTIMALI (da documentazione e best practice)
            .put("prefix_padding_ms", 300)          // 300ms - Cattura più audio prima del VAD
            // Questo è CRUCIALE per non perdere l'inizio delle parole brevi
            // Valore più alto del default (20ms) ma ottimale per brevi utteranze

            .put("silence_duration_ms", 400)        // 400ms - Quanto silenzio prima di terminare
        // Bilanciato: abbastanza veloce ma non taglia frasi con pause naturali

        realtimeInputConfig.put("automatic_activity_detection", automaticActivityDetection)
        setup.put("realtime_input_config", realtimeInputConfig)

        // 7) Transcription config per debug e feedback
        setup.put("output_audio_transcription", JSONObject()) // trascrizione output audio
        setup.put("input_audio_transcription", JSONObject())  // trascrizione input utente

        setupMessage.put("setup", setup)

        Log.d(TAG, "📤 VAD Config: sensitivity(1,2), padding(300ms), silence(400ms)")
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

            // Handle VAD-related events
            if (messageData.has("setupComplete")) {
                Log.d(TAG, "✅ Setup complete - VAD configured")
            }

            // Handle realtime input events (VAD detection)
            if (messageData.has("realtimeInput")) {
                val realtimeInput = messageData.getJSONObject("realtimeInput")
                
                // Partial transcript during user speech
                if (realtimeInput.has("inputAudioTranscription")) {
                    val transcription = realtimeInput.getJSONObject("inputAudioTranscription")
                    val partial = transcription.optString("partialTranscript", "")
                    val final = transcription.optString("finalTranscript", "")
                    
                    if (partial.isNotEmpty()) {
                        Log.d(TAG, "🎤 Partial transcript: $partial")
                        onPartialTranscript?.invoke(partial)
                    }
                    if (final.isNotEmpty()) {
                        Log.d(TAG, "🎤 Final transcript: $final")
                        onFinalTranscript?.invoke(final)
                    }
                }
            }

            if (messageData.has("serverContent")) {
                val serverContent = messageData.getJSONObject("serverContent")

                // Check for turn complete
                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    Log.d(TAG, "✅ Turn complete")
                    onTurnCompleted?.invoke()
                }

                // Check for interrupted state
                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    Log.d(TAG, "⚠️ Response interrupted by user")
                    onInterrupted?.invoke()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    
                    // Model started responding
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

                                if (mimeType == "audio/pcm;rate=24000") {
                                    val audioData = inlineData.getString("data")
                                    Log.d(TAG, "🔊 Audio received: ${audioData.length} chars")
                                    onAudioReceived?.invoke(audioData)
                                }
                            }
                        }
                    }
                }

                // Handle output audio transcription
                if (serverContent.has("outputAudioTranscription")) {
                    val transcription = serverContent.getJSONObject("outputAudioTranscription")
                    val text = transcription.optString("text", "")
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "🔊 Output transcript: $text")
                        onTextReceived?.invoke(text)
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