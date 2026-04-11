package com.lifo.chat.presentation.viewmodel

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.audio.GeminiNativeVoiceSystem
import com.lifo.chat.audio.GeminiVoiceAudioSource
import com.lifo.chat.config.ApiConfigManager
import com.lifo.chat.domain.model.*
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.speech.SpeechAnimationTarget
import com.lifo.util.speech.SpeechEmotion
import com.lifo.util.speech.SpeechRequest
import com.lifo.util.speech.SynchronizedSpeechController
import com.lifo.util.repository.ChatRepository
import com.lifo.util.repository.SubscriptionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ---------------------------------------------------------------------------
// Contract
// ---------------------------------------------------------------------------
object ChatContract {

    sealed interface Intent : MviContract.Intent {
        data class SendMessage(val content: String) : Intent
        data class UpdateInputText(val text: String) : Intent
        data object ClearError : Intent
        data class DeleteMessage(val messageId: String) : Intent
        data class SpeakMessage(val messageId: String) : Intent
        data object StopSpeaking : Intent
        data class LoadExistingSession(val sessionId: String) : Intent
        data class AttachHumanoidController(val controller: SpeechAnimationTarget) : Intent
        data object DetachHumanoidController : Intent
    }

    /** UI state — wraps the existing [ChatUiState] data class. */
    data class State(
        val chat: ChatUiState = ChatUiState()
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data object NavigateToPaywall : Effect
    }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------
class ChatViewModel constructor(
    private val repository: ChatRepository,
    private val context: Context,
    private val voiceSystem: GeminiNativeVoiceSystem,
    private val voiceAudioSource: GeminiVoiceAudioSource,
    private val synchronizedSpeechController: SynchronizedSpeechController,
    private val apiConfigManager: ApiConfigManager,
    private val auth: FirebaseAuth,
    private val subscriptionRepository: SubscriptionRepository
) : MviViewModel<ChatContract.Intent, ChatContract.State, ChatContract.Effect>(
    initialState = ChatContract.State()
) {

    // ── Backward-compatible alias ──────────────────────────────────────
    /** Alias so existing UI code that reads `viewModel.uiState` keeps compiling. */
    val uiState: StateFlow<ChatUiState> = state.map { it.chat }
        .stateIn(scope, SharingStarted.Eagerly, ChatUiState())

    // ── Current session tracking ───────────────────────────────────────
    private var currentSessionId: String? = null

    // ── Voice state exposed to UI (external system, NOT internal state) ─
    val voiceState = voiceSystem.voiceState
    val isVoiceActive: StateFlow<Boolean> = voiceState
        .map { it.isSpeaking }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )
    val voiceEmotion: StateFlow<GeminiNativeVoiceSystem.Emotion> = voiceState
        .map { it.emotion }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = GeminiNativeVoiceSystem.Emotion.NEUTRAL
        )
    val voiceLatency: StateFlow<Long> = voiceState
        .map { it.latencyMs }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = 0L
        )

    // Synchronized speech state for avatar integration
    val isSynchronizedSpeaking = synchronizedSpeechController.isSpeaking

    private val emotionDetector = SimpleEmotionDetector()

    // Pending voice — not UI state, purely internal bookkeeping
    private var pendingVoiceMessage: String? = null
    private var pendingVoiceMessageId: String? = null

    // ── Init ───────────────────────────────────────────────────────────
    init {
        scope.launch {
            println("[ChatViewModel] Initializing ChatViewModel...")
            initializeVoiceSystem()
            initializeSynchronizedSpeech()
            // Only create new session if no existing session is loaded
            if (currentSessionId == null) {
                println("[ChatViewModel] Creating new session (no existing session loaded)")
                createNewSession()
            } else {
                println("[ChatViewModel] Skipping new session creation - existing session loaded: $currentSessionId")
            }
        }
    }

    // ── MVI intent dispatch ────────────────────────────────────────────
    override fun handleIntent(intent: ChatContract.Intent) {
        when (intent) {
            is ChatContract.Intent.SendMessage -> handleSendMessage(intent.content)
            is ChatContract.Intent.UpdateInputText -> handleUpdateInputText(intent.text)
            is ChatContract.Intent.ClearError -> handleClearError()
            is ChatContract.Intent.DeleteMessage -> handleDeleteMessage(intent.messageId)
            is ChatContract.Intent.SpeakMessage -> handleSpeakMessage(intent.messageId)
            is ChatContract.Intent.StopSpeaking -> handleStopSpeaking()
            is ChatContract.Intent.LoadExistingSession -> handleLoadExistingSession(intent.sessionId)
            is ChatContract.Intent.AttachHumanoidController -> handleAttachHumanoid(intent.controller)
            is ChatContract.Intent.DetachHumanoidController -> handleDetachHumanoid()
        }
    }

    // ── Backward-compatible public wrappers ─────────────────────────────
    // Existing UI code calls these directly; they simply delegate to intents.

    fun sendMessage(content: String) = onIntent(ChatContract.Intent.SendMessage(content))

    fun updateInputText(text: String) = onIntent(ChatContract.Intent.UpdateInputText(text))

    fun clearError() = onIntent(ChatContract.Intent.ClearError)

    fun deleteMessage(messageId: String) = onIntent(ChatContract.Intent.DeleteMessage(messageId))

    fun speakMessage(messageId: String) = onIntent(ChatContract.Intent.SpeakMessage(messageId))

    fun stopSpeaking() = onIntent(ChatContract.Intent.StopSpeaking)

    fun loadExistingSession(sessionId: String) = onIntent(ChatContract.Intent.LoadExistingSession(sessionId))

    fun attachHumanoidController(controller: SpeechAnimationTarget) =
        onIntent(ChatContract.Intent.AttachHumanoidController(controller))

    fun detachHumanoidController() = onIntent(ChatContract.Intent.DetachHumanoidController)

    fun getUserPhotoUrl(): String? {
        return try {
            auth.currentUser?.photoUrl?.toString()
        } catch (e: Exception) {
            println("[ChatViewModel] ERROR: Error getting user photo: ${e.message}")
            null
        }
    }

    fun getUserDisplayName(): String? {
        return try {
            auth.currentUser?.displayName?.toString()?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            println("[ChatViewModel] ERROR: Error getting user name: ${e.message}")
            null
        }
    }

    // ── Intent handlers (private business logic) ────────────────────────

    companion object {
        private const val FREE_MESSAGE_LIMIT = 5
    }

    private fun handleSendMessage(content: String) {
        if (content.isBlank()) return

        // Check free tier limit
        val userMessages = currentState.chat.messages.count { it.isUser }
        if (userMessages >= FREE_MESSAGE_LIMIT) {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                scope.launch {
                    val result = subscriptionRepository.getSubscriptionState(userId)
                    if (result is RequestState.Success &&
                        result.data.tier == SubscriptionRepository.SubscriptionTier.FREE
                    ) {
                        updateState { copy(chat = chat.copy(showFreeLimitReached = true)) }
                    }
                }
                return // Don't send the message until tier is checked
            }
        }

        // Stop any ongoing speech
        voiceSystem.stop()
        pendingVoiceMessage = null
        pendingVoiceMessageId = null

        scope.launch {
            updateState { copy(chat = chat.copy(inputText = "")) }

            val sessionId = currentState.chat.currentSession?.id ?: return@launch

            when (val result = repository.sendMessage(sessionId, content)) {
                is RequestState.Success -> {
                    // Add user message to UI immediately
                    loadMessages(sessionId)
                    generateAiResponseWithVoice(sessionId, content)
                }
                is RequestState.Error -> {
                    updateState { copy(chat = chat.copy(error = result.error.message)) }
                }
                else -> {}
            }
        }
    }

    private fun handleUpdateInputText(text: String) {
        updateState { copy(chat = chat.copy(inputText = text)) }
    }

    private fun handleClearError() {
        updateState { copy(chat = chat.copy(error = null)) }
    }

    private fun handleDeleteMessage(messageId: String) {
        scope.launch {
            when (val result = repository.deleteMessage(messageId)) {
                is RequestState.Success -> {
                    val sessionId = currentState.chat.currentSession?.id
                    if (currentState.chat.messages.size <= 1) {
                        updateState { copy(chat = chat.copy(sessionStarted = false)) }
                    }
                    // Reload messages to reflect deletion
                    if (sessionId != null) loadMessages(sessionId)
                }
                is RequestState.Error -> {
                    updateState {
                        copy(chat = chat.copy(error = result.error.message ?: "Failed to delete message"))
                    }
                }
                else -> {}
            }
        }
    }

    private fun handleSpeakMessage(messageId: String) {
        val message = currentState.chat.messages.find { it.id == messageId }
        message?.let {
            if (!it.isUser) {
                // Stop any current speech
                synchronizedSpeechController.stopSynchronized()

                scope.launch {
                    delay(100) // Small delay to ensure stop is processed

                    val cleanText = cleanTextForSpeech(it.content)
                    val emotion = emotionDetector.detectEmotion(cleanText)
                    val speechEmotion = mapToSpeechEmotion(emotion)
                    val estimatedDuration = voiceAudioSource.estimateDuration(cleanText)

                    // Use synchronized speech for lip-sync
                    synchronizedSpeechController.speakSynchronized(
                        SpeechRequest(
                            text = cleanText,
                            messageId = messageId,
                            emotion = speechEmotion,
                            estimatedDurationMs = estimatedDuration
                        )
                    )
                }
            }
        }
    }

    private fun handleStopSpeaking() {
        synchronizedSpeechController.stopSynchronized()
        pendingVoiceMessage = null
        pendingVoiceMessageId = null
    }

    private fun handleLoadExistingSession(sessionId: String) {
        println("[ChatViewModel] Loading existing session: $sessionId")
        scope.launch {
            try {
                val sessionResult = repository.getSession(sessionId)
                val session = when (sessionResult) {
                    is RequestState.Success -> sessionResult.data
                    else -> null
                }
                session?.let {
                    currentSessionId = sessionId

                    updateState {
                        copy(chat = chat.copy(
                            currentSession = it,
                            sessionStarted = true
                        ))
                    }

                    loadMessages(sessionId)

                    println("[ChatViewModel] Loaded session $sessionId with ${it.messageCount} messages")
                } ?: run {
                    println("[ChatViewModel] WARNING: Session $sessionId not found")
                    updateState { copy(chat = chat.copy(error = "Session not found")) }
                }
            } catch (e: Exception) {
                println("[ChatViewModel] ERROR: Error loading session $sessionId: ${e.message}")
                updateState {
                    copy(chat = chat.copy(error = e.message ?: "Failed to load session"))
                }
            }
        }
    }

    private fun handleAttachHumanoid(controller: SpeechAnimationTarget) {
        println("[ChatViewModel] Attaching HumanoidController for synchronized lip-sync")
        synchronizedSpeechController.attachAnimationTarget(controller)
    }

    private fun handleDetachHumanoid() {
        println("[ChatViewModel] Detaching HumanoidController")
        synchronizedSpeechController.detachAnimationTarget()
    }

    // ── Internal helpers ────────────────────────────────────────────────

    private fun initializeSynchronizedSpeech() {
        println("[ChatViewModel] Initializing synchronized speech controller...")
        synchronizedSpeechController.attachAudioSource(voiceAudioSource)
    }

    private suspend fun initializeVoiceSystem() {
        try {
            println("[ChatViewModel] Starting voice system initialization...")

            val apiKey = apiConfigManager.getGeminiApiKey()

            if (apiKey.isEmpty()) {
                println("[ChatViewModel] ERROR: API key is empty")
                updateState { copy(chat = chat.copy(error = "API key not configured")) }
                return
            }

            println("[ChatViewModel] Initializing voice system with API key")
            voiceSystem.initialize(apiKey)

            delay(500)

            val isInitialized = voiceSystem.voiceState.value.isInitialized
            println("[ChatViewModel] Voice system initialized: $isInitialized")

            if (!isInitialized) {
                println("[ChatViewModel] ERROR: Voice system failed to initialize")
                updateState { copy(chat = chat.copy(error = "Voice system initialization failed")) }
            }

        } catch (e: Exception) {
            println("[ChatViewModel] ERROR: Exception during voice initialization: ${e.message}")
            updateState { copy(chat = chat.copy(error = "Voice error: ${e.message}")) }
        }
    }

    private fun generateAiResponseWithVoice(sessionId: String, userMessage: String) {
        scope.launch {
            val streamingMessage = StreamingMessage()
            updateState { copy(chat = chat.copy(streamingMessage = streamingMessage)) }

            var fullContent = ""
            val currentMessageId = "streaming_${System.currentTimeMillis()}"

            repository.generateAiResponse(sessionId, userMessage, currentState.chat.messages)
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            fullContent = result.data

                            // Update UI with streaming text
                            updateState {
                                copy(chat = chat.copy(
                                    streamingMessage = streamingMessage.copy(
                                        content = StringBuilder(fullContent)
                                    )
                                ))
                            }

                            // Store for voice but DON'T speak yet — wait for complete message
                            pendingVoiceMessage = fullContent
                            pendingVoiceMessageId = currentMessageId
                        }
                        is RequestState.Error -> {
                            updateState {
                                copy(chat = chat.copy(
                                    streamingMessage = null,
                                    error = result.error.message
                                ))
                            }
                        }
                        else -> {}
                    }
                }

            // STREAMING COMPLETE — Now speak the ENTIRE message at once
            if (fullContent.isNotEmpty()) {
                repository.saveAiMessage(sessionId, fullContent)
                updateState { copy(chat = chat.copy(streamingMessage = null)) }
                // Reload messages from server so both user + AI messages persist in UI
                loadMessages(sessionId)

                if (voiceSystem.voiceState.value.isInitialized && pendingVoiceMessage != null) {
                    speakCompleteMessage(pendingVoiceMessage!!, currentMessageId)
                }
            }
        }
    }

    private fun speakCompleteMessage(text: String, messageId: String) {
        scope.launch {
            try {
                val cleanText = cleanTextForSpeech(text)
                val emotion = emotionDetector.detectEmotion(cleanText)
                val speechEmotion = mapToSpeechEmotion(emotion)

                println("[ChatViewModel] Speaking complete message (synchronized): ${cleanText.take(50)}...")

                val estimatedDuration = voiceAudioSource.estimateDuration(cleanText)

                synchronizedSpeechController.speakSynchronized(
                    SpeechRequest(
                        text = cleanText,
                        messageId = messageId,
                        emotion = speechEmotion,
                        estimatedDurationMs = estimatedDuration
                    )
                )
            } catch (e: Exception) {
                println("[ChatViewModel] ERROR: Error speaking message: ${e.message}")
            }
        }
    }

    private fun mapToSpeechEmotion(emotion: GeminiNativeVoiceSystem.Emotion): SpeechEmotion {
        return when (emotion) {
            GeminiNativeVoiceSystem.Emotion.NEUTRAL -> SpeechEmotion.NEUTRAL
            GeminiNativeVoiceSystem.Emotion.HAPPY -> SpeechEmotion.HAPPY
            GeminiNativeVoiceSystem.Emotion.SAD -> SpeechEmotion.SAD
            GeminiNativeVoiceSystem.Emotion.EXCITED -> SpeechEmotion.EXCITED
            GeminiNativeVoiceSystem.Emotion.THOUGHTFUL -> SpeechEmotion.THOUGHTFUL
            GeminiNativeVoiceSystem.Emotion.EMPATHETIC -> SpeechEmotion.EMPATHETIC
            GeminiNativeVoiceSystem.Emotion.CURIOUS -> SpeechEmotion.CURIOUS
        }
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace("**", "")
            .replace("*", "")
            .replace("_", "")
            .replace("`", "")
            .replace("```", "")
            .replace("#", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun createNewSession() {
        scope.launch {
            when (val result = repository.createSession(null)) {
                is RequestState.Success -> {
                    updateState {
                        copy(chat = chat.copy(
                            currentSession = result.data,
                            messages = emptyList()
                        ))
                    }
                    loadMessages(result.data.id)
                }
                is RequestState.Error -> {
                    updateState { copy(chat = chat.copy(error = result.error.message)) }
                }
                else -> {}
            }
        }
    }

    private fun loadMessages(sessionId: String) {
        scope.launch {
            repository.getMessagesForSession(sessionId)
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            updateState { copy(chat = chat.copy(messages = result.data)) }
                        }
                        is RequestState.Error -> {
                            updateState { copy(chat = chat.copy(error = result.error.message)) }
                        }
                        else -> {}
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        synchronizedSpeechController.release()
        voiceSystem.cleanup()
    }

    // ── Simplified emotion detector ─────────────────────────────────────
    inner class SimpleEmotionDetector {
        fun detectEmotion(text: String): GeminiNativeVoiceSystem.Emotion {
            val lowercaseText = text.lowercase()

            return when {
                lowercaseText.contains(Regex("fantastico|incredibile|wow|meraviglioso|stupendo")) ->
                    GeminiNativeVoiceSystem.Emotion.EXCITED

                lowercaseText.contains(Regex("felice|contento|bene|ottimo|bravo|perfetto")) ->
                    GeminiNativeVoiceSystem.Emotion.HAPPY

                lowercaseText.contains(Regex("triste|dispiaciuto|purtroppo|male|difficile")) ->
                    GeminiNativeVoiceSystem.Emotion.SAD

                lowercaseText.contains(Regex("penso|credo|forse|probabilmente|consideriamo")) ->
                    GeminiNativeVoiceSystem.Emotion.THOUGHTFUL

                lowercaseText.contains(Regex("capisco|comprendo|mi dispiace|sono qui")) ->
                    GeminiNativeVoiceSystem.Emotion.EMPATHETIC

                text.contains("?") || lowercaseText.contains(Regex("interessante|curioso|dimmi")) ->
                    GeminiNativeVoiceSystem.Emotion.CURIOUS

                else -> GeminiNativeVoiceSystem.Emotion.NEUTRAL
            }
        }
    }
}