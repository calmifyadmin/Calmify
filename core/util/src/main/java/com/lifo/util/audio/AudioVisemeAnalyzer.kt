package com.lifo.util.audio

import kotlin.math.*

/**
 * Real-time FFT-based audio analyzer for viseme detection.
 *
 * Analyzes PCM audio chunks to extract formant-like spectral features
 * and classifies them into VRM 5-vowel viseme weights (a, i, u, e, o).
 *
 * The approach is language-independent: vowel formant patterns (F1/F2)
 * are universal across all human languages because they depend on
 * vocal tract geometry, not linguistic convention.
 *
 * Usage:
 * ```
 * val analyzer = AudioVisemeAnalyzer(sampleRate = 24000)
 * val weights = analyzer.analyze(pcmBytes)  // Map<String, Float>
 * // weights = {"a" to 0.8, "e" to 0.1, "i" to 0.0, "o" to 0.05, "u" to 0.0}
 * ```
 *
 * Thread safety: NOT thread-safe. Call from a single thread (audio playback loop).
 */
class AudioVisemeAnalyzer(private val defaultSampleRate: Int = 24000) {

    companion object {
        private const val FFT_SIZE = 512
        private const val HALF_FFT = FFT_SIZE / 2

        // Voice activity detection
        private const val SILENCE_RMS_THRESHOLD = 0.015f
        private const val VOICED_ENERGY_THRESHOLD = 0.005f

        // Smoothing (exponential moving average)
        // alpha=0.4 gives ~40ms effective time constant at 30fps → responsive but smooth
        private const val SMOOTHING_ALPHA = 0.4f

        // Silence fade rate (how fast weights decay to zero during silence)
        private const val SILENCE_DECAY = 0.7f

        // Viseme centroids in normalized F1/F2 space
        // Derived from standard vowel formant charts (Peterson & Barney 1952, adapted)
        //                    f1Norm, f2Norm
        private val VISEME_A = floatArrayOf(0.70f, 0.45f) // High F1, mid F2 (open, back-center)
        private val VISEME_E = floatArrayOf(0.45f, 0.65f) // Mid F1, high F2 (half-open, front)
        private val VISEME_I = floatArrayOf(0.20f, 0.85f) // Low F1, very high F2 (closed, front)
        private val VISEME_O = floatArrayOf(0.45f, 0.30f) // Mid F1, low F2 (half-open, rounded)
        private val VISEME_U = floatArrayOf(0.22f, 0.18f) // Low F1, very low F2 (closed, rounded)

        // Gaussian width for soft classification (larger = more overlap between visemes)
        private const val SIGMA = 0.25f
        private const val SIGMA_SQ_2 = 2f * SIGMA * SIGMA
    }

    // Pre-computed Hanning window
    private val hanningWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    // Pre-allocated FFT buffers (avoid GC in real-time loop)
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private val tempFloat = FloatArray(FFT_SIZE)

    // Smoothed output
    private val smoothedWeights = FloatArray(5) // a, e, i, o, u (alphabetical)

    // Previous frame for additional smoothing
    private var isFirstFrame = true

