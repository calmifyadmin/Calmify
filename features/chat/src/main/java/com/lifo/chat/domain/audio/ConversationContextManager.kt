package com.lifo.chat.domain.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Conversation Context Awareness System
 * 
 * Intelligently analyzes conversation patterns and context to optimize
 * audio processing parameters in real-time for natural communication.
 * 
 * Features:
 * - Conversation mode detection (casual, meeting, presentation, noisy)
 * - Dynamic audio parameter adjustment based on context
 * - User intent prediction from conversation flow
 * - Adaptive turn-taking optimization
 * - Emotion-aware audio processing
 * 
 * @author Jarvis AI Assistant
 */
@Singleton
class ConversationContextManager @Inject constructor() {
    
    companion object {
        private const val TAG = "ConversationContext"
        
        // Analysis windows
        private const val CONTEXT_ANALYSIS_WINDOW = 20 // Recent messages to analyze
        private const val SPEAKING_PATTERN_WINDOW = 10 // Speaking events to track
        private const val EMOTION_TRACKING_WINDOW = 5  // Recent emotions
        
        // Conversation characteristics
        private const val RAPID_EXCHANGE_THRESHOLD = 3000L // ms between messages
        private const val LONG_MONOLOGUE_THRESHOLD = 30000L // ms for single turn
        private const val HIGH_ENERGY_THRESHOLD = 0.7f // Audio energy level
        
        // Context weights for optimization
        private const val URGENCY_WEIGHT = 0.3f
        private const val FORMALITY_WEIGHT = 0.2f
        private const val NOISE_TOLERANCE_WEIGHT = 0.3f
        private const val CLARITY_PRIORITY_WEIGHT = 0.2f
    }
    
    // Data classes for context modeling
    data class ConversationMessage(
        val content: String,
        val isFromUser: Boolean,
        val timestamp: Long,
        val audioLevel: Float = 0f,
        val duration: Long = 0L,
        val detectedEmotion: Emotion = Emotion.NEUTRAL
    )
    
    data class SpeakingEvent(
        val isUser: Boolean,
        val startTime: Long,
        val duration: Long,
        val averageLevel: Float,
        val interruptionCount: Int = 0
    )
    
    data class ConversationMetrics(
        val averageTurnLength: Long = 0L,
        val interruptionRate: Float = 0f,
        val energyLevel: Float = 0f,
        val speechRate: Float = 0f, // words per minute
        val emotionalIntensity: Float = 0f,
        val formalityLevel: Float = 0f,
        val topicComplexity: Float = 0f
    )
    
    data class AudioOptimizationSettings(
        val bargeinSensitivity: Float = 0.5f,      // 0-1, higher = more sensitive
        val noiseSuppressionLevel: Float = 0.5f,   // 0-1, higher = more aggressive
        val echoCancellationLevel: Float = 0.5f,   // 0-1, higher = more aggressive
        val gainControlLevel: Float = 0.5f,        // 0-1, higher = more normalization
        val latencyPriority: Float = 0.5f,         // 0-1, higher = prioritize low latency
        val qualityPriority: Float = 0.5f,         // 0-1, higher = prioritize quality
        val contextReason: String = "Default settings"
    )
    
    // Enums for classification
    enum class ConversationMode {
        CASUAL_CHAT,      // Relaxed conversation, moderate noise tolerance
        BUSINESS_MEETING, // Formal, high clarity needed, low noise tolerance
        PRESENTATION,     // One-way mostly, very high clarity, minimal interruptions
        BRAINSTORM,       // High energy, rapid exchange, interruptions OK
        NOISY_ENVIRONMENT,// High noise, aggressive processing needed
        INTIMATE,         // Quiet, subtle, high sensitivity
        UNKNOWN
    }
    
