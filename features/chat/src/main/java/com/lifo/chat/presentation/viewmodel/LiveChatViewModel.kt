package com.lifo.chat.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.MongoDB
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LiveChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiConfigManager: ApiConfigManager,
    private val geminiWebSocketClient: GeminiLiveWebSocketClient,
    private val geminiAudioManager: GeminiLiveAudioManager,
    private val geminiCameraManager: GeminiLiveCameraManager,
    private val audioQualityAnalyzer: AudioQualityAnalyzer,
    private val conversationContextManager: ConversationContextManager,
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "LiveChatViewModel"
    }

    // Main UI State
    private val _uiState = MutableStateFlow(
        LiveChatUiState(
            connectionStatus = ConnectionStatus.Disconnected,
            hasAudioPermission = checkAudioPermission(),
            hasCameraPermission = checkCameraPermission(),
            isCameraActive = false,
            isMuted = false,
            turnState = TurnState.WaitingForUser,
            audioLevel = 0f,
            transcript = "",
            partialTranscript = "",
            error = null,
            aiEmotion = AIEmotion.Neutral,
            sessionId = null,
            isChannelOpen = false
        )
    )
    val uiState: StateFlow<LiveChatUiState> = _uiState.asStateFlow()
    private var aiSpeaking: Boolean = false

    // Current transcript from AI
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    // Advanced audio intelligence properties for liquid visualizer
    private val _userVoiceLevel = MutableStateFlow(0f)
    val userVoiceLevel: StateFlow<Float> = _userVoiceLevel.asStateFlow()

    private val _aiVoiceLevel = MutableStateFlow(0f)
    val aiVoiceLevel: StateFlow<Float> = _aiVoiceLevel.asStateFlow()

    private val _emotionalIntensity = MutableStateFlow(0.5f)
    val emotionalIntensity: StateFlow<Float> = _emotionalIntensity.asStateFlow()

    private val _conversationMode = MutableStateFlow("casual")
    val conversationMode: StateFlow<String> = _conversationMode.asStateFlow()

    // Track if audio channel is open
    private var isAudioChannelOpen = false

    // Current Live session ID for unification with Chat
    private var currentLiveSessionId: String? = null

    init {
        Log.d(TAG, "🎙️ Initializing LiveChatViewModel...")
        observeGeminiStates()
        setupGeminiCallbacks()
        setupCameraIntegration()
        setupIntelligentSystems()
        setupFunctionCalling()
    }

    private fun setupIntelligentSystems() {
        Log.d(TAG, "🧠 Setting up intelligent audio systems...")

        // Observe conversation context for adaptive optimization
        viewModelScope.launch {
            conversationContextManager.optimizationSettings.collectLatest { settings ->
                Log.d(TAG, "🎛️ Applying adaptive audio settings: ${settings.contextReason}")
                applyAudioOptimizationSettings(settings)
            }
        }

        // Observe audio quality metrics for real-time optimization
        viewModelScope.launch {
            audioQualityAnalyzer.overallQuality.collectLatest { quality ->
                Log.v(TAG, "📊 Audio quality: ${quality.grade} (${quality.totalScore})")

                if (quality.grade == AudioQualityAnalyzer.QualityGrade.POOR) {
                    handlePoorAudioQuality(quality)
                }
            }
        }

        // Start audio quality measurement when recording begins
        viewModelScope.launch {
            geminiAudioManager.recordingState.collectLatest { isRecording ->
                if (isRecording) {
                    audioQualityAnalyzer.startMeasurement()
                    Log.d(TAG, "📊 Audio quality measurement started")
                } else {
                    audioQualityAnalyzer.stopMeasurement()
                    Log.d(TAG, "📊 Audio quality measurement stopped")
                }
            }
        }

        // Real-time audio level updates for liquid visualizer
        viewModelScope.launch {
            geminiAudioManager.userAudioLevel.collectLatest { userLevel ->
                _userVoiceLevel.value = userLevel

                // Update emotional intensity based on conversation context
                val intensity = conversationContextManager.getCurrentEmotionalIntensity()
                _emotionalIntensity.value = intensity
            }
        }

        // AI voice level from real audio playback
        viewModelScope.launch {
            geminiAudioManager.aiAudioLevel.collectLatest { aiLevel ->
                _aiVoiceLevel.value = aiLevel
            }
        }

        // Conversation mode updates
        viewModelScope.launch {
            conversationContextManager.currentModeString.collectLatest { mode ->
                _conversationMode.value = mode
                Log.v(TAG, "🎭 Conversation mode updated: $mode")
            }
        }
    }

    private fun applyAudioOptimizationSettings(settings: ConversationContextManager.AudioOptimizationSettings) {
        Log.d(TAG, "🔧 Adaptive settings applied:")
        Log.d(TAG, "   • Barge-in sensitivity: ${settings.bargeinSensitivity}")
        Log.d(TAG, "   • Noise suppression: ${settings.noiseSuppressionLevel}")
        Log.d(TAG, "   • Echo cancellation: ${settings.echoCancellationLevel}")
        Log.d(TAG, "   • Reason: ${settings.contextReason}")
    }

    private fun handlePoorAudioQuality(quality: AudioQualityAnalyzer.OverallQualityScore) {
        Log.w(TAG, "⚠️ Poor audio quality detected: ${quality.primaryIssue}")
        quality.recommendations.forEach { recommendation ->
            Log.w(TAG, "💡 Recommendation: $recommendation")
        }

        _uiState.update {
            it.copy(error = "Audio quality issue: ${quality.primaryIssue}")
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun observeGeminiStates() {
        // Observe WebSocket connection state
        viewModelScope.launch {
            geminiWebSocketClient.connectionState.collectLatest { state ->
                Log.d(TAG, "🎯 Connection state: $state")

                val uiConnectionStatus = when (state) {
                    GeminiLiveWebSocketClient.ConnectionState.CONNECTED -> ConnectionStatus.Connected
                    GeminiLiveWebSocketClient.ConnectionState.CONNECTING -> ConnectionStatus.Connecting
                    GeminiLiveWebSocketClient.ConnectionState.ERROR -> ConnectionStatus.Error
                    GeminiLiveWebSocketClient.ConnectionState.DISCONNECTED -> ConnectionStatus.Disconnected
                }

                _uiState.update { it.copy(connectionStatus = uiConnectionStatus) }
            }
        }

        // Observe recording state (channel always open)
        viewModelScope.launch {
            geminiAudioManager.recordingState.collectLatest { isRecording ->
                _uiState.update {
                    it.copy(
                        isChannelOpen = isRecording,
                        aiEmotion = if (!it.isMuted && isRecording) AIEmotion.Thinking else AIEmotion.Neutral
                    )
                }
            }
        }

        // UPDATED: Observe playback state with proper half-duplex gating
        viewModelScope.launch {
            var lastIsPlaying = false
            geminiAudioManager.playbackState.collectLatest { isPlaying ->

                // PATCH #2: When AI starts speaking, immediately close user's stream
                if (isPlaying && !lastIsPlaying) {
                    Log.d(TAG, "🔊 AI started speaking - sending EOS to flush VAD buffer")
                    geminiWebSocketClient.sendEndOfStream()
                }

                aiSpeaking = isPlaying
                lastIsPlaying = isPlaying

                // Notify audio manager for half-duplex control
                geminiAudioManager.setAiSpeaking(isPlaying)

                _uiState.update {
                    it.copy(aiEmotion = if (isPlaying) AIEmotion.Speaking else AIEmotion.Neutral)
                }
            }
        }

        // Observe camera state
        viewModelScope.launch {
            geminiCameraManager.isCameraActive.collectLatest { isActive ->
                _uiState.update { it.copy(isCameraActive = isActive) }
            }
        }
    }

    private fun setupGeminiCallbacks() {
        // Partial transcript from user speech
        geminiWebSocketClient.onPartialTranscript = { partial ->
            Log.d(TAG, "🎤 Partial transcript: $partial")
            _uiState.update { it.copy(partialTranscript = partial) }
        }

        // Final transcript from user speech
        geminiWebSocketClient.onFinalTranscript = { final ->
            Log.d(TAG, "🎤 Final transcript: $final")
            _uiState.update { it.copy(transcript = final, partialTranscript = "") }

            // Add to conversation context for intelligent optimization
            conversationContextManager.addMessage(
                content = final,
                isFromUser = true,
                audioLevel = _uiState.value.audioLevel,
                duration = 0L // Could be calculated from speaking event timing
            )
        }

        // AI turn started
        geminiWebSocketClient.onTurnStarted = {
            Log.d(TAG, "🤖 AI turn started")
            _uiState.update { it.copy(turnState = TurnState.AgentTurn, aiEmotion = AIEmotion.Speaking) }
        }

        // Turn completed
        geminiWebSocketClient.onTurnCompleted = {
            Log.d(TAG, "✅ Turn completed")
            _uiState.update { it.copy(turnState = TurnState.WaitingForUser, aiEmotion = AIEmotion.Neutral) }
        }

        // Interruption (barge-in)
        geminiWebSocketClient.onInterrupted = {
            Log.d(TAG, "⚠️ AI interrupted by user (barge-in detected)")
            handleBargeIn()
        }

        // PATCH #2: Connect TTS events to audio manager
        // Quando parte il TTS dell'AI
        geminiWebSocketClient.onTtsStarted = {
            Log.d(TAG, "🔊 TTS started - blocking mic input")

            // Blocca l'upload audio (PATCH #1 in AudioManager)
            geminiAudioManager.setAiSpeaking(true)

            // Opzionale: se vuoi fermare completamente la registrazione durante il TTS
            // geminiAudioManager.stopRecording()
        }

        // Quando finisce il TTS dell'AI
        geminiWebSocketClient.onTtsEnded = {
            Log.d(TAG, "🔇 TTS ended - resuming mic input")

            // Riabilita l'upload audio
            geminiAudioManager.setAiSpeaking(false)

            // Se avevi fermato la registrazione, riavviala
            // if (_uiState.value.connectionStatus == ConnectionStatus.Connected) {
            //     geminiAudioManager.startRecording()
            // }
        }

        // Text from Gemini
        geminiWebSocketClient.onTextReceived = { text ->
            Log.d(TAG, "📝 Text from Gemini: $text")
            _currentTranscript.value = text
            _uiState.update { it.copy(transcript = text) }

            // Add AI response to conversation context
            conversationContextManager.addMessage(
                content = text,
                isFromUser = false,
                audioLevel = 0f, // AI audio level not applicable for text
                duration = 0L
            )
        }

        // Audio from Gemini
        geminiWebSocketClient.onAudioReceived = { audioBase64 ->
            Log.d(TAG, "🔊 Audio from Gemini (${audioBase64.length} chars)")
            geminiAudioManager.queueAudioForPlayback(audioBase64)

            // Update audio level for visualization (simulated)
            _uiState.update { it.copy(audioLevel = 0.7f) }

            // Clear audio level after a delay
            viewModelScope.launch {
                delay(2000)
                _uiState.update { it.copy(audioLevel = 0f) }
            }
        }

        // Errors
        geminiWebSocketClient.onError = { error ->
            Log.e(TAG, "❌ Error: $error")
            _uiState.update {
                it.copy(
                    error = error,
                    connectionStatus = ConnectionStatus.Error
                )
            }
        }

        // PATCH #1 INTEGRATION: Send audio chunks to Gemini with half-duplex gating
        geminiAudioManager.onAudioChunkReady = { audioBase64 ->
            // Only send audio when not muted AND AI is not speaking
            if (!_uiState.value.isMuted && !aiSpeaking) {
                geminiWebSocketClient.sendAudioData(audioBase64)
            } else if (aiSpeaking) {
                // Log for debugging when audio is blocked due to AI speaking
                Log.v(TAG, "🚫 Audio chunk blocked - AI is speaking (half-duplex)")
            }
        }

        // Gestione salvataggio messaggi Live in Chat DB
        geminiWebSocketClient.onChatMessageSaved = { sessionId, content, isUser ->
            currentLiveSessionId = sessionId
            viewModelScope.launch {
                try {
                    chatRepository.saveLiveMessage(sessionId, content, isUser)
                    Log.d(TAG, "💾 Live message integrated into Chat DB")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to save Live message to Chat DB", e)
                }
            }
        }

        // Gestione barge-in smart
        geminiAudioManager.onBargeInDetected = {
            Log.d(TAG, "🗣️ Smart barge-in detected - interrupting AI")
            handleSmartBargeIn()
        }
    }

    private fun setupCameraIntegration() {
        // Send images to Gemini
        geminiCameraManager.onImageCaptured = { imageBase64 ->
            Log.d(TAG, "📸 Sending image to Gemini Live (${imageBase64.length} chars)")
            viewModelScope.launch {
                try {
                    geminiWebSocketClient.sendImageData(imageBase64)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send image to Gemini", e)
                    _uiState.update { it.copy(error = "Failed to send image: ${e.message}") }
                }
            }
        }
    }

    fun onAudioPermissionGranted() {
        Log.d(TAG, "🎤 Audio permission granted")
        _uiState.update { it.copy(hasAudioPermission = true) }
        connectToRealtime()
    }

    fun onAudioPermissionDenied() {
        Log.d(TAG, "❌ Audio permission denied")
        _uiState.update {
            it.copy(
                hasAudioPermission = false,
                error = "Audio permission required for voice chat"
            )
        }
    }

    fun onCameraPermissionGranted() {
        Log.d(TAG, "📸 Camera permission granted")
        _uiState.update { it.copy(hasCameraPermission = true) }
        Log.d(TAG, "📸 Updated UI state - hasCameraPermission: true")
    }

    fun onCameraPermissionDenied() {
        Log.d(TAG, "❌ Camera permission denied")
        _uiState.update {
            it.copy(
                hasCameraPermission = false,
                error = "Camera permission is optional but enhances the experience"
            )
        }
    }

    fun startCameraPreview(surfaceTexture: SurfaceTexture) {
        Log.d(TAG, "📝 startCameraPreview() called in LiveChatViewModel")
        Log.d(TAG, "📝 hasCameraPermission: ${_uiState.value.hasCameraPermission}")
        Log.d(TAG, "📝 isCameraActive: ${_uiState.value.isCameraActive}")
        Log.d(TAG, "📝 surfaceTexture: $surfaceTexture")

        if (!_uiState.value.hasCameraPermission) {
            Log.w(TAG, "❌ Cannot start camera without permission")
            return
        }

        Log.d(TAG, "📸 Starting camera preview - launching coroutine...")
        viewModelScope.launch {
            try {
                Log.d(TAG, "📸 Calling geminiCameraManager.startCameraPreview()...")
                geminiCameraManager.startCameraPreview(surfaceTexture)
                Log.d(TAG, "📸 geminiCameraManager.startCameraPreview() completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start camera preview", e)
                _uiState.update { it.copy(error = "Failed to start camera: ${e.message}") }
            }
        }
    }

    fun stopCameraPreview() {
        Log.d(TAG, "📸 Stopping camera preview")
        viewModelScope.launch {
            try {
                geminiCameraManager.stopCameraPreview()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to stop camera preview", e)
            }
        }
    }

    fun connectToRealtime() {
        if (!_uiState.value.hasAudioPermission) {
            Log.w(TAG, "Cannot connect without audio permission")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Connecting to Gemini Live with VAD...")
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Connecting, error = null) }

                val apiKey = apiConfigManager.getGeminiApiKey()
                if (apiKey.isEmpty()) {
                    throw IllegalStateException("Gemini API key not configured")
                }

                Log.d(TAG, "🔑 Using API key: ${apiKey.take(10)}...")
                geminiWebSocketClient.connect(apiKey)

                // Avvio canale audio dopo la connessione
                delay(500)
                if (_uiState.value.connectionStatus == ConnectionStatus.Connected) {
                    // Genera ID sessione per unificare Live e Chat
                    currentLiveSessionId = "live-${System.currentTimeMillis()}"
                    Log.d(TAG, "🆔 Generated Live session ID: $currentLiveSessionId")

                    startAudioChannel()

                    // Start voice learning and context reset for new session
                    geminiAudioManager.startVoiceLearning()
                    conversationContextManager.resetContext()
                    Log.d(TAG, "🧠 Intelligent systems initialized for new session")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Connection failed", e)
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.Error,
                        error = "Connection failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnectFromRealtime() {
        viewModelScope.launch {
            Log.d(TAG, "🔌 Disconnecting...")

            isAudioChannelOpen = false
            geminiAudioManager.stopRecording()
            geminiAudioManager.stopPlayback()
            geminiCameraManager.stopCameraPreview()
            geminiWebSocketClient.disconnect()

            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Disconnected,
                    turnState = TurnState.WaitingForUser,
                    isMuted = false,
                    isChannelOpen = false,
                    audioLevel = 0f,
                    transcript = "",
                    partialTranscript = "",
                    error = null,
                    aiEmotion = AIEmotion.Neutral,
                    isCameraActive = false
                )
            }

            _currentTranscript.value = ""
            currentLiveSessionId = null
        }
    }

    /**
     * Avvia il canale audio (always-on con VAD server). Niente commit periodico.
     */
    private fun startAudioChannel() {
        if (!isAudioChannelOpen && _uiState.value.connectionStatus == ConnectionStatus.Connected) {
            Log.d(TAG, "🎤 Starting always-open audio channel with VAD (no commit)")
            try {
                geminiAudioManager.startRecording()
                isAudioChannelOpen = true
                _uiState.update { it.copy(isChannelOpen = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio channel", e)
                _uiState.update { it.copy(error = "Failed to start audio: ${e.message}") }
            }
        }
    }

    /** Gestisce barge-in (interruzione utente durante risposta AI) */
    private fun handleBargeIn() {
        Log.d(TAG, "💯 Handling barge-in: stopping AI, switching to user turn")

        // ✅ Ferma subito la riproduzione TTS locale
        geminiAudioManager.handleInterruption()

        // ✅ Aggiorna la UI: ora è turno utente
        _uiState.update {
            it.copy(
                turnState = TurnState.UserTurn,
                aiEmotion = AIEmotion.Neutral
            )
        }
    }

    /** Gestisce smart barge-in (rilevato localmente) */
    private fun handleSmartBargeIn() {
        Log.d(TAG, "🎯 Smart barge-in: immediate TTS stop, user audio resumes")

        // ✅ Stop immediato TTS locale
        geminiAudioManager.handleInterruption()

        // ✅ Reset stato AI speaking (permette invio audio utente)
        aiSpeaking = false

        // ✅ UI feedback immediato
        _uiState.update {
            it.copy(
                turnState = TurnState.UserTurn,
                aiEmotion = AIEmotion.Thinking
            )
        }
    }

    /** Toggle mute/unmute con gestione VAD (flush su mute) */
    fun toggleMute() {
        val newMuteState = !_uiState.value.isMuted
        Log.d(TAG, if (newMuteState) "🔇 Muting microphone - flushing VAD buffer" else "🎤 Unmuting microphone - resuming VAD")

        _uiState.update { it.copy(isMuted = newMuteState) }

        if (newMuteState) {
            // Quando si muta, invia audioStreamEnd per svuotare buffer VAD
            geminiWebSocketClient.sendEndOfStream()
            _uiState.update { it.copy(turnState = TurnState.WaitingForUser) }
        } else {
            _uiState.update { it.copy(turnState = TurnState.WaitingForUser) }
        }
        // Il canale resta sempre aperto: non fermiamo la registrazione
    }

    fun isMuted(): Boolean = _uiState.value.isMuted

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retryConnection() {
        clearError()
        connectToRealtime()
    }

    /**
     * Configura il function calling per l'accesso ai diari
     */
    private fun setupFunctionCalling() {
        // Callback per fornire dati utente al WebSocket
        geminiWebSocketClient.onNeedUserData = {
            try {
                val userName = firebaseAuth.currentUser?.displayName ?:
                firebaseAuth.currentUser?.email?.substringBefore("@") ?:
                "Utente"

                // Recupera gli ultimi 4 diari per il context
                val diariesSummary = getDiariesSummary()

                Pair(userName, diariesSummary)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user data", e)
                Pair("Utente", "Nessun diario disponibile")
            }
        }

        // Callback per eseguire le funzioni
        geminiWebSocketClient.onExecuteFunction = { functionName, args ->
            try {
                when (functionName) {
                    "get_recent_diaries" -> executeGetRecentDiariesFunction(args)
                    "search_diary" -> executeSearchDiaryFunction(args)
                    else -> JSONObject().apply {
                        put("error", "Unknown function: $functionName")
                        put("results", JSONArray())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing function $functionName", e)
                JSONObject().apply {
                    put("error", "Function execution failed")
                    put("message", e.message)
                    put("results", JSONArray())
                }
            }
        }
    }

    /**
     * Ottiene un sommario dei diari recenti
     */
    private suspend fun getDiariesSummary(): String {
        return try {
            val diariesResult = MongoDB.getAllDiaries().first()
            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val recentDiaries = diariesResult.data
                        .flatMap { it.value }
                        .sortedByDescending { diary ->
                            val realmInstant = diary.date
                            java.time.Instant.ofEpochSecond(
                                realmInstant.epochSeconds,
                                realmInstant.nanosecondsOfSecond.toLong()
                            )
                        }
                        .take(4)

                    recentDiaries.joinToString("\n") { diary ->
                        "- ${diary.title} (${diary.mood}): ${diary.description.take(100)}..."
                    }
                }
                else -> "Nessun diario disponibile"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting diaries summary", e)
            "Errore nel recuperare i diari"
        }
    }

    /**
     * Esegue la funzione get_recent_diaries
     */
    private suspend fun executeGetRecentDiariesFunction(args: JSONObject): JSONObject {
        val limit = args.optInt("limit", 4).coerceAtMost(10)

        return try {
            val diariesResult = MongoDB.getAllDiaries().first()

            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val recentDiaries = diariesResult.data
                        .flatMap { it.value }
                        .sortedByDescending { diary ->
                            val realmInstant = diary.date
                            java.time.Instant.ofEpochSecond(
                                realmInstant.epochSeconds,
                                realmInstant.nanosecondsOfSecond.toLong()
                            )
                        }
                        .take(limit)

                    Log.d(TAG, "📖 Retrieved ${recentDiaries.size} recent diaries for function call")

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            recentDiaries.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id.toHexString())
                                    put("dateISO", try {
                                        val realmInstant = diary.date
                                        java.time.Instant.ofEpochSecond(
                                            realmInstant.epochSeconds,
                                            realmInstant.nanosecondsOfSecond.toLong()
                                        ).toString()
                                    } catch (e: Exception) {
                                        java.time.Instant.now().toString()
                                    })
                                    put("title", diary.title)
                                    put("mood", diary.mood)
                                    put("snippet", diary.description.take(200))
                                })
                            }
                        })
                        put("total", recentDiaries.size)
                    }
                }
                else -> JSONObject().apply {
                    put("error", "Failed to retrieve diaries")
                    put("results", JSONArray())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing get_recent_diaries", e)
            JSONObject().apply {
                put("error", "Function execution failed")
                put("message", e.message)
                put("results", JSONArray())
            }
        }
    }

    /**
     * Esegue la funzione search_diary
     */
    private suspend fun executeSearchDiaryFunction(args: JSONObject): JSONObject {
        val query = args.getString("query")
        val k = args.optInt("k", 5).coerceAtMost(20)

        return try {
            val diariesResult = MongoDB.getAllDiaries().first()

            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val allDiaries = diariesResult.data.flatMap { it.value }

                    // Ricerca semplice nei titoli e descrizioni
                    val searchResults = allDiaries.filter { diary ->
                        diary.title.contains(query, ignoreCase = true) ||
                                diary.description.contains(query, ignoreCase = true)
                    }.sortedByDescending { diary ->
                        val realmInstant = diary.date
                        java.time.Instant.ofEpochSecond(
                            realmInstant.epochSeconds,
                            realmInstant.nanosecondsOfSecond.toLong()
                        )
                    }.take(k)

                    Log.d(TAG, "🔍 Search for '$query' returned ${searchResults.size} results")

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            searchResults.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id.toHexString())
                                    put("dateISO", try {
                                        val realmInstant = diary.date
                                        java.time.Instant.ofEpochSecond(
                                            realmInstant.epochSeconds,
                                            realmInstant.nanosecondsOfSecond.toLong()
                                        ).toString()
                                    } catch (e: Exception) {
                                        java.time.Instant.now().toString()
                                    })
                                    put("title", diary.title)
                                    put("mood", diary.mood)
                                    put("snippet", diary.description.take(200))
                                    put("relevance", calculateRelevance(diary, query))
                                })
                            }
                        })
                        put("query", query)
                        put("total", searchResults.size)
                    }
                }
                else -> JSONObject().apply {
                    put("error", "Failed to search diaries")
                    put("results", JSONArray())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing search_diary", e)
            JSONObject().apply {
                put("error", "Function execution failed")
                put("message", e.message)
                put("results", JSONArray())
            }
        }
    }

    /**
     * Calcola la rilevanza di un diario rispetto alla query
     */
    private fun calculateRelevance(diary: com.lifo.util.model.Diary, query: String): Double {
        val titleMatches = diary.title.contains(query, ignoreCase = true)
        val descriptionMatches = diary.description.contains(query, ignoreCase = true)
        val moodMatches = diary.mood.contains(query, ignoreCase = true)

        return when {
            titleMatches && descriptionMatches -> 1.0
            titleMatches -> 0.8
            descriptionMatches -> 0.6
            moodMatches -> 0.4
            else -> 0.2
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            disconnectFromRealtime()
            geminiAudioManager.release()
            geminiCameraManager.release()
        }
    }
}