package com.lifo.chat.domain.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Advanced Ducking Engine with Spatial Audio Hints
 * 
 * Provides intelligent audio ducking and spatial positioning
 * for natural conversation flow and enhanced voice clarity.
 * 
 * Features:
 * - Intelligent voice-aware ducking
 * - Spatial audio positioning for virtual conversation space
 * - Conversation flow optimization
 * - Adaptive ducking based on content and context
 * - Smooth gain transitions to prevent artifacts
 * 
 * @author Jarvis AI Assistant
 */
@Singleton
class AdvancedDuckingEngine @Inject constructor() {
    
    companion object {
        private const val TAG = "AdvancedDucking"
        
        // Ducking parameters
        private const val DEFAULT_DUCK_AMOUNT = 0.3f     // 30% volume reduction
        private const val INTELLIGENT_DUCK_AMOUNT = 0.6f // 60% for important speech
        private const val GENTLE_DUCK_AMOUNT = 0.15f     // 15% for ambient ducking
        private const val QUICK_FADE_MS = 50L            // Fast transitions
        private const val SMOOTH_FADE_MS = 200L          // Smooth transitions
        private const val BREATHING_ROOM_MS = 300L       // Pause before unduck
        
        // Spatial audio constants
        private const val AI_POSITION_ANGLE = 30f        // AI voice at 30° right
        private const val USER_POSITION_ANGLE = 0f       // User at center
        private const val VIRTUAL_DISTANCE = 1.5f        // Meters in virtual space
        private const val ROOM_REVERB_FACTOR = 0.2f      // Subtle room ambiance
        
        // Voice analysis thresholds
        private const val SPEECH_ENERGY_THRESHOLD = 0.1f
        private const val VOICE_ACTIVITY_SMOOTHING = 0.8f
        private const val URGENCY_DETECTION_THRESHOLD = 0.7f
    }
    
    // Data classes for advanced ducking
    data class DuckingContext(
        val userSpeaking: Boolean = false,
        val aiSpeaking: Boolean = false,
        val userVoiceLevel: Float = 0f,
        val conversationMode: ConversationContextManager.ConversationMode = ConversationContextManager.ConversationMode.CASUAL_CHAT,
        val isUrgent: Boolean = false,
        val isInterruption: Boolean = false,
        val environmentalNoise: Float = 0f
    )
    
    data class SpatialAudioState(
        val userPosition: AudioPosition = AudioPosition(0f, 0f, 0f),
        val aiPosition: AudioPosition = AudioPosition(
            x = sin(Math.toRadians(AI_POSITION_ANGLE.toDouble())).toFloat() * VIRTUAL_DISTANCE,
            y = 0f,
            z = cos(Math.toRadians(AI_POSITION_ANGLE.toDouble())).toFloat() * VIRTUAL_DISTANCE
        ),
        val listenerOrientation: Float = 0f, // Listener facing direction
        val roomAcoustics: RoomAcoustics = RoomAcoustics.SMALL_ROOM
    )
    
    data class AudioPosition(val x: Float, val y: Float, val z: Float)
    
    data class DuckingParameters(
        val targetGain: Float,           // Final gain level (0-1)
        val fadeInDuration: Long,        // Fade in time (ms)
        val fadeOutDuration: Long,       // Fade out time (ms)
        val spatialPanning: Float,       // Stereo position (-1 to 1)
        val distanceAttenuation: Float,  // Distance-based volume (0-1)
        val reverbLevel: Float,          // Reverb amount (0-1)
        val priority: DuckingPriority,   // Ducking priority level
        val reason: String               // Human readable reason
    )
    
    enum class DuckingPriority {
        CRITICAL,    // Immediate ducking (safety, alerts)
        HIGH,        // Important speech (instructions, questions)
        NORMAL,      // Regular conversation
        LOW,         // Background, ambient
        PASSIVE      // Gentle, barely noticeable
    }
    
