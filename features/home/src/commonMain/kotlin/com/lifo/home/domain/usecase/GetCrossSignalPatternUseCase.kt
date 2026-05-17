package com.lifo.home.domain.usecase

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.BioSignal
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.preview.PreviewConfidence
import com.lifo.util.repository.BioSignalRepository
import com.lifo.util.repository.MeditationRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * Cross-signal pattern detection — Phase 5.4 (Card 4).
 *
 * Folds 6 weeks of meditation history × 6 weeks of HRV samples into a
 * weekly-paired aggregate, and decides whether a meaningful correlation exists
 * (≥10% HRV lift on weeks with ≥3 meditation sessions). Returns `null` if:
 *
 * - The user has <6 calendar weeks of bio + meditation data
 * - The "high-meditation" and "low-meditation" buckets are too sparse to
 *   compare honestly (<2 weeks each)
 * - The detected lift is below the [MEANINGFUL_LIFT_PERCENT] floor — claiming
 *   a pattern that's basically noise would violate Decision 2 + dogma #4
 *
 * Per `memory/feedback_biosignal_plan_as_compass.md`: we frame this as
 * observation ("the link has held for six weeks running"), never causation
 * ("meditation IMPROVES HRV"). The user reads meaning, we just surface signal.
 */
class GetCrossSignalPatternUseCase(
    private val authProvider: AuthProvider,
    private val meditationRepository: MeditationRepository,
    private val bioRepository: BioSignalRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(): CrossSignalPattern? {
        val userId = authProvider.currentUserId ?: return previewPatternOrNull()

        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(tz).date
        val windowStart = today.minusDays(WINDOW_WEEKS * 7)
        val windowStartInstant = now.minus((WINDOW_WEEKS * 7).days)

        // ── Source A: meditation sessions, grouped by ISO week of last 6 weeks ──
        val sessions = meditationRepository
            .getRecentSessions(userId, limit = 200)
            .first()
            .filter { it.timestampMillis >= windowStartInstant.toEpochMilliseconds() }

        // ── Source B: HRV samples in same window ────────────────────────────
        val hrvSamples = bioRepository
            .getRawSamples(BioSignalDataType.HRV, windowStartInstant, now)
            .filterIsInstance<BioSignal.HrvSample>()

        if (hrvSamples.isEmpty()) return previewPatternOrNull()

        // ── Bucket both by week index 0..(WINDOW_WEEKS-1) ───────────────────
        val sessionsByWeek = IntArray(WINDOW_WEEKS)
        sessions.forEach { session ->
            val date = kotlinx.datetime.Instant
                .fromEpochMilliseconds(session.timestampMillis)
                .toLocalDateTime(tz).date
            val weekIdx = weekIndex(date, windowStart) ?: return@forEach
            sessionsByWeek[weekIdx] = sessionsByWeek[weekIdx] + 1
        }

        val hrvByWeek = Array(WINDOW_WEEKS) { mutableListOf<Double>() }
        hrvSamples.forEach { sample ->
            val date = sample.timestamp.toLocalDateTime(tz).date
            val weekIdx = weekIndex(date, windowStart) ?: return@forEach
            hrvByWeek[weekIdx].add(sample.rmssdMillis)
        }

        // ── Compute weekly HRV averages; drop weeks with no HRV data ────────
        val weeklyHrvAvg: List<Double?> = hrvByWeek.map { week ->
            if (week.isEmpty()) null else week.average()
        }

        val populatedWeeks = weeklyHrvAvg.count { it != null }
        if (populatedWeeks < MIN_POPULATED_WEEKS) return previewPatternOrNull()

        // ── Phase 6.4 — personalize "high-meditation" threshold ─────────────
        // Old: hardcoded ≥3 sessions/week. New: > the user's own weekly median.
        // Floor at HIGH_THRESHOLD_MIN so a user who hasn't started meditating
        // (median 0) still gets a meaningful split as they ramp up.
        val sortedWeekly = sessionsByWeek.toList().sorted()
        val medianSessions = if (sortedWeekly.isEmpty()) 0.0
        else if (sortedWeekly.size % 2 == 0)
            (sortedWeekly[sortedWeekly.size / 2 - 1] + sortedWeekly[sortedWeekly.size / 2]) / 2.0
        else sortedWeekly[sortedWeekly.size / 2].toDouble()
        val highThreshold = maxOf(HIGH_THRESHOLD_MIN, kotlin.math.ceil(medianSessions).toInt() + 1)

        // ── Compare "high-meditation" weeks vs the rest ─────────────────────
        val highIndices = (0 until WINDOW_WEEKS).filter {
            sessionsByWeek[it] >= highThreshold && weeklyHrvAvg[it] != null
        }
        val lowIndices = (0 until WINDOW_WEEKS).filter {
            sessionsByWeek[it] < highThreshold && weeklyHrvAvg[it] != null
        }
        if (highIndices.size < MIN_BUCKET_WEEKS || lowIndices.size < MIN_BUCKET_WEEKS) return previewPatternOrNull()

        val highAvg = highIndices.mapNotNull { weeklyHrvAvg[it] }.average()
        val lowAvg = lowIndices.mapNotNull { weeklyHrvAvg[it] }.average()
        if (lowAvg <= 0.0) return previewPatternOrNull()
        val liftPercent = ((highAvg - lowAvg) / lowAvg * 100.0).toInt()
        if (liftPercent < MEANINGFUL_LIFT_PERCENT) return previewPatternOrNull()

        val overallHrvAvg = weeklyHrvAvg.filterNotNull().average()
        val sessionAvg = sessionsByWeek.average()

        // ── Build bar pairs (6 weeks of meditation count + HRV avg) ─────────
        val medBars = sessionsByWeek.map {
            BarPoint(value = it.toFloat(), isHi = it >= highThreshold)
        }
        val hrvBars = weeklyHrvAvg.mapIndexed { idx, hrv ->
            BarPoint(
                value = (hrv ?: 0.0).toFloat(),
                isHi = (hrv ?: 0.0) >= (overallHrvAvg) && sessionsByWeek[idx] >= highThreshold,
            )
        }

        // ── Confidence floor + primary source across rendered HRV samples ───
        val confidence = hrvSamples
            .map { it.confidence.level }
            .minByOrNull { rank(it) }
            ?: ConfidenceLevel.LOW
        val primarySource = hrvSamples
            .groupingBy { it.source }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: hrvSamples.first().source

        return CrossSignalPattern(
            windowWeeks = WINDOW_WEEKS,
            sessionsPerWeekAvg = sessionAvg,
            hrvAvgMillis = overallHrvAvg,
            liftPercent = liftPercent,
            highMeditationThreshold = highThreshold,
            medBars = medBars,
            hrvBars = hrvBars,
            confidence = confidence,
            source = primarySource,
        )
    }

    private fun weekIndex(date: LocalDate, windowStart: LocalDate): Int? {
        val days = windowStart.daysUntil(date)
        if (days < 0) return null
        val idx = days / 7
        return if (idx in 0 until WINDOW_WEEKS) idx else null
    }

    private fun rank(level: ConfidenceLevel): Int = when (level) {
        ConfidenceLevel.LOW -> 0
        ConfidenceLevel.MEDIUM -> 1
        ConfidenceLevel.HIGH -> 2
    }

    /**
     * Phase 9.2.4 — preview fallback so fresh-install users see the PRO
     * cross-signal pattern card. Mirrors the mockup's "Meditation × HRV"
     * narrative shape with illustrative values + LOW confidence.
     */
    private fun previewPatternOrNull(): CrossSignalPattern? {
        if (!preview.enabled) return null
        return CrossSignalPattern(
            windowWeeks = 6,
            sessionsPerWeekAvg = 3.7,
            hrvAvgMillis = 41.0,
            liftPercent = 15,
            highMeditationThreshold = 3,
            medBars = listOf(
                BarPoint(2f, false), BarPoint(4f, true), BarPoint(2f, false),
                BarPoint(5f, true), BarPoint(4f, true), BarPoint(5f, true),
            ),
            hrvBars = listOf(
                BarPoint(36f, false), BarPoint(45f, true), BarPoint(38f, false),
                BarPoint(46f, true), BarPoint(43f, true), BarPoint(44f, true),
            ),
            confidence = PreviewConfidence,
            source = preview.previewSource,
        )
    }

    companion object {
        private const val WINDOW_WEEKS = 6
        private const val MIN_POPULATED_WEEKS = 4   // need ≥4 of 6 weeks with HRV data to compare honestly
        private const val MIN_BUCKET_WEEKS = 2      // need ≥2 high-med + ≥2 low-med weeks
        /**
         * Phase 6.4 floor for the personalized HIGH threshold. Even if user's
         * weekly median is 0 (just starting out), "high" still means at least
         * 2 sessions — below this the high/low buckets degenerate into 1 vs many.
         */
        private const val HIGH_THRESHOLD_MIN = 2
        private const val MEANINGFUL_LIFT_PERCENT = 10
    }
}

/**
 * Detected cross-signal pattern over the trailing 6 weeks.
 *
 * Render contract: only show when `liftPercent >= MEANINGFUL_LIFT_PERCENT`
 * AND the bucket sizes are healthy enough to compare honestly. Otherwise the
 * use case returns `null` and the card stays hidden.
 */
data class CrossSignalPattern(
    val windowWeeks: Int,
    val sessionsPerWeekAvg: Double,
    val hrvAvgMillis: Double,
    val liftPercent: Int,
    val highMeditationThreshold: Int,
    val medBars: List<BarPoint>,
    val hrvBars: List<BarPoint>,
    val confidence: ConfidenceLevel,
    val source: BioSignalSource,
)

data class BarPoint(val value: Float, val isHi: Boolean)

private fun LocalDate.minusDays(days: Int): LocalDate =
    LocalDate.fromEpochDays(this.toEpochDays() - days)
