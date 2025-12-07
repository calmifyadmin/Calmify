package com.lifo.app.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.audio.GeminiLiveAudioSource
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.TurnState
import com.lifo.humanoid.domain.model.Emotion
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.MongoRepository
import com.lifo.util.speech.InterruptionReason
import com.lifo.util.speech.SpeechAnimationTarget
import com.lifo.util.speech.SynchronizedSpeechController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Avatar Live Chat ViewModel
 *
 * Orchestrates the integration between Gemini Live API and VRM Avatar.
 * This ViewModel manages:
 * - WebSocket connection to Gemini Live API
 * - Bidirectional audio streaming
 * - Camera streaming for vision capabilities
 * - Synchronized lip-sync with live audio
 * - Real-time emotion mapping
 * - Barge-in detection and handling
 *
 * Architecture:
 * - Uses GeminiLiveAudioSource for lip-sync event bridging
 * - Uses SynchronizedSpeechController for avatar animation sync
 * - Emits emotions based on AI state and transcript analysis
 */
@HiltViewModel
class AvatarLiveChatViewModel @Inject constructor(
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
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "AvatarLiveChatVM"
    }

    // Main UI State
    private val _uiState = MutableStateFlow(AvatarLiveChatUiState())
    val uiState: StateFlow<AvatarLiveChatUiState> = _uiState.asStateFlow()

    // AI speaking state for half-duplex gating
    private var aiSpeaking: Boolean = false

    // Current transcript from AI
    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    // Audio levels for visualizer
    private val _userVoiceLevel = MutableStateFlow(0f)
    val userVoiceLevel: StateFlow<Float> = _userVoiceLevel.asStateFlow()

    private val _aiVoiceLevel = MutableStateFlow(0f)
    val aiVoiceLevel: StateFlow<Float> = _aiVoiceLevel.asStateFlow()

    private val _emotionalIntensity = MutableStateFlow(0.5f)
    val emotionalIntensity: StateFlow<Float> = _emotionalIntensity.asStateFlow()

    private val _conversationMode = MutableStateFlow("casual")
    val conversationMode: StateFlow<String> = _conversationMode.asStateFlow()

    // Avatar emotion for UI display
    private val _avatarEmotion = MutableStateFlow<Emotion>(Emotion.Neutral)
    val avatarEmotion: StateFlow<Emotion> = _avatarEmotion.asStateFlow()

    // Audio channel state
    private var isAudioChannelOpen = false

    // Current Live session ID
    private var currentLiveSessionId: String? = null

    // Synchronized speech state
    val isSynchronizedSpeaking = synchronizedSpeechController.isSpeaking

    init {
        Log.d(TAG, "Initializing AvatarLiveChatViewModel...")
        checkPermissions()
        observeGeminiStates()
        setupGeminiCallbacks()
        setupCameraIntegration()
        setupIntelligentSystems()
        setupFunctionCalling()
        initializeSynchronizedSpeech()
    }

    private fun checkPermissions() {
        _uiState.update {
            it.copy(
                hasAudioPermission = checkAudioPermission(),
                hasCameraPermission = checkCameraPermission()
            )
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

    /**
     * Initialize synchronized speech by attaching the live audio source
     */
    private fun initializeSynchronizedSpeech() {
        Log.d(TAG, "Initializing synchronized speech for Avatar Live mode...")
        synchronizedSpeechController.attachAudioSource(liveAudioSource)
    }

    /**
     * Attach HumanoidController for synchronized lip-sync.
     * Call this from the UI layer when humanoid is ready.
     */
    fun attachHumanoidController(controller: SpeechAnimationTarget) {
        Log.d(TAG, "Attaching HumanoidController for synchronized lip-sync")
        synchronizedSpeechController.attachAnimationTarget(controller)
    }

    /**
     * Detach HumanoidController when no longer needed.
     */
    fun detachHumanoidController() {
        Log.d(TAG, "Detaching HumanoidController")
        synchronizedSpeechController.detachAnimationTarget()
    }

    private fun setupIntelligentSystems() {
        Log.d(TAG, "Setting up intelligent audio systems...")

        // Observe conversation context for adaptive optimization
        viewModelScope.launch {
            conversationContextManager.optimizationSettings.collectLatest { settings ->
                Log.d(TAG, "Applying adaptive audio settings: ${settings.contextReason}")
            }
        }

        // Observe audio quality metrics
        viewModelScope.launch {
            audioQualityAnalyzer.overallQuality.collectLatest { quality ->
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
                } else {
                    audioQualityAnalyzer.stopMeasurement()
                }
            }
        }

        // Real-time audio level updates for visualizer
        viewModelScope.launch {
            geminiAudioManager.userAudioLevel.collectLatest { userLevel ->
                _userVoiceLevel.value = userLevel
                _emotionalIntensity.value = conversationContextManager.getCurrentEmotionalIntensity()
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
            }
        }
    }

    private fun handlePoorAudioQuality(quality: AudioQualityAnalyzer.OverallQualityScore) {
        Log.w(TAG, "Poor audio quality detected: ${quality.primaryIssue}")
        _uiState.update {
            it.copy(error = "Audio quality issue: ${quality.primaryIssue}")
        }
    }

    private fun observeGeminiStates() {
        // Observe WebSocket connection state
        viewModelScope.launch {
            geminiWebSocketClient.connectionState.collectLatest { state ->
                Log.d(TAG, "Connection state: $state")

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

        // Observe playback state (gating half-duplex)
        viewModelScope.launch {
            var lastIsPlaying = false
            geminiAudioManager.playbackState.collectLatest { isPlaying ->
                // When AI starts speaking, close user turn server-side
                if (isPlaying && !lastIsPlaying) {
                    geminiWebSocketClient.sendEndOfStream()
                }
                aiSpeaking = isPlaying
                lastIsPlaying = isPlaying

                // Notify audio manager for barge-in detection
                geminiAudioManager.setAiSpeaking(isPlaying)

                _uiState.update {
                    it.copy(
                        aiEmotion = if (isPlaying) AIEmotion.Speaking else AIEmotion.Neutral,
                        isSpeaking = isPlaying
                    )
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
            Log.d(TAG, "Partial transcript: $partial")
            _uiState.update { it.copy(partialTranscript = partial) }
        }

        // Final transcript from user speech
        geminiWebSocketClient.onFinalTranscript = { final ->
            Log.d(TAG, "Final transcript: $final")
            _uiState.update { it.copy(currentTranscript = final, partialTranscript = "") }

            // Add to conversation context
            conversationContextManager.addMessage(
                content = final,
                isFromUser = true,
                audioLevel = _uiState.value.userAudioLevel,
                duration = 0L
            )
        }

        // AI turn started
        geminiWebSocketClient.onTurnStarted = {
            Log.d(TAG, "AI turn started")
            _uiState.update {
                it.copy(
                    turnState = TurnState.AgentTurn,
                    aiEmotion = AIEmotion.Speaking
                )
            }
            _avatarEmotion.value = Emotion.Neutral // Reset, lip-sync will handle mouth
        }

        // Turn completed
        geminiWebSocketClient.onTurnCompleted = {
            Log.d(TAG, "Turn completed")
            _uiState.update {
                it.copy(
                    turnState = TurnState.WaitingForUser,
                    aiEmotion = AIEmotion.Neutral
                )
            }
            _avatarEmotion.value = Emotion.Neutral
        }

        // Interruption (barge-in)
        geminiWebSocketClient.onInterrupted = {
            Log.d(TAG, "AI interrupted by user (barge-in detected)")
            handleBargeIn()
        }

        // Text from Gemini - triggers emotion detection and lip-sync prep
        geminiWebSocketClient.onTextReceived = { text ->
            Log.d(TAG, "Text from Gemini: $text")
            _currentTranscript.value = text
            _uiState.update { it.copy(currentTranscript = text) }

            // Add AI response to conversation context
            conversationContextManager.addMessage(
                content = text,
                isFromUser = false,
                audioLevel = 0f,
                duration = 0L
            )

            // Detect and apply emotion from text
            val detectedEmotion = detectEmotionFromText(text)
            _avatarEmotion.value = detectedEmotion

            // Prepare synchronized lip-sync with the text
            liveAudioSource.prepareWithText(text)
        }

        // Audio from Gemini
        geminiWebSocketClient.onAudioReceived = { audioBase64 ->
            Log.d(TAG, "Audio from Gemini (${audioBase64.length} chars)")
            geminiAudioManager.queueAudioForPlayback(audioBase64)

            // Update audio level for visualization
            _uiState.update { it.copy(userAudioLevel = 0.7f) }

            viewModelScope.launch {
                delay(2000)
                _uiState.update { it.copy(userAudioLevel = 0f) }
            }
        }

        // Errors
        geminiWebSocketClient.onError = { error ->
            Log.e(TAG, "Error: $error")
            _uiState.update {
                it.copy(
                    error = error,
                    connectionStatus = ConnectionStatus.Error
                )
            }
        }

        // Send audio chunks to Gemini (with gating for half-duplex)
        geminiAudioManager.onAudioChunkReady = { audioBase64 ->
            if (!_uiState.value.isMuted && !aiSpeaking) {
                geminiWebSocketClient.sendAudioData(audioBase64)
            }
        }

        // Handle saving messages to Chat DB
        geminiWebSocketClient.onChatMessageSaved = { sessionId, content, isUser ->
            currentLiveSessionId = sessionId
            viewModelScope.launch {
                try {
                    chatRepository.saveLiveMessage(sessionId, content, isUser)
                    Log.d(TAG, "Live message integrated into Chat DB")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save Live message to Chat DB", e)
                }
            }
        }

        // Smart barge-in handling
        geminiAudioManager.onBargeInDetected = {
            Log.d(TAG, "Smart barge-in detected - interrupting AI")
            handleSmartBargeIn()
        }
    }

    private fun setupCameraIntegration() {
        geminiCameraManager.onImageCaptured = { imageBase64 ->
            Log.d(TAG, "Sending image to Gemini Live (${imageBase64.length} chars)")
            viewModelScope.launch {
                try {
                    geminiWebSocketClient.sendImageData(imageBase64)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send image to Gemini", e)
                    _uiState.update { it.copy(error = "Failed to send image: ${e.message}") }
                }
            }
        }
    }

    /**
     * Detect emotion from AI transcript text
     */
    private fun detectEmotionFromText(text: String): Emotion {
        val lowerText = text.lowercase()

        return when {
            lowerText.containsAny(
                "felice", "contento", "bene", "ottimo", "fantastico",
                "happy", "great", "wonderful", "amazing"
            ) -> Emotion.Happy(0.8f)

            lowerText.containsAny(
                "triste", "dispiaciuto", "mi dispiace", "purtroppo",
                "sorry", "sad", "unfortunately"
            ) -> Emotion.Sad(0.7f)

            lowerText.containsAny(
                "interessante", "curioso", "wow", "incredibile",
                "interesting", "curious", "exciting"
            ) -> Emotion.Excited(0.6f)

            lowerText.containsAny(
                "davvero", "serio", "non ci credo",
                "really", "seriously", "unbelievable"
            ) -> Emotion.Surprised(0.5f)

            lowerText.containsAny(
                "penso", "credo", "forse", "vediamo",
                "think", "maybe", "perhaps", "let me"
            ) -> Emotion.Thinking(0.6f)

            lowerText.containsAny(
                "capisco", "comprendo", "certo", "naturalmente",
                "understand", "of course", "certainly"
            ) -> Emotion.Calm(0.6f)

            else -> Emotion.Neutral
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }

    // === Permission Handling ===

    fun onAudioPermissionGranted() {
        Log.d(TAG, "Audio permission granted")
        _uiState.update { it.copy(hasAudioPermission = true) }
        connectToRealtime()
    }

    fun onAudioPermissionDenied() {
        Log.d(TAG, "Audio permission denied")
        _uiState.update {
            it.copy(
                hasAudioPermission = false,
                error = "Audio permission required for voice chat"
            )
        }
    }

    fun onCameraPermissionGranted() {
        Log.d(TAG, "Camera permission granted")
        _uiState.update { it.copy(hasCameraPermission = true) }
    }

    fun onCameraPermissionDenied() {
        Log.d(TAG, "Camera permission denied")
        _uiState.update {
            it.copy(
                hasCameraPermission = false,
                error = "Camera permission is optional but enhances the experience"
            )
        }
    }

    // === Camera Controls ===

    fun startCameraPreview(surfaceTexture: SurfaceTexture) {
        if (!_uiState.value.hasCameraPermission) {
            Log.w(TAG, "Cannot start camera without permission")
            return
        }

        viewModelScope.launch {
            try {
                geminiCameraManager.startCameraPreview(surfaceTexture)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera preview", e)
                _uiState.update { it.copy(error = "Failed to start camera: ${e.message}") }
            }
        }
    }

    fun stopCameraPreview() {
        viewModelScope.launch {
            try {
                geminiCameraManager.stopCameraPreview()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop camera preview", e)
            }
        }
    }

    // === Connection Management ===

    fun connectToRealtime() {
        if (!_uiState.value.hasAudioPermission) {
            Log.w(TAG, "Cannot connect without audio permission")
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "Connecting to Gemini Live with VAD...")
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.Connecting, error = null) }

                val apiKey = apiConfigManager.getGeminiApiKey()
                if (apiKey.isEmpty()) {
                    throw IllegalStateException("Gemini API key not configured")
                }

                geminiWebSocketClient.connect(apiKey)

                // Start audio channel after connection
                delay(500)
                if (_uiState.value.connectionStatus == ConnectionStatus.Connected) {
                    currentLiveSessionId = "live-${System.currentTimeMillis()}"
                    Log.d(TAG, "Generated Live session ID: $currentLiveSessionId")

                    startAudioChannel()

                    geminiAudioManager.startVoiceLearning()
                    conversationContextManager.resetContext()
                    Log.d(TAG, "Intelligent systems initialized for new session")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
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
            Log.d(TAG, "Disconnecting...")

            isAudioChannelOpen = false
            geminiAudioManager.stopRecording()
            geminiAudioManager.stopPlayback()
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
                    userAudioLevel = 0f,
                    aiAudioLevel = 0f,
                    currentTranscript = "",
                    partialTranscript = "",
                    error = null,
                    aiEmotion = AIEmotion.Neutral,
                    isCameraActive = false,
                    isSpeaking = false
                )
            }

            _currentTranscript.value = ""
            _avatarEmotion.value = Emotion.Neutral
            currentLiveSessionId = null
        }
    }

    /**
     * Starts the audio channel (always-on with server VAD)
     */
    private fun startAudioChannel() {
        if (!isAudioChannelOpen && _uiState.value.connectionStatus == ConnectionStatus.Connected) {
            Log.d(TAG, "Starting always-open audio channel with VAD")
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

    // === Barge-In Handling ===

    private fun handleBargeIn() {
        Log.d(TAG, "Handling barge-in: stopping AI, switching to user turn")

        // Stop TTS playback immediately
        geminiAudioManager.handleInterruption()

        // Stop synchronized lip-sync
        liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

        // Reset avatar to neutral
        _avatarEmotion.value = Emotion.Neutral

        // Update UI state
        _uiState.update {
            it.copy(
                turnState = TurnState.UserTurn,
                aiEmotion = AIEmotion.Neutral,
                isSpeaking = false
            )
        }
    }

    private fun handleSmartBargeIn() {
        Log.d(TAG, "Smart barge-in: immediate TTS stop, user audio resumes")

        // Stop TTS immediately
        geminiAudioManager.handleInterruption()

        // Stop synchronized lip-sync
        liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

        // Reset AI speaking state
        aiSpeaking = false

        // Reset avatar
        _avatarEmotion.value = Emotion.Thinking(0.5f)

        // UI feedback
        _uiState.update {
            it.copy(
                turnState = TurnState.UserTurn,
                aiEmotion = AIEmotion.Thinking,
                isSpeaking = false
            )
        }
    }

    // === User Controls ===

    fun toggleMute() {
        val newMuteState = !_uiState.value.isMuted
        Log.d(TAG, if (newMuteState) "Muting microphone" else "Unmuting microphone")

        _uiState.update { it.copy(isMuted = newMuteState) }

        if (newMuteState) {
            // Send audioStreamEnd to flush VAD buffer
            geminiWebSocketClient.sendEndOfStream()
            _uiState.update { it.copy(turnState = TurnState.WaitingForUser) }
        }
    }

    fun toggleCamera() {
        val wantsCameraOn = !_uiState.value.wantsCameraOn
        _uiState.update { it.copy(wantsCameraOn = wantsCameraOn) }

        if (!wantsCameraOn && _uiState.value.isCameraActive) {
            stopCameraPreview()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun retryConnection() {
        clearError()
        connectToRealtime()
    }

    // === Function Calling Setup ===

    private fun setupFunctionCalling() {
        geminiWebSocketClient.onNeedUserData = {
            try {
                val userName = firebaseAuth.currentUser?.displayName
                    ?: firebaseAuth.currentUser?.email?.substringBefore("@")
                    ?: "Utente"

                val diariesSummary = getDiariesSummary()
                Pair(userName, diariesSummary)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting user data", e)
                Pair("Utente", "Nessun diario disponibile")
            }
        }

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

    private suspend fun getDiariesSummary(): String {
        return try {
            val diariesResult = diaryRepository.getAllDiaries().first()
            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val recentDiaries = diariesResult.data
                        .flatMap { it.value }
                        .sortedByDescending { it.date.toInstant() }
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

    private suspend fun executeGetRecentDiariesFunction(args: JSONObject): JSONObject {
        val limit = args.optInt("limit", 4).coerceAtMost(10)

        return try {
            val diariesResult = diaryRepository.getAllDiaries().first()

            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val recentDiaries = diariesResult.data
                        .flatMap { it.value }
                        .sortedByDescending { it.date.toInstant() }
                        .take(limit)

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

    private suspend fun executeSearchDiaryFunction(args: JSONObject): JSONObject {
        val query = args.getString("query")
        val k = args.optInt("k", 5).coerceAtMost(20)

        return try {
            val diariesResult = diaryRepository.getAllDiaries().first()

            when (diariesResult) {
                is com.lifo.util.model.RequestState.Success -> {
                    val allDiaries = diariesResult.data.flatMap { it.value }

                    val searchResults = allDiaries.filter { diary ->
                        diary.title.contains(query, ignoreCase = true) ||
                                diary.description.contains(query, ignoreCase = true)
                    }.sortedByDescending { it.date.toInstant() }
                        .take(k)

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            searchResults.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id)
                                    put("dateISO", diary.date.toInstant().toString())
                                    put("title", diary.title)
                                    put("mood", diary.mood)
                                    put("snippet", diary.description.take(200))
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

/**
 * UI State for Avatar Live Chat Screen
 */
data class AvatarLiveChatUiState(
    // Connection
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isChannelOpen: Boolean = false,

    // Permissions
    val hasAudioPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,

    // Audio
    val isMuted: Boolean = false,
    val userAudioLevel: Float = 0f,
    val aiAudioLevel: Float = 0f,

    // Camera
    val isCameraActive: Boolean = false,
    val wantsCameraOn: Boolean = false,

    // Turn Management
    val turnState: TurnState = TurnState.WaitingForUser,
    val aiEmotion: AIEmotion = AIEmotion.Neutral,

    // Transcripts
    val currentTranscript: String = "",
    val partialTranscript: String = "",

    // Avatar
    val isSpeaking: Boolean = false,

    // Errors
    val error: String? = null
)
