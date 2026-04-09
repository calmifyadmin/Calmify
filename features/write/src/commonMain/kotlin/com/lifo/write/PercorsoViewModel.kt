package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.*
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
            val reframesResult = reframeRepository.getRecentReframes(30).firstOrNull()
            val reframes = (reframesResult as? com.lifo.util.model.RequestState.Success)?.data ?: emptyList()
            val gratitudesResult = gratitudeRepository.getEntriesInRange(weekStartMillis, nowMillis).firstOrNull()
            val weekGratitudes = (gratitudesResult as? com.lifo.util.model.RequestState.Success)?.data ?: emptyList()

            val menteItems = listOf(
                PercorsoContract.SectionItem("Meditazione", "${weekMeditations.size} sessioni", minOf(weekMeditations.size / 7f, 1f)),
                PercorsoContract.SectionItem("Reframing", "${reframes.size} fatti", minOf(reframes.size / 10f, 1f)),
                PercorsoContract.SectionItem("Gratitudine", "${weekGratitudes.size}/7", minOf(weekGratitudes.size / 7f, 1f)),
            )

            // CORPO
            val energyResult = energyRepository.getCheckInsInRange(weekStartMillis, nowMillis).firstOrNull()
            val weekEnergy = (energyResult as? com.lifo.util.model.RequestState.Success)?.data ?: emptyList()
            val avgEnergy = if (weekEnergy.isNotEmpty()) weekEnergy.map { it.energyLevel }.average() else 0.0
            val sleepResult = sleepRepository.getRecentLogs(7).firstOrNull()
            val weekSleep = (sleepResult as? com.lifo.util.model.RequestState.Success)?.data ?: emptyList()
            val avgSleep = if (weekSleep.isNotEmpty()) weekSleep.map { it.sleepHours }.average() else 0.0
            val weekMovement = movementRepository.getLogsInRange(userId, weekStartDayKey, todayKey).firstOrNull() ?: emptyList()

            val corpoItems = listOf(
                PercorsoContract.SectionItem("Energia", "%.1f".format(avgEnergy), minOf(avgEnergy.toFloat() / 10f, 1f)),
                PercorsoContract.SectionItem("Sonno", "%.1fh".format(avgSleep), minOf(avgSleep.toFloat() / 8f, 1f)),
                PercorsoContract.SectionItem("Movimento", "${weekMovement.size}/7", minOf(weekMovement.size / 7f, 1f)),
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
                    "Valori",
                    if (values != null && values.confirmedValues.isNotEmpty()) "Confermati" else "Da scoprire",
                    if (values != null && values.confirmedValues.isNotEmpty()) 1f else 0f,
                ),
                PercorsoContract.SectionItem("Connessioni", "${weekConnections.size} atti", minOf(weekConnections.size / 5f, 1f)),
            )

            // ABITUDINI
            val habitsResult = habitRepository.getActiveHabits().firstOrNull()
            val habits = (habitsResult as? com.lifo.util.model.RequestState.Success)?.data ?: emptyList()
            val completionsResult = habitRepository.getCompletionsForDay(todayKey).firstOrNull()
            val todayCompletions = (completionsResult as? com.lifo.util.model.RequestState.Success)?.data ?: emptyList()
            val habitProgress = if (habits.isNotEmpty()) todayCompletions.size.toFloat() / habits.size else 0f

            val abitudiniItems = listOf(
                PercorsoContract.SectionItem("Oggi", "${todayCompletions.size}/${habits.size}", habitProgress),
                PercorsoContract.SectionItem("Totali", "${habits.size} abitudini", minOf(habits.size / 5f, 1f)),
            )

            // Overall
            val allProgress = menteItems.map { it.progress } + corpoItems.map { it.progress } + spiritoItems.map { it.progress } + abitudiniItems.map { it.progress }
            val overall = if (allProgress.isNotEmpty()) allProgress.average().toFloat() else 0f

            updateState {
                copy(
                    mente = PercorsoContract.SectionSummary("Mente", menteItems),
                    corpo = PercorsoContract.SectionSummary("Corpo", corpoItems),
                    spirito = PercorsoContract.SectionSummary("Spirito", spiritoItems),
                    abitudini = PercorsoContract.SectionSummary("Abitudini", abitudiniItems),
                    overallProgress = overall,
                )
            }
        }
    }
}