    /**
     * Analyze a PCM audio chunk and return VRM viseme weights.
     *
     * @param pcmBytes ByteArray of PCM 16-bit little-endian mono audio
     * @param sampleRate Sample rate in Hz (default: constructor value)
     * @return Map with keys "a","e","i","o","u" and values 0.0-1.0
     */
    fun analyze(pcmBytes: ByteArray, sampleRate: Int = defaultSampleRate): Map<String, Float> {
        if (pcmBytes.size < 4) {
            return decayToSilence()
        }

        // Convert bytes to float samples
        val sampleCount = minOf(pcmBytes.size / 2, FFT_SIZE)
        bytesToFloat(pcmBytes, tempFloat, sampleCount)

        // Check voice activity (RMS energy)
        val rms = computeRms(tempFloat, sampleCount)
        if (rms < SILENCE_RMS_THRESHOLD) {
            return decayToSilence()
        }

        // Prepare FFT input: apply Hanning window
        for (i in 0 until FFT_SIZE) {
            fftReal[i] = if (i < sampleCount) tempFloat[i] * hanningWindow[i] else 0f
            fftImag[i] = 0f
        }

        // Compute FFT
        computeFFT(fftReal, fftImag, FFT_SIZE)

        // Extract spectral band energies
        val freqResolution = sampleRate.toFloat() / FFT_SIZE

        // F1 band: 200-900 Hz (mouth openness / jaw)
        val f1Low = extractBandEnergy(fftReal, fftImag, 200f, 500f, freqResolution)
        val f1High = extractBandEnergy(fftReal, fftImag, 500f, 900f, freqResolution)
        val f1Total = f1Low + f1High

        // F2 band: 900-2800 Hz (tongue position front/back)
        val f2Low = extractBandEnergy(fftReal, fftImag, 900f, 1500f, freqResolution)
        val f2High = extractBandEnergy(fftReal, fftImag, 1500f, 2800f, freqResolution)
        val f2Total = f2Low + f2High

        // High frequency: 3000+ Hz (sibilants, fricatives)
        val hfEnergy = extractBandEnergy(fftReal, fftImag, 3000f, 8000f, freqResolution)

        // Total voiced energy
        val totalEnergy = f1Total + f2Total + hfEnergy + 1e-10f

        // Normalize F1 and F2 to [0, 1] range
        val f1Norm = (f1Total / totalEnergy).coerceIn(0f, 1f)
        val f2Norm = (f2Total / totalEnergy).coerceIn(0f, 1f)

        // Classify into visemes using soft Gaussian assignment
        val rawWeights = classifyVisemes(f1Norm, f2Norm, hfEnergy, totalEnergy, rms)

        // Apply temporal smoothing
        applySmoothing(rawWeights)

        // Build result map
        return mapOf(
            "a" to smoothedWeights[0],
            "e" to smoothedWeights[1],
            "i" to smoothedWeights[2],
            "o" to smoothedWeights[3],
            "u" to smoothedWeights[4]
        )
    }

    /**
     * Reset smoothing state. Call when a new speech segment begins.
     */
    fun reset() {
        smoothedWeights.fill(0f)
        isFirstFrame = true
    }

    // ─── Internal Methods ───────────────────────────────────────────────

    private fun bytesToFloat(bytes: ByteArray, output: FloatArray, count: Int) {
        for (i in 0 until count) {
            val idx = i * 2
            if (idx + 1 >= bytes.size) break
            val sample = (bytes[idx].toInt() and 0xFF) or (bytes[idx + 1].toInt() shl 8)
            output[i] = sample.toShort().toFloat() / 32768f
        }
    }

