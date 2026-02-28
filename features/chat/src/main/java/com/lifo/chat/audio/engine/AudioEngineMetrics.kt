package com.lifo.chat.audio.engine


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Audio Engine Metrics - Sistema di monitoring real-time AAA
 *
 * Raccoglie e aggrega tutte le metriche dei componenti audio:
 * - Latenza end-to-end
 * - Qualità buffer
 * - Performance PLC
 * - Health score complessivo
 *
 * Usato per:
 * - Dashboard diagnostica
 * - Adaptive tuning
 * - Analytics
 * - Debug
 *
 * @author Jarvis AI Assistant - AAA Audio Engine
 */
class AudioEngineMetrics {

    companion object {
        // Soglie per health score
        private const val EXCELLENT_LATENCY_MS = 100f
        private const val GOOD_LATENCY_MS = 150f
        private const val ACCEPTABLE_LATENCY_MS = 250f

        private const val EXCELLENT_BUFFER_PERCENT = 80
        private const val GOOD_BUFFER_PERCENT = 50
        private const val LOW_BUFFER_PERCENT = 25

        private const val EXCELLENT_UNDERRUN_RATE = 0.1f
        private const val GOOD_UNDERRUN_RATE = 1.0f
        private const val ACCEPTABLE_UNDERRUN_RATE = 5.0f
    }

    /**
     * Snapshot completo delle metriche
     */
    data class MetricsSnapshot(
        // Timestamp
        val timestamp: Long = System.currentTimeMillis(),

        // Latenza
        val endToEndLatencyMs: Float = 0f,
        val jitterMs: Float = 0f,
        val bufferLatencyMs: Float = 0f,
        val networkLatencyMs: Float = 0f,

        // Buffer Ring
        val ringBufferLevelPercent: Int = 0,
        val ringBufferLevelMs: Float = 0f,
        val ringBufferOverruns: Int = 0,
        val ringBufferUnderruns: Int = 0,

        // Jitter Buffer
        val jitterBufferLevelMs: Float = 0f,
        val jitterBufferTargetMs: Int = 100,
        val jitterBufferState: AdaptiveJitterBuffer.BufferState = AdaptiveJitterBuffer.BufferState.BUFFERING,
        val packetsReceived: Long = 0,
        val packetsLost: Long = 0,
        val packetLossRate: Float = 0f,

        // PLC
        val plcActivations: Long = 0,
        val plcRate: Float = 0f,
        val plcQualityEstimate: Float = 1f,
        val concealmentType: PacketLossConcealmentEngine.ConcealmentType = PacketLossConcealmentEngine.ConcealmentType.NONE,

        // Playback Thread
        val playbackState: HighPriorityAudioThread.PlaybackState = HighPriorityAudioThread.PlaybackState.IDLE,
        val totalBytesPlayed: Long = 0,
        val playbackUnderruns: Long = 0,
        val avgLoopTimeUs: Long = 0,

        // Health Score
        val healthScore: Float = 100f,
        val healthGrade: HealthGrade = HealthGrade.EXCELLENT,
        val primaryIssue: String = ""
    )

    /**
     * Grado di salute complessivo
     */
    enum class HealthGrade {
        EXCELLENT,  // > 90%
        GOOD,       // 70-90%
        FAIR,       // 50-70%
        POOR,       // 30-50%
        CRITICAL    // < 30%
    }

    /**
     * Tipo di problema rilevato
     */
    enum class IssueType {
        NONE,
        HIGH_LATENCY,
        BUFFER_UNDERRUN,
        PACKET_LOSS,
        JITTER,
        PLC_OVERUSE,
        THREAD_STARVATION
    }

    // State flow per metriche real-time
    private val _metrics = MutableStateFlow(MetricsSnapshot())
    val metrics: StateFlow<MetricsSnapshot> = _metrics.asStateFlow()

    // Metriche accumulate
    private var totalPacketsReceived = AtomicLong(0)
    private var totalPacketsLost = AtomicLong(0)
    private var totalUnderruns = AtomicLong(0)
    private var totalPLCActivations = AtomicLong(0)
    private var totalBytesPlayed = AtomicLong(0)

