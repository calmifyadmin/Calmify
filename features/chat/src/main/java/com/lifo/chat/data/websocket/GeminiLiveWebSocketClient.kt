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
import com.google.firebase.auth.FirebaseAuth

@Singleton
class GeminiLiveWebSocketClient @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

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
    var onToolCallReceived: ((String) -> Unit)? = null
    var onChatMessageSaved: ((String, String, Boolean) -> Unit)? = null

    // Cache per i diari dell'utente (delegato al ViewModel)
    private var cachedUserName: String = ""
    private var cachedDiariesSummary: String = ""

    // Callbacks per delegare operazioni al ViewModel
    var onNeedUserData: (suspend () -> Pair<String, String>)? = null
    var onExecuteFunction: (suspend (String, JSONObject) -> JSONObject)? = null

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

                // Prefetch dati utente
                scope.launch {
                    try {
                        loadUserData()
                        sendInitialSetupMessage()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during preload: ${e.message}", e)
                        sendInitialSetupMessage() // Continua comunque
                    }
                }
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

    /**
     * Carica i dati utente tramite callback
     */
    private suspend fun loadUserData() {
        Log.d(TAG, "🔄 Loading user data...")

        try {
            // Recupera nome utente da Firebase
            cachedUserName = firebaseAuth.currentUser?.displayName ?:
                    firebaseAuth.currentUser?.email?.substringBefore("@") ?:
                    "Utente"

            // Recupera diari tramite callback dal ViewModel
            val userData = onNeedUserData?.invoke()
            if (userData != null) {
                cachedUserName = userData.first
                cachedDiariesSummary = userData.second
                Log.d(TAG, "✅ User data loaded: $cachedUserName")
            } else {
                Log.w(TAG, "⚠️ No user data callback provided")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading user data", e)
            cachedDiariesSummary = "Nessun diario disponibile"
        }
    }

    private fun sendInitialSetupMessage() {
        Log.d(TAG, "📤 Sending initial setup message with function calling support")

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

            // System instruction con contesto personalizzato
            val systemInstructionText = buildSystemInstruction()
            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
            }
            put("systemInstruction", systemInstruction)

            // Function declarations per accesso ai diari
            val tools = JSONArray().apply {
                put(JSONObject().apply {
                    put("functionDeclarations", JSONArray().apply {
                        // Function 1: get_recent_diaries
                        put(JSONObject().apply {
                            put("name", "get_recent_diaries")
                            put("description", "Ritorna gli ultimi diari dell'utente per comprendere meglio il suo stato attuale")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("limit", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Numero di diari da ritornare (max 10)")
                                    })
                                })
                            })
                        })

                        // Function 2: search_diary
                        put(JSONObject().apply {
                            put("name", "search_diary")
                            put("description", "Cerca tra tutti i diari dell'utente basandosi su una query specifica")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Parola chiave o frase da cercare nei diari")
                                    })
                                    put("k", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Numero massimo di risultati da ritornare")
                                    })
                                })
                                put("required", JSONArray().put("query"))
                            })
                        })
                    })
                })
            }
            put("tools", tools)

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
        Log.d(TAG, "📤 Setup sent with function calling")
    }

    /**
     * Costruisce il system instruction personalizzato
     */
    private fun buildSystemInstruction(): String {
        val diariesInfo = if (cachedDiariesSummary.isNotEmpty()) {
            "\n\nULTIMI DIARI DI $cachedUserName:\n$cachedDiariesSummary"
        } else {
            "\n\nNessun diario recente disponibile."
        }

        return """Sei l'assistente personale di $cachedUserName.
Conosci l'utente e i suoi ultimi diari.

- Prima usa i diari in cache per rispondere.
- Se mancano informazioni specifiche → chiama 'search_diary'.
- Se servono più diari recenti → chiama 'get_recent_diaries'.
- Rispondi sempre in italiano, tono naturale e affettuoso.
- Cita data+titolo del diario quando lo usi.
- Parla con voce dolce, allegra e affettuosa.
- Mantieni un tono caldo, sorridente e carino.
- Usa frasi brevi e naturali.$diariesInfo"""
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
                if (text.isNotEmpty()) {
                    onPartialTranscript?.invoke(text)
                    // Salva messaggio utente automaticamente
                    saveMessageToChat(text, true)
                }
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
                                // Salva messaggio AI automaticamente
                                saveMessageToChat(text, false)
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
                handleToolCall(messageData.getJSONObject("toolCall"))
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

    /**
     * Gestisce le chiamate ai tool da parte del modello
     */
    private fun handleToolCall(toolCall: JSONObject) {
        scope.launch {
            try {
                val functionCalls = toolCall.getJSONArray("functionCalls")
                val responses = mutableListOf<JSONObject>()

                for (i in 0 until functionCalls.length()) {
                    val functionCall = functionCalls.getJSONObject(i)
                    val id = functionCall.getString("id")
                    val name = functionCall.getString("name")
                    val args = functionCall.getJSONObject("args")

                    Log.d(TAG, "🔧 Executing function: $name with args: $args")

                    val result = when (name) {
                        "get_recent_diaries" -> executeGetRecentDiaries(args)
                        "search_diary" -> executeSearchDiary(args)
                        else -> {
                            Log.w(TAG, "⚠️ Unknown function: $name")
                            JSONObject().apply {
                                put("error", "Unknown function: $name")
                                put("message", "Function $name is not supported")
                            }
                        }
                    }

                    responses.add(JSONObject().apply {
                        put("id", id)
                        put("name", name)
                        put("response", JSONObject().put("result", result))
                    })
                }

                // Invia la risposta al tool call
                val toolResponse = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", JSONArray(responses))
                    })
                }

                webSocket?.send(toolResponse.toString())
                Log.d(TAG, "📤 Tool response sent")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error handling tool call", e)
                onError?.invoke("Tool execution failed: ${e.message}")
            }
        }
    }

    /**
     * Esegue get_recent_diaries tramite callback
     */
    private suspend fun executeGetRecentDiaries(args: JSONObject): JSONObject {
        return onExecuteFunction?.invoke("get_recent_diaries", args) ?: JSONObject().apply {
            put("error", "No function executor available")
            put("results", JSONArray())
        }
    }

    /**
     * Esegue search_diary tramite callback
     */
    private suspend fun executeSearchDiary(args: JSONObject): JSONObject {
        return onExecuteFunction?.invoke("search_diary", args) ?: JSONObject().apply {
            put("error", "No function executor available")
            put("results", JSONArray())
        }
    }

    /**
     * Salva un messaggio della conversazione Live nel database Chat
     */
    fun saveMessageToChat(content: String, isUser: Boolean, sessionId: String? = null) {
        scope.launch {
            try {
                val actualSessionId = sessionId ?: "live-${System.currentTimeMillis()}"
                Log.d(TAG, "💬 Saving Live message to Chat DB: ${content.take(50)}...")

                // Callback per notificare il salvataggio
                onChatMessageSaved?.invoke(actualSessionId, content, isUser)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving message to chat", e)
            }
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