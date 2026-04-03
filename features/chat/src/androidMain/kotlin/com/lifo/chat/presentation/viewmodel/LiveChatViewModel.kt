package com.lifo.chat.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import com.lifo.chat.audio.GeminiLiveAudioSource
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.data.websocket.GeminiLiveWebSocketClient
import com.lifo.chat.data.audio.GeminiLiveAudioManager
import com.lifo.chat.data.camera.GeminiLiveCameraManager
import com.lifo.chat.domain.audio.AudioQualityAnalyzer
import com.lifo.chat.domain.audio.ConversationContextManager
import com.lifo.chat.domain.audio.HeadphoneDetector
import com.lifo.chat.domain.model.*
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.model.AvatarStatus
import com.lifo.util.repository.AvatarRepository
import com.lifo.util.repository.ChatRepository
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.util.speech.InterruptionReason
import com.lifo.util.speech.SpeechAnimationTarget
import com.lifo.util.speech.SynchronizedSpeechController
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ---------------------------------------------------------------------------
// MVI Contract
// ---------------------------------------------------------------------------

object LiveChatContract {

    sealed interface Intent : MviContract.Intent {
        data object AudioPermissionGranted : Intent
        data object AudioPermissionDenied : Intent
        data object CameraPermissionGranted : Intent
        data object CameraPermissionDenied : Intent
        data object ConnectToRealtime : Intent
        data object DisconnectFromRealtime : Intent
        data class StartCameraPreview(val surfaceTexture: SurfaceTexture) : Intent
        data object StopCameraPreview : Intent
        data object ToggleMute : Intent
        data class SendTextMessage(val text: String) : Intent
        data object ClearError : Intent
        data object RetryConnection : Intent
        data class SelectAudioDevice(val deviceId: Int) : Intent
    }