    // Latency tracking
    private var lastNetworkLatencyMs = 0f
    private var smoothedLatencyMs = 0f

    // Issue tracking
    private var currentIssue = IssueType.NONE
    private var issueStartTime = 0L

    /**
     * Aggiorna metriche dal Ring Buffer
     */
    fun updateRingBufferMetrics(stats: LockFreeAudioRingBuffer.BufferStatistics) {
        val current = _metrics.value
        _metrics.value = current.copy(
            ringBufferLevelPercent = stats.bufferLevelPercent,
            ringBufferLevelMs = (stats.availableToRead * 1000f) / (24000 * 2), // Assumendo 24kHz 16-bit
            ringBufferOverruns = stats.overrunCount,
            ringBufferUnderruns = stats.underrunCount
        )
    }

    /**
     * Aggiorna metriche dal Jitter Buffer
     */
    fun updateJitterBufferMetrics(stats: AdaptiveJitterBuffer.JitterBufferStatistics) {
        totalPacketsReceived.set(stats.packetsReceived)
        totalPacketsLost.set(stats.packetsLost)

        val current = _metrics.value
        _metrics.value = current.copy(
            jitterBufferLevelMs = stats.currentBufferMs,
            jitterBufferTargetMs = stats.targetBufferMs,
            jitterBufferState = stats.bufferState,
            packetsReceived = stats.packetsReceived,
            packetsLost = stats.packetsLost,
            packetLossRate = stats.packetLossRate,
            jitterMs = stats.smoothedJitterMs
        )

        recalculateHealth()
    }

    /**
     * Aggiorna metriche dal PLC Engine
     */
    fun updatePLCMetrics(stats: PacketLossConcealmentEngine.PLCStatistics) {
        totalPLCActivations.set(stats.totalFramesConcealed)

        val current = _metrics.value
        _metrics.value = current.copy(
            plcActivations = stats.totalFramesConcealed,
            plcRate = stats.concealmentRate,
            plcQualityEstimate = 1f - (stats.concealmentRate / 100f).coerceIn(0f, 1f)
        )

        recalculateHealth()
    }

    /**
     * Aggiorna metriche dal Playback Thread
     */
    fun updatePlaybackMetrics(stats: HighPriorityAudioThread.PlaybackStatistics) {
        totalUnderruns.set(stats.underrunCount)
        totalBytesPlayed.set(stats.totalBytesPlayed)

        val current = _metrics.value
        _metrics.value = current.copy(
            playbackState = stats.playbackState,
            totalBytesPlayed = stats.totalBytesPlayed,
            playbackUnderruns = stats.underrunCount,
            avgLoopTimeUs = stats.avgLoopTimeUs
        )

        recalculateHealth()
    }

    /**
     * Registra misurazione latenza di rete
     */
    fun recordNetworkLatency(latencyMs: Float) {
        // Exponential moving average
        smoothedLatencyMs = if (smoothedLatencyMs == 0f) {
            latencyMs
        } else {
            smoothedLatencyMs * 0.9f + latencyMs * 0.1f
        }

        lastNetworkLatencyMs = latencyMs

        val current = _metrics.value
        val totalLatency = smoothedLatencyMs + current.jitterBufferLevelMs + current.ringBufferLevelMs

        _metrics.value = current.copy(
            networkLatencyMs = smoothedLatencyMs,
            bufferLatencyMs = current.jitterBufferLevelMs + current.ringBufferLevelMs,
            endToEndLatencyMs = totalLatency
        )

        recalculateHealth()
    }

