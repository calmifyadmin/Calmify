package com.lifo.chat.domain.audio


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reference Signal Barge-In Detector
 *
 * Sophisticated echo-aware barge-in detection that distinguishes between:
 * - Echo: AI audio coming back through the microphone (IGNORE)
 * - User speech: New audio from the user (TRIGGER BARGE-IN)
 *
 * Algorithm:
 * 1. Maintains a circular buffer of the AI audio being played (reference signal)
 * 2. When mic input arrives, cross-correlates with reference to find echo delay
 * 3. Subtracts estimated echo from mic input
 * 4. Measures residual energy - if high, user is speaking
 * 5. Triggers barge-in when residual energy exceeds adaptive threshold
 */
@Singleton
class ReferenceSignalBargeInDetector @Inject constructor() {

    companion object {
        // Reference buffer: ~500ms of audio at 16kHz
        private const val REFERENCE_BUFFER_SIZE = 8000

        // Analysis frame size: 32ms at 16kHz (512 samples)
        private const val FRAME_SIZE = 512

        // Cross-correlation search range: ±100ms (1600 samples)
        private const val MAX_DELAY_SAMPLES = 1600

        // Energy thresholds - CONSERVATIVE to avoid self-interruption
        // Higher threshold = less likely to trigger on AI's own audio
        private const val MIN_RESIDUAL_ENERGY = 0.06f  // Higher: Ignore AI echo and background noise
        private const val ECHO_CORRELATION_THRESHOLD = 0.4f  // Lower: More easily recognize echo

        // Barge-in confirmation - Require sustained speech to avoid false positives
        // 5 frames = ~160ms of continuous user speech required
        private const val BARGE_IN_FRAMES_REQUIRED = 5  // ~160ms of speech needed

        // Hysteresis: Reset quickly if user stops speaking
        private const val FRAMES_TO_RESET = 3  // 96ms of silence to reset

        // Adaptive threshold parameters
        private const val NOISE_FLOOR_ALPHA = 0.03f  // Slower adaptation for stability
        private const val PEAK_DECAY = 0.92f  // Slower decay for better tracking
    }

    // Circular buffer for reference signal (AI audio being played)
    private val referenceBuffer = ShortArray(REFERENCE_BUFFER_SIZE)
    private var referenceWritePos = 0
    private var referenceAvailable = 0

    // State tracking
    private var consecutiveBargeInFrames = 0
    private var consecutiveSilenceFrames = 0  // Track consecutive silence for reset
    private var noiseFloor = 0.01f
    private var peakEnergy = 0f

    // Sample rate conversion factor (AI audio is 24kHz, mic is 16kHz)
    private val resampleRatio = 24000f / 16000f

    // Detection state
    private val _bargeInDetected = MutableStateFlow(false)
    val bargeInDetected: StateFlow<Boolean> = _bargeInDetected

    private val _residualEnergy = MutableStateFlow(0f)
    val residualEnergy: StateFlow<Float> = _residualEnergy

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    /**
     * Feed AI audio into the reference buffer.
     * Call this whenever AI audio is played.
     *
     * @param audioData Raw PCM samples (24kHz, mono, 16-bit)
     */
    // Counter for feed logging
    private var feedCounter = 0

    fun feedReferenceSignal(audioData: ShortArray) {
        synchronized(referenceBuffer) {
            // Downsample from 24kHz to 16kHz to match mic input
            val downsampledSize = (audioData.size / resampleRatio).toInt()

            for (i in 0 until downsampledSize) {
                val srcIndex = (i * resampleRatio).toInt().coerceIn(0, audioData.size - 1)
                referenceBuffer[referenceWritePos] = audioData[srcIndex]
                referenceWritePos = (referenceWritePos + 1) % REFERENCE_BUFFER_SIZE
            }

            referenceAvailable = min(referenceAvailable + downsampledSize, REFERENCE_BUFFER_SIZE)

            // Log every 10 feeds to confirm reference signal is being received
            if (feedCounter++ % 10 == 0) {
                println("[RefSignalBargeIn] Fed ${audioData.size} samples -> refAvail=$referenceAvailable")
            }
        }
    }

    /**
     * Process microphone input and detect if user is speaking (not echo).
     *
     * @param micInput Raw PCM samples from microphone (16kHz, mono, 16-bit)
     * @param frameSize Number of valid samples in the buffer
     * @return BargeInResult with detection status and confidence
     */
    // Debug counter to avoid log spam
    private var debugLogCounter = 0
    private var processCallCounter = 0

