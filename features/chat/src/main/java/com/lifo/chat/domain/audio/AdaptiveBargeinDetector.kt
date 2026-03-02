package com.lifo.chat.domain.audio


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

/**
 * Adaptive Barge-In Detection System
 * 
 * Learns user voice patterns and ambient conditions to provide
 * increasingly accurate voice activity detection for natural interruptions.
 * 
 * Features:
 * - User voice profiling with spectral analysis
 * - Adaptive thresholds based on environment
 * - Machine learning-inspired pattern recognition
 * - Noise floor auto-calibration
 * 
 * @author Jarvis AI Assistant
 */
class AdaptiveBargeinDetector {
    
    companion object {
        // Learning parameters
        private const val VOICE_PROFILE_SAMPLES = 50 // Samples needed for initial profile
        private const val ADAPTATION_RATE = 0.1f // How quickly we adapt to new patterns
        private const val CONFIDENCE_THRESHOLD = 0.75f // Confidence needed for barge-in
        
        // Spectral analysis
        private const val SPEECH_FREQ_MIN = 300f // Human speech frequency range
        private const val SPEECH_FREQ_MAX = 3400f
        private const val SPECTRAL_BANDS = 8 // Frequency bands for analysis
        
        // Environmental adaptation
        private const val NOISE_FLOOR_SAMPLES = 100 // Samples for noise floor estimation
        private const val SNR_MIN_THRESHOLD = 6.0f // Minimum SNR for reliable detection
    }
    
    // Voice Profile Data Class
    data class VoiceProfile(
        val spectralSignature: FloatArray = FloatArray(SPECTRAL_BANDS),
        val averageAmplitude: Float = 0f,
        val pitchRange: Pair<Float, Float> = Pair(0f, 0f),
        val speechCadence: Float = 0f, // Words per minute estimate
        val confidence: Float = 0f,
        val sampleCount: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as VoiceProfile
            return spectralSignature.contentEquals(other.spectralSignature) &&
                   averageAmplitude == other.averageAmplitude &&
                   pitchRange == other.pitchRange &&
                   speechCadence == other.speechCadence &&
                   confidence == other.confidence &&
                   sampleCount == other.sampleCount
        }
        
