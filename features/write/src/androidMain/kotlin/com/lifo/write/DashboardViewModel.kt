package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.EnergyRepository
import com.lifo.util.repository.MovementRepository
import com.lifo.util.repository.SleepRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val energyRepository: EnergyRepository,
    private val sleepRepository: SleepRepository,
    private val movementRepository: MovementRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<DashboardContract.Intent, DashboardContract.State, DashboardContract.Effect>(
    DashboardContract.State()
) {

    init {
        onIntent(DashboardContract.Intent.Load)
    }

    override fun handleIntent(intent: DashboardContract.Intent) {
        when (intent) {
            is DashboardContract.Intent.Load -> load()
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            // Gather last 7 energy check-ins
            val energyResult = energyRepository.getRecentCheckIns(7).first()
            val energyList = when (energyResult) {
                is RequestState.Success -> energyResult.data
                else -> emptyList()
            }

            // Gather last 7 sleep logs
            val sleepResult = sleepRepository.getRecentLogs(7).first()
            val sleepList = when (sleepResult) {
                is RequestState.Success -> sleepResult.data
                else -> emptyList()
            }

            // Gather last 7 movement logs
            val movementList = movementRepository.getRecentLogs(userId, 7).first()

            // Compute averages
            val avgSleep = if (sleepList.isNotEmpty()) sleepList.map { it.sleepHours }.average().toFloat() else 0f
            val avgEnergy = if (energyList.isNotEmpty()) energyList.map { it.energyLevel }.average().toFloat() else 0f
            val avgWater = if (energyList.isNotEmpty()) energyList.map { it.waterGlasses }.average().toFloat() else 0f
            val moveDays = movementList.size

            // Terrain level based on composite score
            val sleepScore = (avgSleep / 8f).coerceIn(0f, 1f)
            val energyScore = (avgEnergy / 10f).coerceIn(0f, 1f)
            val waterScore = (avgWater / 8f).coerceIn(0f, 1f)
            val moveScore = (moveDays / 7f).coerceIn(0f, 1f)
            val composite = (sleepScore + energyScore + waterScore + moveScore) / 4f

            val terrainLevel = when {
                composite >= 0.75f -> "OTTIMO"
                composite >= 0.5f -> "BUONO"
                composite >= 0.25f -> "DA MIGLIORARE"
                else -> "FRAGILE"
            }

            // Simple AI narrative (local, no API call)
            val narrative = buildNarrative(avgSleep, avgEnergy, avgWater, moveDays)
            val correlation = buildCorrelation(energyList, sleepList, movementList)

            updateState {
                copy(
                    isLoading = false,
                    avgSleepHours = avgSleep,
                    avgEnergy = avgEnergy,
                    avgWaterGlasses = avgWater,
                    movementDays = moveDays,
                    terrainLevel = terrainLevel,
                    aiNarrative = narrative,
                    correlationInsight = correlation,
                )
            }
        }
    }

    private fun buildNarrative(sleep: Float, energy: Float, water: Float, moveDays: Int): String {
        val parts = mutableListOf<String>()

        if (sleep >= 7f) parts.add("Sonno stabile sopra le 7 ore")
        else if (sleep >= 6f) parts.add("Sonno nella media, punta a 7+ ore")
        else parts.add("Sonno insufficiente — il recupero e' la priorita'")

        if (energy >= 7f) parts.add("energia alta")
        else if (energy >= 5f) parts.add("energia nella media")
        else parts.add("energia bassa — il corpo ti sta parlando")

        if (water >= 6f) parts.add("buona idratazione")
        else parts.add("idratazione da migliorare")

        if (moveDays >= 4) parts.add("movimento costante ($moveDays/7 giorni)")
        else if (moveDays >= 2) parts.add("movimento discreto ($moveDays/7 giorni)")
        else parts.add("poco movimento questa settimana")

        return "Questa settimana: ${parts.joinToString(", ")}."
    }

    private fun buildCorrelation(
        energy: List<com.lifo.util.model.EnergyCheckIn>,
        sleep: List<com.lifo.util.model.SleepLog>,
        movement: List<com.lifo.util.model.MovementLog>,
    ): String {
        if (energy.size < 3 || sleep.size < 3) return "Servono piu' dati per individuare correlazioni."

        val goodSleepDays = sleep.filter { it.sleepHours >= 7f }
        val badSleepDays = sleep.filter { it.sleepHours < 6f }

        val goodSleepDayKeys = goodSleepDays.map { it.dayKey }.toSet()
        val badSleepDayKeys = badSleepDays.map { it.dayKey }.toSet()

        val energyOnGoodSleep = energy.filter { it.dayKey in goodSleepDayKeys }.map { it.energyLevel }
        val energyOnBadSleep = energy.filter { it.dayKey in badSleepDayKeys }.map { it.energyLevel }

        if (energyOnGoodSleep.isNotEmpty() && energyOnBadSleep.isNotEmpty()) {
            val avgGood = energyOnGoodSleep.average()
            val avgBad = energyOnBadSleep.average()
            val diff = avgGood - avgBad
            if (diff > 1.0) {
                return "Nei giorni con 7+ ore di sonno, la tua energia e' in media ${formatDecimal(avgGood)}. Con meno di 6 ore, scende a ${formatDecimal(avgBad)}."
            }
        }

        if (movement.isNotEmpty() && energy.isNotEmpty()) {
            val moveDayKeys = movement.map { it.dayKey }.toSet()
            val energyOnMoveDays = energy.filter { it.dayKey in moveDayKeys }.map { it.energyLevel }
            val energyOnRestDays = energy.filter { it.dayKey !in moveDayKeys }.map { it.energyLevel }
            if (energyOnMoveDays.isNotEmpty() && energyOnRestDays.isNotEmpty()) {
                val avgMove = energyOnMoveDays.average()
                val avgRest = energyOnRestDays.average()
                if (avgMove - avgRest > 0.5) {
                    return "Nei giorni in cui ti muovi, la tua energia e' in media ${formatDecimal(avgMove)} vs ${formatDecimal(avgRest)} nei giorni di riposo."
                }
            }
        }

        return "Continua a tracciare — le correlazioni emergono col tempo."
    }

    private fun formatDecimal(value: Double): String {
        val rounded = (value * 10).toInt() / 10.0
        return rounded.toString()
    }
}
