package com.lifo.chat.audio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic utility for troubleshooting TTS performance issues
 * Helps identify:
 * - Network latency issues
 * - Buffer underruns
 * - Audio system problems
 * - Text processing bottlenecks
 */
@Singleton
class VoiceSystemDiagnostics @Inject constructor(
    private val context: Context,
    private val voiceSystem: GeminiNativeVoiceSystem
) {
    companion object {
        private const val TAG = "VoiceDiagnostics"
    }

    data class DiagnosticReport(
        val timestamp: Long = System.currentTimeMillis(),
        val systemChecks: SystemChecks,
        val performanceMetrics: PerformanceMetrics,
        val networkMetrics: NetworkMetrics,
        val recommendations: List<String>
    )

    data class SystemChecks(
        val audioSystemHealthy: Boolean,
        val deviceVolume: Int,
        val maxVolume: Int,
        val isMuted: Boolean,
        val hasAudioFocus: Boolean,
        val availableMemoryMB: Long,
        val cpuUsagePercent: Float,
        val isLowPowerMode: Boolean
    )

    data class PerformanceMetrics(
        val averageLatencyMs: Long,
        val maxLatencyMs: Long,
        val minLatencyMs: Long,
        val droppedFrames: Long,
        val bufferUnderruns: Int,
        val successRate: Float
    )

    data class NetworkMetrics(
        val averageResponseTimeMs: Long,
        val connectionFailures: Int,
        val timeouts: Int,
        val bandwidth: String
    )

    private val diagnosticScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val metricsCollector = MetricsCollector()

    /**
     * Run complete diagnostic check
     */
    suspend fun runFullDiagnostics(): DiagnosticReport = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔍 Starting full voice system diagnostics...")

        val systemChecks = performSystemChecks()
        val performanceMetrics = metricsCollector.getPerformanceMetrics()
        val networkMetrics = metricsCollector.getNetworkMetrics()
        val recommendations = generateRecommendations(systemChecks, performanceMetrics, networkMetrics)

        val report = DiagnosticReport(
            systemChecks = systemChecks,
            performanceMetrics = performanceMetrics,
            networkMetrics = networkMetrics,
            recommendations = recommendations
        )

        logDiagnosticReport(report)

        return@withContext report
    }

    /**
     * Test voice system with sample text
     */
    suspend fun runVoiceTest(testText: String = "Ciao, questo è un test del sistema vocale. Sto verificando che tutto funzioni correttamente."): TestResult {
        Log.d(TAG, "🧪 Running voice test...")

        val testId = "test_${System.currentTimeMillis()}"
        val startTime = System.currentTimeMillis()

        return try {
            // Monitor voice state changes
            val stateChanges = mutableListOf<String>()
            val stateJob = diagnosticScope.launch {
                voiceSystem.voiceState.collect { state ->
                    stateChanges.add("${System.currentTimeMillis() - startTime}ms: Speaking=${state.isSpeaking}, Progress=${state.streamProgress}")
                }
            }

            // Speak test text
            voiceSystem.speakWithEmotion(
                text = testText,
                emotion = GeminiNativeVoiceSystem.Emotion.NEUTRAL,
                messageId = testId
            )

            // Wait for completion with timeout
            val timeout = 30000L // 30 seconds
            val completed = withTimeoutOrNull(timeout) {
                while (voiceSystem.voiceState.value.isSpeaking ||
                    voiceSystem.voiceState.value.streamProgress < 1f) {
                    delay(100)
                }
                true
            } ?: false

            stateJob.cancel()

            val duration = System.currentTimeMillis() - startTime

            TestResult(
                success = completed,
                durationMs = duration,
                stateChanges = stateChanges,
                issues = if (!completed) listOf("Test timed out after ${timeout}ms") else emptyList()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Voice test failed", e)
            TestResult(
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                stateChanges = emptyList(),
                issues = listOf("Exception: ${e.message}")
            )
        }
    }

    data class TestResult(
        val success: Boolean,
        val durationMs: Long,
        val stateChanges: List<String>,
        val issues: List<String>
    )

    private suspend fun performSystemChecks(): SystemChecks {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val runtime = Runtime.getRuntime()
        val availableMemory = runtime.freeMemory() / (1024 * 1024)

        return SystemChecks(
            audioSystemHealthy = checkAudioSystemHealth(),
            deviceVolume = currentVolume,
            maxVolume = maxVolume,
            isMuted = currentVolume == 0,
            hasAudioFocus = true, // Simplified - check actual focus in production
            availableMemoryMB = availableMemory,
            cpuUsagePercent = estimateCpuUsage(),
            isLowPowerMode = checkLowPowerMode()
        )
    }

    private fun checkAudioSystemHealth(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode != AudioManager.MODE_IN_CALL &&
                    audioManager.isMusicActive.not() // No other music playing
        } catch (e: Exception) {
            false
        }
    }

    private fun estimateCpuUsage(): Float {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            val toks = load.split(" +".toRegex()).toTypedArray()

            val idle1 = toks[4].toLong()
            val cpu1 = toks.slice(1..7).map { it.toLong() }.sum()

            Thread.sleep(360)

            reader.seek(0)
            val load2 = reader.readLine()
            reader.close()
            val toks2 = load2.split(" +".toRegex()).toTypedArray()

            val idle2 = toks2[4].toLong()
            val cpu2 = toks2.slice(1..7).map { it.toLong() }.sum()

            val idleDelta = idle2 - idle1
            val cpuDelta = cpu2 - cpu1

            ((cpuDelta - idleDelta).toFloat() / cpuDelta.toFloat() * 100.0f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.e("VoiceDiagnostics", "Errore calcolo CPU: ${e.message}")
            -1f
        }
    }


    private fun checkLowPowerMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }

    private fun generateRecommendations(
        systemChecks: SystemChecks,
        performanceMetrics: PerformanceMetrics,
        networkMetrics: NetworkMetrics
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // System recommendations
        if (systemChecks.isMuted || systemChecks.deviceVolume == 0) {
            recommendations.add("🔇 Il dispositivo è silenziato. Aumenta il volume per sentire l'audio.")
        }

        if (systemChecks.deviceVolume < systemChecks.maxVolume * 0.3) {
            recommendations.add("🔊 Il volume è basso (${systemChecks.deviceVolume}/${systemChecks.maxVolume}). Considera di aumentarlo.")
        }

        if (systemChecks.availableMemoryMB < 100) {
            recommendations.add("💾 Memoria disponibile bassa (${systemChecks.availableMemoryMB}MB). Chiudi altre app.")
        }

        if (systemChecks.isLowPowerMode) {
            recommendations.add("🔋 Modalità risparmio energetico attiva. Potrebbe influire sulle prestazioni.")
        }

        // Performance recommendations
        if (performanceMetrics.bufferUnderruns > 5) {
            recommendations.add("⚠️ Rilevati ${performanceMetrics.bufferUnderruns} buffer underrun. Possibili interruzioni audio.")
        }

        if (performanceMetrics.averageLatencyMs > 500) {
            recommendations.add("⏱️ Latenza elevata (${performanceMetrics.averageLatencyMs}ms). Verifica la connessione.")
        }

        if (performanceMetrics.successRate < 0.9f) {
            recommendations.add("📉 Tasso di successo basso (${(performanceMetrics.successRate * 100).toInt()}%).")
        }

        // Network recommendations
        if (networkMetrics.connectionFailures > 0) {
            recommendations.add("🌐 ${networkMetrics.connectionFailures} errori di connessione. Verifica la rete.")
        }

        if (networkMetrics.averageResponseTimeMs > 1000) {
            recommendations.add("🐌 Tempo di risposta lento (${networkMetrics.averageResponseTimeMs}ms).")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("✅ Il sistema vocale sembra funzionare correttamente!")
        }

        return recommendations
    }

    private fun logDiagnosticReport(report: DiagnosticReport) {
        Log.d(TAG, """
            
            ===== VOICE SYSTEM DIAGNOSTIC REPORT =====
            Timestamp: ${report.timestamp}
            
            SYSTEM CHECKS:
            - Audio System: ${if (report.systemChecks.audioSystemHealthy) "✅" else "❌"}
            - Volume: ${report.systemChecks.deviceVolume}/${report.systemChecks.maxVolume} ${if (report.systemChecks.isMuted) "(MUTED)" else ""}
            - Memory: ${report.systemChecks.availableMemoryMB}MB available
            - CPU: ${report.systemChecks.cpuUsagePercent}%
            - Power Save: ${if (report.systemChecks.isLowPowerMode) "ON" else "OFF"}
            
            PERFORMANCE:
            - Avg Latency: ${report.performanceMetrics.averageLatencyMs}ms
            - Latency Range: ${report.performanceMetrics.minLatencyMs}-${report.performanceMetrics.maxLatencyMs}ms
            - Dropped Frames: ${report.performanceMetrics.droppedFrames}
            - Buffer Underruns: ${report.performanceMetrics.bufferUnderruns}
            - Success Rate: ${(report.performanceMetrics.successRate * 100).toInt()}%
            
            NETWORK:
            - Avg Response Time: ${report.networkMetrics.averageResponseTimeMs}ms
            - Connection Failures: ${report.networkMetrics.connectionFailures}
            - Timeouts: ${report.networkMetrics.timeouts}
            
            RECOMMENDATIONS:
            ${report.recommendations.joinToString("\n")}
            
            ==========================================
            
        """.trimIndent())
    }

    /**
     * Metrics collector for ongoing performance tracking
     */
    inner class MetricsCollector {
        private val latencies = mutableListOf<Long>()
        private val responseTime = mutableListOf<Long>()
        private var droppedFrames = 0L
        private var bufferUnderruns = 0
        private var connectionFailures = 0
        private var timeouts = 0
        private var successfulPlays = 0
        private var totalPlays = 0

        fun recordLatency(latency: Long) {
            latencies.add(latency)
            if (latencies.size > 100) latencies.removeAt(0)
        }

        fun recordDroppedFrames(count: Long) {
            droppedFrames += count
        }

        fun recordBufferUnderrun() {
            bufferUnderruns++
        }

        fun recordNetworkResponse(time: Long) {
            responseTime.add(time)
            if (responseTime.size > 50) responseTime.removeAt(0)
        }

        fun recordConnectionFailure() {
            connectionFailures++
        }

        fun recordTimeout() {
            timeouts++
        }

        fun recordPlayResult(success: Boolean) {
            totalPlays++
            if (success) successfulPlays++
        }

        fun getPerformanceMetrics(): PerformanceMetrics {
            return PerformanceMetrics(
                averageLatencyMs = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0,
                maxLatencyMs = latencies.maxOrNull() ?: 0,
                minLatencyMs = latencies.minOrNull() ?: 0,
                droppedFrames = droppedFrames,
                bufferUnderruns = bufferUnderruns,
                successRate = if (totalPlays > 0) successfulPlays.toFloat() / totalPlays else 1f
            )
        }

        fun getNetworkMetrics(): NetworkMetrics {
            return NetworkMetrics(
                averageResponseTimeMs = responseTime.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0,
                connectionFailures = connectionFailures,
                timeouts = timeouts,
                bandwidth = estimateBandwidth()
            )
        }

        private fun estimateBandwidth(): String {
            // Simplified bandwidth estimation based on response times
            val avgResponse = responseTime.takeIf { it.isNotEmpty() }?.average() ?: 0.0
            return when {
                avgResponse < 100 -> "Excellent"
                avgResponse < 300 -> "Good"
                avgResponse < 1000 -> "Fair"
                else -> "Poor"
            }
        }

        fun reset() {
            latencies.clear()
            responseTime.clear()
            droppedFrames = 0
            bufferUnderruns = 0
            connectionFailures = 0
            timeouts = 0
            successfulPlays = 0
            totalPlays = 0
        }
    }

    /**
     * Real-time monitoring for debugging
     */
    fun startRealTimeMonitoring(callback: (String) -> Unit) {
        diagnosticScope.launch {
            voiceSystem.voiceState.collect { state ->
                val status = buildString {
                    append("🎙️ ")
                    append(if (state.isSpeaking) "SPEAKING" else "IDLE")
                    append(" | Progress: ${(state.streamProgress * 100).toInt()}%")
                    append(" | Latency: ${state.latencyMs}ms")
                    state.error?.let { append(" | ERROR: $it") }
                }
                callback(status)
            }
        }
    }

    fun cleanup() {
        diagnosticScope.cancel()
    }
}