    enum class UserIntent {
        ASKING_QUESTION,   // Expecting detailed response
        GIVING_INSTRUCTION,// Clear, direct communication needed
        CASUAL_TALKING,    // Natural flow, balanced settings
        SEEKING_HELP,      // Patient interaction, high clarity
        EXPLAINING,        // User doing most talking
        LISTENING,         // User mostly receiving
        INTERRUPTED,       // Quick back-and-forth
        UNKNOWN
    }
    
    enum class Emotion {
        EXCITED, CALM, FRUSTRATED, HAPPY, SERIOUS, CONCERNED, NEUTRAL
    }
    
    // State management
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val speakingHistory = mutableListOf<SpeakingEvent>()
    private var currentMode = ConversationMode.UNKNOWN
    private var currentIntent = UserIntent.UNKNOWN
    
    private val _conversationMode = MutableStateFlow(ConversationMode.CASUAL_CHAT)
    val conversationMode: StateFlow<ConversationMode> = _conversationMode
    
    private val _userIntent = MutableStateFlow(UserIntent.CASUAL_TALKING)
    val userIntent: StateFlow<UserIntent> = _userIntent
    
    private val _optimizationSettings = MutableStateFlow(AudioOptimizationSettings())
    val optimizationSettings: StateFlow<AudioOptimizationSettings> = _optimizationSettings
    
    private val _conversationMetrics = MutableStateFlow(ConversationMetrics())
    val conversationMetrics: StateFlow<ConversationMetrics> = _conversationMetrics
    
    /**
     * Add new message to conversation context
     */
    fun addMessage(
        content: String,
        isFromUser: Boolean,
        audioLevel: Float = 0f,
        duration: Long = 0L
    ) {
        val emotion = detectEmotionFromContent(content, audioLevel)
        val message = ConversationMessage(
            content = content,
            isFromUser = isFromUser,
            timestamp = System.currentTimeMillis(),
            audioLevel = audioLevel,
            duration = duration,
            detectedEmotion = emotion
        )
        
        conversationHistory.add(message)
        
        // Maintain sliding window
        if (conversationHistory.size > CONTEXT_ANALYSIS_WINDOW) {
            conversationHistory.removeAt(0)
        }
        
        // Analyze context after each message
        analyzeConversationContext()
        
        Log.v(TAG, "💬 Message added: mode=$currentMode, intent=$currentIntent, emotion=$emotion")
    }
    
    /**
     * Record speaking event for pattern analysis
     */
    fun recordSpeakingEvent(
        isUser: Boolean,
        duration: Long,
        averageLevel: Float,
        wasInterrupted: Boolean = false
    ) {
        val event = SpeakingEvent(
            isUser = isUser,
            startTime = System.currentTimeMillis(),
            duration = duration,
            averageLevel = averageLevel,
            interruptionCount = if (wasInterrupted) 1 else 0
        )
        
        speakingHistory.add(event)
        
        if (speakingHistory.size > SPEAKING_PATTERN_WINDOW) {
            speakingHistory.removeAt(0)
        }
        
        updateSpeakingMetrics()
    }
    
    /**
     * Analyze conversation context and update mode/intent
     */
    private fun analyzeConversationContext() {
        if (conversationHistory.size < 3) return // Need minimum context
        
        val metrics = calculateConversationMetrics()
        _conversationMetrics.value = metrics
        
        val detectedMode = detectConversationMode(metrics)
        val detectedIntent = detectUserIntent()
        
        if (detectedMode != currentMode) {
            currentMode = detectedMode
            _conversationMode.value = detectedMode
            Log.d(TAG, "🎭 Conversation mode changed to: $detectedMode")
        }
        
        if (detectedIntent != currentIntent) {
            currentIntent = detectedIntent
            _userIntent.value = detectedIntent
            Log.d(TAG, "🎯 User intent detected: $detectedIntent")
        }
        
        // Update audio optimization settings based on new context
        updateAudioOptimization(detectedMode, detectedIntent, metrics)
    }
    