        override fun hashCode(): Int {
            var result = spectralSignature.contentHashCode()
            result = 31 * result + averageAmplitude.hashCode()
            result = 31 * result + pitchRange.hashCode()
            result = 31 * result + speechCadence.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + sampleCount
            return result
        }
    }
    
    // Environmental Context
    data class EnvironmentalContext(
        val noiseFloor: Float = 0f,
        val ambientSpectrum: FloatArray = FloatArray(SPECTRAL_BANDS),
        val signalToNoiseRatio: Float = 0f,
        val isCalibrated: Boolean = false
    )
    
    // Detection Result
    data class BargeinResult(
        val shouldTrigger: Boolean,
        val confidence: Float,
        val reason: String,
        val adaptiveThreshold: Float
    )
    
    // State management
    private var currentVoiceProfile = VoiceProfile()
    private var environmentalContext = EnvironmentalContext()
    private val recentAmplitudes = mutableListOf<Float>()
    private val recentSpectrums = mutableListOf<FloatArray>()
    
    private val _profileConfidence = MutableStateFlow(0f)
    val profileConfidence: StateFlow<Float> = _profileConfidence
    
    private val _adaptiveThreshold = MutableStateFlow(0.08f) // Start with base threshold
    val adaptiveThreshold: StateFlow<Float> = _adaptiveThreshold
    
    /**
     * Process audio frame and determine if barge-in should trigger
     */
    fun processAudioFrame(
        audioFrame: ShortArray,
        frameLength: Int,
        sampleRate: Int = 16000
    ): BargeinResult {
        val amplitude = calculateFrameAmplitude(audioFrame, frameLength)
        val spectrum = calculateSpectralFeatures(audioFrame, frameLength, sampleRate)
        
        // Update environmental context during silence
        if (amplitude < 0.02f) {
            updateEnvironmentalContext(amplitude, spectrum)
        }
        
        // Check if we should learn from this frame (user is speaking)
        if (amplitude > environmentalContext.noiseFloor * 2.0f) {
            updateVoiceProfile(amplitude, spectrum)
        }
        
        // Perform adaptive detection
        return performAdaptiveDetection(amplitude, spectrum)
    }
    
    /**
     * Force learning mode for initial voice calibration
     */
    fun startVoiceLearning() {
        println("[AdaptiveBargeinDetector] Starting voice learning mode")
        currentVoiceProfile = VoiceProfile()
        recentAmplitudes.clear()
        recentSpectrums.clear()
    }
    
    /**
     * Calculate frame amplitude with RMS
     */
    private fun calculateFrameAmplitude(audioFrame: ShortArray, length: Int): Float {
        if (length == 0) return 0f
        
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = audioFrame[i].toFloat() / 32767.0f
            sumSquares += sample * sample
        }
        
        return sqrt(sumSquares / length).toFloat()
    }
    
    /**
     * Calculate spectral features using simple frequency band analysis
     */
    private fun calculateSpectralFeatures(
        audioFrame: ShortArray, 
        length: Int, 
        sampleRate: Int
    ): FloatArray {
        val features = FloatArray(SPECTRAL_BANDS)
        val bandWidth = (SPEECH_FREQ_MAX - SPEECH_FREQ_MIN) / SPECTRAL_BANDS
        
        // Simple spectral analysis - in production would use FFT
        for (band in 0 until SPECTRAL_BANDS) {
            val freqStart = SPEECH_FREQ_MIN + band * bandWidth
            val freqEnd = freqStart + bandWidth
            
            // Simulate frequency band energy (simplified)
            var bandEnergy = 0f
            val samplesPerBand = length / SPECTRAL_BANDS
            val startIdx = band * samplesPerBand
            val endIdx = min(startIdx + samplesPerBand, length)
            
            for (i in startIdx until endIdx) {
                val sample = audioFrame[i].toFloat() / 32767.0f
                bandEnergy += abs(sample)
            }
            
            features[band] = if (samplesPerBand > 0) bandEnergy / samplesPerBand else 0f
        }
        
        return features
    }
    
    /**
     * Update voice profile with new speech sample
     */
    private fun updateVoiceProfile(amplitude: Float, spectrum: FloatArray) {
        recentAmplitudes.add(amplitude)
        recentSpectrums.add(spectrum.copyOf())
        
        // Keep only recent samples
        if (recentAmplitudes.size > VOICE_PROFILE_SAMPLES) {
            recentAmplitudes.removeAt(0)
            recentSpectrums.removeAt(0)
        }
        
        if (recentAmplitudes.size >= 10) { // Minimum samples for profile update
            val newProfile = calculateVoiceProfile()
            
            // Adaptive learning - blend old and new
            currentVoiceProfile = if (currentVoiceProfile.sampleCount == 0) {
                newProfile
            } else {
                blendVoiceProfiles(currentVoiceProfile, newProfile, ADAPTATION_RATE)
            }
            
            _profileConfidence.value = currentVoiceProfile.confidence
            
            println("[AdaptiveBargeinDetector] Voice profile updated - confidence: ${currentVoiceProfile.confidence}")
        }
    }
    
    /**
     * Calculate voice profile from recent samples
     */
    private fun calculateVoiceProfile(): VoiceProfile {
        if (recentAmplitudes.isEmpty()) return VoiceProfile()
        
        val avgAmplitude = recentAmplitudes.average().toFloat()
        val spectralSig = FloatArray(SPECTRAL_BANDS)
        
        // Average spectral signature
        for (band in 0 until SPECTRAL_BANDS) {
            spectralSig[band] = recentSpectrums.map { it[band] }.average().toFloat()
        }
        
        // Calculate confidence based on consistency
        val amplitudeVariance = calculateVariance(recentAmplitudes)
        val spectralConsistency = calculateSpectralConsistency()
        val confidence = max(0f, 1f - (amplitudeVariance + spectralConsistency) / 2f)
        
        return VoiceProfile(
            spectralSignature = spectralSig,
            averageAmplitude = avgAmplitude,
            pitchRange = Pair(avgAmplitude * 0.7f, avgAmplitude * 1.3f), // Simplified
            speechCadence = estimateSpeechCadence(),
            confidence = confidence,
            sampleCount = recentAmplitudes.size
        )
    }
    
    /**
     * Blend two voice profiles for adaptive learning
     */
    private fun blendVoiceProfiles(
        existing: VoiceProfile, 
        new: VoiceProfile, 
        rate: Float
    ): VoiceProfile {
        val blendedSpectral = FloatArray(SPECTRAL_BANDS)
        for (i in 0 until SPECTRAL_BANDS) {
            blendedSpectral[i] = existing.spectralSignature[i] * (1f - rate) + 
                                new.spectralSignature[i] * rate
        }
        
        return VoiceProfile(
            spectralSignature = blendedSpectral,
            averageAmplitude = existing.averageAmplitude * (1f - rate) + new.averageAmplitude * rate,
            pitchRange = Pair(
                existing.pitchRange.first * (1f - rate) + new.pitchRange.first * rate,
                existing.pitchRange.second * (1f - rate) + new.pitchRange.second * rate
            ),
            speechCadence = existing.speechCadence * (1f - rate) + new.speechCadence * rate,
            confidence = max(existing.confidence, new.confidence * 0.9f), // Gradual confidence build
            sampleCount = existing.sampleCount + new.sampleCount
        )
    }
    
    /**
     * Update environmental noise context
     */
    private fun updateEnvironmentalContext(amplitude: Float, spectrum: FloatArray) {
        if (!environmentalContext.isCalibrated || recentAmplitudes.size < NOISE_FLOOR_SAMPLES) {
            val noiseFloor = if (recentAmplitudes.isEmpty()) amplitude else {
                (environmentalContext.noiseFloor * 0.95f + amplitude * 0.05f)
            }
            
            val avgSpectrum = FloatArray(SPECTRAL_BANDS)
            for (i in 0 until SPECTRAL_BANDS) {
                avgSpectrum[i] = environmentalContext.ambientSpectrum[i] * 0.95f + spectrum[i] * 0.05f
            }
            
            environmentalContext = EnvironmentalContext(
                noiseFloor = noiseFloor,
                ambientSpectrum = avgSpectrum,
                signalToNoiseRatio = if (noiseFloor > 0) currentVoiceProfile.averageAmplitude / noiseFloor else 0f,
                isCalibrated = recentAmplitudes.size >= 20
            )
        }
    }
    
    /**
     * Perform adaptive barge-in detection
     */
    private fun performAdaptiveDetection(amplitude: Float, spectrum: FloatArray): BargeinResult {
        if (currentVoiceProfile.confidence < 0.3f) {
            // Not enough learning yet, use basic threshold
            val basicThreshold = 0.08f
            return BargeinResult(
                shouldTrigger = amplitude > basicThreshold,
                confidence = 0.5f,
                reason = "Basic threshold (learning)",
                adaptiveThreshold = basicThreshold
            )
        }
        
        // Calculate adaptive threshold based on voice profile and environment
        val baseThreshold = calculateAdaptiveThreshold()
        val voiceMatch = calculateVoiceMatch(amplitude, spectrum)
        val environmentalFactor = calculateEnvironmentalFactor(amplitude)
        
        val finalConfidence = voiceMatch * environmentalFactor * currentVoiceProfile.confidence
        val shouldTrigger = finalConfidence > CONFIDENCE_THRESHOLD
        
        _adaptiveThreshold.value = baseThreshold
        
        val reason = when {
            voiceMatch < 0.5f -> "Voice pattern mismatch"
            environmentalFactor < 0.5f -> "Poor environmental conditions"
            finalConfidence > CONFIDENCE_THRESHOLD -> "High confidence voice match"
            else -> "Below confidence threshold"
        }
        
        println("[AdaptiveBargeinDetector] Detection: trigger=$shouldTrigger, confidence=$finalConfidence, reason=$reason")
        
        return BargeinResult(
            shouldTrigger = shouldTrigger,
            confidence = finalConfidence,
            reason = reason,
            adaptiveThreshold = baseThreshold
        )
    }
    
    /**
     * Calculate adaptive threshold based on learned patterns
     */
    private fun calculateAdaptiveThreshold(): Float {
        val baseThreshold = 0.08f
        val profileBonus = currentVoiceProfile.confidence * 0.02f // Max 2% reduction
        val noiseAdjustment = max(0f, environmentalContext.noiseFloor * 2f) // Increase in noisy environments
        
        return (baseThreshold - profileBonus + noiseAdjustment).coerceIn(0.04f, 0.15f)
    }
    
    /**
     * Calculate how well current audio matches learned voice profile
     */
    private fun calculateVoiceMatch(amplitude: Float, spectrum: FloatArray): Float {
        if (currentVoiceProfile.sampleCount == 0) return 0.5f
        
        // Amplitude similarity
        val amplitudeMatch = when {
            amplitude < currentVoiceProfile.pitchRange.first -> 0.3f
            amplitude > currentVoiceProfile.pitchRange.second -> 0.7f
            else -> 1.0f
        }
        
        // Spectral similarity using cosine similarity
        val spectralSimilarity = cosineSimilarity(spectrum, currentVoiceProfile.spectralSignature)
        
        return (amplitudeMatch + spectralSimilarity) / 2f
    }
    
    /**
     * Calculate environmental factor affecting detection reliability
     */
    private fun calculateEnvironmentalFactor(amplitude: Float): Float {
        if (!environmentalContext.isCalibrated) return 0.7f
        
        val snr = if (environmentalContext.noiseFloor > 0f) {
            amplitude / environmentalContext.noiseFloor
        } else Float.MAX_VALUE
        
        return when {
            snr > SNR_MIN_THRESHOLD -> 1.0f
            snr > SNR_MIN_THRESHOLD / 2f -> 0.8f
            snr > SNR_MIN_THRESHOLD / 4f -> 0.6f
            else -> 0.3f
        }
    }
    
    // Utility functions
    private fun calculateVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance).toFloat()
    }
    
    private fun calculateSpectralConsistency(): Float {
        if (recentSpectrums.size < 2) return 0f
        
        var totalVariance = 0f
        for (band in 0 until SPECTRAL_BANDS) {
            val bandValues = recentSpectrums.map { it[band] }
            totalVariance += calculateVariance(bandValues)
        }
        
        return (totalVariance / SPECTRAL_BANDS).coerceIn(0f, 1f)
    }
    
    private fun estimateSpeechCadence(): Float {
        // Simplified cadence estimation based on amplitude peaks
        return 150f // Average words per minute placeholder
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }
    
    /**
     * Get current detection statistics for debugging/analytics
     */
    fun getDetectionStats(): Map<String, Any> {
        return mapOf(
            "voiceProfileConfidence" to currentVoiceProfile.confidence,
            "sampleCount" to currentVoiceProfile.sampleCount,
            "noiseFloor" to environmentalContext.noiseFloor,
            "isCalibrated" to environmentalContext.isCalibrated,
            "adaptiveThreshold" to _adaptiveThreshold.value,
            "averageAmplitude" to currentVoiceProfile.averageAmplitude
        )
    }
}