package com.lifo.chat.domain.audio


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

/**
 * Real-Time Audio Quality Analytics System
 * 
 * Monitors and analyzes audio performance metrics to enable
 * continuous optimization of the voice chat experience.
 * 
 * Features:
 * - Latency measurement and tracking
 * - Echo cancellation effectiveness monitoring
 * - Noise reduction performance analysis
 * - Audio quality scoring with recommendations
 * - Real-time optimization suggestions
 * 
 * @author Jarvis AI Assistant
 */
class AudioQualityAnalyzer {
    
    companion object {
        // Measurement windows
        private const val LATENCY_MEASUREMENT_SAMPLES = 50
        private const val QUALITY_ANALYSIS_WINDOW = 100 // frames
        private const val ECHO_DETECTION_WINDOW = 200
        
        // Quality thresholds
        private const val EXCELLENT_QUALITY_THRESHOLD = 0.85f
        private const val GOOD_QUALITY_THRESHOLD = 0.70f
        private const val POOR_QUALITY_THRESHOLD = 0.50f
        
        // Latency targets (milliseconds)
        private const val EXCELLENT_LATENCY_MS = 150f
        private const val GOOD_LATENCY_MS = 250f
        private const val POOR_LATENCY_MS = 500f
    }
    
    // Data classes for metrics
    data class LatencyMetrics(
        val endToEndLatency: Float = 0f, // Total latency in ms
        val captureLatency: Float = 0f,  // Microphone to processing
        val processLatency: Float = 0f,  // Processing time
        val networkLatency: Float = 0f,  // Network round trip
        val playbackLatency: Float = 0f, // Processing to speaker
        val jitter: Float = 0f,          // Latency variation
        val averageLatency: Float = 0f,  // Moving average
        val quality: LatencyQuality = LatencyQuality.UNKNOWN
    )
    
    data class EchoMetrics(
        val echoReturnLoss: Float = 0f,        // dB
        val echoReturnLossEnhancement: Float = 0f, // dB improvement from AEC
        val residualEcho: Float = 0f,          // Remaining echo level
        val aecEffectiveness: Float = 0f,      // 0-1 scale
        val recommendation: EchoRecommendation = EchoRecommendation.NONE
    )
    
    data class NoiseMetrics(
        val signalToNoiseRatio: Float = 0f,    // dB
        val noiseFloor: Float = 0f,            // Current noise level
        val noiseReduction: Float = 0f,        // dB of reduction applied
        val nsEffectiveness: Float = 0f,       // 0-1 scale
        val speechClarity: Float = 0f,         // Speech intelligibility
        val recommendation: NoiseRecommendation = NoiseRecommendation.NONE
    )
    
    data class OverallQualityScore(
        val totalScore: Float = 0f,            // 0-1 composite score
        val latencyScore: Float = 0f,          // Individual component scores
        val echoScore: Float = 0f,
        val noiseScore: Float = 0f,
        val stabilityScore: Float = 0f,
        val grade: QualityGrade = QualityGrade.UNKNOWN,
        val primaryIssue: String = "",
        val recommendations: List<String> = emptyList()
    )
    
    // Enums for quality classification
    enum class LatencyQuality { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN }
    enum class EchoRecommendation { NONE, INCREASE_AEC, ADJUST_VOLUME, CHECK_HARDWARE, SWITCH_DEVICE }
    enum class NoiseRecommendation { NONE, MOVE_QUIET, ENABLE_NS, ADJUST_GAIN, USE_HEADSET }
    enum class QualityGrade { EXCELLENT, GOOD, FAIR, POOR, CRITICAL, UNKNOWN }
    
    // State flows for real-time monitoring
    private val _latencyMetrics = MutableStateFlow(LatencyMetrics())
    val latencyMetrics: StateFlow<LatencyMetrics> = _latencyMetrics
    
    private val _echoMetrics = MutableStateFlow(EchoMetrics())
    val echoMetrics: StateFlow<EchoMetrics> = _echoMetrics
    
    private val _noiseMetrics = MutableStateFlow(NoiseMetrics())
    val noiseMetrics: StateFlow<NoiseMetrics> = _noiseMetrics
    
