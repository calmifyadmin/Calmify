package com.lifo.chat.presentation.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.audio.GeminiKillerAudioSystem
import com.lifo.chat.audio.OnDeviceNaturalVoiceSystem
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatMessage
import com.lifo.mongo.repository.ChatRepository
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @ApplicationContext private val context: Context,
    private val voiceSystem: OnDeviceNaturalVoiceSystem,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "StreamlinedChatVM"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Voice indicators exposed directly to UI
    val voiceState = voiceSystem.voiceState
    val isVoiceActive = voiceState
        .map { it.isSpeaking }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false
        )
    val voiceEmotion = voiceState
        .map { it.currentEmotion }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = OnDeviceNaturalVoiceSystem.Emotion.NEUTRAL
        )
    val voiceLatency = voiceState
        .map { it.latencyMs }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = 0L
        )

    // Simple emotion detector
    private val emotionDetector = SimpleEmotionDetector()
    private val conversationHistory = mutableListOf<ConversationTurn>()
    private var emotionalJourney = EmotionalJourney()
    init {
        viewModelScope.launch {
            // Initialize voice system immediately
            voiceSystem.initialize()

            // Start session
            createNewSession()
        }
    }
    private fun getUserProfile(): UserProfile {
        val user = FirebaseAuth.getInstance().currentUser
        return UserProfile(
            name = user?.displayName ?: "User",
            conversationCount = conversationHistory.size,
            averageSessionLength = _uiState.value.messages.size,
            preferredTopics = identifyPreferredTopics()
        )
    }

    private fun identifyPreferredTopics(): List<ConversationTopic> {
        // Analizza la storia per identificare topic preferiti
        return listOf(ConversationTopic.EMOTIONAL_SUPPORT) // Placeholder
    }

    fun getUserPhotoUrl(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user photo", e)
            null
        }
    }

    fun getUserDisplayName(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.displayName?.toString()?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user name", e)
            null
        }
    }
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        // Stop any ongoing speech before sending new message
        voiceSystem.stop()

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "") }

            val sessionId = _uiState.value.currentSession?.id ?: return@launch

            // Send user message
            when (val result = repository.sendMessage(sessionId, content)) {
                is RequestState.Success -> {
                    // Generate AI response with streaming
                    generateAiResponseWithVoice(sessionId, content)
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message)
                    }
                }
                else -> {}
            }
        }
    }

    private fun generateAiResponseWithVoice(sessionId: String, userMessage: String) {
        viewModelScope.launch {
            val streamingMessage = StreamingMessage()
            _uiState.update { it.copy(streamingMessage = streamingMessage) }

            var previousContent = ""
            val spokenSentences = mutableSetOf<String>()

            repository.generateAiResponse(sessionId, userMessage, emptyList())
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            val currentContent = result.data

                            // Debug logging
                            Log.d(TAG, "📥 Received content length: ${currentContent.length}")
                            Log.d(TAG, "📥 Previous content length: ${previousContent.length}")

                            // Extract only the NEW text (delta)
                            val newText = when {
                                currentContent.startsWith(previousContent) -> {
                                    // Repository sends cumulative text
                                    currentContent.substring(previousContent.length)
                                }
                                currentContent == previousContent -> {
                                    // Same content, skip
                                    Log.d(TAG, "⏭️ Same content, skipping")
                                    ""
                                }
                                currentContent.length < previousContent.length -> {
                                    // Repository reset or error - treat as new content
                                    Log.w(TAG, "⚠️ Content shorter than previous, possible reset")
                                    spokenSentences.clear() // Clear to avoid blocking
                                    currentContent
                                }
                                else -> {
                                    // Repository sends only new text or different content
                                    Log.d(TAG, "🔄 Repository sent delta or different content")
                                    currentContent
                                }
                            }

                            Log.d(TAG, "📝 New text delta (${newText.length} chars): ${newText.take(100)}...")

                            // Update UI with full content
                            _uiState.update { state ->
                                state.copy(
                                    streamingMessage = streamingMessage.copy(
                                        content = StringBuilder(currentContent)
                                    )
                                )
                            }

                            // Process only NEW text for speech
                            if (newText.isNotBlank()) {
                                val sentences = extractCompleteSentences(newText)
                                Log.d(TAG, "📖 Found ${sentences.size} complete sentences")

                                sentences.forEach { sentence ->
                                    val cleanSentence = sentence.trim()
                                        .replace(Regex("\\s+"), " ") // Normalize whitespace

                                    if (cleanSentence.isNotBlank() &&
                                        cleanSentence.length > 3 &&
                                        !spokenSentences.contains(cleanSentence)) {

                                        spokenSentences.add(cleanSentence)
                                        Log.d(TAG, "🎤 Speaking sentence: $cleanSentence")

                                        val emotion = emotionDetector.detectEmotion(cleanSentence)

                                        voiceSystem.speakWithEmotion(
                                            text = cleanSentence,
                                            emotion = emotion,
                                            priority = 1,
                                            messageId = "streaming_${sessionId}"
                                        )
                                    } else if (spokenSentences.contains(cleanSentence)) {
                                        Log.d(TAG, "⏭️ Sentence already spoken: $cleanSentence")
                                    }
                                }
                            }

                            // Update previous content for next iteration
                            previousContent = currentContent
                        }
                        is RequestState.Error -> {
                            _uiState.update {
                                it.copy(
                                    streamingMessage = null,
                                    error = result.error.message
                                )
                            }
                        }
                        else -> {}
                    }
                }

            // Save complete message
            if (previousContent.isNotEmpty()) {
                repository.saveAiMessage(sessionId, previousContent)
                _uiState.update { it.copy(streamingMessage = null) }
            }
        }
    }

    fun speakMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId }
        message?.let {
            if (!it.isUser) {
                // Stop any current speech
                voiceSystem.stop()

                // Small delay to ensure stop is processed
                viewModelScope.launch {
                    delay(100)

                    // Detect overall emotion
                    val emotion = emotionDetector.detectEmotion(it.content)

                    Log.d(TAG, "🔊 Speaking saved message: ${it.content.take(50)}...")

                    // Speak with natural prosody
                    // Note: For saved messages, we speak the entire content at once
                    voiceSystem.speakWithEmotion(
                        text = it.content,
                        emotion = emotion,
                        priority = 2,
                        messageId = messageId
                    )
                }
            }
        }
    }

    fun stopSpeaking() {
        voiceSystem.stop()
        // Also clear any pending streaming chunks
        viewModelScope.launch {
            delay(100) // Small delay to ensure stop is processed
            // Reset any UI state related to speaking
        }
    }

    private fun extractCompleteSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val sentences = mutableListOf<String>()
        val sentenceEnders = listOf('.', '!', '?')

        // Clean text from any HTML/Markdown before processing
        val cleanText = text
            .replace(Regex("<[^>]+>"), "") // Remove HTML tags
            .replace("**", "")
            .replace("*", "")
            .replace("_", "")
            .replace("`", "")
            .replace("```", "")
            .replace("#", "")

        // Find complete sentences only (must end with punctuation)
        var currentPos = 0
        for (i in cleanText.indices) {
            if (cleanText[i] in sentenceEnders) {
                // Found sentence end
                val sentence = cleanText.substring(currentPos, i + 1).trim()
                if (sentence.length > 3) { // Minimum length check
                    sentences.add(sentence)
                }
                currentPos = i + 1
            }
        }

        // Don't include incomplete sentences (text after last punctuation)
        // This prevents repeating partial sentences

        return sentences
    }

    /**
     * Simplified emotion detector for real-time processing
     */
    inner class SimpleEmotionDetector {
        fun detectEmotion(text: String): OnDeviceNaturalVoiceSystem.Emotion {
            val lowercaseText = text.lowercase()

            return when {
                // Excited
                lowercaseText.contains(Regex("fantastico|incredibile|wow|meraviglioso|stupendo")) ->
                    OnDeviceNaturalVoiceSystem.Emotion.EXCITED

                // Happy
                lowercaseText.contains(Regex("felice|contento|bene|ottimo|bravo|perfetto")) ->
                    OnDeviceNaturalVoiceSystem.Emotion.HAPPY

                // Sad
                lowercaseText.contains(Regex("triste|dispiaciuto|purtroppo|male|difficile")) ->
                    OnDeviceNaturalVoiceSystem.Emotion.SAD

                // Thoughtful
                lowercaseText.contains(Regex("penso|credo|forse|probabilmente|consideriamo")) ->
                    OnDeviceNaturalVoiceSystem.Emotion.THOUGHTFUL

                // Empathetic
                lowercaseText.contains(Regex("capisco|comprendo|mi dispiace|sono qui")) ->
                    OnDeviceNaturalVoiceSystem.Emotion.EMPATHETIC

                // Curious
                text.contains("?") || lowercaseText.contains(Regex("interessante|curioso|dimmi")) ->
                    OnDeviceNaturalVoiceSystem.Emotion.CURIOUS

                else -> OnDeviceNaturalVoiceSystem.Emotion.NEUTRAL
            }
        }
    }

    private fun createNewSession() {
        viewModelScope.launch {
            when (val result = repository.createSession(null)) {
                is RequestState.Success -> {
                    _uiState.update {
                        it.copy(
                            currentSession = result.data,
                            messages = emptyList()
                        )
                    }
                    loadMessages(result.data.id)
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message)
                    }
                }
                else -> {}
            }
        }
    }

    private fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            repository.getMessagesForSession(sessionId)
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            _uiState.update {
                                it.copy(messages = result.data)
                            }
                        }
                        is RequestState.Error -> {
                            _uiState.update {
                                it.copy(error = result.error.message)
                            }
                        }
                        else -> {}
                    }
                }
        }
    }
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteMessage(messageId)) {
                is RequestState.Success -> {
                    if (_uiState.value.messages.size <= 1) {
                        _uiState.update { it.copy(sessionStarted = false) }
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to delete message")
                    }
                }
                else -> {}
            }
        }
    }
    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceSystem.cleanup()
    }
}

