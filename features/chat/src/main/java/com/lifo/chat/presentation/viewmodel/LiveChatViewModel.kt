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
import com.lifo.chat.audio.GeminiLiveAudioSource
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.MongoRepository
import com.lifo.util.speech.InterruptionReason
import com.lifo.util.speech.SpeechAnimationTarget
import com.lifo.util.speech.SynchronizedSpeechController
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
    private val liveAudioSource: GeminiLiveAudioSource,
    private val synchronizedSpeechController: SynchronizedSpeechController,
    private val audioQualityAnalyzer: AudioQualityAnalyzer,
    private val conversationContextManager: ConversationContextManager,
    private val chatRepository: ChatRepository,
    private val diaryRepository: MongoRepository,
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

    // Synchronized speech state for avatar integration
    val isSynchronizedSpeaking = synchronizedSpeechController.isSpeaking

    // Callback for playing avatar gestures (injected at integration layer to avoid circular dependency)
    private var onPlayGestureCallback: ((String) -> Unit)? = null

    init {
        Log.d(TAG, "🎙️ Initializing LiveChatViewModel...")
        observeGeminiStates()
        setupGeminiCallbacks()
        setupCameraIntegration()
        setupIntelligentSystems()
        setupFunctionCalling()
        initializeSynchronizedSpeech()
    }

    /**
     * Initialize synchronized speech by attaching the live audio source
     */
    private fun initializeSynchronizedSpeech() {
        Log.d(TAG, "🔗 Initializing synchronized speech for Live mode...")
        synchronizedSpeechController.attachAudioSource(liveAudioSource)
    }

    /**
     * Attach HumanoidController for synchronized lip-sync.
     * Call this from the UI layer when humanoid is ready.
     */
    fun attachHumanoidController(controller: SpeechAnimationTarget) {
        Log.d(TAG, "🤖 Attaching HumanoidController for synchronized lip-sync (Live mode)")
        synchronizedSpeechController.attachAnimationTarget(controller)
    }

    /**
     * Attach gesture animation callback.
     * This is called from the integration layer (app module) to avoid circular dependency.
     * The callback receives animation names from Gemini AI and maps them to avatar gestures.
     */
    fun attachGestureCallback(callback: (String) -> Unit) {
        Log.d(TAG, "🎭 Attaching gesture callback for avatar animations")
        onPlayGestureCallback = callback

        // Setup animation callback from Gemini AI
        geminiWebSocketClient.onPlayAnimation = { animationName ->
            Log.d(TAG, "🎭 AI requested animation: $animationName")
            callback(animationName)
        }
    }

    /**
     * Detach HumanoidController when no longer needed.
     */
    fun detachHumanoidController() {
        Log.d(TAG, "🤖 Detaching HumanoidController (Live mode)")
        synchronizedSpeechController.detachAnimationTarget()
        onPlayGestureCallback = null
        geminiWebSocketClient.onPlayAnimation = null
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

                // Start audio channel when WebSocket connects
                if (state == GeminiLiveWebSocketClient.ConnectionState.CONNECTED && !isAudioChannelOpen) {
                    Log.d(TAG, "🎤 WebSocket connected - starting audio streaming")
                    startAudioChannel()
                }
            }
        }

        // Observe recording state from audio manager
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

        // Observe user audio level from audio manager
        viewModelScope.launch {
            geminiAudioManager.userAudioLevel.collectLatest { level ->
                _uiState.update { it.copy(audioLevel = level) }
            }
        }

        // Observe AI playback state
        viewModelScope.launch {
            geminiAudioManager.playbackState.collectLatest { isPlaying ->
                aiSpeaking = isPlaying
                geminiAudioManager.setAiSpeaking(isPlaying)

                _uiState.update {
                    it.copy(aiEmotion = if (isPlaying) AIEmotion.Speaking else AIEmotion.Neutral)
                }

                if (!isPlaying) {
                    Log.d(TAG, "🎤 AI finished speaking - user can now speak again")
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
        // Partial transcript from Gemini (user's speech transcribed by server)
        geminiWebSocketClient.onPartialTranscript = { partial ->
            Log.d(TAG, "🎤 Partial transcript: $partial")
            _uiState.update { it.copy(partialTranscript = partial) }
        }

        // Final transcript from Gemini (user's speech transcribed by server)
        geminiWebSocketClient.onFinalTranscript = { final ->
            Log.d(TAG, "✅ Final transcript: $final")
            _uiState.update { it.copy(transcript = final, partialTranscript = "") }

            // Add to conversation context
            conversationContextManager.addMessage(
                content = final,
                isFromUser = true,
                audioLevel = _uiState.value.audioLevel,
                duration = 0L
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

        // Text from Gemini
        geminiWebSocketClient.onTextReceived = { text ->
            Log.d(TAG, "📝 Text from Gemini: $text")
            _currentTranscript.value = text
            _uiState.update { it.copy(transcript = text) }

            // NUOVO: Add AI response to conversation context
            conversationContextManager.addMessage(
                content = text,
                isFromUser = false,
                audioLevel = 0f, // AI audio level not applicable for text
                duration = 0L
            )

            // 🎬 Prepare synchronized lip-sync with the text
            // Audio will start shortly after, and lip-sync will begin simultaneously
            liveAudioSource.prepareWithText(text)
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

        // NO LONGER NEEDED: Audio chunks are not sent, we send text instead
        // The VAD+STT system handles transcription locally

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
        Log.d(TAG, "🔍 startCameraPreview() called in LiveChatViewModel")
        Log.d(TAG, "🔍 hasCameraPermission: ${_uiState.value.hasCameraPermission}")
        Log.d(TAG, "🔍 isCameraActive: ${_uiState.value.isCameraActive}")
        Log.d(TAG, "🔍 surfaceTexture: $surfaceTexture")

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

                // Session ID and context will be initialized when connection state changes to CONNECTED
                // (handled in observeGeminiStates)
                currentLiveSessionId = "live-${System.currentTimeMillis()}"
                Log.d(TAG, "🆔 Generated Live session ID: $currentLiveSessionId")
                conversationContextManager.resetContext()
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
            geminiAudioManager.stopRecording()  // Stop audio recording
            geminiAudioManager.stopPlayback()   // Stop AI audio playback
            geminiCameraManager.stopCameraPreview()
            geminiWebSocketClient.disconnect()

            // Reset synchronized speech
            liveAudioSource.reset()

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
     * Avvia il canale audio (always-on con VAD server). Audio streaming diretto a Gemini.
     */
    private fun startAudioChannel() {
        if (!isAudioChannelOpen && _uiState.value.connectionStatus == ConnectionStatus.Connected) {
            Log.d(TAG, "🎤 Starting audio streaming to Gemini")
            try {
                // Setup audio callback to send chunks to WebSocket
                geminiAudioManager.onAudioChunkReady = { audioBase64 ->
                    geminiWebSocketClient.sendAudioData(audioBase64)
                }

                // Setup barge-in callback
                geminiAudioManager.onBargeInDetected = {
                    Log.d(TAG, "🗣️ Barge-in detected!")
                    handleSmartBargeIn()
                }

                // Start recording
                geminiAudioManager.startRecording()
                isAudioChannelOpen = true
                _uiState.update { it.copy(isChannelOpen = true) }

                Log.d(TAG, "✅ Audio streaming started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio streaming", e)
                _uiState.update { it.copy(error = "Failed to start voice: ${e.message}") }
            }
        }
    }

    /** Gestisce barge-in (interruzione utente durante risposta AI) */
    private fun handleBargeIn() {
        Log.d(TAG, "💯 Handling barge-in: stopping AI with fade-out, switching to user turn")

        // ✅ Ferma la riproduzione TTS con fade-out graduale (come Gemini Live desktop)
        geminiAudioManager.handleInterruption()

        // 🎬 Stop synchronized lip-sync immediately
        liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

        // ✅ Reset stato AI speaking (permette invio audio utente IMMEDIATAMENTE)
        aiSpeaking = false
        geminiAudioManager.setAiSpeaking(false)

        // ✅ Aggiorna la UI: ora è turno utente
        _uiState.update {
            it.copy(
                turnState = TurnState.UserTurn,
                aiEmotion = AIEmotion.Neutral
            )
        }
    }

    /** NUOVO: Gestisce smart barge-in (rilevato localmente con echo cancellation) */
    private fun handleSmartBargeIn() {
        Log.d(TAG, "🎯 Smart barge-in: fade-out TTS, user audio resumes immediately")

        // ✅ Stop TTS con fade-out graduale (come Gemini Live desktop)
        geminiAudioManager.handleInterruption()

        // 🎬 Stop synchronized lip-sync immediately
        liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

        // ✅ Reset stato AI speaking (permette invio audio utente IMMEDIATAMENTE)
        aiSpeaking = false
        geminiAudioManager.setAiSpeaking(false)

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

    /**
     * Invia un messaggio testuale durante una conversazione Live.
     * Utile per digitare mentre si è in modalità vocale.
     */
    fun sendTextMessage(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Cannot send empty text message")
            return
        }

        Log.d(TAG, "💬 Sending text message from ViewModel: ${text.take(50)}...")
        geminiWebSocketClient.sendTextMessage(text)

        // Aggiorna lo stato UI per mostrare il messaggio inviato
        _uiState.update { it.copy(transcript = text, partialTranscript = "") }

        // Aggiungi al context manager per intelligenza conversazionale
        conversationContextManager.addMessage(
            content = text,
            isFromUser = true,
            audioLevel = 0f,
            duration = 0L
        )
    }

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
            val diariesResult = diaryRepository.getAllDiaries().first()
            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val recentDiaries = diariesResult.data
                        .flatMap { it.value }
                        .sortedByDescending { diary ->
                            diary.date.toInstant()
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
            val diariesResult = diaryRepository.getAllDiaries().first()

            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val recentDiaries = diariesResult.data
                        .flatMap { it.value }
                        .sortedByDescending { diary ->
                            diary.date.toInstant()
                        }
                        .take(limit)

                    Log.d(TAG, "📖 Retrieved ${recentDiaries.size} recent diaries for function call")

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            recentDiaries.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id)
                                    put("dateISO", diary.date.toInstant().toString())
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
            val diariesResult = diaryRepository.getAllDiaries().first()

            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val allDiaries = diariesResult.data.flatMap { it.value }

                    // Ricerca semplice nei titoli e descrizioni
                    val searchResults = allDiaries.filter { diary ->
                        diary.title.contains(query, ignoreCase = true) ||
                                diary.description.contains(query, ignoreCase = true)
                    }.sortedByDescending { diary ->
                        diary.date.toInstant()
                    }.take(k)

                    Log.d(TAG, "🔍 Search for '$query' returned ${searchResults.size} results")

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            searchResults.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id)
                                    put("dateISO", diary.date.toInstant().toString())
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
            synchronizedSpeechController.release()
            geminiAudioManager.release()
            geminiCameraManager.release()
        }
    }
}