    enum class RoomAcoustics {
        ANECHOIC,     // Dead room, no reverb
        SMALL_ROOM,   // Typical room acoustics
        LARGE_ROOM,   // Conference room
        OUTDOOR,      // Open space
        CUSTOM        // User-defined
    }
    
    // State management
    private val _duckingContext = MutableStateFlow(DuckingContext())
    val duckingContext: StateFlow<DuckingContext> = _duckingContext
    
    private val _spatialState = MutableStateFlow(SpatialAudioState())
    val spatialState: StateFlow<SpatialAudioState> = _spatialState
    
    private val _currentParameters = MutableStateFlow(createDefaultParameters())
    val currentParameters: StateFlow<DuckingParameters> = _currentParameters
    
    // Internal state
    private var currentUserGain = 1.0f
    private var currentAiGain = 1.0f
    private var lastUserActivity = 0L
    private var lastAiActivity = 0L
    private var smoothedUserLevel = 0f
    private var smoothedAiLevel = 0f
    
    // Voice activity tracking
    private val userVoiceHistory = mutableListOf<Float>()
    private val aiVoiceHistory = mutableListOf<Float>()
    
    /**
     * Initialize advanced ducking engine
     */
    fun initialize() {
        Log.d(TAG, "🎛️ Initializing advanced ducking engine with spatial audio")
        _spatialState.value = createOptimalSpatialState()
        _currentParameters.value = createDefaultParameters()
    }
    
    /**
     * Update ducking context from real-time audio analysis
     */
    fun updateContext(
        userSpeaking: Boolean,
        aiSpeaking: Boolean,
        userVoiceLevel: Float,
        aiVoiceLevel: Float,
        conversationMode: ConversationContextManager.ConversationMode = ConversationContextManager.ConversationMode.CASUAL_CHAT,
        environmentalNoise: Float = 0f
    ) {
        val currentTime = System.currentTimeMillis()
        
        // Smooth voice level transitions
        smoothedUserLevel = smoothedUserLevel * VOICE_ACTIVITY_SMOOTHING + userVoiceLevel * (1f - VOICE_ACTIVITY_SMOOTHING)
        smoothedAiLevel = smoothedAiLevel * VOICE_ACTIVITY_SMOOTHING + aiVoiceLevel * (1f - VOICE_ACTIVITY_SMOOTHING)
        
        // Track voice activity timing
        if (userSpeaking) lastUserActivity = currentTime
        if (aiSpeaking) lastAiActivity = currentTime
        
        // Detect urgency from voice characteristics
        val isUrgent = detectUrgency(userVoiceLevel, userVoiceHistory)
        val isInterruption = detectInterruption(currentTime)
        
        // Update context
        val newContext = DuckingContext(
            userSpeaking = userSpeaking,
            aiSpeaking = aiSpeaking,
            userVoiceLevel = smoothedUserLevel,
            conversationMode = conversationMode,
            isUrgent = isUrgent,
            isInterruption = isInterruption,
            environmentalNoise = environmentalNoise
        )
        
        _duckingContext.value = newContext
        
        // Calculate and apply new ducking parameters
        val parameters = calculateIntelligentDucking(newContext)
        _currentParameters.value = parameters
        
        Log.v(TAG, "🎚️ Ducking updated: user=${smoothedUserLevel}, ai=${smoothedAiLevel}, urgent=$isUrgent")
    }
    
