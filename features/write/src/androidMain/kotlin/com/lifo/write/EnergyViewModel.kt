package com.lifo.write

import com.lifo.util.model.EnergyCheckIn
import com.lifo.util.model.MovementType
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.EnergyRepository
import com.lifo.util.auth.AuthProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────
// Contract
// ──────────────────────────────────────────────────────────

object EnergyContract {

    sealed interface Intent : MviContract.Intent {
        data class SetEnergyLevel(val level: Int) : Intent
        data class SetSleepHours(val hours: Float) : Intent
        data class SetWaterGlasses(val glasses: Int) : Intent
        data class SetDidMovement(val did: Boolean) : Intent
        data class SetMovementType(val type: MovementType) : Intent
        data class SetRegularMeals(val regular: Boolean) : Intent
        data object Save : Intent
        data object LoadToday : Intent
    }

    data class State(
        val energyLevel: Int = 5,
        val sleepHours: Float = 7f,
        val waterGlasses: Int = 0,
        val didMovement: Boolean = false,
        val movementType: MovementType = MovementType.NESSUNO,
        val regularMeals: Boolean = true,
        val isSaving: Boolean = false,
        val isLoading: Boolean = true,
        val savedToday: Boolean = false,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object SavedSuccessfully : Effect
        data class Error(val message: String) : Effect
    }
}

// ──────────────────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────────────────

class EnergyViewModel(
    private val repository: EnergyRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<EnergyContract.Intent, EnergyContract.State, EnergyContract.Effect>(
    EnergyContract.State()
) {

    init {
        onIntent(EnergyContract.Intent.LoadToday)
    }

    override fun handleIntent(intent: EnergyContract.Intent) {
        when (intent) {
            is EnergyContract.Intent.SetEnergyLevel -> updateState { copy(energyLevel = intent.level.coerceIn(1, 10)) }
            is EnergyContract.Intent.SetSleepHours -> updateState { copy(sleepHours = intent.hours.coerceIn(0f, 16f)) }
            is EnergyContract.Intent.SetWaterGlasses -> updateState { copy(waterGlasses = intent.glasses.coerceAtLeast(0)) }
            is EnergyContract.Intent.SetDidMovement -> {
                updateState {
                    copy(
                        didMovement = intent.did,
                        movementType = if (!intent.did) MovementType.NESSUNO else movementType
                    )
                }
            }
            is EnergyContract.Intent.SetMovementType -> updateState { copy(movementType = intent.type, didMovement = true) }
            is EnergyContract.Intent.SetRegularMeals -> updateState { copy(regularMeals = intent.regular) }
            is EnergyContract.Intent.Save -> handleSave()
            is EnergyContract.Intent.LoadToday -> handleLoadToday()
        }
    }

    private fun handleLoadToday() {
        scope.launch {
            repository.getTodayCheckIn().collectLatest { result ->
                when (result) {
                    is RequestState.Loading -> updateState { copy(isLoading = true) }
                    is RequestState.Success -> {
                        val checkIn = result.data
                        if (checkIn != null) {
                            updateState {
                                copy(
                                    energyLevel = checkIn.energyLevel,
                                    sleepHours = checkIn.sleepHours,
                                    waterGlasses = checkIn.waterGlasses,
                                    didMovement = checkIn.didMovement,
                                    movementType = checkIn.movementType,
                                    regularMeals = checkIn.regularMeals,
                                    savedToday = true,
                                    isLoading = false,
                                )
                            }
                        } else {
                            updateState { copy(isLoading = false, savedToday = false) }
                        }
                    }
                    is RequestState.Error -> updateState { copy(isLoading = false) }
                    else -> {}
                }
            }
        }
    }

    private fun handleSave() {
        val userId = authProvider.currentUserId ?: return
        updateState { copy(isSaving = true) }

        scope.launch {
            val state = currentState
            val checkIn = EnergyCheckIn.create(
                ownerId = userId
            ).copy(
                energyLevel = state.energyLevel,
                sleepHours = state.sleepHours,
                waterGlasses = state.waterGlasses,
                didMovement = state.didMovement,
                movementType = state.movementType,
                regularMeals = state.regularMeals,
            )

            when (repository.upsertCheckIn(checkIn)) {
                is RequestState.Success -> {
                    updateState { copy(isSaving = false, savedToday = true) }
                    sendEffect(EnergyContract.Effect.SavedSuccessfully)
                }
                is RequestState.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(EnergyContract.Effect.Error("Errore nel salvataggio"))
                }
                else -> {}
            }
        }
    }
}