    /**
     * Ricalcola health score complessivo
     */
    private fun recalculateHealth() {
        val current = _metrics.value

        // Calcola score componenti (0-100)
        val latencyScore = calculateLatencyScore(current.endToEndLatencyMs)
        val bufferScore = calculateBufferScore(current.ringBufferLevelPercent)
        val packetLossScore = calculatePacketLossScore(current.packetLossRate)
        val underrunScore = calculateUnderrunScore(current.playbackUnderruns, current.totalBytesPlayed)
        val jitterScore = calculateJitterScore(current.jitterMs)

        // Weighted average (latenza e underrun più importanti)
        val healthScore = (
            latencyScore * 0.25f +
            bufferScore * 0.20f +
            packetLossScore * 0.20f +
            underrunScore * 0.25f +
            jitterScore * 0.10f
        ).coerceIn(0f, 100f)

        // Determina grade
        val grade = when {
            healthScore >= 90 -> HealthGrade.EXCELLENT
            healthScore >= 70 -> HealthGrade.GOOD
            healthScore >= 50 -> HealthGrade.FAIR
            healthScore >= 30 -> HealthGrade.POOR
            else -> HealthGrade.CRITICAL
        }

        // Identifica problema principale
        val issue = identifyPrimaryIssue(latencyScore, bufferScore, packetLossScore, underrunScore, jitterScore)
        val issueDescription = when (issue) {
            IssueType.NONE -> ""
            IssueType.HIGH_LATENCY -> "Latenza elevata (${current.endToEndLatencyMs.toInt()}ms)"
            IssueType.BUFFER_UNDERRUN -> "Buffer underrun frequenti"
            IssueType.PACKET_LOSS -> "Perdita pacchetti (${current.packetLossRate}%)"
            IssueType.JITTER -> "Jitter elevato (${current.jitterMs.toInt()}ms)"
            IssueType.PLC_OVERUSE -> "PLC sovraccarico (${current.plcRate}%)"
            IssueType.THREAD_STARVATION -> "Thread audio in starvation"
        }

        _metrics.value = current.copy(
            healthScore = healthScore,
            healthGrade = grade,
            primaryIssue = issueDescription
        )

        // Log se health degradato
        if (grade == HealthGrade.POOR || grade == HealthGrade.CRITICAL) {
            println("[AudioEngineMetrics] WARNING: Audio health degradato: $grade - $issueDescription")
        }
    }

    private fun calculateLatencyScore(latencyMs: Float): Float {
        return when {
            latencyMs <= EXCELLENT_LATENCY_MS -> 100f
            latencyMs <= GOOD_LATENCY_MS -> 80f + (GOOD_LATENCY_MS - latencyMs) / (GOOD_LATENCY_MS - EXCELLENT_LATENCY_MS) * 20f
            latencyMs <= ACCEPTABLE_LATENCY_MS -> 50f + (ACCEPTABLE_LATENCY_MS - latencyMs) / (ACCEPTABLE_LATENCY_MS - GOOD_LATENCY_MS) * 30f
            else -> (50f * (500f - latencyMs) / (500f - ACCEPTABLE_LATENCY_MS)).coerceIn(0f, 50f)
        }
    }

    private fun calculateBufferScore(levelPercent: Int): Float {
        return when {
            levelPercent >= EXCELLENT_BUFFER_PERCENT -> 100f
            levelPercent >= GOOD_BUFFER_PERCENT -> 70f + (levelPercent - GOOD_BUFFER_PERCENT) / (EXCELLENT_BUFFER_PERCENT - GOOD_BUFFER_PERCENT).toFloat() * 30f
            levelPercent >= LOW_BUFFER_PERCENT -> 40f + (levelPercent - LOW_BUFFER_PERCENT) / (GOOD_BUFFER_PERCENT - LOW_BUFFER_PERCENT).toFloat() * 30f
            else -> (levelPercent / LOW_BUFFER_PERCENT.toFloat() * 40f).coerceIn(0f, 40f)
        }
    }

    private fun calculatePacketLossScore(lossRate: Float): Float {
        return when {
            lossRate <= 0.1f -> 100f
            lossRate <= 1f -> 80f + (1f - lossRate) / 0.9f * 20f
            lossRate <= 5f -> 50f + (5f - lossRate) / 4f * 30f
            else -> (50f * (10f - lossRate) / 5f).coerceIn(0f, 50f)
        }
    }