    private fun computeRms(samples: FloatArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            sum += samples[i].toDouble() * samples[i].toDouble()
        }
        return sqrt(sum / count).toFloat()
    }

    /**
     * In-place Cooley-Tukey radix-2 FFT.
     */
    private fun computeFFT(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                // Swap real[i] <-> real[j]
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }

        // FFT butterfly computation
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until halfLen) {
                    val uRe = real[i + k]
                    val uIm = imag[i + k]
                    val tRe = wRe * real[i + k + halfLen] - wIm * imag[i + k + halfLen]
                    val tIm = wRe * imag[i + k + halfLen] + wIm * real[i + k + halfLen]

                    real[i + k] = uRe + tRe
                    imag[i + k] = uIm + tIm
                    real[i + k + halfLen] = uRe - tRe
                    imag[i + k + halfLen] = uIm - tIm

                    val newWRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = newWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Extract energy in a frequency band from FFT result.
     * Sums magnitude-squared of bins in [freqLow, freqHigh].
     */
    private fun extractBandEnergy(
        real: FloatArray,
        imag: FloatArray,
        freqLow: Float,
        freqHigh: Float,
        freqResolution: Float
    ): Float {
        val binLow = (freqLow / freqResolution).toInt().coerceIn(1, HALF_FFT - 1)
        val binHigh = (freqHigh / freqResolution).toInt().coerceIn(binLow + 1, HALF_FFT)

        var energy = 0f
        for (bin in binLow until binHigh) {
            energy += real[bin] * real[bin] + imag[bin] * imag[bin]
        }
        return energy
    }

    /**
     * Classify normalized F1/F2 into 5 VRM viseme weights using Gaussian soft assignment.
     * Each viseme has a centroid in (f1Norm, f2Norm) space.
     * Weight = exp(-distance² / (2σ²)), then normalized to sum=1.
     */
    private fun classifyVisemes(
        f1Norm: Float,
        f2Norm: Float,
        hfEnergy: Float,
        totalEnergy: Float,
        rms: Float
    ): FloatArray {
        val raw = FloatArray(5)

        // Compute Gaussian distance to each viseme centroid
        raw[0] = gaussianWeight(f1Norm, f2Norm, VISEME_A)
        raw[1] = gaussianWeight(f1Norm, f2Norm, VISEME_E)
        raw[2] = gaussianWeight(f1Norm, f2Norm, VISEME_I)
        raw[3] = gaussianWeight(f1Norm, f2Norm, VISEME_O)
        raw[4] = gaussianWeight(f1Norm, f2Norm, VISEME_U)

        // Boost "i" and "s/z" detection when high-frequency energy is dominant
        val hfRatio = hfEnergy / totalEnergy.toFloat()
        if (hfRatio > 0.3f) {
            // Sibilant sounds — slightly spread lips (like "i" but less extreme)
            raw[2] = raw[2] * 1.0f + hfRatio * 0.3f // Boost "i" slightly
        }

        // Normalize so sum = 1 (soft classification)
        val sum = raw.sum()
        if (sum > 1e-6f) {
            for (i in raw.indices) {
                raw[i] /= sum
            }
        }

        // Scale by voice energy (louder = more mouth movement)
        // Use sqrt for more natural response (logarithmic perception)
        val energyScale = sqrt(rms / 0.3f).coerceIn(0.2f, 1.2f)
        for (i in raw.indices) {
            raw[i] *= energyScale
        }

        return raw
    }

    /**
     * Gaussian weight: exp(-d²/2σ²) where d is Euclidean distance in F1/F2 space.
     */
    private fun gaussianWeight(f1: Float, f2: Float, centroid: FloatArray): Float {
        val df1 = f1 - centroid[0]
        val df2 = f2 - centroid[1]
        val distSq = df1 * df1 + df2 * df2
        return exp(-distSq / SIGMA_SQ_2)
    }

    /**
     * Apply exponential moving average smoothing to prevent jitter.
     */
    private fun applySmoothing(raw: FloatArray) {
        if (isFirstFrame) {
            // First frame: no smoothing, just copy
            raw.copyInto(smoothedWeights)
            isFirstFrame = false
        } else {
            for (i in smoothedWeights.indices) {
                smoothedWeights[i] = smoothedWeights[i] * (1f - SMOOTHING_ALPHA) + raw[i] * SMOOTHING_ALPHA
            }
        }
    }

    /**
     * Decay smoothed weights toward silence.
     * Called when audio is below voice activity threshold.
     */
    private fun decayToSilence(): Map<String, Float> {
        if (isFirstFrame) {
            return emptyMap()
        }

        var allZero = true
        for (i in smoothedWeights.indices) {
            smoothedWeights[i] *= SILENCE_DECAY
            if (smoothedWeights[i] > 0.01f) {
                allZero = false
            } else {
                smoothedWeights[i] = 0f
            }
        }

        if (allZero) {
            return emptyMap()
        }

        return mapOf(
            "a" to smoothedWeights[0],
            "e" to smoothedWeights[1],
            "i" to smoothedWeights[2],
            "o" to smoothedWeights[3],
            "u" to smoothedWeights[4]
        )
    }
}