    /**
     * Calculate intelligent ducking parameters based on context
     */
    private fun calculateIntelligentDucking(context: DuckingContext): DuckingParameters {
        var targetGain = 1.0f
        var fadeInDuration = SMOOTH_FADE_MS
        var fadeOutDuration = SMOOTH_FADE_MS
        var priority = DuckingPriority.NORMAL
        var reason = "No ducking needed"
        
        when {
            // Critical: User interrupting AI (barge-in scenario)
            context.isInterruption && context.userSpeaking && context.aiSpeaking -> {
                targetGain = 0.1f // Aggressive ducking
                fadeInDuration = QUICK_FADE_MS
                fadeOutDuration = QUICK_FADE_MS
                priority = DuckingPriority.CRITICAL
                reason = "User interruption - immediate ducking"
            }
            
            // High: Urgent user speech while AI is speaking
            context.isUrgent && context.userSpeaking && context.aiSpeaking -> {
                targetGain = 0.2f
                fadeInDuration = QUICK_FADE_MS
                priority = DuckingPriority.HIGH
                reason = "Urgent user speech detected"
            }
            
            // Normal: Regular user speech over AI
            context.userSpeaking && context.aiSpeaking -> {
                targetGain = getDuckingAmountForMode(context.conversationMode)
                priority = DuckingPriority.NORMAL
                reason = "User speaking over AI"
            }
            
            // Gentle: AI speaking with some user activity
            context.aiSpeaking && context.userVoiceLevel > SPEECH_ENERGY_THRESHOLD -> {
                targetGain = 0.7f // Gentle ducking
                fadeInDuration = SMOOTH_FADE_MS * 2 // Slower fade
                priority = DuckingPriority.LOW
                reason = "User activity during AI speech"
            }
            
            // Adaptive: Environmental noise compensation
            context.environmentalNoise > 0.3f -> {
                targetGain = 1.0f + (context.environmentalNoise * 0.2f) // Slight boost
                priority = DuckingPriority.PASSIVE
                reason = "Environmental noise compensation"
            }
            
            // Default: No ducking
            else -> {
                targetGain = 1.0f
                priority = DuckingPriority.PASSIVE
                reason = "Normal audio levels"
            }
        }
        
        // Calculate spatial audio parameters
        val spatial = calculateSpatialParameters(context)
        
        return DuckingParameters(
            targetGain = targetGain.coerceIn(0.05f, 1.2f),
            fadeInDuration = fadeInDuration,
            fadeOutDuration = fadeOutDuration,
            spatialPanning = spatial.first,
            distanceAttenuation = spatial.second,
            reverbLevel = calculateReverbLevel(context),
            priority = priority,
            reason = reason
        )
    }
    
    /**
     * Get conversation mode specific ducking amount
     */
    private fun getDuckingAmountForMode(mode: ConversationContextManager.ConversationMode): Float {
        return when (mode) {
            ConversationContextManager.ConversationMode.BUSINESS_MEETING -> 0.2f // Aggressive for clarity
            ConversationContextManager.ConversationMode.PRESENTATION -> 0.1f    // Very aggressive
            ConversationContextManager.ConversationMode.BRAINSTORM -> 0.5f      // Gentle for flow
            ConversationContextManager.ConversationMode.INTIMATE -> 0.4f        // Subtle
            ConversationContextManager.ConversationMode.NOISY_ENVIRONMENT -> 0.3f // Moderate
            else -> DEFAULT_DUCK_AMOUNT
        }
    }
    
    /**
     * Calculate spatial audio positioning
     */
    private fun calculateSpatialParameters(context: DuckingContext): Pair<Float, Float> {
        val spatialState = _spatialState.value
        
        // Calculate stereo panning based on virtual positions
        val aiAngle = atan2(spatialState.aiPosition.x, spatialState.aiPosition.z)
        val userAngle = atan2(spatialState.userPosition.x, spatialState.userPosition.z)
        
        // AI positioned slightly to the right, user centered
        val aiPanning = sin(aiAngle) * 0.3f // Subtle right positioning
        val userPanning = sin(userAngle) * 0.1f // Mostly centered
        
        // Distance-based attenuation for AI voice
        val aiDistance = sqrt(
            spatialState.aiPosition.x.pow(2) + 
            spatialState.aiPosition.z.pow(2)
        )
        val distanceAttenuation = 1.0f / (1.0f + aiDistance * 0.1f) // Subtle distance effect
        
        // Choose panning based on who's speaking
        val finalPanning = when {
            context.aiSpeaking && !context.userSpeaking -> aiPanning
            context.userSpeaking && !context.aiSpeaking -> userPanning
            else -> 0f // Center when both or neither speaking
        }
        
        return Pair(finalPanning, distanceAttenuation)
    }
    