    private val _overallQuality = MutableStateFlow(OverallQualityScore())
    val overallQuality: StateFlow<OverallQualityScore> = _overallQuality
    
    // Internal tracking variables
    private val latencyMeasurements = mutableListOf<Float>()
    private val recentAudioFrames = mutableListOf<FloatArray>()
    private val recentPlaybackFrames = mutableListOf<FloatArray>()
    private var measurementStartTime = 0L
    private var isCapturing = false
    
    // Frame counters for analysis windows
    private var frameCount = 0L
    private var lastAnalysisFrame = 0L
    
    /**
     * Start quality measurement session
     */
    fun startMeasurement() {
        println("[AudioQualityAnalyzer] Starting audio quality measurement")
        measurementStartTime = System.currentTimeMillis()
        isCapturing = true
        frameCount = 0L
        clearHistories()
    }
    
    /**
     * Stop quality measurement
     */
    fun stopMeasurement() {
        println("[AudioQualityAnalyzer] Stopping audio quality measurement")
        isCapturing = false
        generateFinalReport()
    }
    
    /**
     * Process captured audio frame for analysis
     */
    fun processCapturedFrame(audioFrame: ShortArray, timestamp: Long = System.currentTimeMillis()) {
        if (!isCapturing) return
        
        val floatFrame = audioFrame.map { it.toFloat() / 32767f }.toFloatArray()
        recentAudioFrames.add(floatFrame)
        
        // Maintain sliding window
        if (recentAudioFrames.size > QUALITY_ANALYSIS_WINDOW) {
            recentAudioFrames.removeAt(0)
        }
        
        frameCount++
        
        // Perform periodic analysis
        if (frameCount - lastAnalysisFrame >= 50) { // Analyze every ~1 second at 50fps
            performQualityAnalysis()
            lastAnalysisFrame = frameCount
        }
    }
    
