package com.lifo.chat.audio.engine

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Packet Loss Concealment (PLC) Engine
 *
 * Architettura AAA per mascherare perdite di pacchetti audio:
 * - Waveform extrapolation basata su storia audio
 * - Crossfade smoothing ai bordi dei gap
 * - Comfort noise generation per silenzi naturali
 * - Pattern matching per ripetizione intelligente
 *
 * Design basato su:
 * - ITU-T G.711 Appendix I PLC
 * - WebRTC NetEQ concealment
 * - Opus codec PLC
 *
 * Tecniche implementate:
 * 1. Zero Insertion (baseline)
 * 2. Waveform Repetition (good)
 * 3. Waveform Extrapolation (better)
 * 4. Pitch-synchronized Overlap-Add (best)
 *
 * @param sampleRate Sample rate audio
 * @param frameSize Dimensione frame in campioni
 * @param historyFrames Numero di frame da mantenere in storia
 *
 * @author Jarvis AI Assistant - AAA Audio Engine
 */
class PacketLossConcealmentEngine(
    private val sampleRate: Int = 24000,
    private val frameSize: Int = DEFAULT_FRAME_SIZE,
    private val historyFrames: Int = HISTORY_FRAMES
) {
    companion object {
        private const val TAG = "PLCEngine"

        // Frame size: 20ms @ 24kHz = 480 samples
        const val DEFAULT_FRAME_SIZE = 480
        const val HISTORY_FRAMES = 4 // 80ms di storia

        // Crossfade
        private const val CROSSFADE_SAMPLES = 48 // 2ms @ 24kHz

        // Pitch detection
        private const val MIN_PITCH_SAMPLES = 20  // ~1200Hz max pitch
        private const val MAX_PITCH_SAMPLES = 400 // ~60Hz min pitch

        // Attenuation per frame consecutivi persi
        private const val ATTENUATION_PER_FRAME = 0.9f
        private const val MAX_CONSECUTIVE_CONCEALMENT = 10

        // Comfort noise
        private const val COMFORT_NOISE_LEVEL = 0.002f
    }

    /**
     * Tipo di concealment applicato
     */
    enum class ConcealmentType {
        NONE,               // Nessun concealment necessario
        ZERO_FILL,          // Riempimento con zeri (fallback)
        REPETITION,         // Ripetizione ultimo frame
        EXTRAPOLATION,      // Estrapolazione basata su pitch
        INTERPOLATION,      // Interpolazione tra frame buoni
        COMFORT_NOISE       // Rumore di comfort
    }

    /**
     * Risultato del concealment
     */
    data class ConcealmentResult(
        val samples: ShortArray,
        val type: ConcealmentType,
        val qualityEstimate: Float // 0-1, dove 1 è qualità perfetta
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ConcealmentResult) return false
            return samples.contentEquals(other.samples) && type == other.type
        }

        override fun hashCode(): Int = samples.contentHashCode()
    }

    // Storia audio per concealment
    private val historyBuffer = ShortArray(frameSize * historyFrames)
    private var historyWriteIndex = 0
    private var historyFilled = false

    // Pitch tracking
    private var lastDetectedPitch = 0
    private var pitchConfidence = 0f

    // Stato concealment
    private var consecutiveLostFrames = 0
    private var currentAttenuation = 1.0f

    // Ultimo frame buono per crossfade
    private var lastGoodFrame = ShortArray(frameSize)
    private var hasLastGoodFrame = false

    // Buffer temporanei (pre-allocati per zero-allocation)
    private val tempBuffer = ShortArray(frameSize)
    private val crossfadeBuffer = ShortArray(frameSize)

    // Statistiche
    private val totalFramesConcealed = AtomicLong(0)
    private val totalFramesProcessed = AtomicLong(0)
    private val concealmentByType = mutableMapOf<ConcealmentType, AtomicInteger>()

    init {
        ConcealmentType.values().forEach {
            concealmentByType[it] = AtomicInteger(0)
        }
    }

    /**
     * Processa un frame audio buono (aggiorna storia)
     *
     * @param frame Frame audio ricevuto correttamente
     */
    fun processGoodFrame(frame: ShortArray) {
        require(frame.size == frameSize) { "Frame size mismatch: ${frame.size} vs $frameSize" }

        // Aggiungi alla storia
        addToHistory(frame)

        // Aggiorna ultimo frame buono
        System.arraycopy(frame, 0, lastGoodFrame, 0, frameSize)
        hasLastGoodFrame = true

        // Reset consecutive lost
        if (consecutiveLostFrames > 0) {
            Log.d(TAG, "✅ Ripresa dopo $consecutiveLostFrames frame persi")
        }
        consecutiveLostFrames = 0
        currentAttenuation = 1.0f

        // Aggiorna pitch detection periodicamente
        if (totalFramesProcessed.incrementAndGet() % 5 == 0L) {
            detectPitch(frame)
        }
    }

    /**
     * Genera frame di concealment per pacchetto perso
     *
     * @param nextGoodFrame Frame successivo buono (per interpolazione), null se non disponibile
     * @return Risultato del concealment
     */
    fun concealLostFrame(nextGoodFrame: ShortArray? = null): ConcealmentResult {
        consecutiveLostFrames++
        totalFramesConcealed.incrementAndGet()

        // Applica attenuation progressiva
        currentAttenuation *= ATTENUATION_PER_FRAME

        // Scegli strategia di concealment
        val result = when {
            // Troppi frame persi consecutivi -> comfort noise
            consecutiveLostFrames > MAX_CONSECUTIVE_CONCEALMENT -> {
                generateComfortNoise()
            }

            // Interpolazione se abbiamo frame successivo
            nextGoodFrame != null && hasLastGoodFrame -> {
                interpolateFrames(lastGoodFrame, nextGoodFrame)
            }

            // Extrapolazione se abbiamo storia e pitch
            historyFilled && pitchConfidence > 0.5f -> {
                extrapolateFrame()
            }

            // Ripetizione se abbiamo ultimo frame
            hasLastGoodFrame -> {
                repeatLastFrame()
            }

            // Fallback: zero fill
            else -> {
                zeroFillFrame()
            }
        }

        // Applica crossfade se abbiamo frame precedente
        val finalSamples = if (hasLastGoodFrame && consecutiveLostFrames == 1) {
            applyCrossfade(lastGoodFrame, result.samples)
        } else {
            result.samples
        }

        // Aggiorna statistiche
        concealmentByType[result.type]?.incrementAndGet()

        return ConcealmentResult(
            samples = finalSamples,
            type = result.type,
            qualityEstimate = calculateQualityEstimate(result.type)
        )
    }

    /**
     * Applica smoothing tra frame concealed e frame buono successivo
     */
    fun smoothTransition(concealedFrame: ShortArray, goodFrame: ShortArray): ShortArray {
        return applyCrossfade(concealedFrame, goodFrame)
    }

    // ==================== Concealment Strategies ====================

    /**
     * Zero fill (fallback)
     */
    private fun zeroFillFrame(): ConcealmentResult {
        val samples = ShortArray(frameSize) // Già inizializzato a 0
        return ConcealmentResult(samples, ConcealmentType.ZERO_FILL, 0.1f)
    }

    /**
     * Ripetizione ultimo frame con attenuation
     */
    private fun repeatLastFrame(): ConcealmentResult {
        val samples = ShortArray(frameSize)

        for (i in 0 until frameSize) {
            samples[i] = (lastGoodFrame[i] * currentAttenuation).toInt().toShort()
        }

        return ConcealmentResult(samples, ConcealmentType.REPETITION, 0.6f)
    }

    /**
     * Extrapolazione basata su pitch
     */
    private fun extrapolateFrame(): ConcealmentResult {
        val samples = ShortArray(frameSize)
        val pitchPeriod = if (lastDetectedPitch > 0) lastDetectedPitch else frameSize / 4

        // Estrai ultimo periodo dalla storia
        val historyStart = (historyWriteIndex - pitchPeriod + historyBuffer.size) % historyBuffer.size

        for (i in 0 until frameSize) {
            val historyIndex = (historyStart + (i % pitchPeriod)) % historyBuffer.size
            val sample = historyBuffer[historyIndex]

            // Applica attenuation e leggera variazione per naturalezza
            val variation = 1.0f + (Random.nextFloat() - 0.5f) * 0.02f
            samples[i] = (sample * currentAttenuation * variation).toInt().coerceIn(-32768, 32767).toShort()
        }

        return ConcealmentResult(samples, ConcealmentType.EXTRAPOLATION, 0.75f)
    }

    /**
     * Interpolazione tra frame precedente e successivo
     */
    private fun interpolateFrames(prevFrame: ShortArray, nextFrame: ShortArray): ConcealmentResult {
        val samples = ShortArray(frameSize)

        for (i in 0 until frameSize) {
            val t = i.toFloat() / frameSize // 0 -> 1
            // Crossfade lineare con curva smooth
            val smoothT = smoothstep(t)

            val prev = prevFrame[min(i, prevFrame.size - 1)]
            val next = nextFrame[min(i, nextFrame.size - 1)]

            samples[i] = ((prev * (1 - smoothT) + next * smoothT) * currentAttenuation)
                .toInt().coerceIn(-32768, 32767).toShort()
        }

        return ConcealmentResult(samples, ConcealmentType.INTERPOLATION, 0.85f)
    }

    /**
     * Genera comfort noise
     */
    private fun generateComfortNoise(): ConcealmentResult {
        val samples = ShortArray(frameSize)

        // Stima livello rumore dalla storia
        val noiseLevel = estimateNoiseLevel()

        for (i in 0 until frameSize) {
            // Rumore rosa (più naturale del rumore bianco)
            val white = (Random.nextFloat() - 0.5f) * 2
            val pink = pinkNoiseFilter(white)

            samples[i] = (pink * noiseLevel * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }

        return ConcealmentResult(samples, ConcealmentType.COMFORT_NOISE, 0.3f)
    }

    // ==================== Helper Methods ====================

    /**
     * Aggiunge frame alla storia circolare
     */
    private fun addToHistory(frame: ShortArray) {
        for (sample in frame) {
            historyBuffer[historyWriteIndex] = sample
            historyWriteIndex = (historyWriteIndex + 1) % historyBuffer.size

            if (historyWriteIndex == 0) {
                historyFilled = true
            }
        }
    }

    /**
     * Rileva pitch usando autocorrelazione
     */
    private fun detectPitch(frame: ShortArray) {
        var maxCorrelation = 0f
        var bestPitch = 0

        for (lag in MIN_PITCH_SAMPLES until min(MAX_PITCH_SAMPLES, frame.size / 2)) {
            var correlation = 0f
            var energy1 = 0f
            var energy2 = 0f

            for (i in 0 until frame.size - lag) {
                val s1 = frame[i].toFloat()
                val s2 = frame[i + lag].toFloat()

                correlation += s1 * s2
                energy1 += s1 * s1
                energy2 += s2 * s2
            }

            // Normalizza correlazione
            val normalizedCorr = if (energy1 > 0 && energy2 > 0) {
                correlation / sqrt(energy1 * energy2)
            } else 0f

            if (normalizedCorr > maxCorrelation) {
                maxCorrelation = normalizedCorr
                bestPitch = lag
            }
        }

        if (maxCorrelation > 0.5f) {
            lastDetectedPitch = bestPitch
            pitchConfidence = maxCorrelation
        } else {
            pitchConfidence *= 0.9f // Decay confidence
        }
    }

    /**
     * Applica crossfade tra due frame
     */
    private fun applyCrossfade(fromFrame: ShortArray, toFrame: ShortArray): ShortArray {
        val result = ShortArray(frameSize)
        val fadeLength = min(CROSSFADE_SAMPLES, frameSize)

        for (i in 0 until frameSize) {
            if (i < fadeLength) {
                // Crossfade region
                val t = i.toFloat() / fadeLength
                val smoothT = smoothstep(t)

                val from = fromFrame[min(fromFrame.size - fadeLength + i, fromFrame.size - 1)]
                val to = toFrame[i]

                result[i] = ((from * (1 - smoothT) + to * smoothT))
                    .toInt().coerceIn(-32768, 32767).toShort()
            } else {
                // Rest of frame
                result[i] = toFrame[i]
            }
        }

        return result
    }

    /**
     * Smoothstep function per transizioni fluide
     */
    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    /**
     * Stima livello rumore dalla storia
     */
    private fun estimateNoiseLevel(): Float {
        if (!historyFilled) return COMFORT_NOISE_LEVEL

        // Trova il livello minimo nella storia (approssimazione noise floor)
        var minRms = Float.MAX_VALUE

        for (frame in 0 until historyFrames) {
            var sumSquares = 0f
            val startIndex = frame * frameSize

            for (i in 0 until frameSize) {
                val index = (startIndex + i) % historyBuffer.size
                val sample = historyBuffer[index].toFloat() / 32767f
                sumSquares += sample * sample
            }

            val rms = sqrt(sumSquares / frameSize)
            if (rms < minRms && rms > 0.0001f) {
                minRms = rms
            }
        }

        return if (minRms < Float.MAX_VALUE) minRms else COMFORT_NOISE_LEVEL
    }

    // Pink noise state
    private var pinkNoiseState = FloatArray(7)

    /**
     * Filtro per generare rumore rosa da rumore bianco
     * Usa algoritmo Paul Kellet
     */
    private fun pinkNoiseFilter(white: Float): Float {
        pinkNoiseState[0] = 0.99886f * pinkNoiseState[0] + white * 0.0555179f
        pinkNoiseState[1] = 0.99332f * pinkNoiseState[1] + white * 0.0750759f
        pinkNoiseState[2] = 0.96900f * pinkNoiseState[2] + white * 0.1538520f
        pinkNoiseState[3] = 0.86650f * pinkNoiseState[3] + white * 0.3104856f
        pinkNoiseState[4] = 0.55000f * pinkNoiseState[4] + white * 0.5329522f
        pinkNoiseState[5] = -0.7616f * pinkNoiseState[5] - white * 0.0168980f

        val pink = pinkNoiseState[0] + pinkNoiseState[1] + pinkNoiseState[2] +
                pinkNoiseState[3] + pinkNoiseState[4] + pinkNoiseState[5] +
                pinkNoiseState[6] + white * 0.5362f

        pinkNoiseState[6] = white * 0.115926f

        return pink * 0.11f // Normalizza
    }

    /**
     * Calcola stima qualità basata su tipo concealment
     */
    private fun calculateQualityEstimate(type: ConcealmentType): Float {
        val baseQuality = when (type) {
            ConcealmentType.NONE -> 1.0f
            ConcealmentType.INTERPOLATION -> 0.85f
            ConcealmentType.EXTRAPOLATION -> 0.75f
            ConcealmentType.REPETITION -> 0.6f
            ConcealmentType.COMFORT_NOISE -> 0.3f
            ConcealmentType.ZERO_FILL -> 0.1f
        }

        // Riduci qualità per frame consecutivi persi
        val consecutivePenalty = 1.0f - (consecutiveLostFrames * 0.05f).coerceIn(0f, 0.5f)

        return (baseQuality * consecutivePenalty).coerceIn(0f, 1f)
    }

    /**
     * Reset engine
     */
    fun reset() {
        historyBuffer.fill(0)
        historyWriteIndex = 0
        historyFilled = false

        lastGoodFrame.fill(0)
        hasLastGoodFrame = false

        lastDetectedPitch = 0
        pitchConfidence = 0f

        consecutiveLostFrames = 0
        currentAttenuation = 1.0f

        pinkNoiseState.fill(0f)

        Log.d(TAG, "🔄 PLC Engine reset")
    }

    /**
     * Ottieni statistiche
     */
    fun getStatistics(): PLCStatistics {
        return PLCStatistics(
            totalFramesProcessed = totalFramesProcessed.get(),
            totalFramesConcealed = totalFramesConcealed.get(),
            concealmentRate = if (totalFramesProcessed.get() > 0) {
                (totalFramesConcealed.get().toFloat() / totalFramesProcessed.get()) * 100
            } else 0f,
            consecutiveLostFrames = consecutiveLostFrames,
            lastDetectedPitch = lastDetectedPitch,
            pitchConfidence = pitchConfidence,
            concealmentByType = concealmentByType.mapValues { it.value.get() }
        )
    }

    /**
     * Reset statistiche
     */
    fun resetStatistics() {
        totalFramesConcealed.set(0)
        totalFramesProcessed.set(0)
        concealmentByType.values.forEach { it.set(0) }
    }

    /**
     * Data class per statistiche PLC
     */
    data class PLCStatistics(
        val totalFramesProcessed: Long,
        val totalFramesConcealed: Long,
        val concealmentRate: Float,
        val consecutiveLostFrames: Int,
        val lastDetectedPitch: Int,
        val pitchConfidence: Float,
        val concealmentByType: Map<ConcealmentType, Int>
    )
}