data class EmotionalJourney(
    private val points: List<EmotionPoint> = emptyList()
) {
    fun addPoint(
        emotion: GeminiKillerAudioSystem.VoiceEmotion,
        intensity: Float,
        timestamp: Long
    ): EmotionalJourney {
        return copy(
            points = points + EmotionPoint(emotion, intensity, timestamp)
        ).let {
            // Mantieni solo ultimi 50 punti
            if (it.points.size > 50) {
                it.copy(points = it.points.takeLast(50))
            } else it
        }
    }

    fun getCurrentMood(): GeminiKillerAudioSystem.VoiceEmotion {
        if (points.isEmpty()) return GeminiKillerAudioSystem.VoiceEmotion.NEUTRAL

        // Media pesata degli ultimi punti (più recenti hanno più peso)
        val recentPoints = points.takeLast(10)
        val weightedEmotions = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()

        recentPoints.forEachIndexed { index, point ->
            val weight = (index + 1).toFloat() / recentPoints.size
            weightedEmotions[point.emotion] =
                weightedEmotions.getOrDefault(point.emotion, 0f) + (weight * point.intensity)
        }

        return weightedEmotions.maxByOrNull { it.value }?.key
            ?: GeminiKillerAudioSystem.VoiceEmotion.NEUTRAL
    }

    fun getTrend(): EmotionalTrend {
        if (points.size < 3) return EmotionalTrend.STABLE

        val recent = points.takeLast(5).map { it.intensity }.average()
        val previous = points.dropLast(5).takeLast(5).map { it.intensity }.average()

        return when {
            recent > previous + 0.2 -> EmotionalTrend.IMPROVING
            recent < previous - 0.2 -> EmotionalTrend.DECLINING
            else -> EmotionalTrend.STABLE
        }
    }

    fun getInfluence(): Map<GeminiKillerAudioSystem.VoiceEmotion, Float> {
        val influence = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()
        val recentPoints = points.takeLast(20)

        recentPoints.forEach { point ->
            influence[point.emotion] =
                influence.getOrDefault(point.emotion, 0f) + (point.intensity / recentPoints.size)
        }

        return influence
    }

    data class EmotionPoint(
        val emotion: GeminiKillerAudioSystem.VoiceEmotion,
        val intensity: Float,
        val timestamp: Long
    )
}