    /**
     * Calculate conversation metrics from recent history
     */
    private fun calculateConversationMetrics(): ConversationMetrics {
        if (conversationHistory.isEmpty()) return ConversationMetrics()
        
        val recentMessages = conversationHistory.takeLast(min(10, conversationHistory.size))
        
        // Average turn length
        val userMessages = recentMessages.filter { it.isFromUser }
        val avgTurnLength = if (userMessages.isNotEmpty()) {
            userMessages.map { it.duration }.average().toLong()
        } else 0L
        
        // Energy level from audio
        val avgEnergyLevel = recentMessages.map { it.audioLevel }.average().toFloat()
        
        // Interruption rate from speaking events
        val interruptionRate = if (speakingHistory.isNotEmpty()) {
            speakingHistory.map { it.interruptionCount }.sum().toFloat() / speakingHistory.size
        } else 0f
        
        // Speech rate estimation (simplified)
        val speechRate = estimateSpeechRate(recentMessages)
        
        // Emotional intensity
        val emotionalIntensity = calculateEmotionalIntensity(recentMessages)
        
        // Formality level
        val formalityLevel = calculateFormalityLevel(recentMessages)
        
        // Topic complexity
        val topicComplexity = calculateTopicComplexity(recentMessages)
        
        return ConversationMetrics(
            averageTurnLength = avgTurnLength,
            interruptionRate = interruptionRate,
            energyLevel = avgEnergyLevel,
            speechRate = speechRate,
            emotionalIntensity = emotionalIntensity,
            formalityLevel = formalityLevel,
            topicComplexity = topicComplexity
        )
    }
    
    /**
     * Detect conversation mode based on metrics
     */
    private fun detectConversationMode(metrics: ConversationMetrics): ConversationMode {
        return when {
            // High formality + low interruption rate = Business meeting
            metrics.formalityLevel > 0.7f && metrics.interruptionRate < 0.2f -> 
                ConversationMode.BUSINESS_MEETING
                
            // Very low interruption + long turns = Presentation mode
            metrics.interruptionRate < 0.1f && metrics.averageTurnLength > 20000L -> 
                ConversationMode.PRESENTATION
                
            // High energy + high interruption = Brainstorming
            metrics.energyLevel > 0.6f && metrics.interruptionRate > 0.4f -> 
                ConversationMode.BRAINSTORM
                
            // High energy but structured = Noisy environment adaptation needed
            metrics.energyLevel > 0.8f && metrics.formalityLevel > 0.5f -> 
                ConversationMode.NOISY_ENVIRONMENT
                
            // Low energy + short turns = Intimate conversation
            metrics.energyLevel < 0.3f && metrics.averageTurnLength < 5000L -> 
                ConversationMode.INTIMATE
                
            // Default to casual chat
            else -> ConversationMode.CASUAL_CHAT
        }
    }
    
    /**
     * Detect user intent from recent patterns
     */
    private fun detectUserIntent(): UserIntent {
        val recentUserMessages = conversationHistory.filter { it.isFromUser }.takeLast(3)
        if (recentUserMessages.isEmpty()) return UserIntent.UNKNOWN
        
        val lastMessage = recentUserMessages.last()
        val content = lastMessage.content.lowercase()
        
        return when {
            // Question patterns
            content.contains("?") || content.startsWith("how") || content.startsWith("what") || 
            content.startsWith("when") || content.startsWith("where") || content.startsWith("why") ->
                UserIntent.ASKING_QUESTION
                
            // Instruction patterns
            content.startsWith("please") || content.contains("can you") || 
            content.contains("could you") || content.contains("would you") ->
                UserIntent.GIVING_INSTRUCTION
                
            // Help-seeking patterns
            content.contains("help") || content.contains("problem") || content.contains("issue") ->
                UserIntent.SEEKING_HELP
                
            // Explanation patterns (longer messages)
            lastMessage.content.length > 100 || lastMessage.duration > 10000L ->
                UserIntent.EXPLAINING
                
            // Rapid back-and-forth
            recentUserMessages.size >= 2 && 
            (lastMessage.timestamp - recentUserMessages[recentUserMessages.size - 2].timestamp) < RAPID_EXCHANGE_THRESHOLD ->
                UserIntent.INTERRUPTED
                
            // Short responses suggest listening mode
            lastMessage.content.length < 20 ->
                UserIntent.LISTENING
                
            // Default
            else -> UserIntent.CASUAL_TALKING
        }
    }
    