    private fun calculateUnderrunScore(underruns: Long, bytesPlayed: Long): Float {
        if (bytesPlayed == 0L) return 100f

        // Underrun rate per MB di audio
        val underrunsPerMB = (underruns.toFloat() / (bytesPlayed / 1_000_000f))

        return when {
            underrunsPerMB <= EXCELLENT_UNDERRUN_RATE -> 100f
            underrunsPerMB <= GOOD_UNDERRUN_RATE -> 80f
            underrunsPerMB <= ACCEPTABLE_UNDERRUN_RATE -> 50f
            else -> (30f * (10f - underrunsPerMB) / 5f).coerceIn(0f, 30f)
        }
    }

    private fun calculateJitterScore(jitterMs: Float): Float {
        return when {
            jitterMs <= 10f -> 100f
            jitterMs <= 20f -> 80f
            jitterMs <= 50f -> 50f
            else -> (30f * (100f - jitterMs) / 50f).coerceIn(0f, 30f)
        }
    }

    private fun identifyPrimaryIssue(
        latencyScore: Float,
        bufferScore: Float,
        packetLossScore: Float,
        underrunScore: Float,
        jitterScore: Float
    ): IssueType {
        val minScore = minOf(latencyScore, bufferScore, packetLossScore, underrunScore, jitterScore)

        if (minScore >= 70f) return IssueType.NONE

        return when (minScore) {
            latencyScore -> IssueType.HIGH_LATENCY
            bufferScore -> IssueType.BUFFER_UNDERRUN
            packetLossScore -> IssueType.PACKET_LOSS
            underrunScore -> IssueType.BUFFER_UNDERRUN
            jitterScore -> IssueType.JITTER
            else -> IssueType.NONE
        }
    }

    /**
     * Reset tutte le metriche
     */
    fun reset() {
        totalPacketsReceived.set(0)
        totalPacketsLost.set(0)
        totalUnderruns.set(0)
        totalPLCActivations.set(0)
        totalBytesPlayed.set(0)
        smoothedLatencyMs = 0f
        lastNetworkLatencyMs = 0f

        _metrics.value = MetricsSnapshot()

        println("[AudioEngineMetrics] Metriche reset")
    }

    /**
     * Genera report testuale
     */
    fun generateReport(): String {
        val m = _metrics.value

        return buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("       AAA AUDIO ENGINE METRICS        ")
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("📊 HEALTH: ${m.healthGrade} (${m.healthScore.toInt()}%)")
            if (m.primaryIssue.isNotEmpty()) {
                appendLine("⚠️ Issue: ${m.primaryIssue}")
            }
            appendLine()
            appendLine("⏱️ LATENZA")
            appendLine("   End-to-End: ${m.endToEndLatencyMs.toInt()}ms")
            appendLine("   Network: ${m.networkLatencyMs.toInt()}ms")
            appendLine("   Buffer: ${m.bufferLatencyMs.toInt()}ms")
            appendLine("   Jitter: ${m.jitterMs.toInt()}ms")
            appendLine()
            appendLine("📦 BUFFER")
            appendLine("   Ring Buffer: ${m.ringBufferLevelPercent}% (${m.ringBufferLevelMs.toInt()}ms)")
            appendLine("   Jitter Buffer: ${m.jitterBufferLevelMs.toInt()}ms / ${m.jitterBufferTargetMs}ms")
            appendLine("   State: ${m.jitterBufferState}")
            appendLine()
            appendLine("📡 PACKETS")
            appendLine("   Received: ${m.packetsReceived}")
            appendLine("   Lost: ${m.packetsLost} (${m.packetLossRate}%)")
            appendLine()
            appendLine("🔧 PLC")
            appendLine("   Activations: ${m.plcActivations}")
            appendLine("   Rate: ${m.plcRate}%")
            appendLine()
            appendLine("🎵 PLAYBACK")
            appendLine("   State: ${m.playbackState}")
            appendLine("   Bytes: ${m.totalBytesPlayed / 1024}KB")
            appendLine("   Underruns: ${m.playbackUnderruns}")
            appendLine("   Avg Loop: ${m.avgLoopTimeUs}μs")
            appendLine("═══════════════════════════════════════")
        }
    }
}
