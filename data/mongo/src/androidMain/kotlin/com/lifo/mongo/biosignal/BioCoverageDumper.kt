package com.lifo.mongo.biosignal

import android.util.Log
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.repository.HealthDataProvider
import com.lifo.util.repository.ProviderStatus
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Debug-only coverage report — answers the question:
 *
 *   "For my actual device + wearable pair, how many samples per data type does
 *    Health Connect actually contain over the last N hours?"
 *
 * This is the ground-truth check before scoping features that depend on a
 * specific data type. The Mi Band 10 + S24 worst-case target (per
 * `memory/user_hardware.md`) is the primary day-1 validation: if the pipeline
 * works here, it works everywhere.
 *
 * **Not intended to ship in production**: gate calls behind `BuildConfig.DEBUG`
 * or a Settings → Developer toggle. Output goes to Logcat under tag
 * `BioCoverageDumper`.
 *
 * **Usage** (from a debug Activity / Settings screen, on a CoroutineScope):
 * ```kotlin
 * val report = BioCoverageDumper.run(healthDataProvider, windowHours = 48)
 * Log.i("BioCoverage", report.summary())
 * ```
 */
object BioCoverageDumper {

    private const val TAG = "BioCoverageDumper"

    data class CoverageRow(
        val type: BioSignalDataType,
        val sampleCount: Int,
        val uniqueSourceCount: Int,
        val firstTimestampMillis: Long?,
        val lastTimestampMillis: Long?,
    )

    data class Report(
        val providerStatus: ProviderStatus,
        val windowHours: Int,
        val rows: List<CoverageRow>,
    ) {
        fun summary(): String = buildString {
            appendLine("=== Bio-Signal Coverage Report ===")
            appendLine("Provider status: $providerStatus")
            appendLine("Window: last $windowHours hours")
            appendLine()
            if (rows.isEmpty()) {
                appendLine("(no rows — provider not ready or all types empty)")
            } else {
                appendLine("Type                | Samples | Sources | Span")
                appendLine("--------------------+---------+---------+----------------------")
                rows.forEach { row ->
                    val span = if (row.firstTimestampMillis != null && row.lastTimestampMillis != null) {
                        val hours = (row.lastTimestampMillis - row.firstTimestampMillis) / 1000.0 / 3600.0
                        "%.1fh".format(hours)
                    } else "—"
                    val name = row.type.name.padEnd(19)
                    val s = row.sampleCount.toString().padStart(7)
                    val src = row.uniqueSourceCount.toString().padStart(7)
                    appendLine("$name | $s | $src | $span")
                }
            }
        }
    }

    /**
     * Run the coverage probe.
     *
     * @param windowHours how far back to look (default: 48h — enough to catch
     *   one full sleep cycle + multiple HR/HRV samples for typical wearables).
     */
    suspend fun run(
        provider: HealthDataProvider,
        windowHours: Int = 48,
    ): Report {
        val now = Clock.System.now()
        val from = now.minus(windowHours.hours)

        val status = provider.checkAvailability()
        if (status !is ProviderStatus.Ready) {
            Log.w(TAG, "Provider not ready ($status) — no coverage data")
            return Report(status, windowHours, emptyList())
        }

        val rows = mutableListOf<CoverageRow>()

        // Each read may legitimately return empty for an unsupported data type
        // on the user's wearable — that's the point of this report.
        runCatching {
            val samples = provider.readHeartRate(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.HEART_RATE,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.timestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "HEART_RATE read failed: ${it.message}") }

        runCatching {
            val samples = provider.readHrv(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.HRV,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.timestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "HRV read failed: ${it.message}") }

        runCatching {
            val samples = provider.readSleepSessions(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.SLEEP,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.endTimestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "SLEEP read failed: ${it.message}") }

        runCatching {
            val samples = provider.readStepCounts(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.STEPS,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.timestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "STEPS read failed: ${it.message}") }

        runCatching {
            val samples = provider.readRestingHeartRate(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.RESTING_HEART_RATE,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.timestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "RESTING_HEART_RATE read failed: ${it.message}") }

        runCatching {
            val samples = provider.readOxygenSaturation(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.OXYGEN_SATURATION,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.timestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "OXYGEN_SATURATION read failed: ${it.message}") }

        runCatching {
            val samples = provider.readActivitySessions(from, now)
            rows += CoverageRow(
                type = BioSignalDataType.ACTIVITY,
                sampleCount = samples.size,
                uniqueSourceCount = samples.map { it.source.deviceName }.distinct().size,
                firstTimestampMillis = samples.minOfOrNull { it.timestamp.toEpochMilliseconds() },
                lastTimestampMillis = samples.maxOfOrNull { it.endTimestamp.toEpochMilliseconds() },
            )
        }.onFailure { Log.w(TAG, "ACTIVITY read failed: ${it.message}") }

        return Report(status, windowHours, rows)
    }
}
