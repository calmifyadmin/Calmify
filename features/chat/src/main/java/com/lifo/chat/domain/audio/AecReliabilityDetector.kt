package com.lifo.chat.domain.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Runtime hardware AEC reliability detector.
 *
 * Many Android devices report [AcousticEchoCanceler.isAvailable] == true but the AEC
 * does nothing useful. This detector measures actual echo cancellation quality at runtime
 * by computing cross-correlation between the reference signal (AI playback) and mic input.
 *
 * ## Algorithm
 * During the first 2 seconds of AI playback:
 * 1. Capture frames from both reference signal and mic input
 * 2. Compute normalized cross-correlation between them
 * 3. If correlation consistently > 0.3 → hardware AEC is failing (echo leaking through)
 * 4. If correlation < 0.1 → hardware AEC is working (echo properly cancelled)
 *
 * ## Thread Safety
 * All public methods are synchronized. This class is accessed from:
 * - Recording thread (URGENT_AUDIO priority): processFrame()
 * - IO dispatcher: feedReferenceSignal() (from playAudio)
 * - Main/ViewModel scope: startMonitoring(), stopMonitoring(), reset()
 */
@Singleton
class AecReliabilityDetector @Inject constructor() {

    enum class AecStatus {
        IDLE,               // Not monitoring (AI not speaking)
        CHECKING,           // Accumulating correlation samples
        HARDWARE_WORKING,   // AEC is effective (correlation < 0.1)
        HARDWARE_FAILING,   // AEC is not working (correlation > 0.3)
        SOFTWARE_ACTIVE     // Software AEC has been activated as fallback
    }

    companion object {
        private const val WORKING_THRESHOLD = 0.1f
        private const val FAILING_THRESHOLD = 0.3f
        private const val MIN_SAMPLES_FOR_DECISION = 30
        private const val MAX_MONITORING_FRAMES = 62
        private const val CORRELATION_WINDOW = 256
        private const val REF_BUFFER_SIZE = 4000
    }

    private val _status = MutableStateFlow(AecStatus.IDLE)
    val status: StateFlow<AecStatus> = _status.asStateFlow()

    private val _averageCorrelation = MutableStateFlow(0f)
    val averageCorrelation: StateFlow<Float> = _averageCorrelation.asStateFlow()

    // All mutable state guarded by this lock
    private val lock = Any()
    private val referenceBuffer = ShortArray(REF_BUFFER_SIZE)
    private var refWritePos = 0
    private var refAvailable = 0
    private val correlationSamples = mutableListOf<Float>()
    private var frameCount = 0

    fun startMonitoring() {
        synchronized(lock) {
            if (_status.value == AecStatus.SOFTWARE_ACTIVE) return
            _status.value = AecStatus.CHECKING
            correlationSamples.clear()
            frameCount = 0
            refWritePos = 0
            refAvailable = 0
            referenceBuffer.fill(0)
        }
        println("[AecReliabilityDetector] Started monitoring AEC quality")
    }

    fun stopMonitoring() {
        synchronized(lock) {
            if (_status.value == AecStatus.CHECKING) {
                makeDecisionLocked()
            }
        }
    }

    /**
     * Feed reference signal (AI playback audio, at 24kHz).
     * Downsampled to 16kHz to match mic input rate.
     */
    fun feedReferenceSignal(playbackData: ShortArray) {
        synchronized(lock) {
            if (_status.value != AecStatus.CHECKING) return

            // 24kHz → 16kHz: keep 2 out of every 3 samples
            var srcIdx = 0
            var count = 0
            while (srcIdx < playbackData.size) {
                count++
                if (count % 3 != 0) {
                    // Keep this sample (samples 1,2 of every 3)
                    referenceBuffer[refWritePos] = playbackData[srcIdx]
                    refWritePos = (refWritePos + 1) % REF_BUFFER_SIZE
                    if (refAvailable < REF_BUFFER_SIZE) refAvailable++
                }
                // Skip every 3rd sample (drop sample 3 of every 3)
                srcIdx++
            }
        }
    }