    data class State(
        val live: LiveChatUiState = LiveChatUiState(),
        val currentTranscript: String = "",
        val userVoiceLevel: Float = 0f,
        val aiVoiceLevel: Float = 0f,
        val emotionalIntensity: Float = 0.5f,
        val conversationMode: String = "casual"
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ConnectionError(val message: String) : Effect
        data class AudioQualityWarning(val message: String) : Effect
    }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class LiveChatViewModel constructor(
    private val context: Context,
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
    private val subscriptionRepository: SubscriptionRepository,
    private val avatarRepository: AvatarRepository?,
    private val headphoneDetector: HeadphoneDetector,
    private val savedStateHandle: SavedStateHandle
) : MviViewModel<LiveChatContract.Intent, LiveChatContract.State, LiveChatContract.Effect>(
    initialState = LiveChatContract.State(
        live = LiveChatUiState(
            connectionStatus = ConnectionStatus.Disconnected,
            hasAudioPermission = false, // will be set in init
            hasCameraPermission = false, // will be set in init
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
) {

    // -----------------------------------------------------------------------
    // Backward-compatible aliases (callers keep using these without changes)
    // -----------------------------------------------------------------------

    val uiState: StateFlow<LiveChatUiState> =
        state.map { it.live }
            .stateIn(scope, SharingStarted.Eagerly, currentState.live)

    val currentTranscript: StateFlow<String> =
        state.map { it.currentTranscript }
            .stateIn(scope, SharingStarted.Eagerly, currentState.currentTranscript)

    val userVoiceLevel: StateFlow<Float> =
        state.map { it.userVoiceLevel }
            .stateIn(scope, SharingStarted.Eagerly, currentState.userVoiceLevel)

    val aiVoiceLevel: StateFlow<Float> =
        state.map { it.aiVoiceLevel }
            .stateIn(scope, SharingStarted.Eagerly, currentState.aiVoiceLevel)

    val emotionalIntensity: StateFlow<Float> =
        state.map { it.emotionalIntensity }
            .stateIn(scope, SharingStarted.Eagerly, currentState.emotionalIntensity)

    val conversationMode: StateFlow<String> =
        state.map { it.conversationMode }
            .stateIn(scope, SharingStarted.Eagerly, currentState.conversationMode)

    // -----------------------------------------------------------------------
    // Private internal state (not suitable for MVI State)
    // -----------------------------------------------------------------------

    companion object {
        // Free tier: 3 minutes per session
        // PRO tier: 15 minutes (Gemini Live API max without session resumption)
        private const val FREE_SESSION_LIMIT_SECONDS = 180L  // 3 minutes
        private const val PRO_SESSION_LIMIT_SECONDS = 900L   // 15 minutes
    }

    private var aiSpeaking: Boolean = false
    private var isAudioChannelOpen = false
    private var currentLiveSessionId: String? = null
    private var onPlayGestureCallback: ((String) -> Unit)? = null
    private var sessionTimerJob: Job? = null

    // Synchronized speech state for avatar integration (direct delegation)
    val isSynchronizedSpeaking = synchronizedSpeechController.isSpeaking

    // -----------------------------------------------------------------------
    // Init
    // -----------------------------------------------------------------------

    init {
        println("[LiveChatViewModel] Initializing LiveChatViewModel...")

        // Set initial permissions from actual system state
        updateState {
            copy(
                live = live.copy(
                    hasAudioPermission = checkAudioPermission(),
                    hasCameraPermission = checkCameraPermission()
                )
            )
        }

        observeGeminiStates()
        setupGeminiCallbacks()
        setupCameraIntegration()
        setupIntelligentSystems()
        setupFunctionCalling()
        initializeSynchronizedSpeech()
        observeAudioDevices()
    }

    // -----------------------------------------------------------------------
    // MVI intent handling
    // -----------------------------------------------------------------------

    override fun handleIntent(intent: LiveChatContract.Intent) {
        when (intent) {
            is LiveChatContract.Intent.AudioPermissionGranted -> onAudioPermissionGranted()
            is LiveChatContract.Intent.AudioPermissionDenied -> onAudioPermissionDenied()
            is LiveChatContract.Intent.CameraPermissionGranted -> onCameraPermissionGranted()
            is LiveChatContract.Intent.CameraPermissionDenied -> onCameraPermissionDenied()
            is LiveChatContract.Intent.ConnectToRealtime -> connectToRealtime()
            is LiveChatContract.Intent.DisconnectFromRealtime -> disconnectFromRealtime()
            is LiveChatContract.Intent.StartCameraPreview -> startCameraPreview(intent.surfaceTexture)
            is LiveChatContract.Intent.StopCameraPreview -> stopCameraPreview()
            is LiveChatContract.Intent.ToggleMute -> toggleMute()
            is LiveChatContract.Intent.SendTextMessage -> sendTextMessage(intent.text)
            is LiveChatContract.Intent.ClearError -> clearError()
            is LiveChatContract.Intent.RetryConnection -> retryConnection()
            is LiveChatContract.Intent.SelectAudioDevice -> selectAudioDevice(intent.deviceId)
        }
    }

    // -----------------------------------------------------------------------
    // Backward-compatible public methods (delegate to intents internally)
    // -----------------------------------------------------------------------

    fun onAudioPermissionGranted() {
        println("[LiveChatViewModel] Audio permission granted")
        updateState { copy(live = live.copy(hasAudioPermission = true)) }
        connectToRealtime()
    }

    fun onAudioPermissionDenied() {
        println("[LiveChatViewModel] Audio permission denied")
        updateState {
            copy(
                live = live.copy(
                    hasAudioPermission = false,
                    error = "Audio permission required for voice chat"
                )
            )
        }
    }

    fun onCameraPermissionGranted() {
        println("[LiveChatViewModel] Camera permission granted")
        updateState { copy(live = live.copy(hasCameraPermission = true)) }
        println("[LiveChatViewModel] Updated UI state - hasCameraPermission: true")
    }

    fun onCameraPermissionDenied() {
        println("[LiveChatViewModel] Camera permission denied")
        updateState {
            copy(
                live = live.copy(
                    hasCameraPermission = false,
                    error = "Camera permission is optional but enhances the experience"
                )
            )
        }
    }

    fun startCameraPreview(surfaceTexture: SurfaceTexture) {
        println("[LiveChatViewModel] startCameraPreview() called in LiveChatViewModel")
        println("[LiveChatViewModel] hasCameraPermission: ${currentState.live.hasCameraPermission}")
        println("[LiveChatViewModel] isCameraActive: ${currentState.live.isCameraActive}")
        println("[LiveChatViewModel] surfaceTexture: $surfaceTexture")

        if (!currentState.live.hasCameraPermission) {
            println("[LiveChatViewModel] WARNING: Cannot start camera without permission")
            return
        }

        println("[LiveChatViewModel] Starting camera preview - launching coroutine...")
        scope.launch {
            try {
                println("[LiveChatViewModel] Calling geminiCameraManager.startCameraPreview()...")
                geminiCameraManager.startCameraPreview(surfaceTexture)
                println("[LiveChatViewModel] geminiCameraManager.startCameraPreview() completed")
            } catch (e: Exception) {
                println("[LiveChatViewModel] ERROR: Failed to start camera preview: ${e.message}")
                updateState { copy(live = live.copy(error = "Failed to start camera: ${e.message}")) }
            }
        }
    }

    fun stopCameraPreview() {
        println("[LiveChatViewModel] Stopping camera preview")
        scope.launch {
            try {
                geminiCameraManager.stopCameraPreview()
            } catch (e: Exception) {
                println("[LiveChatViewModel] ERROR: Failed to stop camera preview: ${e.message}")
            }
        }
    }

    fun connectToRealtime() {
        if (!currentState.live.hasAudioPermission) {
            println("[LiveChatViewModel] WARNING: Cannot connect without audio permission")
            return
        }

        scope.launch {
            try {
                println("[LiveChatViewModel] Connecting to Gemini Live with VAD...")
                updateState { copy(live = live.copy(connectionStatus = ConnectionStatus.Connecting, error = null)) }

                val apiKey = apiConfigManager.getGeminiApiKey()
                if (apiKey.isEmpty()) {
                    throw IllegalStateException("Gemini API key not configured")
                }

                // Carica personalita' avatar attivo (se esiste)
                loadActiveAvatarPersonality()

                println("[LiveChatViewModel] Using API key: ${apiKey.take(10)}...")
                geminiWebSocketClient.connect(apiKey)

                // Session ID and context will be initialized when connection state changes to CONNECTED
                // (handled in observeGeminiStates)
                currentLiveSessionId = "live-${System.currentTimeMillis()}"
                println("[LiveChatViewModel] Generated Live session ID: $currentLiveSessionId")
                conversationContextManager.resetContext()

                // Start session timer for subscription gating
                startSessionTimer()
            } catch (e: Exception) {
                println("[LiveChatViewModel] ERROR: Connection failed: ${e.message}")
                updateState {
                    copy(
                        live = live.copy(
                            connectionStatus = ConnectionStatus.Error,
                            error = "Connection failed: ${e.message}"
                        )
                    )
                }
                sendEffect(LiveChatContract.Effect.ConnectionError("Connection failed: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    /**
     * Carica l'avatar READY piu' recente dell'utente e imposta la sua personalita'
     * sul WebSocket client. Se non c'e' un avatar, usa il default (Karen).
     */
    private suspend fun loadActiveAvatarPersonality() {
        val repo = avatarRepository ?: run {
            println("[LiveChatViewModel] No AvatarRepository available, using default personality")
            return
        }
        val userId = firebaseAuth.currentUser?.uid ?: run {
            println("[LiveChatViewModel] No user logged in, using default personality")
            return
        }

        try {
            // Prendi l'avatar READY piu' recente
            val avatars = repo.getUserAvatars(userId)
            val activeAvatar = avatars.firstOrNull { it.status == AvatarStatus.READY }

            if (activeAvatar != null && activeAvatar.systemPrompt.raw.isNotBlank()) {
                println("[LiveChatViewModel] Loading avatar personality: ${activeAvatar.name} (voice=${activeAvatar.voiceId})")
                geminiWebSocketClient.setAvatarPersonality(
                    name = activeAvatar.name,
                    systemPrompt = activeAvatar.systemPrompt.raw,
                    voiceId = activeAvatar.voiceId,
                    speakingRate = activeAvatar.voiceConfig.speakingRate,
                )
            } else {
                println("[LiveChatViewModel] No READY avatar with system prompt found, using default Karen personality")
            }
        } catch (e: Exception) {
            println("[LiveChatViewModel] Error loading avatar personality: ${e.message}, using default")
        }
    }

    fun disconnectFromRealtime() {
        scope.launch {
            println("[LiveChatViewModel] Disconnecting...")

            isAudioChannelOpen = false
            geminiAudioManager.stopRecording()  // Stop audio recording
            geminiAudioManager.stopPlayback()   // Stop AI audio playback
            geminiCameraManager.stopCameraPreview()
            geminiWebSocketClient.disconnect()

            // Reset synchronized speech
            liveAudioSource.reset()

            updateState {
                copy(
                    live = live.copy(
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
                    ),
                    currentTranscript = ""
                )
            }

            currentLiveSessionId = null

            // Stop session timer
            stopSessionTimer()
        }
    }

    // -----------------------------------------------------------------------
    // Session time limit (subscription gating)
    // -----------------------------------------------------------------------

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()

        scope.launch {
            // Determine user's tier and set the time limit
            val timeLimitSeconds = resolveSessionTimeLimit()
            updateState {
                copy(live = live.copy(
                    sessionElapsedSeconds = 0L,
                    sessionTimeLimitSeconds = timeLimitSeconds,
                    showTimeLimitReached = false
                ))
            }

            println("[LiveChatViewModel] Session timer started: limit=${timeLimitSeconds}s (${timeLimitSeconds / 60}min)")
        }

        // Tick every second
        sessionTimerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = currentState.live.sessionElapsedSeconds + 1
                val limit = currentState.live.sessionTimeLimitSeconds

                updateState { copy(live = live.copy(sessionElapsedSeconds = elapsed)) }

                // Check if time limit reached (limit > 0 means there IS a limit)
                if (limit > 0 && elapsed >= limit && !currentState.live.showTimeLimitReached) {
                    println("[LiveChatViewModel] Session time limit reached: ${elapsed}s / ${limit}s")
                    updateState { copy(live = live.copy(showTimeLimitReached = true)) }
                    // Disconnect gracefully
                    disconnectFromRealtime()
                }
            }
        }
    }

    private fun stopSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = null
    }

    private suspend fun resolveSessionTimeLimit(): Long {
        val userId = firebaseAuth.currentUser?.uid ?: return FREE_SESSION_LIMIT_SECONDS

        return try {
            val result = subscriptionRepository.getSubscriptionState(userId)
            if (result is RequestState.Success &&
                result.data.tier == SubscriptionRepository.SubscriptionTier.PRO
            ) {
                PRO_SESSION_LIMIT_SECONDS
            } else {
                FREE_SESSION_LIMIT_SECONDS
            }
        } catch (e: Exception) {
            println("[LiveChatViewModel] Error checking subscription: ${e.message}")
            FREE_SESSION_LIMIT_SECONDS // Default to free tier on error
        }
    }

    fun toggleMute() {
        val newMuteState = !currentState.live.isMuted
        println("[LiveChatViewModel] ${if (newMuteState) "REAL MUTE - stopping audio to server" else "UNMUTE - resuming audio to server"}")

        updateState { copy(live = live.copy(isMuted = newMuteState)) }

        // Mute reale: svuota buffer audio + resetta VAD nell'AudioManager
        geminiAudioManager.setMuted(newMuteState)

        if (newMuteState) {
            // Invia audioStreamEnd per notificare il server che l'utente ha smesso
            geminiWebSocketClient.sendEndOfStream()
            updateState { copy(live = live.copy(turnState = TurnState.WaitingForUser)) }
        } else {
            updateState { copy(live = live.copy(turnState = TurnState.WaitingForUser)) }
        }
        // Il canale di registrazione resta aperto (serve per AEC), ma l'audio NON viene inviato
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) {
            println("[LiveChatViewModel] WARNING: Cannot send empty text message")
            return
        }

        println("[LiveChatViewModel] Sending text message from ViewModel: ${text.take(50)}...")
        geminiWebSocketClient.sendTextMessage(text)

        // Aggiorna lo stato UI per mostrare il messaggio inviato
        updateState { copy(live = live.copy(transcript = text, partialTranscript = "")) }

        // Aggiungi al context manager per intelligenza conversazionale
        conversationContextManager.addMessage(
            content = text,
            isFromUser = true,
            audioLevel = 0f,
            duration = 0L
        )
    }

    fun clearError() {
        updateState { copy(live = live.copy(error = null)) }
    }

    fun retryConnection() {
        clearError()
        connectToRealtime()
    }

    fun isMuted(): Boolean = currentState.live.isMuted

    // -----------------------------------------------------------------------
    // Non-intent public methods (callback/object attachment — not suitable as intents)
    // -----------------------------------------------------------------------

    /**
     * Initialize synchronized speech by attaching the live audio source
     */
    private fun initializeSynchronizedSpeech() {
        println("[LiveChatViewModel] Initializing synchronized speech for Live mode...")
        synchronizedSpeechController.attachAudioSource(liveAudioSource)
    }

    /**
     * Attach HumanoidController for synchronized lip-sync.
     * Call this from the UI layer when humanoid is ready.
     */
    fun attachHumanoidController(controller: SpeechAnimationTarget) {
        println("[LiveChatViewModel] Attaching HumanoidController for synchronized lip-sync (Live mode)")
        synchronizedSpeechController.attachAnimationTarget(controller)
    }

    /**
     * Attach gesture animation callback.
     * This is called from the integration layer (app module) to avoid circular dependency.
     * The callback receives animation names from Gemini AI and maps them to avatar gestures.
     */
    fun attachGestureCallback(callback: (String) -> Unit) {
        println("[LiveChatViewModel] Attaching gesture callback for avatar animations")
        onPlayGestureCallback = callback

        // Setup animation callback from Gemini AI
        geminiWebSocketClient.onPlayAnimation = { animationName ->
            println("[LiveChatViewModel] AI requested animation: $animationName")
            callback(animationName)
        }
    }

    /**
     * Detach HumanoidController when no longer needed.
     */
    fun detachHumanoidController() {
        println("[LiveChatViewModel] Detaching HumanoidController (Live mode)")
        synchronizedSpeechController.detachAnimationTarget()
        onPlayGestureCallback = null
        geminiWebSocketClient.onPlayAnimation = null
    }

    // -----------------------------------------------------------------------
    // Private: Gemini state observation
    // -----------------------------------------------------------------------

    private fun observeGeminiStates() {
        // Observe WebSocket connection state
        scope.launch {
            geminiWebSocketClient.connectionState.collectLatest { wsState ->
                println("[LiveChatViewModel] Connection state: $wsState")

                val uiConnectionStatus = when (wsState) {
                    GeminiLiveWebSocketClient.ConnectionState.CONNECTED -> ConnectionStatus.Connected
                    GeminiLiveWebSocketClient.ConnectionState.CONNECTING -> ConnectionStatus.Connecting
                    GeminiLiveWebSocketClient.ConnectionState.ERROR -> ConnectionStatus.Error
                    GeminiLiveWebSocketClient.ConnectionState.DISCONNECTED -> ConnectionStatus.Disconnected
                }

                updateState { copy(live = live.copy(connectionStatus = uiConnectionStatus)) }

                // Start audio channel when WebSocket connects
                if (wsState == GeminiLiveWebSocketClient.ConnectionState.CONNECTED && !isAudioChannelOpen) {
                    println("[LiveChatViewModel] WebSocket connected - starting audio streaming")
                    startAudioChannel()
                }
            }
        }

        // Observe recording state from audio manager
        scope.launch {
            geminiAudioManager.recordingState.collectLatest { isRecording ->
                updateState {
                    copy(
                        live = live.copy(
                            isChannelOpen = isRecording,
                            aiEmotion = if (!live.isMuted && isRecording) AIEmotion.Thinking else AIEmotion.Neutral
                        )
                    )
                }
            }
        }

        // Observe user audio level from audio manager
        scope.launch {
            geminiAudioManager.userAudioLevel.collectLatest { level ->
                updateState { copy(live = live.copy(audioLevel = level)) }
            }
        }

        // Observe AI playback state
        scope.launch {
            geminiAudioManager.playbackState.collectLatest { isPlaying ->
                aiSpeaking = isPlaying
                geminiAudioManager.setAiSpeaking(isPlaying)

                updateState {
                    copy(live = live.copy(aiEmotion = if (isPlaying) AIEmotion.Speaking else AIEmotion.Neutral))
                }

                if (!isPlaying) {
                    println("[LiveChatViewModel] AI finished speaking - user can now speak again")
                }
            }
        }

        // Observe camera state
        scope.launch {
            geminiCameraManager.isCameraActive.collectLatest { isActive ->
                updateState { copy(live = live.copy(isCameraActive = isActive)) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private: Gemini callbacks
    // -----------------------------------------------------------------------

    private fun setupGeminiCallbacks() {
        // Partial transcript from Gemini (user's speech transcribed by server)
        geminiWebSocketClient.onPartialTranscript = { partial ->
            println("[LiveChatViewModel] Partial transcript: $partial")
            updateState { copy(live = live.copy(partialTranscript = partial)) }
        }

        // Final transcript from Gemini (user's speech transcribed by server)
        geminiWebSocketClient.onFinalTranscript = { final ->
            println("[LiveChatViewModel] Final transcript: $final")
            updateState { copy(live = live.copy(transcript = final, partialTranscript = "")) }

            // Add to conversation context
            conversationContextManager.addMessage(
                content = final,
                isFromUser = true,
                audioLevel = currentState.live.audioLevel,
                duration = 0L
            )
        }

        // AI turn started
        geminiWebSocketClient.onTurnStarted = {
            println("[LiveChatViewModel] AI turn started")
            updateState { copy(live = live.copy(turnState = TurnState.AgentTurn, aiEmotion = AIEmotion.Speaking)) }
        }

        // Turn completed
        geminiWebSocketClient.onTurnCompleted = {
            println("[LiveChatViewModel] Turn completed")
            updateState { copy(live = live.copy(turnState = TurnState.WaitingForUser, aiEmotion = AIEmotion.Neutral)) }
        }

        // Interruption (barge-in)
        geminiWebSocketClient.onInterrupted = {
            println("[LiveChatViewModel] AI interrupted by user (barge-in detected)")
            handleBargeIn()
        }

        // Text from Gemini
        geminiWebSocketClient.onTextReceived = { text ->
            println("[LiveChatViewModel] Text from Gemini: $text")
            updateState { copy(currentTranscript = text, live = live.copy(transcript = text)) }

            // Add AI response to conversation context
            conversationContextManager.addMessage(
                content = text,
                isFromUser = false,
                audioLevel = 0f, // AI audio level not applicable for text
                duration = 0L
            )

            // Prepare synchronized lip-sync with the text
            // Audio will start shortly after, and lip-sync will begin simultaneously
            liveAudioSource.prepareWithText(text)
        }

        // Audio from Gemini
        geminiWebSocketClient.onAudioReceived = { audioBase64 ->
            println("[LiveChatViewModel] Audio from Gemini (${audioBase64.length} chars)")
            geminiAudioManager.queueAudioForPlayback(audioBase64)

            // Update audio level for visualization (simulated)
            updateState { copy(live = live.copy(audioLevel = 0.7f)) }

            // Clear audio level after a delay
            scope.launch {
                delay(2000)
                updateState { copy(live = live.copy(audioLevel = 0f)) }
            }
        }

        // Errors
        geminiWebSocketClient.onError = { error ->
            println("[LiveChatViewModel] ERROR: $error")
            updateState {
                copy(
                    live = live.copy(
                        error = error,
                        connectionStatus = ConnectionStatus.Error
                    )
                )
            }
        }

        // Gestione salvataggio messaggi Live in Chat DB
        geminiWebSocketClient.onChatMessageSaved = { sessionId, content, isUser ->
            currentLiveSessionId = sessionId
            scope.launch {
                try {
                    chatRepository.saveLiveMessage(sessionId, content, isUser)
                    println("[LiveChatViewModel] Live message integrated into Chat DB")
                } catch (e: Exception) {
                    println("[LiveChatViewModel] ERROR: Failed to save Live message to Chat DB: ${e.message}")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private: Camera integration
    // -----------------------------------------------------------------------

    private fun setupCameraIntegration() {
        // Send images to Gemini
        geminiCameraManager.onImageCaptured = { imageBase64 ->
            println("[LiveChatViewModel] Sending image to Gemini Live (${imageBase64.length} chars)")
            scope.launch {
                try {
                    geminiWebSocketClient.sendImageData(imageBase64)
                } catch (e: Exception) {
                    println("[LiveChatViewModel] ERROR: Failed to send image to Gemini: ${e.message}")
                    updateState { copy(live = live.copy(error = "Failed to send image: ${e.message}")) }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private: Intelligent audio systems
    // -----------------------------------------------------------------------

    private fun setupIntelligentSystems() {
        println("[LiveChatViewModel] Setting up intelligent audio systems...")

        // Observe conversation context for adaptive optimization
        scope.launch {
            conversationContextManager.optimizationSettings.collectLatest { settings ->
                println("[LiveChatViewModel] Applying adaptive audio settings: ${settings.contextReason}")
                applyAudioOptimizationSettings(settings)
            }
        }

        // Observe audio quality metrics for real-time optimization
        scope.launch {
            audioQualityAnalyzer.overallQuality.collectLatest { quality ->
                println("[LiveChatViewModel] Audio quality: ${quality.grade} (${quality.totalScore})")

                if (quality.grade == AudioQualityAnalyzer.QualityGrade.POOR) {
                    handlePoorAudioQuality(quality)
                }
            }
        }

        // Start audio quality measurement when recording begins
        scope.launch {
            geminiAudioManager.recordingState.collectLatest { isRecording ->
                if (isRecording) {
                    audioQualityAnalyzer.startMeasurement()
                    println("[LiveChatViewModel] Audio quality measurement started")
                } else {
                    audioQualityAnalyzer.stopMeasurement()
                    println("[LiveChatViewModel] Audio quality measurement stopped")
                }
            }
        }

        // Real-time audio level updates for liquid visualizer
        scope.launch {
            geminiAudioManager.userAudioLevel.collectLatest { userLevel ->
                updateState { copy(userVoiceLevel = userLevel) }

                // Update emotional intensity based on conversation context
                val intensity = conversationContextManager.getCurrentEmotionalIntensity()
                updateState { copy(emotionalIntensity = intensity) }
            }
        }

        // AI voice level from real audio playback
        scope.launch {
            geminiAudioManager.aiAudioLevel.collectLatest { aiLevel ->
                updateState { copy(aiVoiceLevel = aiLevel) }
            }
        }

        // Conversation mode updates
        scope.launch {
            conversationContextManager.currentModeString.collectLatest { mode ->
                updateState { copy(conversationMode = mode) }
                println("[LiveChatViewModel] Conversation mode updated: $mode")
            }
        }
    }

    private fun applyAudioOptimizationSettings(settings: ConversationContextManager.AudioOptimizationSettings) {
        println("[LiveChatViewModel] Adaptive settings applied:")
        println("[LiveChatViewModel]    Barge-in sensitivity: ${settings.bargeinSensitivity}")
        println("[LiveChatViewModel]    Noise suppression: ${settings.noiseSuppressionLevel}")
        println("[LiveChatViewModel]    Echo cancellation: ${settings.echoCancellationLevel}")
        println("[LiveChatViewModel]    Reason: ${settings.contextReason}")
    }

    private fun handlePoorAudioQuality(quality: AudioQualityAnalyzer.OverallQualityScore) {
        println("[LiveChatViewModel] WARNING: Poor audio quality detected: ${quality.primaryIssue}")
        quality.recommendations.forEach { recommendation ->
            println("[LiveChatViewModel] WARNING: Recommendation: $recommendation")
        }

        updateState { copy(live = live.copy(error = "Audio quality issue: ${quality.primaryIssue}")) }
        sendEffect(LiveChatContract.Effect.AudioQualityWarning("Audio quality issue: ${quality.primaryIssue}"))
    }

    // -----------------------------------------------------------------------
    // Private: Audio channel
    // -----------------------------------------------------------------------

    /**
     * Avvia il canale audio (always-on con VAD server). Audio streaming diretto a Gemini.
     */
    private fun startAudioChannel() {
        if (!isAudioChannelOpen && currentState.live.connectionStatus == ConnectionStatus.Connected) {
            println("[LiveChatViewModel] Starting audio streaming to Gemini")
            try {
                // Setup audio callback to send chunks to WebSocket
                geminiAudioManager.onAudioChunkReady = { audioBase64 ->
                    // REAL MUTE: non inviare audio al server se mutato
                    if (!currentState.live.isMuted) {
                        geminiWebSocketClient.sendAudioData(audioBase64)
                    }
                }

                // Setup barge-in callback
                geminiAudioManager.onBargeInDetected = {
                    println("[LiveChatViewModel] Barge-in detected!")
                    handleSmartBargeIn()
                }

                // Start recording
                geminiAudioManager.startRecording()
                isAudioChannelOpen = true
                updateState { copy(live = live.copy(isChannelOpen = true)) }

                println("[LiveChatViewModel] Audio streaming started successfully")
            } catch (e: Exception) {
                println("[LiveChatViewModel] ERROR: Failed to start audio streaming: ${e.message}")
                updateState { copy(live = live.copy(error = "Failed to start voice: ${e.message}")) }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private: Barge-in handling
    // -----------------------------------------------------------------------

    /** Gestisce barge-in (interruzione utente durante risposta AI) */
    private fun handleBargeIn() {
        println("[LiveChatViewModel] Handling barge-in: stopping AI with fade-out, switching to user turn")

        // Ferma la riproduzione TTS con fade-out graduale (come Gemini Live desktop)
        geminiAudioManager.handleInterruption()

        // Stop synchronized lip-sync immediately
        liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

        // Reset stato AI speaking (permette invio audio utente IMMEDIATAMENTE)
        aiSpeaking = false
        geminiAudioManager.setAiSpeaking(false)

        // Aggiorna la UI: ora e' turno utente
        updateState {
            copy(
                live = live.copy(
                    turnState = TurnState.UserTurn,
                    aiEmotion = AIEmotion.Neutral
                )
            )
        }
    }

    /** Smart barge-in (rilevato localmente con echo cancellation) */
    private fun handleSmartBargeIn() {
        println("[LiveChatViewModel] Smart barge-in: fade-out TTS, user audio resumes immediately")

        // Stop TTS con fade-out graduale (come Gemini Live desktop)
        geminiAudioManager.handleInterruption()

        // Stop synchronized lip-sync immediately
        liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

        // Reset stato AI speaking (permette invio audio utente IMMEDIATAMENTE)
        aiSpeaking = false
        geminiAudioManager.setAiSpeaking(false)

        // UI feedback immediato
        updateState {
            copy(
                live = live.copy(
                    turnState = TurnState.UserTurn,
                    aiEmotion = AIEmotion.Thinking
                )
            )
        }
    }

    // -----------------------------------------------------------------------
    // Private: Permissions
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Private: Audio device detection & selection
    // -----------------------------------------------------------------------

    private fun observeAudioDevices() {
        headphoneDetector.startMonitoring()

        scope.launch {
            headphoneDetector.availableDevices.collectLatest { devices ->
                updateState { copy(live = live.copy(
                    availableDevices = devices,
                    activeDevice = devices.firstOrNull { it.isActive }
                )) }

                // Auto-configure audio mode based on route
                val isHeadphone = headphoneDetector.isHeadphoneConnected
                geminiAudioManager.configureAudioMode(isHeadphone = isHeadphone)
            }
        }
    }

    fun selectAudioDevice(deviceId: Int) {
        val device = currentState.live.availableDevices.find { it.id == deviceId } ?: return
        println("[LiveChatViewModel] Selecting audio device: ${device.name} (type=${device.type})")

        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // API 31+: Use setCommunicationDevice for explicit routing
            val audioDevices = am.availableCommunicationDevices
            val targetDevice = audioDevices.firstOrNull { it.id == deviceId }

            if (targetDevice != null) {
                val success = am.setCommunicationDevice(targetDevice)
                println("[LiveChatViewModel] setCommunicationDevice(${device.name}): $success")
            } else {
                // Fallback: match by type
                val targetType = when (device.type) {
                    com.lifo.chat.domain.model.AudioDeviceType.SPEAKER -> android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    com.lifo.chat.domain.model.AudioDeviceType.EARPIECE -> android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    com.lifo.chat.domain.model.AudioDeviceType.BLUETOOTH -> android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    com.lifo.chat.domain.model.AudioDeviceType.WIRED_HEADSET -> android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                    com.lifo.chat.domain.model.AudioDeviceType.USB -> android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                }
                val fallbackDevice = audioDevices.firstOrNull { it.type == targetType }
                if (fallbackDevice != null) {
                    am.setCommunicationDevice(fallbackDevice)
                    println("[LiveChatViewModel] setCommunicationDevice by type: ${device.type}")
                } else if (device.type == com.lifo.chat.domain.model.AudioDeviceType.SPEAKER) {
                    am.clearCommunicationDevice()
                    println("[LiveChatViewModel] Cleared communication device → default speaker")
                }
            }
        } else {
            // API <31: Limited control via speakerphone toggle
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = (device.type == com.lifo.chat.domain.model.AudioDeviceType.SPEAKER)
        }

        // Update AEC mode based on device type
        val isHeadphone = device.type != com.lifo.chat.domain.model.AudioDeviceType.SPEAKER
                && device.type != com.lifo.chat.domain.model.AudioDeviceType.EARPIECE
        geminiAudioManager.configureAudioMode(isHeadphone = isHeadphone)

        // Update UI state — mark selected device as active
        val updatedDevices = currentState.live.availableDevices.map {
            it.copy(isActive = it.id == deviceId)
        }
        updateState { copy(live = live.copy(
            availableDevices = updatedDevices,
            activeDevice = updatedDevices.firstOrNull { it.isActive }
        )) }
    }

    // -----------------------------------------------------------------------
    // Private: Function calling (diary access)
    // -----------------------------------------------------------------------

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

                // Aggiunge contesto dalle chat testuali recenti per continuità cross-sessione
                val textChatContext = getRecentTextChatContext()
                val fullContext = if (textChatContext.isNotBlank()) {
                    "$diariesSummary\n\nConversazioni testuali recenti (per continuità di contesto):\n$textChatContext"
                } else {
                    diariesSummary
                }

                Pair(userName, fullContext)
            } catch (e: Exception) {
                println("[LiveChatViewModel] ERROR: Error getting user data: ${e.message}")
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
                println("[LiveChatViewModel] ERROR: Error executing function $functionName: ${e.message}")
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
                            diary.dateMillis
                        }
                        .take(4)

                    recentDiaries.joinToString("\n") { diary ->
                        "- ${diary.title} (${diary.mood}): ${diary.description.take(100)}..."
                    }
                }
                else -> "Nessun diario disponibile"
            }
        } catch (e: Exception) {
            println("[LiveChatViewModel] ERROR: Error getting diaries summary: ${e.message}")
            "Errore nel recuperare i diari"
        }
    }

    /**
     * Recupera un riassunto delle conversazioni testuali recenti per il contesto cross-sessione
     */
    private suspend fun getRecentTextChatContext(): String {
        return try {
            // currentLiveSessionId è null prima che la sessione live venga creata,
            // quindi "" non escluderà nulla (corretto: vogliamo TUTTE le sessioni testuali)
            chatRepository.getCrossSessionContext(
                currentSessionId = "",
                fromLiveSessions = false,
                maxMessages = 8
            )
        } catch (e: Exception) {
            println("[LiveChatViewModel] ERROR: Error getting text chat context: ${e.message}")
            ""
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
                            diary.dateMillis
                        }
                        .take(limit)

                    println("[LiveChatViewModel] Retrieved ${recentDiaries.size} recent diaries for function call")

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            recentDiaries.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id)
                                    put("dateISO", kotlinx.datetime.Instant.fromEpochMilliseconds(diary.dateMillis).toString())
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
            println("[LiveChatViewModel] ERROR: Error executing get_recent_diaries: ${e.message}")
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
                        diary.dateMillis
                    }.take(k)

                    println("[LiveChatViewModel] Search for '$query' returned ${searchResults.size} results")

                    JSONObject().apply {
                        put("results", JSONArray().apply {
                            searchResults.forEach { diary ->
                                put(JSONObject().apply {
                                    put("id", diary._id)
                                    put("dateISO", kotlinx.datetime.Instant.fromEpochMilliseconds(diary.dateMillis).toString())
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
            println("[LiveChatViewModel] ERROR: Error executing search_diary: ${e.message}")
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

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        headphoneDetector.stopMonitoring()
        scope.launch {
            disconnectFromRealtime()
            synchronizedSpeechController.release()
            geminiAudioManager.release()
            geminiCameraManager.release()
        }
    }
}