// Data classes di supporto
data class VoiceState(
    val isTTSReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val isStreaming: Boolean = false,
    val currentSpeakingMessageId: String? = null,
    val autoSpeak: Boolean = false,
    val bufferHealth: Float = 1f,
    val chunksReceived: Int = 0,
    val chunksPlayed: Int = 0,
    val currentEmotion: String = "neutral",
    val naturalness: Float = 1.0f,
    val emotionalIntensity: Float = 0.5f,
    val error: String? = null
)

data class ConversationTurn(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val detectedEmotion: GeminiKillerAudioSystem.VoiceEmotion?
)

enum class EmotionalTrend {
    IMPROVING, STABLE, DECLINING
}

data class EmotionalAnalysis(
    val dominantEmotion: GeminiKillerAudioSystem.VoiceEmotion,
    val intensity: Float,
    val secondaryEmotions: List<GeminiKillerAudioSystem.VoiceEmotion>,
    val emotionScores: Map<GeminiKillerAudioSystem.VoiceEmotion, Float>,
    val confidence: Float
)

data class ContextualAnalysis(
    val topic: ConversationTopic,
    val tone: ConversationTone,
    val patterns: List<ConversationPattern>,
    val intimacyLevel: Float,
    val keyMoments: List<KeyMoment>,
    val userEngagement: Float,
    val emotionalDepth: Float
)

enum class ConversationTopic {
    EMOTIONAL_SUPPORT, PLANNING, PROBLEM_SOLVING, GRATITUDE, PERSONAL_GROWTH, GENERAL
}

enum class ConversationTone {
    POSITIVE, NEUTRAL, SUPPORTIVE
}

enum class ConversationPattern {
    QUESTION_ANSWER, NARRATIVE, EMOTIONAL_EXPLORATION
}

enum class KeyMoment {
    BREAKTHROUGH, VULNERABILITY, GRATITUDE
}

data class UserProfile(
    val name: String,
    val conversationCount: Int,
    val averageSessionLength: Int,
    val preferredTopics: List<ConversationTopic>
)