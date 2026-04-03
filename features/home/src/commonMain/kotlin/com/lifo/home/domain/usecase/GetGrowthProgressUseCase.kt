package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.GrowthProgress
import com.lifo.util.model.Block
import com.lifo.util.model.GratitudeEntry
import com.lifo.util.model.Habit
import com.lifo.util.model.HabitCompletion
import com.lifo.util.model.IkigaiExploration
import com.lifo.util.model.RecurringThought
import com.lifo.util.model.ThoughtReframe
import com.lifo.util.model.ValuesDiscovery
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Aggregates progress across all self-growth dimensions into Italian-language
 * strength statements and actionable suggestions.
 *
 * Input data is pre-collected by [com.lifo.home.domain.aggregator.WellbeingAggregator].
 */
class GetGrowthProgressUseCase {

    operator fun invoke(
        habits: List<Habit>,
        habitCompletionsLast7d: List<HabitCompletion>,
        gratitudeEntries: List<GratitudeEntry>,
        reframes: List<ThoughtReframe>,
        activeBlocks: List<Block>,
        resolvedBlocks: List<Block>,
        ikigai: IkigaiExploration?,
        values: ValuesDiscovery?,
        recurringThoughts: List<RecurringThought>
    ): GrowthProgress {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val last7DayKeys = (0 until 7).map { today.minus(it, DateTimeUnit.DAY).toString() }.toSet()

        // Habit completion rate for the last 7 days
        val maxPossible = habits.size * 7
        val completed = habitCompletionsLast7d.count { it.dayKey in last7DayKeys }
        val completionRate = if (maxPossible > 0) completed.toFloat() / maxPossible else 0f

        // Gratitude days in last 7
        val gratitudeDays7d = gratitudeEntries.count { it.dayKey in last7DayKeys }

        // Ikigai progress (0-1, fraction of 4 circles with at least one item)
        val ikigaiProgress = ikigai?.let { exp ->
            listOf(
                exp.passionItems.isNotEmpty(),
                exp.talentItems.isNotEmpty(),
                exp.missionItems.isNotEmpty(),
                exp.professionItems.isNotEmpty()
            ).count { it } / 4f
        } ?: 0f

        val valuesCount = values?.confirmedValues?.size ?: 0
        val tamedThoughts = recurringThoughts.count { it.reframeId != null }

        return GrowthProgress(
            habitCompletionRate7d = completionRate,
            gratitudeDays7d = gratitudeDays7d,
            reframesCompleted = reframes.size,
            blocksActive = activeBlocks.size,
            blocksResolved = resolvedBlocks.size,
            ikigaiProgress = ikigaiProgress,
            valuesCount = valuesCount,
            recurringThoughtsTamed = tamedThoughts,
            strengths = buildStrengths(completionRate, gratitudeDays7d, reframes.size,
                resolvedBlocks.size, ikigaiProgress, valuesCount, tamedThoughts),
            suggestions = buildSuggestions(completionRate, gratitudeDays7d, activeBlocks.size,
                ikigaiProgress, valuesCount, reframes.size)
        )
    }

    private fun buildStrengths(
        completionRate: Float,
        gratitudeDays: Int,
        reframes: Int,
        resolvedBlocks: Int,
        ikigaiProgress: Float,
        valuesCount: Int,
        tamedThoughts: Int
    ): List<String> = buildList {
        if (completionRate >= 0.7f)
            add("Costanza nelle abitudini — ${(completionRate * 100).toInt()}% di completamento settimanale")
        if (gratitudeDays >= 5)
            add("Pratica della gratitudine radicata — $gratitudeDays/7 giorni questa settimana")
        if (reframes >= 2)
            add("Lavori attivamente sui tuoi pensieri — $reframes reframe completati")
        if (resolvedBlocks >= 1)
            add("Hai superato ${resolvedBlocks} blocco${if (resolvedBlocks > 1) "chi" else ""} mentale${if (resolvedBlocks > 1) "i" else ""}")
        if (ikigaiProgress >= 0.75f)
            add("Ikigai esplorato in profondità — ${(ikigaiProgress * 100).toInt()}% completato")
        if (valuesCount >= 3)
            add("I tuoi valori sono chiari — $valuesCount valori confermati")
        if (tamedThoughts >= 1)
            add("Hai trasformato $tamedThoughts pensiero${if (tamedThoughts > 1) "i" else ""} limitante${if (tamedThoughts > 1) "i" else ""}")
    }

    private fun buildSuggestions(
        completionRate: Float,
        gratitudeDays: Int,
        activeBlocks: Int,
        ikigaiProgress: Float,
        valuesCount: Int,
        reframes: Int
    ): List<String> = buildList {
        if (completionRate < 0.5f && completionRate >= 0f)
            add("Riduci le abitudini a 1-2 fondamentali — la consistenza batte la quantità")
        if (gratitudeDays < 3)
            add("Prova la gratitudine anche nei giorni difficili — sono quelli che contano di più")
        if (activeBlocks > 0)
            add("${activeBlocks} blocco${if (activeBlocks > 1) "chi" else ""} mentale${if (activeBlocks > 1) "i" else ""} attiv${if (activeBlocks > 1) "i" else "o"} — affronta ${if (activeBlocks > 1) "il più urgente" else "lo"} questa settimana")
        if (ikigaiProgress < 0.5f)
            add("Completa l'Ikigai per chiarire il tuo scopo — ${((1f - ikigaiProgress) * 4).toInt()} cerch${if (((1f - ikigaiProgress) * 4).toInt() > 1) "i" else "io"} ancora da esplorare")
        if (valuesCount == 0)
            add("Scopri i tuoi valori con 'La Tua Bussola' — la chiarezza riduce l'ansia decisionale")
        if (reframes == 0)
            add("Prova il Laboratorio dei Pensieri: trasforma un pensiero limitante in qualcosa di utile")
    }
}
