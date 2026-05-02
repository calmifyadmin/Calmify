package com.lifo.write

import com.lifo.ui.i18n.Strings
import com.lifo.util.auth.AuthProvider
import com.lifo.util.formatDecimal
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.model.RequestState
import com.lifo.util.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

class PercorsoViewModel(
    private val authProvider: AuthProvider,
    private val meditationRepository: MeditationRepository,
    private val reframeRepository: ReframeRepository,
    private val gratitudeRepository: GratitudeRepository,
    private val energyRepository: EnergyRepository,
    private val sleepRepository: SleepRepository,
    private val movementRepository: MovementRepository,
    private val valuesRepository: ValuesRepository,
    private val habitRepository: HabitRepository,
    private val connectionRepository: ConnectionRepository,
) : MviViewModel<PercorsoContract.Intent, PercorsoContract.State, PercorsoContract.Effect>(
    PercorsoContract.State()
) {

    override fun handleIntent(intent: PercorsoContract.Intent) {
        when (intent) {
            is PercorsoContract.Intent.Load -> loadAll()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadAll() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            try {
                loadAllInternal(userId)
            } catch (e: Exception) {
                println("PercorsoViewModel: loadAll failed: ${e.message}")
            }
        }
    }

    private suspend fun <T> safeList(block: suspend () -> RequestState<List<T>>): List<T> {
        return try {
            when (val result = block()) {
                is RequestState.Success -> result.data
                else -> emptyList()
            }
        } catch (e: Exception) {
            println("PercorsoViewModel: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadAllInternal(userId: String) {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val today = now.toLocalDateTime(tz).date
            val weekStartDate = LocalDate.fromEpochDays(today.toEpochDays() - today.dayOfWeek.ordinal)
            val weekStartMillis = weekStartDate.atStartOfDayIn(tz).toEpochMilliseconds()
            val nowMillis = now.toEpochMilliseconds()
            val weekStartDayKey = weekStartDate.toString()
            val todayKey = today.toString()

            // MENTE
            val meditations = meditationRepository.getRecentSessions(userId, 50).firstOrNull() ?: emptyList()
            val weekMeditations = meditations.filter { it.timestampMillis >= weekStartMillis }
            val reframes = safeList { reframeRepository.getRecentReframes(30).first { it !is RequestState.Loading } }
            val weekGratitudes = safeList { gratitudeRepository.getEntriesInRange(weekStartMillis, nowMillis).first { it !is RequestState.Loading } }

            val menteItems = listOf(
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.meditationName,
                    if (weekMeditations.size == 1) Strings.Percorso.statSessionsOne else Strings.Percorso.statSessionsMany,
                    valueArg = weekMeditations.size,
                    progress = minOf(weekMeditations.size / 7f, 1f),
                ),
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.reframingName,
                    if (reframes.size == 1) Strings.Percorso.statFactsOne else Strings.Percorso.statFactsMany,
                    valueArg = reframes.size,
                    progress = minOf(reframes.size / 10f, 1f),
                ),
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.gratitudeName,
                    Strings.Percorso.statProgressFraction,
                    valueOverride = "${weekGratitudes.size}/7",
                    progress = minOf(weekGratitudes.size / 7f, 1f),
                ),
            )

            // CORPO
            val weekEnergy = safeList { energyRepository.getCheckInsInRange(weekStartMillis, nowMillis).first { it !is RequestState.Loading } }
            val avgEnergy = if (weekEnergy.isNotEmpty()) weekEnergy.map { it.energyLevel }.average() else 0.0
            val weekSleep = safeList { sleepRepository.getRecentLogs(7).first { it !is RequestState.Loading } }
            val avgSleep = if (weekSleep.isNotEmpty()) weekSleep.map { it.sleepHours }.average() else 0.0
            val weekMovement = movementRepository.getLogsInRange(userId, weekStartDayKey, todayKey).firstOrNull() ?: emptyList()

            val corpoItems = listOf(
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.energyName,
                    Strings.Percorso.statProgressFraction,
                    valueOverride = formatDecimal(1, avgEnergy.toFloat()),
                    progress = minOf(avgEnergy.toFloat() / 10f, 1f),
                ),
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.sleepName,
                    Strings.Percorso.statProgressFraction,
                    valueOverride = "${formatDecimal(1, avgSleep.toFloat())}h",
                    progress = minOf(avgSleep.toFloat() / 8f, 1f),
                ),
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.movementName,
                    Strings.Percorso.statProgressFraction,
                    valueOverride = "${weekMovement.size}/7",
                    progress = minOf(weekMovement.size / 7f, 1f),
                ),
            )

            // SPIRITO
            val values = valuesRepository.getDiscovery(userId).firstOrNull()
            val connections = connectionRepository.getEntries(userId).firstOrNull() ?: emptyList()
            val weekConnections = connections.filter { c ->
                val d = try { LocalDate.parse(c.dayKey) } catch (_: Exception) { return@filter false }
                d.toEpochDays() >= weekStartDate.toEpochDays()
            }

            val spiritoItems = listOf(
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.valuesName,
                    if (values != null && values.confirmedValues.isNotEmpty()) Strings.Percorso.statConfirmed else Strings.Percorso.statToDiscover,
                    progress = if (values != null && values.confirmedValues.isNotEmpty()) 1f else 0f,
                ),
                PercorsoContract.SectionItem(
                    Strings.Garden.Activity.connectionsName,
                    if (weekConnections.size == 1) Strings.Percorso.statActsOne else Strings.Percorso.statActsMany,
                    valueArg = weekConnections.size,
                    progress = minOf(weekConnections.size / 5f, 1f),
                ),
            )

            // ABITUDINI
            val habits = safeList { habitRepository.getActiveHabits().first { it !is RequestState.Loading } }
            val todayCompletions = safeList { habitRepository.getCompletionsForDay(todayKey).first { it !is RequestState.Loading } }
            val habitProgress = if (habits.isNotEmpty()) todayCompletions.size.toFloat() / habits.size else 0f

            val abitudiniItems = listOf(
                PercorsoContract.SectionItem(
                    Strings.Percorso.statToday,
                    Strings.Percorso.statProgressFraction,
                    valueOverride = "${todayCompletions.size}/${habits.size}",
                    progress = habitProgress,
                ),
                PercorsoContract.SectionItem(
                    Strings.Percorso.statTotal,
                    if (habits.size == 1) Strings.Percorso.statHabitsOne else Strings.Percorso.statHabitsMany,
                    valueArg = habits.size,
                    progress = minOf(habits.size / 5f, 1f),
                ),
            )

            // Overall
            val allProgress = menteItems.map { it.progress } + corpoItems.map { it.progress } + spiritoItems.map { it.progress } + abitudiniItems.map { it.progress }
            val overall = if (allProgress.isNotEmpty()) allProgress.average().toFloat() else 0f

            updateState {
                copy(
                    mente = PercorsoContract.SectionSummary(Strings.Percorso.pillarMind, menteItems),
                    corpo = PercorsoContract.SectionSummary(Strings.Percorso.pillarBody, corpoItems),
                    spirito = PercorsoContract.SectionSummary(Strings.Percorso.pillarSpirit, spiritoItems),
                    abitudini = PercorsoContract.SectionSummary(Strings.Percorso.pillarHabits, abitudiniItems),
                    overallProgress = overall,
                )
            }
    }
}
