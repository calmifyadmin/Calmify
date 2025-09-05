package com.lifo.chat.data.realtime

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.json.JSONException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAIRealtimeClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "OpenAIRealtimeClient"
        private const val REALTIME_URL = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01"
        private const val RECONNECT_DELAY_BASE_MS = 1000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val HEARTBEAT_INTERVAL_MS = 30000L
    }

    private var webSocket: WebSocket? = null
    private var apiKey: String = ""
    private var reconnectAttempts = 0
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _realtimeEvents = MutableSharedFlow<RealtimeEvent>(
        extraBufferCapacity = 100,
        replay = 0
    )
    val realtimeEvents = _realtimeEvents.asSharedFlow()

    /**
     * Initialize and connect to OpenAI Realtime API
     */
    suspend fun connect(apiKey: String): Flow<RealtimeConnectionState> = callbackFlow {
        if (apiKey.isEmpty()) {
            trySend(RealtimeConnectionState.Error("API key is required", null))
            close()
            return@callbackFlow
        }

        this@OpenAIRealtimeClient.apiKey = apiKey
        
        try {
            trySend(RealtimeConnectionState.Connecting)
            
            val request = createWebSocketRequest()
            
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected successfully")
                    this@OpenAIRealtimeClient.webSocket = webSocket
                    reconnectAttempts = 0
                    _connectionState.value = RealtimeConnectionState.Connected
                    trySend(RealtimeConnectionState.Connected)
                    startHeartbeat()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleTextMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // Handle binary audio data if needed
                    Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code - $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code - $reason")
                    _connectionState.value = RealtimeConnectionState.Disconnected
                    trySend(RealtimeConnectionState.Disconnected)
                    stopHeartbeat()
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failed", t)
                    val errorState = RealtimeConnectionState.Error(
                        t.message ?: "Connection failed", t
                    )
                    _connectionState.value = errorState
                    trySend(errorState)
                    stopHeartbeat()
                    scheduleReconnect()
                }
            }

            this@OpenAIRealtimeClient.webSocket = httpClient.newWebSocket(request, listener)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WebSocket", e)
            val errorState = RealtimeConnectionState.Error(e.message ?: "Connection error", e)
            trySend(errorState)
            close(e)
        }

        awaitClose {
            disconnect()
        }
    }

    /**
     * Disconnect from OpenAI Realtime API
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        stopHeartbeat()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    /**
     * Send an event to the OpenAI Realtime API
     */
    suspend fun sendEvent(event: RealtimeEvent.Client): Boolean {
        val socket = webSocket
        if (socket == null) {
            Log.w(TAG, "Cannot send event: WebSocket not connected")
            return false
        }

        return try {
            val jsonObject = JSONObject().apply {
                put("event_id", event.eventId)
                put("type", event.type)
                
                when (event) {
                    is RealtimeEvent.Client.SessionUpdate -> {
                        put("session", mapToJsonObject(event.session))
                    }
                    is RealtimeEvent.Client.InputAudioBufferAppend -> {
                        put("audio", event.audio)
                    }
                    is RealtimeEvent.Client.ConversationItemCreate -> {
                        put("item", mapToJsonObject(event.item))
                    }
                    is RealtimeEvent.Client.ResponseCreate -> {
                        event.response?.let { put("response", mapToJsonObject(it)) }
                    }
                    // Other client events don't need additional data
                    else -> {}
                }
            }
            
            val jsonString = jsonObject.toString()
            Log.d(TAG, "Sending event: ${event::class.simpleName}")
            Log.v(TAG, "Event JSON: $jsonString")
            socket.send(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending event", e)
            false
        }
    }

    /**
     * Send audio data to the OpenAI Realtime API
     */
    suspend fun sendAudioData(audioData: ByteArray): Boolean {
        if (audioData.isEmpty()) return false
        
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val event = RealtimeEvent.Client.InputAudioBufferAppend(audio = base64Audio)
        return sendEvent(event)
    }

    /**
     * Commit audio buffer (indicates end of user speech)
     */
    suspend fun commitAudioBuffer(): Boolean {
        val event = RealtimeEvent.Client.InputAudioBufferCommit()
        return sendEvent(event)
    }

    /**
     * Clear audio buffer
     */
    suspend fun clearAudioBuffer(): Boolean {
        val event = RealtimeEvent.Client.InputAudioBufferClear()
        return sendEvent(event)
    }

    /**
     * Create session with configuration
     */
    suspend fun createSession(config: SessionConfig): Boolean {
        val event = RealtimeEvent.Client.SessionUpdate(session = config.toMap())
        return sendEvent(event)
    }

    /**
     * Request AI response
     */
    suspend fun generateResponse(config: ResponseConfig = ResponseConfig.default()): Boolean {
        val event = RealtimeEvent.Client.ResponseCreate(response = config.toMap())
        return sendEvent(event)
    }

    /**
     * Cancel ongoing response
     */
    suspend fun cancelResponse(): Boolean {
        val event = RealtimeEvent.Client.ResponseCancel()
        return sendEvent(event)
    }

    /**
     * Add conversation item
     */
    suspend fun addConversationItem(item: ConversationItem): Boolean {
        val event = RealtimeEvent.Client.ConversationItemCreate(item = item.toMap())
        return sendEvent(event)
    }

    private fun createWebSocketRequest(): Request {
        return Request.Builder()
            .url(REALTIME_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("OpenAI-Beta", "realtime=v1")
            .build()
    }

    private fun handleTextMessage(text: String) {
        try {
            Log.v(TAG, "Received message: $text")
            
            val jsonObject = JSONObject(text)
            val eventType = jsonObject.getString("type")
            val eventId = jsonObject.getString("event_id")
            
            Log.d(TAG, "Processing event: $eventType")
            
            val event: RealtimeEvent.Server = when (eventType) {
                "error" -> {
                    val errorObj = jsonObject.getJSONObject("error")
                    RealtimeEvent.Server.Error(
                        eventId = eventId,
                        error = ErrorDetails(
                            type = errorObj.getString("type"),
                            code = errorObj.getString("code"), 
                            message = errorObj.getString("message"),
                            param = errorObj.optString("param", null),
                            eventId = errorObj.optString("event_id", null)
                        )
                    )
                }
                "session.created", "session.updated" -> {
                    val sessionMap = jsonObjectToMap(jsonObject.getJSONObject("session"))
                    if (eventType == "session.created") {
                        RealtimeEvent.Server.SessionCreated(eventId = eventId, session = sessionMap)
                    } else {
                        RealtimeEvent.Server.SessionUpdated(eventId = eventId, session = sessionMap)
                    }
                }
                "input_audio_buffer.committed" -> {
                    RealtimeEvent.Server.InputAudioBufferCommitted(
                        eventId = eventId,
                        previousItemId = jsonObject.optString("previous_item_id", null),
                        itemId = jsonObject.getString("item_id")
                    )
                }
                "input_audio_buffer.speech_started" -> {
                    RealtimeEvent.Server.InputAudioBufferSpeechStarted(
                        eventId = eventId,
                        audioStartMs = jsonObject.getInt("audio_start_ms"),
                        itemId = jsonObject.getString("item_id")
                    )
                }
                "input_audio_buffer.speech_stopped" -> {
                    RealtimeEvent.Server.InputAudioBufferSpeechStopped(
                        eventId = eventId,
                        audioEndMs = jsonObject.getInt("audio_end_ms"),
                        itemId = jsonObject.getString("item_id")
                    )
                }
                "response.created", "response.done" -> {
                    val responseMap = jsonObjectToMap(jsonObject.getJSONObject("response"))
                    if (eventType == "response.created") {
                        RealtimeEvent.Server.ResponseCreated(eventId = eventId, response = responseMap)
                    } else {
                        RealtimeEvent.Server.ResponseDone(eventId = eventId, response = responseMap)
                    }
                }
                "response.text.delta" -> {
                    RealtimeEvent.Server.ResponseTextDelta(
                        eventId = eventId,
                        responseId = jsonObject.getString("response_id"),
                        itemId = jsonObject.getString("item_id"),
                        outputIndex = jsonObject.getInt("output_index"),
                        contentIndex = jsonObject.getInt("content_index"),
                        delta = jsonObject.getString("delta")
                    )
                }
                "response.text.done" -> {
                    RealtimeEvent.Server.ResponseTextDone(
                        eventId = eventId,
                        responseId = jsonObject.getString("response_id"),
                        itemId = jsonObject.getString("item_id"),
                        outputIndex = jsonObject.getInt("output_index"),
                        contentIndex = jsonObject.getInt("content_index"),
                        text = jsonObject.getString("text")
                    )
                }
                "response.audio.delta" -> {
                    RealtimeEvent.Server.ResponseAudioDelta(
                        eventId = eventId,
                        responseId = jsonObject.getString("response_id"),
                        itemId = jsonObject.getString("item_id"),
                        outputIndex = jsonObject.getInt("output_index"),
                        contentIndex = jsonObject.getInt("content_index"),
                        delta = jsonObject.getString("delta")
                    )
                }
                "response.audio.done" -> {
                    RealtimeEvent.Server.ResponseAudioDone(
                        eventId = eventId,
                        responseId = jsonObject.getString("response_id"),
                        itemId = jsonObject.getString("item_id"),
                        outputIndex = jsonObject.getInt("output_index"),
                        contentIndex = jsonObject.getInt("content_index")
                    )
                }
                "response.audio_transcript.delta" -> {
                    RealtimeEvent.Server.ResponseAudioTranscriptDelta(
                        eventId = eventId,
                        responseId = jsonObject.getString("response_id"),
                        itemId = jsonObject.getString("item_id"),
                        outputIndex = jsonObject.getInt("output_index"),
                        contentIndex = jsonObject.getInt("content_index"),
                        delta = jsonObject.getString("delta")
                    )
                }
                "response.audio_transcript.done" -> {
                    RealtimeEvent.Server.ResponseAudioTranscriptDone(
                        eventId = eventId,
                        responseId = jsonObject.getString("response_id"),
                        itemId = jsonObject.getString("item_id"),
                        outputIndex = jsonObject.getInt("output_index"),
                        contentIndex = jsonObject.getInt("content_index"),
                        transcript = jsonObject.getString("transcript")
                    )
                }
                else -> {
                    Log.w(TAG, "Unknown event type: $eventType")
                    return
                }
            }
            
            // Emit the event to subscribers
            _realtimeEvents.tryEmit(event)
            
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error: $text", e)
            val errorEvent = RealtimeEvent.Server.Error(
                eventId = "parse_error_${System.currentTimeMillis()}",
                error = ErrorDetails(
                    type = "parse_error",
                    code = "json_parse_failed",
                    message = "Failed to parse server message: ${e.message}"
                )
            )
            _realtimeEvents.tryEmit(errorEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: $text", e)
            val errorEvent = RealtimeEvent.Server.Error(
                eventId = "parse_error_${System.currentTimeMillis()}",
                error = ErrorDetails(
                    type = "parse_error",
                    code = "general_error",
                    message = "Failed to parse server message: ${e.message}"
                )
            )
            _realtimeEvents.tryEmit(errorEvent)
        }
    }
    
    private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            map[key] = value
        }
        return map
    }
    
    private fun mapToJsonObject(map: Map<String, Any>): JSONObject {
        val jsonObject = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    jsonObject.put(key, mapToJsonObject(value as Map<String, Any>))
                }
                is List<*> -> {
                    val jsonArray = org.json.JSONArray()
                    for (item in value) {
                        when (item) {
                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                jsonArray.put(mapToJsonObject(item as Map<String, Any>))
                            }
                            else -> jsonArray.put(item)
                        }
                    }
                    jsonObject.put(key, jsonArray)
                }
                else -> jsonObject.put(key, value)
            }
        }
        return jsonObject
    }

    private fun startHeartbeat() {
        // OpenAI Realtime API doesn't require explicit heartbeat messages
        // The WebSocket connection is maintained by the underlying protocol
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached")
            _connectionState.value = RealtimeConnectionState.Error(
                "Max reconnection attempts reached", 
                null
            )
            return
        }

        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            val delay = RECONNECT_DELAY_BASE_MS * (1L shl reconnectAttempts.coerceAtMost(5))
            Log.d(TAG, "Scheduling reconnection attempt ${reconnectAttempts + 1} in ${delay}ms")
            
            delay(delay)
            
            if (isActive && apiKey.isNotEmpty()) {
                reconnectAttempts++
                Log.d(TAG, "Attempting reconnection #$reconnectAttempts")
                connect(apiKey).collect { /* Restart connection flow */ }
            }
        }
    }
}