    /**
     * Calculate reverb level based on context and room acoustics
     */
    private fun calculateReverbLevel(context: DuckingContext): Float {
        val baseReverb = when (_spatialState.value.roomAcoustics) {
            RoomAcoustics.ANECHOIC -> 0f
            RoomAcoustics.SMALL_ROOM -> 0.1f
            RoomAcoustics.LARGE_ROOM -> 0.3f
            RoomAcoustics.OUTDOOR -> 0.05f
            RoomAcoustics.CUSTOM -> ROOM_REVERB_FACTOR
        }
        
        // Reduce reverb during important speech for clarity
        val clarityFactor = when {
            context.isUrgent -> 0.3f
            context.conversationMode == ConversationContextManager.ConversationMode.BUSINESS_MEETING -> 0.5f
            context.conversationMode == ConversationContextManager.ConversationMode.PRESENTATION -> 0.3f
            else -> 1.0f
        }
        
        return (baseReverb * clarityFactor).coerceIn(0f, 0.5f)
    }
    
    /**
     * Detect urgency from voice characteristics
     */
    private fun detectUrgency(voiceLevel: Float, history: MutableList<Float>): Boolean {
        history.add(voiceLevel)
        if (history.size > 10) history.removeAt(0)
        
        if (history.size < 5) return false
        
        // Check for sudden increase in voice level (urgency indicator)
        val recent = history.takeLast(3).average()
        val baseline = history.dropLast(3).average()
        
        val increase = if (baseline > 0) (recent - baseline) / baseline else 0.0
        
        return increase > URGENCY_DETECTION_THRESHOLD && voiceLevel > 0.5f
    }
    
    /**
     * Detect interruption from timing patterns
     */
    private fun detectInterruption(currentTime: Long): Boolean {
        val timeSinceAiSpeech = currentTime - lastAiActivity
        val timeSinceUserSpeech = currentTime - lastUserActivity
        
        // Interruption if user starts speaking shortly after AI
        return timeSinceAiSpeech < 500L && timeSinceUserSpeech < 100L
    }
    
    /**
     * Set spatial audio configuration
     */
    fun configureSpatialAudio(
        roomAcoustics: RoomAcoustics = RoomAcoustics.SMALL_ROOM,
        aiAngle: Float = AI_POSITION_ANGLE,
        distance: Float = VIRTUAL_DISTANCE
    ) {
        val newState = SpatialAudioState(
            userPosition = AudioPosition(0f, 0f, 0f), // User always at center
            aiPosition = AudioPosition(
                x = sin(Math.toRadians(aiAngle.toDouble())).toFloat() * distance,
                y = 0f,
                z = cos(Math.toRadians(aiAngle.toDouble())).toFloat() * distance
            ),
            listenerOrientation = 0f,
            roomAcoustics = roomAcoustics
        )
        
        _spatialState.value = newState
        Log.d(TAG, "🌀 Spatial audio configured: room=$roomAcoustics, angle=${aiAngle}°, distance=${distance}m")
    }
    