    /**
     * Process playback audio frame for echo analysis
     */
    fun processPlaybackFrame(audioFrame: ByteArray, timestamp: Long = System.currentTimeMillis()) {
        if (!isCapturing) return
        
        // Convert to float array (assuming 16-bit PCM)
        val floatFrame = FloatArray(audioFrame.size / 2)
        for (i in floatFrame.indices) {
            val sampleIndex = i * 2
            if (sampleIndex + 1 < audioFrame.size) {
                val sample = (audioFrame[sampleIndex].toInt() and 0xFF) or 
                            ((audioFrame[sampleIndex + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                floatFrame[i] = signedSample.toFloat() / 32767f
            }
        }
        
        recentPlaybackFrames.add(floatFrame)
        
        // Maintain sliding window for echo analysis
        if (recentPlaybackFrames.size > ECHO_DETECTION_WINDOW) {
            recentPlaybackFrames.removeAt(0)
        }
    }
    
    /**
     * Record latency measurement point
     */
    fun recordLatencyMeasurement(latencyMs: Float) {
        latencyMeasurements.add(latencyMs)
        
        if (latencyMeasurements.size > LATENCY_MEASUREMENT_SAMPLES) {
            latencyMeasurements.removeAt(0)
        }
        
        updateLatencyMetrics()
    }
    
    /**
     * Perform comprehensive quality analysis
     */
    private fun performQualityAnalysis() {
        analyzeNoiseMetrics()
        analyzeEchoMetrics()
        calculateOverallQuality()
    }
    
    /**
     * Update latency metrics based on recent measurements
     */
    private fun updateLatencyMetrics() {
        if (latencyMeasurements.isEmpty()) return
        
        val average = latencyMeasurements.average().toFloat()
        val jitter = calculateJitter()
        val quality = classifyLatencyQuality(average)
        
        _latencyMetrics.value = LatencyMetrics(
            endToEndLatency = latencyMeasurements.lastOrNull() ?: 0f,
            averageLatency = average,
            jitter = jitter,
            quality = quality,
            // Additional detailed metrics would be calculated here
            captureLatency = average * 0.2f, // Simplified estimation
            processLatency = average * 0.3f,
            networkLatency = average * 0.4f,
            playbackLatency = average * 0.1f
        )
        
        println("[AudioQualityAnalyzer] Latency updated: avg=${average}ms, jitter=${jitter}ms, quality=$quality")
    }
    
    /**
     * Analyze noise-related metrics
     */
    private fun analyzeNoiseMetrics() {
        if (recentAudioFrames.isEmpty()) return
        
        val currentFrame = recentAudioFrames.last()
        val signalPower = calculateSignalPower(currentFrame)
        val noisePower = estimateNoisePower()
        val snr = if (noisePower > 0f) 20f * log10(signalPower / noisePower) else Float.MAX_VALUE
        
        // Estimate noise reduction effectiveness
        val nsEffectiveness = estimateNoiseSuppressionEffectiveness()
        val speechClarity = calculateSpeechClarity(currentFrame)
        val recommendation = generateNoiseRecommendation(snr, speechClarity)
        
        _noiseMetrics.value = NoiseMetrics(
            signalToNoiseRatio = snr.coerceIn(-20f, 60f),
            noiseFloor = noisePower,
            noiseReduction = nsEffectiveness * 10f, // Convert to dB estimate
            nsEffectiveness = nsEffectiveness,
            speechClarity = speechClarity,
            recommendation = recommendation
        )
        
        println("[AudioQualityAnalyzer] Noise metrics updated: SNR=${snr}dB, clarity=$speechClarity")
    }
    
    /**
     * Analyze echo-related metrics
     */
    private fun analyzeEchoMetrics() {
        if (recentAudioFrames.size < 50 || recentPlaybackFrames.size < 50) return
        
        val captureFrame = recentAudioFrames.takeLast(50)
        val playbackFrame = recentPlaybackFrames.takeLast(50)
        
        val echoEstimate = estimateEchoLevel(captureFrame, playbackFrame)
        val aecEffectiveness = estimateAECEffectiveness(echoEstimate)
        val recommendation = generateEchoRecommendation(echoEstimate, aecEffectiveness)
        
        _echoMetrics.value = EchoMetrics(
            echoReturnLoss = echoEstimate.coerceIn(0f, 50f),
            aecEffectiveness = aecEffectiveness,
            residualEcho = max(0f, echoEstimate - (aecEffectiveness * 20f)),
            recommendation = recommendation
        )
        
        println("[AudioQualityAnalyzer] Echo metrics updated: ERL=${echoEstimate}dB, AEC effectiveness=$aecEffectiveness")
    }
    
    /**
     * Calculate overall quality score and recommendations
     */
    private fun calculateOverallQuality() {
        val latencyScore = calculateLatencyScore()
        val echoScore = calculateEchoScore()
        val noiseScore = calculateNoiseScore()
        val stabilityScore = calculateStabilityScore()
        
        val totalScore = (latencyScore + echoScore + noiseScore + stabilityScore) / 4f
        val grade = classifyQualityGrade(totalScore)
        val primaryIssue = identifyPrimaryIssue(latencyScore, echoScore, noiseScore, stabilityScore)
        val recommendations = generateRecommendations(grade, primaryIssue)
        
        _overallQuality.value = OverallQualityScore(
            totalScore = totalScore,
            latencyScore = latencyScore,
            echoScore = echoScore,
            noiseScore = noiseScore,
            stabilityScore = stabilityScore,
            grade = grade,
            primaryIssue = primaryIssue,
            recommendations = recommendations
        )
        
        println("[AudioQualityAnalyzer] Overall quality: ${totalScore} ($grade) - Issue: $primaryIssue")
    }
    
    // Utility calculation methods
    private fun calculateJitter(): Float {
        if (latencyMeasurements.size < 2) return 0f
        
        val differences = latencyMeasurements.zipWithNext { a, b -> abs(b - a) }
        return differences.average().toFloat()
    }
    
    private fun calculateSignalPower(frame: FloatArray): Float {
        return frame.map { it * it }.average().toFloat()
    }
    
    private fun estimateNoisePower(): Float {
        // Simplified noise estimation using minimum power in recent frames
        return recentAudioFrames.map { calculateSignalPower(it) }.minOrNull() ?: 0f
    }
    
    private fun estimateNoiseSuppressionEffectiveness(): Float {
        // Simplified estimation based on power variation reduction
        if (recentAudioFrames.size < 10) return 0.5f
        
        val powerVariation = recentAudioFrames.map { calculateSignalPower(it) }.let { powers ->
            val mean = powers.average()
            powers.map { (it - mean).pow(2) }.average()
        }
        
        // Lower variation suggests better noise suppression
        return (1f - powerVariation.toFloat().coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }
    
    private fun calculateSpeechClarity(frame: FloatArray): Float {
        // Simplified speech clarity based on spectral characteristics
        // In production, would use proper spectral analysis
        val highFreqEnergy = frame.takeLast(frame.size / 4).map { abs(it) }.average()
        val totalEnergy = frame.map { abs(it) }.average()
        
        return if (totalEnergy > 0) (highFreqEnergy / totalEnergy).toFloat().coerceIn(0f, 1f) else 0f
    }
    
    private fun estimateEchoLevel(captureFrames: List<FloatArray>, playbackFrames: List<FloatArray>): Float {
        // Simplified echo estimation using cross-correlation
        if (captureFrames.isEmpty() || playbackFrames.isEmpty()) return 0f
        
        val captureSignal = captureFrames.flatMap { it.toList() }.toFloatArray()
        val playbackSignal = playbackFrames.flatMap { it.toList() }.toFloatArray()
        
        // Simple correlation-based echo detection
        val correlation = calculateCrossCorrelation(captureSignal, playbackSignal)
        
        // Convert correlation to echo return loss estimate
        return if (correlation > 0f) -20f * log10(correlation) else 40f // High ERL if no correlation
    }
    
    private fun calculateCrossCorrelation(signal1: FloatArray, signal2: FloatArray): Float {
        if (signal1.isEmpty() || signal2.isEmpty()) return 0f
        
        val minSize = min(signal1.size, signal2.size)
        var correlation = 0.0
        var power1 = 0.0
        var power2 = 0.0
        
        for (i in 0 until minSize) {
            correlation += signal1[i] * signal2[i]
            power1 += signal1[i] * signal1[i]
            power2 += signal2[i] * signal2[i]
        }
        
        correlation /= minSize
        power1 /= minSize
        power2 /= minSize
        
        return if (power1 > 0 && power2 > 0) {
            (correlation / sqrt(power1 * power2)).toFloat().coerceIn(0f, 1f)
        } else 0f
    }
    
    private fun estimateAECEffectiveness(echoLevel: Float): Float {
        // Estimate AEC effectiveness based on echo return loss
        return when {
            echoLevel > 30f -> 0.9f // Excellent AEC
            echoLevel > 20f -> 0.7f // Good AEC
            echoLevel > 10f -> 0.5f // Fair AEC
            else -> 0.3f // Poor AEC
        }
    }
    
    // Classification and scoring methods
    private fun classifyLatencyQuality(latency: Float): LatencyQuality = when {
        latency <= EXCELLENT_LATENCY_MS -> LatencyQuality.EXCELLENT
        latency <= GOOD_LATENCY_MS -> LatencyQuality.GOOD
        latency <= POOR_LATENCY_MS -> LatencyQuality.FAIR
        else -> LatencyQuality.POOR
    }
    
    private fun calculateLatencyScore(): Float {
        val avgLatency = _latencyMetrics.value.averageLatency
        return when {
            avgLatency <= EXCELLENT_LATENCY_MS -> 1.0f
            avgLatency <= GOOD_LATENCY_MS -> 0.8f
            avgLatency <= POOR_LATENCY_MS -> 0.6f
            else -> 0.3f
        }
    }
    
    private fun calculateEchoScore(): Float {
        val echoLevel = _echoMetrics.value.echoReturnLoss
        return when {
            echoLevel > 30f -> 1.0f
            echoLevel > 20f -> 0.8f
            echoLevel > 10f -> 0.6f
            else -> 0.3f
        }
    }
    
    private fun calculateNoiseScore(): Float {
        val snr = _noiseMetrics.value.signalToNoiseRatio
        return when {
            snr > 20f -> 1.0f
            snr > 15f -> 0.8f
            snr > 10f -> 0.6f
            else -> 0.3f
        }
    }
    
    private fun calculateStabilityScore(): Float {
        val jitter = _latencyMetrics.value.jitter
        return when {
            jitter < 20f -> 1.0f
            jitter < 50f -> 0.8f
            jitter < 100f -> 0.6f
            else -> 0.3f
        }
    }
    
    private fun classifyQualityGrade(score: Float): QualityGrade = when {
        score >= EXCELLENT_QUALITY_THRESHOLD -> QualityGrade.EXCELLENT
        score >= GOOD_QUALITY_THRESHOLD -> QualityGrade.GOOD
        score >= POOR_QUALITY_THRESHOLD -> QualityGrade.FAIR
        score >= 0.3f -> QualityGrade.POOR
        else -> QualityGrade.CRITICAL
    }
    
    private fun identifyPrimaryIssue(latency: Float, echo: Float, noise: Float, stability: Float): String {
        val minScore = minOf(latency, echo, noise, stability)
        return when (minScore) {
            latency -> "High Latency"
            echo -> "Echo Issues"
            noise -> "Background Noise"
            stability -> "Connection Instability"
            else -> "Multiple Issues"
        }
    }
    
    private fun generateNoiseRecommendation(snr: Float, clarity: Float): NoiseRecommendation = when {
        snr < 10f && clarity < 0.5f -> NoiseRecommendation.USE_HEADSET
        snr < 15f -> NoiseRecommendation.MOVE_QUIET
        clarity < 0.6f -> NoiseRecommendation.ENABLE_NS
        else -> NoiseRecommendation.NONE
    }
    
    private fun generateEchoRecommendation(echo: Float, aecEff: Float): EchoRecommendation = when {
        echo < 10f && aecEff < 0.5f -> EchoRecommendation.CHECK_HARDWARE
        echo < 15f -> EchoRecommendation.INCREASE_AEC
        aecEff < 0.6f -> EchoRecommendation.ADJUST_VOLUME
        else -> EchoRecommendation.NONE
    }
    
    private fun generateRecommendations(grade: QualityGrade, primaryIssue: String): List<String> {
        val recommendations = mutableListOf<String>()
        
        when (grade) {
            QualityGrade.CRITICAL -> {
                recommendations.add("Consider switching to wired headset")
                recommendations.add("Check network connection stability")
                recommendations.add("Move to quieter environment")
            }
            QualityGrade.POOR -> {
                recommendations.add("Adjust microphone positioning")
                recommendations.add("Enable noise suppression")
            }
            QualityGrade.FAIR -> {
                when (primaryIssue) {
                    "High Latency" -> recommendations.add("Check network conditions")
                    "Echo Issues" -> recommendations.add("Lower speaker volume")
                    "Background Noise" -> recommendations.add("Find quieter location")
                }
            }
            else -> { /* Good quality, no recommendations */ }
        }
        
        return recommendations
    }
    
    private fun generateFinalReport() {
        val report = buildString {
            appendLine("=== Audio Quality Analysis Report ===")
            appendLine("Overall Score: ${_overallQuality.value.totalScore}")
            appendLine("Grade: ${_overallQuality.value.grade}")
            appendLine("Average Latency: ${_latencyMetrics.value.averageLatency}ms")
            appendLine("SNR: ${_noiseMetrics.value.signalToNoiseRatio}dB")
            appendLine("Echo Return Loss: ${_echoMetrics.value.echoReturnLoss}dB")
            appendLine("Recommendations: ${_overallQuality.value.recommendations.joinToString()}")
        }
        
        println("[AudioQualityAnalyzer] $report")
    }
    
    private fun clearHistories() {
        latencyMeasurements.clear()
        recentAudioFrames.clear()
        recentPlaybackFrames.clear()
    }
    
    /**
     * Get comprehensive quality report for analytics
     */
    fun getQualityReport(): Map<String, Any> {
        return mapOf(
            "overallScore" to _overallQuality.value.totalScore,
            "grade" to _overallQuality.value.grade.name,
            "latency" to _latencyMetrics.value.averageLatency,
            "jitter" to _latencyMetrics.value.jitter,
            "snr" to _noiseMetrics.value.signalToNoiseRatio,
            "echoReturnLoss" to _echoMetrics.value.echoReturnLoss,
            "aecEffectiveness" to _echoMetrics.value.aecEffectiveness,
            "recommendations" to _overallQuality.value.recommendations,
            "measurementDuration" to (System.currentTimeMillis() - measurementStartTime),
            "framesAnalyzed" to frameCount
        )
    }
}