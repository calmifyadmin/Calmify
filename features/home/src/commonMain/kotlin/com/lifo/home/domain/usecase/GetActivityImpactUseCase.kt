package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.ActivityImpact
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.EnergyCheckIn
import com.lifo.util.model.GratitudeEntry
import com.lifo.util.model.MovementLog
import kotlin.math.roundToInt

private const val MIN_SAMPLES = 5

/**
 * Computes the impact of physical activity on self-reported energy levels, and
 * the sentiment lift associated with gratitude practice.
 *
 * Energy impact: pairs [EnergyCheckIn.didMovement] with [EnergyCheckIn.energyLevel].
 * Gratitude lift: compares [DiaryInsight.sentimentPolarity] on days with/without a
 * [GratitudeEntry], matched by dayKey.
 */
class GetActivityImpactUseCase {

    operator fun invoke(
        energyCheckIns: List<EnergyCheckIn>,
        movementLogs: List<MovementLog>,
        gratitudeEntries: List<GratitudeEntry>,
        insights: List<DiaryInsight>
    ): ActivityImpact? {
        if (energyCheckIns.size < MIN_SAMPLES) return null

        val activeDays = energyCheckIns.filter { it.didMovement }
        val restDays = energyCheckIns.filter { !it.didMovement }

        if (activeDays.isEmpty() || restDays.isEmpty()) return null

        val avgEnergyActive = activeDays.map { it.energyLevel.toFloat() }.average().toFloat()
        val avgEnergyRest = restDays.map { it.energyLevel.toFloat() }.average().toFloat()
        val boost = if (avgEnergyRest > 0f) (avgEnergyActive - avgEnergyRest) / avgEnergyRest * 100f else 0f

        // Gratitude → sentiment lift
        val moodByDay = insights
            .groupBy { it.dayKey }
            .mapValues { (_, list) -> list.map { it.sentimentPolarity }.average().toFloat() }

        val gratitudeDayKeys = gratitudeEntries.map { it.dayKey }.toSet()
        val sentimentWithGratitude = moodByDay.filterKeys { it in gratitudeDayKeys }.values
        val sentimentWithout = moodByDay.filterKeys { it !in gratitudeDayKeys }.values

        val gratitudeLift = if (sentimentWithGratitude.isNotEmpty() && sentimentWithout.isNotEmpty()) {
            sentimentWithGratitude.average().toFloat() - sentimentWithout.average().toFloat()
        } else 0f

        return ActivityImpact(
            avgEnergyActiveDay = avgEnergyActive,
            avgEnergyRestDay = avgEnergyRest,
            energyBoostPercent = boost,
            gratitudeSentimentLift = gratitudeLift,
            sampleSize = energyCheckIns.size,
            narrative = buildNarrative(boost, gratitudeLift)
        )
    }

    private fun buildNarrative(boost: Float, gratitudeLift: Float): String = when {
        boost >= 20f ->
            "Nelle giornate in cui ti muovi, la tua energia è del ${boost.roundToInt()}% più alta"
        boost >= 5f ->
            "Il movimento fisico ti dà una spinta energetica di +${boost.roundToInt()}%"
        boost > 0f ->
            "Anche una lieve attività fisica ha un impatto positivo sulla tua energia"
        else ->
            "Registra il movimento ogni giorno per vedere come influenza la tua energia"
    }
}