    fun processMicInput(micInput: ShortArray, frameSize: Int): BargeInResult {
        // ALWAYS log every 50 calls to confirm method is being invoked
        if (processCallCounter++ % 50 == 0) {
            println("[RefSignalBargeIn] processMicInput called #$processCallCounter: frameSize=$frameSize, refAvail=$referenceAvailable")
        }

        if (frameSize < FRAME_SIZE || referenceAvailable < FRAME_SIZE) {
            // Log more frequently during insufficient data
            if (debugLogCounter++ % 30 == 0) {
                println("[RefSignalBargeIn] Insufficient data: frameSize=$frameSize, refAvail=$referenceAvailable (need $FRAME_SIZE)")
            }
            return BargeInResult(false, 0f, "Insufficient data")
        }

        _isProcessing.value = true

        try {
            // Step 1: Calculate mic input energy
            val micEnergy = calculateEnergy(micInput, frameSize)

            // Step 2: If mic energy is very low, no speech at all
            if (micEnergy < MIN_RESIDUAL_ENERGY * 0.5f) {
                updateNoiseFloor(micEnergy)
                consecutiveBargeInFrames = 0
                _residualEnergy.value = 0f
                return BargeInResult(false, 0f, "Below noise floor")
            }

            // Step 3: Find best correlation with reference (echo detection)
            val (correlation, bestDelay) = findBestCorrelation(micInput, frameSize)

            // Step 4: Calculate residual energy after echo estimation
            val residual = calculateResidualEnergy(micInput, frameSize, bestDelay)
            _residualEnergy.value = residual

            // Step 5: Update adaptive thresholds
            updateNoiseFloor(residual)
            peakEnergy = max(peakEnergy * PEAK_DECAY, residual)

            // Step 6: Determine if this is user speech using dual-signal validation
            // Both residual energy AND low correlation required (AND logic per research)
            // VERY LOW threshold for aggressive barge-in
            val adaptiveThreshold = noiseFloor * 2f + MIN_RESIDUAL_ENERGY
            val isHighEnergy = residual > adaptiveThreshold
            val isNotEcho = correlation < ECHO_CORRELATION_THRESHOLD
            val isUserSpeech = isHighEnergy && isNotEcho

            // DEBUG: Log every ~500ms to understand what's happening
            if (debugLogCounter++ % 15 == 0) {
                println("[RefSignalBargeIn] res=${"%.3f".format(residual)} thresh=${"%.3f".format(adaptiveThreshold)} " +
                        "corr=${"%.2f".format(correlation)} highE=$isHighEnergy notEcho=$isNotEcho")
            }

            if (isUserSpeech) {
                consecutiveBargeInFrames++
                consecutiveSilenceFrames = 0  // Reset silence counter when speech detected
                // Log every detection
                println("[RefSignalBargeIn] SPEECH frames=$consecutiveBargeInFrames/$BARGE_IN_FRAMES_REQUIRED res=${"%.3f".format(residual)}")
            } else {
                // Only reset after SUSTAINED silence (not just one frame)
                consecutiveSilenceFrames++

                // Log silence progress occasionally
                if (consecutiveSilenceFrames % 5 == 0 && consecutiveBargeInFrames > 0) {
                    println("[RefSignalBargeIn] Silence frames=$consecutiveSilenceFrames/$FRAMES_TO_RESET (speech was $consecutiveBargeInFrames)")
                }

                if (consecutiveSilenceFrames >= FRAMES_TO_RESET) {
                    // Only now reset the speech counter
                    if (consecutiveBargeInFrames > 0) {
                        println("[RefSignalBargeIn] Reset: ${consecutiveSilenceFrames * 32}ms silence, lost $consecutiveBargeInFrames frames")
                    }
                    consecutiveBargeInFrames = 0
                }
            }

            // Step 7: Confirm barge-in after 500ms of consistent detection
            val shouldBargeIn = consecutiveBargeInFrames >= BARGE_IN_FRAMES_REQUIRED

            if (shouldBargeIn && !_bargeInDetected.value) {
                println("[RefSignalBargeIn] BARGE-IN CONFIRMED after ${consecutiveBargeInFrames * 32}ms! User is speaking over AI")
                _bargeInDetected.value = true
            }

            // Confidence based on how close we are to threshold
            val confidence = min(1f, consecutiveBargeInFrames.toFloat() / BARGE_IN_FRAMES_REQUIRED)

            return BargeInResult(
                shouldBargeIn = shouldBargeIn,
                confidence = confidence,
                reason = if (shouldBargeIn) "User speech detected"
                        else if (correlation >= ECHO_CORRELATION_THRESHOLD) "Echo detected"
                        else "Below threshold"
            )

        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Calculate RMS energy of audio frame
     */
    private fun calculateEnergy(samples: ShortArray, size: Int): Float {
        var sum = 0.0
        val actualSize = min(size, samples.size)

        for (i in 0 until actualSize) {
            val normalized = samples[i] / 32768f
            sum += normalized * normalized
        }

        return sqrt(sum / actualSize).toFloat()
    }

    /**
     * Find best correlation between mic input and reference buffer.
     * Returns (correlation coefficient, best delay in samples)
     */
    private fun findBestCorrelation(micInput: ShortArray, frameSize: Int): Pair<Float, Int> {
        var bestCorrelation = 0f
        var bestDelay = 0

        val searchStep = 32  // Skip samples for efficiency
        val actualFrameSize = min(frameSize, FRAME_SIZE)

        synchronized(referenceBuffer) {
            for (delay in 0 until MAX_DELAY_SAMPLES step searchStep) {
                val correlation = calculateCorrelation(micInput, actualFrameSize, delay)
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation
                    bestDelay = delay
                }
            }
        }

        return Pair(bestCorrelation, bestDelay)
    }

    /**
     * Calculate normalized cross-correlation at a specific delay
     */
    private fun calculateCorrelation(micInput: ShortArray, frameSize: Int, delay: Int): Float {
        var crossSum = 0.0
        var micSum = 0.0
        var refSum = 0.0

        val startPos = (referenceWritePos - referenceAvailable + REFERENCE_BUFFER_SIZE) % REFERENCE_BUFFER_SIZE

        for (i in 0 until frameSize) {
            val micSample = micInput[i] / 32768f
            val refIndex = (startPos + delay + i) % REFERENCE_BUFFER_SIZE
            val refSample = referenceBuffer[refIndex] / 32768f

            crossSum += micSample * refSample
            micSum += micSample * micSample
            refSum += refSample * refSample
        }

        val denominator = sqrt(micSum * refSum)
        return if (denominator > 0.0001) (crossSum / denominator).toFloat() else 0f
    }

    /**
     * Calculate residual energy after subtracting estimated echo.
     * Uses adaptive echo gain estimation based on correlation.
     */
    private fun calculateResidualEnergy(micInput: ShortArray, frameSize: Int, echoDelay: Int): Float {
        var residualSum = 0.0
        var micSum = 0.0
        var refSum = 0.0
        val actualFrameSize = min(frameSize, FRAME_SIZE)
        val startPos = (referenceWritePos - referenceAvailable + REFERENCE_BUFFER_SIZE) % REFERENCE_BUFFER_SIZE

        // First pass: calculate energies to estimate adaptive echo gain
        synchronized(referenceBuffer) {
            for (i in 0 until actualFrameSize) {
                val micSample = micInput[i] / 32768f
                val refIndex = (startPos + echoDelay + i) % REFERENCE_BUFFER_SIZE
                val refSample = referenceBuffer[refIndex] / 32768f

                micSum += micSample * micSample
                refSum += refSample * refSample
            }
        }

        // Adaptive echo gain: estimate based on energy ratio, clamped to reasonable range
        // If mic energy ≈ ref energy, echo gain is likely high (close to 1.0)
        // If mic energy >> ref energy, user is likely speaking
        val micRms = sqrt(micSum / actualFrameSize)
        val refRms = sqrt(refSum / actualFrameSize)
        val echoGain = if (refRms > 0.001) {
            // Estimate echo gain as ratio, clamped between 0.3 and 1.0
            (micRms / refRms).toFloat().coerceIn(0.3f, 1.0f)
        } else {
            0.5f // Default when no reference
        }

        // Second pass: calculate residual with adaptive echo gain
        synchronized(referenceBuffer) {
            for (i in 0 until actualFrameSize) {
                val micSample = micInput[i] / 32768f
                val refIndex = (startPos + echoDelay + i) % REFERENCE_BUFFER_SIZE
                val refSample = referenceBuffer[refIndex] / 32768f

                // Subtract estimated echo with adaptive gain
                val residual = micSample - (refSample * echoGain)
                residualSum += residual * residual
            }
        }

        return sqrt(residualSum / actualFrameSize).toFloat()
    }

    /**
     * Update noise floor with slow adaptation
     */
    private fun updateNoiseFloor(energy: Float) {
        if (energy < noiseFloor) {
            noiseFloor = noiseFloor * (1 - NOISE_FLOOR_ALPHA) + energy * NOISE_FLOOR_ALPHA
        } else if (energy < noiseFloor * 2) {
            noiseFloor = noiseFloor * (1 - NOISE_FLOOR_ALPHA * 0.1f) + energy * NOISE_FLOOR_ALPHA * 0.1f
        }
        noiseFloor = max(0.005f, noiseFloor)  // Minimum noise floor
    }

    /**
     * Reset detector state. Call when AI stops speaking.
     */
    fun reset() {
        consecutiveBargeInFrames = 0
        consecutiveSilenceFrames = 0
        _bargeInDetected.value = false
        _residualEnergy.value = 0f
        println("[RefSignalBargeIn] Detector reset")
    }

    /**
     * Clear reference buffer. Call when starting new AI response.
     */
    fun clearReferenceBuffer() {
        synchronized(referenceBuffer) {
            referenceBuffer.fill(0)
            referenceWritePos = 0
            referenceAvailable = 0
        }
        println("[RefSignalBargeIn] Reference buffer cleared")
    }

    /**
     * Get current detection statistics
     */
    fun getStats(): Map<String, Any> = mapOf(
        "noiseFloor" to noiseFloor,
        "peakEnergy" to peakEnergy,
        "referenceAvailable" to referenceAvailable,
        "consecutiveFrames" to consecutiveBargeInFrames,
        "bargeInDetected" to _bargeInDetected.value
    )

    data class BargeInResult(
        val shouldBargeIn: Boolean,
        val confidence: Float,
        val reason: String
    )
}