    /**
     * Apply smart ducking for specific conversation scenarios
     */
    fun applyScenarioBasedDucking(scenario: String) {
        val parameters = when (scenario.lowercase()) {
            "presentation" -> DuckingParameters(
                targetGain = 0.1f,
                fadeInDuration = QUICK_FADE_MS,
                fadeOutDuration = SMOOTH_FADE_MS,
                spatialPanning = 0f,
                distanceAttenuation = 1f,
                reverbLevel = 0.05f,
                priority = DuckingPriority.CRITICAL,
                reason = "Presentation mode - maximum clarity"
            )
            
            "meeting" -> DuckingParameters(
                targetGain = 0.25f,
                fadeInDuration = SMOOTH_FADE_MS,
                fadeOutDuration = SMOOTH_FADE_MS,
                spatialPanning = 0.2f,
                distanceAttenuation = 0.9f,
                reverbLevel = 0.1f,
                priority = DuckingPriority.HIGH,
                reason = "Meeting mode - professional clarity"
            )
            
            "casual" -> DuckingParameters(
                targetGain = 0.4f,
                fadeInDuration = SMOOTH_FADE_MS * 2,
                fadeOutDuration = SMOOTH_FADE_MS,
                spatialPanning = 0.3f,
                distanceAttenuation = 0.85f,
                reverbLevel = 0.15f,
                priority = DuckingPriority.NORMAL,
                reason = "Casual mode - natural conversation"
            )
            
            else -> createDefaultParameters()
        }
        
        _currentParameters.value = parameters
        Log.d(TAG, "🎭 Applied scenario ducking: $scenario")
    }
    
    /**
     * Get real-time audio gain adjustments
     */
    fun getAudioGainAdjustments(): Pair<Float, Float> {
        val params = _currentParameters.value
        val context = _duckingContext.value
        
        // Calculate user audio gain (for feedback/monitoring)
        val userGain = when {
            context.aiSpeaking && context.userSpeaking -> params.targetGain
            context.aiSpeaking -> 0.8f // Slight reduction when AI speaks
            else -> 1.0f
        }
        
        // Calculate AI audio gain
        val aiGain = when {
            context.userSpeaking && context.isInterruption -> 0.1f // Aggressive duck on interruption
            context.userSpeaking -> params.targetGain
            else -> 1.0f * params.distanceAttenuation // Apply spatial attenuation
        }
        
        return Pair(userGain, aiGain)
    }
    
    /**
     * Create optimal spatial state for voice chat
     */
    private fun createOptimalSpatialState(): SpatialAudioState {
        return SpatialAudioState(
            userPosition = AudioPosition(0f, 0f, 0f),
            aiPosition = AudioPosition(
                x = sin(Math.toRadians(AI_POSITION_ANGLE.toDouble())).toFloat() * VIRTUAL_DISTANCE,
                y = 0f,
                z = cos(Math.toRadians(AI_POSITION_ANGLE.toDouble())).toFloat() * VIRTUAL_DISTANCE
            ),
            listenerOrientation = 0f,
            roomAcoustics = RoomAcoustics.SMALL_ROOM
        )
    }
    
    /**
     * Create default ducking parameters
     */
    private fun createDefaultParameters(): DuckingParameters {
        return DuckingParameters(
            targetGain = 1.0f,
            fadeInDuration = SMOOTH_FADE_MS,
            fadeOutDuration = SMOOTH_FADE_MS,
            spatialPanning = 0f,
            distanceAttenuation = 1f,
            reverbLevel = ROOM_REVERB_FACTOR,
            priority = DuckingPriority.NORMAL,
            reason = "Default settings"
        )
    }
    
    /**
     * Get comprehensive ducking analytics
     */
    fun getDuckingAnalytics(): Map<String, Any> {
        val params = _currentParameters.value
        val context = _duckingContext.value
        val spatial = _spatialState.value
        
        return mapOf(
            "currentGain" to params.targetGain,
            "duckingReason" to params.reason,
            "priority" to params.priority.name,
            "spatialPanning" to params.spatialPanning,
            "reverbLevel" to params.reverbLevel,
            "userSpeaking" to context.userSpeaking,
            "aiSpeaking" to context.aiSpeaking,
            "isUrgent" to context.isUrgent,
            "isInterruption" to context.isInterruption,
            "conversationMode" to context.conversationMode.name,
            "roomAcoustics" to spatial.roomAcoustics.name,
            "aiPosition" to mapOf(
                "x" to spatial.aiPosition.x,
                "y" to spatial.aiPosition.y,
                "z" to spatial.aiPosition.z
            ),
            "smoothedUserLevel" to smoothedUserLevel,
            "smoothedAiLevel" to smoothedAiLevel
        )
    }
}