    /**
     * Update audio optimization settings based on context
     */
    private fun updateAudioOptimization(
        mode: ConversationMode,
        intent: UserIntent,
        metrics: ConversationMetrics
    ) {
        val settings = calculateOptimalSettings(mode, intent, metrics)
        _optimizationSettings.value = settings
        
        Log.d(TAG, "🔧 Audio settings updated: ${settings.contextReason}")
    }
    
    /**
     * Calculate optimal audio settings for current context
     */
    private fun calculateOptimalSettings(
        mode: ConversationMode,
        intent: UserIntent,
        metrics: ConversationMetrics
    ): AudioOptimizationSettings {
        var bargeinSensitivity = 0.5f
        var noiseSuppressionLevel = 0.5f
        var echoCancellationLevel = 0.5f
        var gainControlLevel = 0.5f
        var latencyPriority = 0.5f
        var qualityPriority = 0.5f
        var reason = "Adaptive optimization"
        
        // Mode-based adjustments
        when (mode) {
            ConversationMode.BUSINESS_MEETING -> {
                bargeinSensitivity = 0.3f // Less sensitive, avoid interrupting
                noiseSuppressionLevel = 0.8f // High noise suppression
                echoCancellationLevel = 0.8f // High echo cancellation
                qualityPriority = 0.8f // Prioritize quality
                reason = "Business meeting mode - prioritizing clarity"
            }
            
            ConversationMode.PRESENTATION -> {
                bargeinSensitivity = 0.2f // Very low, minimal interruption
                noiseSuppressionLevel = 0.9f // Maximum noise suppression
                echoCancellationLevel = 0.9f // Maximum echo cancellation
                qualityPriority = 0.9f // Maximum quality
                reason = "Presentation mode - maximum clarity"
            }
            
            ConversationMode.BRAINSTORM -> {
                bargeinSensitivity = 0.8f // High sensitivity for quick exchanges
                latencyPriority = 0.8f // Low latency for rapid conversation
                noiseSuppressionLevel = 0.6f // Moderate, preserve energy
                reason = "Brainstorm mode - enabling rapid exchange"
            }
            
            ConversationMode.NOISY_ENVIRONMENT -> {
                noiseSuppressionLevel = 0.9f // Maximum noise suppression
                echoCancellationLevel = 0.9f // Maximum echo cancellation
                gainControlLevel = 0.8f // High gain control
                bargeinSensitivity = 0.7f // Higher threshold due to noise
                reason = "Noisy environment - aggressive processing"
            }
            
            ConversationMode.INTIMATE -> {
                bargeinSensitivity = 0.7f // Sensitive to subtle cues
                noiseSuppressionLevel = 0.3f // Preserve natural ambiance
                echoCancellationLevel = 0.4f // Gentle processing
                qualityPriority = 0.7f // Good quality without over-processing
                reason = "Intimate mode - subtle and natural"
            }
            
            ConversationMode.CASUAL_CHAT -> {
                // Balanced settings (defaults)
                reason = "Casual chat - balanced settings"
            }
            
            ConversationMode.UNKNOWN -> {
                reason = "Unknown context - default settings"
            }
        }
        
        // Intent-based fine-tuning
        when (intent) {
            UserIntent.ASKING_QUESTION -> {
                bargeinSensitivity *= 0.8f // Slightly less sensitive
                qualityPriority += 0.1f // Ensure clear answers
            }
            
            UserIntent.GIVING_INSTRUCTION -> {
                qualityPriority += 0.15f // Critical clarity
                noiseSuppressionLevel += 0.1f
            }
            
            UserIntent.SEEKING_HELP -> {
                qualityPriority += 0.2f // Maximum clarity needed
                bargeinSensitivity *= 0.7f // Patient interaction
            }
            
            UserIntent.INTERRUPTED -> {
                latencyPriority += 0.2f // Fast response needed
                bargeinSensitivity += 0.1f // Quick turn-taking
            }
            
            else -> { /* No adjustment */ }
        }
        
        // Metrics-based fine-tuning
        if (metrics.energyLevel > HIGH_ENERGY_THRESHOLD) {
            noiseSuppressionLevel += 0.1f
            echoCancellationLevel += 0.1f
        }
        
        if (metrics.interruptionRate > 0.5f) {
            latencyPriority += 0.15f // Fast response for high interruption rate
        }
        
        // Clamp all values to [0, 1]
        return AudioOptimizationSettings(
            bargeinSensitivity = bargeinSensitivity.coerceIn(0f, 1f),
            noiseSuppressionLevel = noiseSuppressionLevel.coerceIn(0f, 1f),
            echoCancellationLevel = echoCancellationLevel.coerceIn(0f, 1f),
            gainControlLevel = gainControlLevel.coerceIn(0f, 1f),
            latencyPriority = latencyPriority.coerceIn(0f, 1f),
            qualityPriority = qualityPriority.coerceIn(0f, 1f),
            contextReason = reason
        )
    }
    