    /**
     * Process a mic input frame during AI playback.
     * Computes cross-correlation between mic and reference signal.
     */
    fun processFrame(micFrame: ShortArray, length: Int): AecStatus {
        synchronized(lock) {
            if (_status.value != AecStatus.CHECKING) return _status.value
            if (refAvailable < CORRELATION_WINDOW) return _status.value

            frameCount++

            val correlation = computeCorrelationLocked(micFrame, length)
            correlationSamples.add(correlation)

            _averageCorrelation.value = correlationSamples.average().toFloat()

            if (correlationSamples.size >= MIN_SAMPLES_FOR_DECISION || frameCount >= MAX_MONITORING_FRAMES) {
                makeDecisionLocked()
            }

            return _status.value
        }
    }

    private fun computeCorrelationLocked(micFrame: ShortArray, length: Int): Float {
        val windowSize = minOf(length, CORRELATION_WINDOW, refAvailable)
        if (windowSize < 64) return 0f

        val refStart = (refWritePos - windowSize + REF_BUFFER_SIZE) % REF_BUFFER_SIZE

        var sumMicRef = 0.0
        var sumMicMic = 0.0
        var sumRefRef = 0.0

        for (i in 0 until windowSize) {
            val micSample = micFrame[i].toDouble()
            val refSample = referenceBuffer[(refStart + i) % REF_BUFFER_SIZE].toDouble()
            sumMicRef += micSample * refSample
            sumMicMic += micSample * micSample
            sumRefRef += refSample * refSample
        }

        val denominator = sqrt(sumMicMic * sumRefRef)
        if (denominator < 1.0) return 0f

        return (abs(sumMicRef) / denominator).toFloat().coerceIn(0f, 1f)
    }

    /** Must be called while holding [lock]. */
    private fun makeDecisionLocked() {
        if (correlationSamples.isEmpty()) {
            _status.value = AecStatus.IDLE
            return
        }

        val avg = correlationSamples.average().toFloat()
        _averageCorrelation.value = avg

        val sorted = correlationSamples.sorted()
        val median = sorted[sorted.size / 2]

        _status.value = when {
            avg < WORKING_THRESHOLD && median < WORKING_THRESHOLD -> {
                println("[AecReliabilityDetector] Hardware AEC WORKING " +
                    "(avg=${"%.3f".format(avg)}, median=${"%.3f".format(median)}, " +
                    "samples=${correlationSamples.size})")
                AecStatus.HARDWARE_WORKING
            }
            avg > FAILING_THRESHOLD || median > FAILING_THRESHOLD -> {
                println("[AecReliabilityDetector] Hardware AEC FAILING! " +
                    "(avg=${"%.3f".format(avg)}, median=${"%.3f".format(median)}, " +
                    "samples=${correlationSamples.size})")
                AecStatus.HARDWARE_FAILING
            }
            else -> {
                println("[AecReliabilityDetector] Hardware AEC MARGINAL " +
                    "(avg=${"%.3f".format(avg)}, median=${"%.3f".format(median)}) — " +
                    "treating as working")
                AecStatus.HARDWARE_WORKING
            }
        }
    }

    fun markSoftwareAecActive() {
        synchronized(lock) {
            _status.value = AecStatus.SOFTWARE_ACTIVE
        }
        println("[AecReliabilityDetector] Software AEC activated")
    }

    fun reset() {
        synchronized(lock) {
            _status.value = AecStatus.IDLE
            correlationSamples.clear()
            _averageCorrelation.value = 0f
            frameCount = 0
            refWritePos = 0
            refAvailable = 0
        }
    }

    fun getDiagnostics(): Map<String, Any> = synchronized(lock) {
        mapOf(
            "status" to _status.value.name,
            "averageCorrelation" to _averageCorrelation.value,
            "sampleCount" to correlationSamples.size,
            "frameCount" to frameCount,
            "referenceAvailable" to refAvailable
        )
    }
}
