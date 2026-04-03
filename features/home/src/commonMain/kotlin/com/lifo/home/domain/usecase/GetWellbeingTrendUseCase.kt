package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.TrendDirection
import com.lifo.home.domain.model.WellbeingTrend
import com.lifo.util.model.WellbeingSnapshot
import kotlin.math.round

private const val MIN_SNAPSHOTS = 2

/**
 * Computes a holistic wellbeing trend from SDT-based weekly snapshots.
 *
 * Scores each [WellbeingSnapshot] using the same weighted formula as
 * [WellbeingSnapshot.calculateOverallScore()], then identifies trends,
 * dominant strengths, and areas to improve using Italian dimension names.
 */
class GetWellbeingTrendUseCase {

    operator fun invoke(snapshots: List<WellbeingSnapshot>): WellbeingTrend? {
        if (snapshots.size < MIN_SNAPSHOTS) return null

        val sorted = snapshots.sortedBy { it.timestampMillis }
        val scores = sorted.map { it.overallScore() }
        val current = scores.last()
        val trend = computeTrend(scores)
        val latest = sorted.last()
        val (dominant, weakest) = latest.dominantAndWeakest()

        return WellbeingTrend(
            recentScores = scores,
            currentScore = current,
            trend = trend,
            dominantStrength = dominant,
            areaToImprove = weakest,
            narrative = buildNarrative(current, trend, dominant, weakest)
        )
    }

    // ── Score ─────────────────────────────────────────────────────────────────

    private fun WellbeingSnapshot.overallScore(): Float = (
        lifeSatisfaction * 1.5f +
        workSatisfaction * 1.0f +
        relationshipsQuality * 1.2f +
        mindfulnessScore * 1.0f +
        purposeMeaning * 1.2f +
        gratitude * 1.0f +
        autonomy * 1.3f +
        competence * 1.3f +
        relatedness * 1.3f +
        (10 - loneliness) * 1.2f
    ) / 11.8f

    // ── Dimension names ───────────────────────────────────────────────────────

    private fun WellbeingSnapshot.dominantAndWeakest(): Pair<String, String> {
        val dims = linkedMapOf(
            "Soddisfazione di vita" to lifeSatisfaction.toFloat(),
            "Soddisfazione lavorativa" to workSatisfaction.toFloat(),
            "Qualità delle relazioni" to relationshipsQuality.toFloat(),
            "Consapevolezza" to mindfulnessScore.toFloat(),
            "Senso di scopo" to purposeMeaning.toFloat(),
            "Gratitudine" to gratitude.toFloat(),
            "Autonomia" to autonomy.toFloat(),
            "Competenza" to competence.toFloat(),
            "Connessione sociale" to relatedness.toFloat(),
            "Riduzione dell'isolamento" to (10 - loneliness).toFloat()
        )
        val dominant = dims.maxByOrNull { it.value }?.key ?: "Benessere globale"
        val weakest = dims.minByOrNull { it.value }?.key ?: "Connessione sociale"
        return Pair(dominant, weakest)
    }

    // ── Trend ─────────────────────────────────────────────────────────────────

    private fun computeTrend(scores: List<Float>): TrendDirection {
        if (scores.size < 2) return TrendDirection.STABLE
        val half = maxOf(1, scores.size / 2)
        val firstHalfAvg = scores.take(half).average()
        val secondHalfAvg = scores.drop(half).average()
        val delta = secondHalfAvg - firstHalfAvg
        return when {
            delta > 0.5 -> TrendDirection.UP
            delta < -0.5 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
    }

    // ── Narrative ─────────────────────────────────────────────────────────────

    private fun buildNarrative(
        score: Float,
        trend: TrendDirection,
        dominant: String,
        weakest: String
    ): String {
        val scoreStr = (round(score * 10) / 10.0).toString().take(3)
        return when (trend) {
            TrendDirection.UP ->
                "Il tuo benessere globale è in crescita ($scoreStr/10). Punto di forza: $dominant."
            TrendDirection.DOWN ->
                "Alcune aree sono in calo ($scoreStr/10). Dedica attenzione a: $weakest. Forza: $dominant."
            TrendDirection.STABLE ->
                "Benessere stabile a $scoreStr/10. Forza: $dominant. Area di crescita: $weakest."
        }
    }
}