    // Utility analysis methods
    private fun detectEmotionFromContent(content: String, audioLevel: Float): Emotion {
        val text = content.lowercase()
        return when {
            text.contains("!") && audioLevel > 0.6f -> Emotion.EXCITED
            text.contains("problem") || text.contains("issue") -> Emotion.CONCERNED
            text.contains("great") || text.contains("good") || text.contains("thanks") -> Emotion.HAPPY
            text.contains("sorry") || text.contains("apologize") -> Emotion.SERIOUS
            audioLevel > 0.7f -> Emotion.EXCITED
            audioLevel < 0.3f -> Emotion.CALM
            else -> Emotion.NEUTRAL
        }
    }
    
    private fun estimateSpeechRate(messages: List<ConversationMessage>): Float {
        val userMessages = messages.filter { it.isFromUser && it.duration > 0 }
        if (userMessages.isEmpty()) return 150f // Default WPM
        
        val totalWords = userMessages.sumOf { it.content.split(" ").size }
        val totalDuration = userMessages.sumOf { it.duration } / 1000f / 60f // minutes
        
        return if (totalDuration > 0) totalWords / totalDuration else 150f
    }
    
    private fun calculateEmotionalIntensity(messages: List<ConversationMessage>): Float {
        if (messages.isEmpty()) return 0f
        
        val emotionScores = messages.map { message ->
            when (message.detectedEmotion) {
                Emotion.EXCITED -> 0.9f
                Emotion.FRUSTRATED -> 0.8f
                Emotion.CONCERNED -> 0.7f
                Emotion.HAPPY -> 0.6f
                Emotion.SERIOUS -> 0.5f
                Emotion.CALM -> 0.2f
                Emotion.NEUTRAL -> 0.3f
            }
        }
        
        return emotionScores.average().toFloat()
    }
    
    private fun calculateFormalityLevel(messages: List<ConversationMessage>): Float {
        if (messages.isEmpty()) return 0.5f
        
        val formalityMarkers = listOf("please", "thank you", "would", "could", "may i", "excuse me")
        val casualMarkers = listOf("hey", "yeah", "ok", "cool", "awesome", "lol")
        
        var formalityScore = 0f
        var totalWords = 0
        
        messages.forEach { message ->
            val words = message.content.lowercase().split(" ")
            totalWords += words.size
            
            words.forEach { word ->
                when {
                    formalityMarkers.any { marker -> word.contains(marker) } -> formalityScore += 1f
                    casualMarkers.any { marker -> word.contains(marker) } -> formalityScore -= 0.5f
                }
            }
        }
        
        return if (totalWords > 0) {
            (formalityScore / totalWords + 0.5f).coerceIn(0f, 1f)
        } else 0.5f
    }
    
