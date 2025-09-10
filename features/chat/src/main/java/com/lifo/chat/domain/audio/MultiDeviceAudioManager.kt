package com.lifo.chat.domain.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-Device Audio Handoff Manager
 * 
 * Provides seamless audio device switching and routing optimization
 * for the best possible voice chat experience across different devices.
 * 
 * Features:
 * - Automatic optimal device detection
 * - Seamless handoff between audio devices
 * - Quality-based device scoring and recommendation
 * - Context-aware device selection
 * - Bluetooth SCO optimization
 * 
 * @author Jarvis AI Assistant
 */
@Singleton
class MultiDeviceAudioManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "MultiDeviceAudioManager"
        
        // Device priority scores (higher = better for voice chat)
        private const val WIRED_HEADSET_SCORE = 100f
        private const val USB_HEADSET_SCORE = 95f
        private const val BLUETOOTH_SCO_SCORE = 85f
        private const val BLUETOOTH_A2DP_SCORE = 60f // Lower for voice
        private const val EARPIECE_SCORE = 75f
        private const val SPEAKER_SCORE = 50f
        private const val DEFAULT_SCORE = 25f
        
        // Quality factors
        private const val LATENCY_FACTOR = 0.3f
        private const val ECHO_REDUCTION_FACTOR = 0.3f
        private const val NOISE_ISOLATION_FACTOR = 0.2f
        private const val CONVENIENCE_FACTOR = 0.2f
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Data classes for device management
    data class AudioDeviceProfile(
        val deviceInfo: AudioDeviceInfo,
        val deviceType: AudioDeviceType,
        val qualityScore: Float,
        val isAvailable: Boolean,
        val isRecommended: Boolean,
        val strengths: List<String>,
        val limitations: List<String>,
        val estimatedLatency: Float, // milliseconds
        val echoReductionCapability: Float, // 0-1 scale
        val noiseIsolation: Float // 0-1 scale
    )
    
    data class HandoffResult(
        val success: Boolean,
        val fromDevice: AudioDeviceProfile?,
        val toDevice: AudioDeviceProfile,
        val reason: String,
        val optimizationApplied: List<String>
    )
    
    enum class AudioDeviceType {
        WIRED_HEADSET,
        USB_HEADSET, 
        BLUETOOTH_SCO,
        BLUETOOTH_A2DP,
        EARPIECE,
        SPEAKER,
        UNKNOWN
    }
    
    enum class HandoffTrigger {
        USER_MANUAL,      // User explicitly selected device
        DEVICE_CONNECTED, // New device connected
        QUALITY_OPTIMIZATION, // Switching for better quality
        CONTEXT_CHANGE,   // Conversation context changed
        AUTOMATIC         // System-recommended optimal switch
    }
    
    // State management
    private val _currentDevice = MutableStateFlow<AudioDeviceProfile?>(null)
    val currentDevice: StateFlow<AudioDeviceProfile?> = _currentDevice
    
    private val _availableDevices = MutableStateFlow<List<AudioDeviceProfile>>(emptyList())
    val availableDevices: StateFlow<List<AudioDeviceProfile>> = _availableDevices
    
    private val _recommendedDevice = MutableStateFlow<AudioDeviceProfile?>(null)
    val recommendedDevice: StateFlow<AudioDeviceProfile?> = _recommendedDevice
    
    private var lastHandoffTime = 0L
    private val handoffHistory = mutableListOf<HandoffResult>()
    
    /**
     * Initialize multi-device management
     */
    fun initialize() {
        Log.d(TAG, "🎧 Initializing multi-device audio management")
        refreshAvailableDevices()
        detectOptimalDevice()
    }
    
    /**
     * Refresh list of available audio devices
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun refreshAvailableDevices() {
        val devices = mutableListOf<AudioDeviceProfile>()
        
        try {
            // Get all available audio devices
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            
            audioDevices.forEach { deviceInfo ->
                val profile = createDeviceProfile(deviceInfo)
                if (profile != null) {
                    devices.add(profile)
                    Log.v(TAG, "📱 Found device: ${profile.deviceType} (score: ${profile.qualityScore})")
                }
            }
            
            // Sort by quality score (best first)
            devices.sortByDescending { it.qualityScore }
            
            _availableDevices.value = devices
            
            Log.d(TAG, "🔍 Found ${devices.size} audio devices")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices", e)
        }
    }
    
    /**
     * Create detailed device profile with quality scoring
     */
    private fun createDeviceProfile(deviceInfo: AudioDeviceInfo): AudioDeviceProfile? {
        val deviceType = classifyDeviceType(deviceInfo)
        if (deviceType == AudioDeviceType.UNKNOWN) return null
        
        val baseScore = getBaseScore(deviceType)
        val qualityFactors = calculateQualityFactors(deviceType)
        val finalScore = baseScore * qualityFactors
        
        val strengths = mutableListOf<String>()
        val limitations = mutableListOf<String>()
        
        // Analyze device characteristics
        when (deviceType) {
            AudioDeviceType.WIRED_HEADSET -> {
                strengths.addAll(listOf("Zero latency", "Excellent echo cancellation", "Private"))
                limitations.add("Requires cable connection")
            }
            AudioDeviceType.USB_HEADSET -> {
                strengths.addAll(listOf("Digital quality", "Low latency", "Professional grade"))
                limitations.add("Limited mobility")
            }
            AudioDeviceType.BLUETOOTH_SCO -> {
                strengths.addAll(listOf("Wireless freedom", "Optimized for voice", "Good battery"))
                limitations.addAll(listOf("Higher latency", "Compressed audio"))
            }
            AudioDeviceType.BLUETOOTH_A2DP -> {
                strengths.addAll(listOf("High quality audio", "Wireless"))
                limitations.addAll(listOf("Not optimized for voice", "Higher latency"))
            }
            AudioDeviceType.EARPIECE -> {
                strengths.addAll(listOf("Private", "Built-in", "Low power"))
                limitations.addAll(listOf("Single speaker", "Limited quality"))
            }
            AudioDeviceType.SPEAKER -> {
                strengths.addAll(listOf("Hands-free", "Built-in", "Good for groups"))
                limitations.addAll(listOf("Echo prone", "Not private", "Background noise"))
            }
            AudioDeviceType.UNKNOWN -> return null
        }
        
        return AudioDeviceProfile(
            deviceInfo = deviceInfo,
            deviceType = deviceType,
            qualityScore = finalScore,
            isAvailable = true,
            isRecommended = finalScore == _availableDevices.value.maxOfOrNull { it.qualityScore },
            strengths = strengths,
            limitations = limitations,
            estimatedLatency = estimateLatency(deviceType),
            echoReductionCapability = estimateEchoReduction(deviceType),
            noiseIsolation = estimateNoiseIsolation(deviceType)
        )
    }
    
    /**
     * Classify audio device type from AudioDeviceInfo
     */
    private fun classifyDeviceType(deviceInfo: AudioDeviceInfo): AudioDeviceType {
        return when (deviceInfo.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED_HEADSET
            AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceType.USB_HEADSET
            AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceType.USB_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceType.BLUETOOTH_SCO
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioDeviceType.EARPIECE
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
            else -> AudioDeviceType.UNKNOWN
        }
    }
    
    /**
     * Get base quality score for device type
     */
    private fun getBaseScore(deviceType: AudioDeviceType): Float {
        return when (deviceType) {
            AudioDeviceType.WIRED_HEADSET -> WIRED_HEADSET_SCORE
            AudioDeviceType.USB_HEADSET -> USB_HEADSET_SCORE
            AudioDeviceType.BLUETOOTH_SCO -> BLUETOOTH_SCO_SCORE
            AudioDeviceType.BLUETOOTH_A2DP -> BLUETOOTH_A2DP_SCORE
            AudioDeviceType.EARPIECE -> EARPIECE_SCORE
            AudioDeviceType.SPEAKER -> SPEAKER_SCORE
            AudioDeviceType.UNKNOWN -> DEFAULT_SCORE
        }
    }
    
    /**
     * Calculate quality adjustment factors
     */
    private fun calculateQualityFactors(deviceType: AudioDeviceType): Float {
        var factor = 1.0f
        
        // Latency factor
        val latencyPenalty = when (deviceType) {
            AudioDeviceType.BLUETOOTH_A2DP -> 0.15f
            AudioDeviceType.BLUETOOTH_SCO -> 0.1f
            else -> 0f
        }
        factor -= latencyPenalty * LATENCY_FACTOR
        
        // Echo reduction bonus
        val echoBonus = when (deviceType) {
            AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> 0.2f
            AudioDeviceType.BLUETOOTH_SCO -> 0.1f
            AudioDeviceType.EARPIECE -> 0.15f
            else -> 0f
        }
        factor += echoBonus * ECHO_REDUCTION_FACTOR
        
        // Noise isolation bonus
        val noiseBonus = when (deviceType) {
            AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> 0.25f
            AudioDeviceType.BLUETOOTH_SCO -> 0.15f
            AudioDeviceType.EARPIECE -> 0.1f
            else -> 0f
        }
        factor += noiseBonus * NOISE_ISOLATION_FACTOR
        
        return factor.coerceIn(0.5f, 1.5f)
    }
    
    /**
     * Detect and recommend optimal device for current context
     */
    fun detectOptimalDevice(): AudioDeviceProfile? {
        val devices = _availableDevices.value
        if (devices.isEmpty()) return null
        
        // Find highest scoring available device
        val optimal = devices.filter { it.isAvailable }.maxByOrNull { it.qualityScore }
        
        optimal?.let {
            _recommendedDevice.value = it
            Log.d(TAG, "🎯 Optimal device: ${it.deviceType} (score: ${it.qualityScore})")
        }
        
        return optimal
    }
    
    /**
     * Perform seamless device handoff
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun performHandoff(
        targetDevice: AudioDeviceProfile,
        trigger: HandoffTrigger = HandoffTrigger.AUTOMATIC
    ): HandoffResult {
        val currentTime = System.currentTimeMillis()
        
        // Prevent too frequent handoffs
        if (currentTime - lastHandoffTime < 2000) {
            return HandoffResult(
                success = false,
                fromDevice = _currentDevice.value,
                toDevice = targetDevice,
                reason = "Handoff too soon after last attempt",
                optimizationApplied = emptyList()
            )
        }
        
        val fromDevice = _currentDevice.value
        val optimizations = mutableListOf<String>()
        
        try {
            Log.d(TAG, "🔄 Performing handoff to ${targetDevice.deviceType}")
            
            // Pre-handoff optimizations
            prepareForHandoff(targetDevice, optimizations)
            
            // Perform the actual device switch
            val success = switchToDevice(targetDevice)
            
            if (success) {
                // Post-handoff optimizations
                optimizeForDevice(targetDevice, optimizations)
                
                _currentDevice.value = targetDevice
                lastHandoffTime = currentTime
                
                Log.d(TAG, "✅ Handoff successful to ${targetDevice.deviceType}")
                
                val result = HandoffResult(
                    success = true,
                    fromDevice = fromDevice,
                    toDevice = targetDevice,
                    reason = getTriggerReason(trigger),
                    optimizationApplied = optimizations
                )
                
                handoffHistory.add(result)
                return result
                
            } else {
                Log.e(TAG, "❌ Handoff failed to ${targetDevice.deviceType}")
                return HandoffResult(
                    success = false,
                    fromDevice = fromDevice,
                    toDevice = targetDevice,
                    reason = "Device switch failed",
                    optimizationApplied = optimizations
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during handoff", e)
            return HandoffResult(
                success = false,
                fromDevice = fromDevice,
                toDevice = targetDevice,
                reason = "Exception: ${e.message}",
                optimizationApplied = optimizations
            )
        }
    }
    
    /**
     * Prepare audio system for device handoff
     */
    private fun prepareForHandoff(device: AudioDeviceProfile, optimizations: MutableList<String>) {
        // Set appropriate audio mode for device type
        when (device.deviceType) {
            AudioDeviceType.BLUETOOTH_SCO -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isBluetoothScoOn = true
                audioManager.startBluetoothSco()
                optimizations.add("Enabled Bluetooth SCO mode")
            }
            AudioDeviceType.SPEAKER -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                optimizations.add("Enabled speakerphone mode")
            }
            else -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                optimizations.add("Set communication mode")
            }
        }
    }
    
    /**
     * Switch to target audio device
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun switchToDevice(device: AudioDeviceProfile): Boolean {
        return try {
            val success = audioManager.setCommunicationDevice(device.deviceInfo)
            if (success) {
                Log.d(TAG, "🎧 Successfully switched to device: ${device.deviceInfo.productName}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch device", e)
            false
        }
    }
    
    /**
     * Apply device-specific optimizations
     */
    private fun optimizeForDevice(device: AudioDeviceProfile, optimizations: MutableList<String>) {
        when (device.deviceType) {
            AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> {
                // Optimize for low latency and high quality
                optimizations.add("Optimized for wired audio quality")
            }
            AudioDeviceType.BLUETOOTH_SCO -> {
                // Optimize for voice communication
                optimizations.add("Optimized for Bluetooth voice profile")
            }
            AudioDeviceType.SPEAKER -> {
                // Optimize for speakerphone use
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                optimizations.add("Applied speakerphone optimizations")
            }
            AudioDeviceType.EARPIECE -> {
                // Optimize for private conversation
                audioManager.isSpeakerphoneOn = false
                optimizations.add("Optimized for private conversation")
            }
            else -> {
                optimizations.add("Applied default optimizations")
            }
        }
    }
    
    /**
     * Auto-handoff when better device becomes available
     */
    fun checkForBetterDevice(): Boolean {
        refreshAvailableDevices()
        val current = _currentDevice.value
        val optimal = detectOptimalDevice()
        
        if (optimal != null && current != null && 
            optimal.qualityScore > current.qualityScore + 10f) { // Significant improvement threshold
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val result = performHandoff(optimal, HandoffTrigger.QUALITY_OPTIMIZATION)
                return result.success
            }
        }
        
        return false
    }
    
    /**
     * Recommend device for specific conversation context
     */
    fun recommendDeviceForContext(
        isNoisy: Boolean = false,
        isPrivate: Boolean = false,
        needsLowLatency: Boolean = false,
        isGroupConversation: Boolean = false
    ): AudioDeviceProfile? {
        val devices = _availableDevices.value.filter { it.isAvailable }
        if (devices.isEmpty()) return null
        
        var bestDevice: AudioDeviceProfile? = null
        var bestScore = 0f
        
        devices.forEach { device ->
            var contextScore = device.qualityScore
            
            // Adjust score based on context
            when (device.deviceType) {
                AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> {
                    if (isNoisy) contextScore += 20f // Excellent noise isolation
                    if (isPrivate) contextScore += 15f // Private conversation
                    if (needsLowLatency) contextScore += 25f // Zero latency
                    if (isGroupConversation) contextScore -= 10f // Less convenient for groups
                }
                AudioDeviceType.BLUETOOTH_SCO -> {
                    if (isNoisy) contextScore += 10f // Good noise isolation
                    if (isPrivate) contextScore += 10f // Wireless privacy
                    if (needsLowLatency) contextScore -= 5f // Slight latency
                }
                AudioDeviceType.SPEAKER -> {
                    if (isGroupConversation) contextScore += 20f // Perfect for groups
                    if (isPrivate) contextScore -= 20f // Not private
                    if (isNoisy) contextScore -= 15f // Echo prone in noise
                }
                AudioDeviceType.EARPIECE -> {
                    if (isPrivate) contextScore += 15f // Very private
                    if (isGroupConversation) contextScore -= 15f // Single person only
                }
                else -> { /* No adjustment */ }
            }
            
            if (contextScore > bestScore) {
                bestScore = contextScore
                bestDevice = device
            }
        }
        
        return bestDevice
    }
    
    // Utility methods
    private fun estimateLatency(deviceType: AudioDeviceType): Float {
        return when (deviceType) {
            AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> 5f
            AudioDeviceType.EARPIECE, AudioDeviceType.SPEAKER -> 10f
            AudioDeviceType.BLUETOOTH_SCO -> 40f
            AudioDeviceType.BLUETOOTH_A2DP -> 100f
            AudioDeviceType.UNKNOWN -> 50f
        }
    }
    
    private fun estimateEchoReduction(deviceType: AudioDeviceType): Float {
        return when (deviceType) {
            AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> 0.95f
            AudioDeviceType.BLUETOOTH_SCO -> 0.8f
            AudioDeviceType.EARPIECE -> 0.85f
            AudioDeviceType.SPEAKER -> 0.3f
            AudioDeviceType.BLUETOOTH_A2DP -> 0.6f
            AudioDeviceType.UNKNOWN -> 0.5f
        }
    }
    
    private fun estimateNoiseIsolation(deviceType: AudioDeviceType): Float {
        return when (deviceType) {
            AudioDeviceType.WIRED_HEADSET, AudioDeviceType.USB_HEADSET -> 0.9f
            AudioDeviceType.BLUETOOTH_SCO -> 0.75f
            AudioDeviceType.EARPIECE -> 0.6f
            AudioDeviceType.SPEAKER -> 0.1f
            AudioDeviceType.BLUETOOTH_A2DP -> 0.7f
            AudioDeviceType.UNKNOWN -> 0.5f
        }
    }
    
    private fun getTriggerReason(trigger: HandoffTrigger): String {
        return when (trigger) {
            HandoffTrigger.USER_MANUAL -> "User manually selected device"
            HandoffTrigger.DEVICE_CONNECTED -> "New device connected"
            HandoffTrigger.QUALITY_OPTIMIZATION -> "Switching for better audio quality"
            HandoffTrigger.CONTEXT_CHANGE -> "Conversation context changed"
            HandoffTrigger.AUTOMATIC -> "System recommended optimal device"
        }
    }
    
    /**
     * Get comprehensive device management report
     */
    fun getDeviceReport(): Map<String, Any> {
        return mapOf(
            "currentDevice" to (_currentDevice.value?.deviceType?.name ?: "None"),
            "recommendedDevice" to (_recommendedDevice.value?.deviceType?.name ?: "None"),
            "availableDevices" to _availableDevices.value.size,
            "handoffHistory" to handoffHistory.size,
            "lastHandoffTime" to lastHandoffTime,
            "deviceProfiles" to _availableDevices.value.map { device ->
                mapOf(
                    "type" to device.deviceType.name,
                    "score" to device.qualityScore,
                    "latency" to device.estimatedLatency,
                    "echoReduction" to device.echoReductionCapability,
                    "noiseIsolation" to device.noiseIsolation,
                    "strengths" to device.strengths,
                    "limitations" to device.limitations
                )
            }
        )
    }
}