    private fun calculateTopicComplexity(messages: List<ConversationMessage>): Float {
        if (messages.isEmpty()) return 0.5f
        
        val complexWords = listOf("implementation", "architecture", "optimization", "algorithm", 
                                 "configuration", "specification", "analysis", "technical")
        
        var complexityScore = 0f
        var totalWords = 0
        
        messages.forEach { message ->
            val words = message.content.lowercase().split(" ")
            totalWords += words.size
            
            // Longer sentences suggest complexity
            complexityScore += words.size * 0.01f
            
            // Technical terms suggest complexity
            words.forEach { word ->
                if (complexWords.any { complex -> word.contains(complex) }) {
                    complexityScore += 0.1f
                }
            }
        }
        
        return if (totalWords > 0) {
            (complexityScore / totalWords).coerceIn(0f, 1f)
        } else 0.5f
    }
    
    private fun updateSpeakingMetrics() {
        // Update conversation metrics based on speaking patterns
        // This would trigger a re-analysis of the conversation context
        if (conversationHistory.isNotEmpty()) {
            analyzeConversationContext()
        }
    }
    
    /**
     * Manual mode override for specific scenarios
     */
    fun setManualMode(mode: ConversationMode, reason: String = "Manual override") {
        currentMode = mode
        _conversationMode.value = mode
        
        val metrics = _conversationMetrics.value
        val settings = calculateOptimalSettings(mode, currentIntent, metrics)
        _optimizationSettings.value = settings.copy(contextReason = reason)
        
        Log.d(TAG, "🎛️ Manual mode set: $mode - $reason")
    }
    
    /**
     * Reset conversation context (e.g., new session)
     */
    fun resetContext() {
        conversationHistory.clear()
        speakingHistory.clear()
        currentMode = ConversationMode.UNKNOWN
        currentIntent = UserIntent.UNKNOWN
        
        _conversationMode.value = ConversationMode.CASUAL_CHAT
        _userIntent.value = UserIntent.CASUAL_TALKING
        _optimizationSettings.value = AudioOptimizationSettings()
        _conversationMetrics.value = ConversationMetrics()
        
        Log.d(TAG, "🔄 Conversation context reset")
    }
    
    /**
     * Get comprehensive context report for analytics
     */
    fun getContextReport(): Map<String, Any> {
        return mapOf(
            "conversationMode" to currentMode.name,
            "userIntent" to currentIntent.name,
            "messageCount" to conversationHistory.size,
            "speakingEventCount" to speakingHistory.size,
            "metrics" to mapOf(
                "averageTurnLength" to _conversationMetrics.value.averageTurnLength,
                "interruptionRate" to _conversationMetrics.value.interruptionRate,
                "energyLevel" to _conversationMetrics.value.energyLevel,
                "speechRate" to _conversationMetrics.value.speechRate,
                "emotionalIntensity" to _conversationMetrics.value.emotionalIntensity,
                "formalityLevel" to _conversationMetrics.value.formalityLevel,
                "topicComplexity" to _conversationMetrics.value.topicComplexity
            ),
            "optimizationSettings" to mapOf(
                "bargeinSensitivity" to _optimizationSettings.value.bargeinSensitivity,
                "noiseSuppressionLevel" to _optimizationSettings.value.noiseSuppressionLevel,
                "echoCancellationLevel" to _optimizationSettings.value.echoCancellationLevel,
                "contextReason" to _optimizationSettings.value.contextReason
            )
        )
    }
}