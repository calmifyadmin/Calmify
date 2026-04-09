package com.lifo.write

import com.lifo.util.model.RequestState
import com.lifo.util.model.SleepDisturbance
import com.lifo.util.model.SleepLog
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.SleepRepository
import com.lifo.util.auth.AuthProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object SleepContract {

    sealed interface Intent : MviContract.Intent {
        data class SetBedtime(val hour: Int, val minute: Int) : Intent
        data class SetWaketime(val hour: Int, val minute: Int) : Intent
        data class SetQuality(val quality: Int) : Intent
        data class ToggleDisturbance(val disturbance: SleepDisturbance) : Intent
        data class SetScreenFree(val screenFree: Boolean) : Intent
        data class SetNotes(val notes: String) : Intent
        data object Save : Intent
        data object LoadToday : Intent
    }

    data class State(
        val bedtimeHour: Int = 23,
        val bedtimeMinute: Int = 0,
        val waketimeHour: Int = 7,
        val waketimeMinute: Int = 0,
        val quality: Int = 3,
        val disturbances: Set<SleepDisturbance> = emptySet(),
        val screenFreeLastHour: Boolean = false,
        val notes: String = "",
        val isSaving: Boolean = false,
        val isLoading: Boolean = true,
        val savedToday: Boolean = false,
    ) : MviContract.State {
        val sleepHours: Float
            get() {
                val bedMinutes = bedtimeHour * 60 + bedtimeMinute
                val wakeMinutes = waketimeHour * 60 + waketimeMinute
                val diff = if (wakeMinutes > bedMinutes) {
                    wakeMinutes - bedMinutes
                } else {
                    (24 * 60 - bedMinutes) + wakeMinutes
                }
                return diff / 60f
            }
    }

    sealed interface Effect : MviContract.Effect {
        data object SavedSuccessfully : Effect
        data class Error(val message: String) : Effect
    }
}

class SleepLogViewModel(
    private val repository: SleepRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<SleepContract.Intent, SleepContract.State, SleepContract.Effect>(
    SleepContract.State()
) {
    init { onIntent(SleepContract.Intent.LoadToday) }

    override fun handleIntent(intent: SleepContract.Intent) {
        when (intent) {
            is SleepContract.Intent.SetBedtime -> updateState { copy(bedtimeHour = intent.hour, bedtimeMinute = intent.minute) }
            is SleepContract.Intent.SetWaketime -> updateState { copy(waketimeHour = intent.hour, waketimeMinute = intent.minute) }
            is SleepContract.Intent.SetQuality -> updateState { copy(quality = intent.quality.coerceIn(1, 5)) }
            is SleepContract.Intent.ToggleDisturbance -> {
                updateState {
                    val updated = disturbances.toMutableSet()
                    if (intent.disturbance in updated) updated.remove(intent.disturbance) else updated.add(intent.disturbance)
                    copy(disturbances = updated)
                }
            }
            is SleepContract.Intent.SetScreenFree -> updateState { copy(screenFreeLastHour = intent.screenFree) }
            is SleepContract.Intent.SetNotes -> updateState { copy(notes = intent.notes) }
            is SleepContract.Intent.Save -> handleSave()
            is SleepContract.Intent.LoadToday -> handleLoadToday()
        }
    }

    private fun handleLoadToday() {
        scope.launch {
            repository.getTodayLog().collectLatest { result ->
                when (result) {
                    is RequestState.Loading -> updateState { copy(isLoading = true) }
                    is RequestState.Success -> {
                        val log = result.data
                        if (log != null) {
                            updateState {
                                copy(
                                    bedtimeHour = log.bedtimeHour, bedtimeMinute = log.bedtimeMinute,
                                    waketimeHour = log.waketimeHour, waketimeMinute = log.waketimeMinute,
                                    quality = log.quality, disturbances = log.disturbances.toSet(),
                                    screenFreeLastHour = log.screenFreeLastHour, notes = log.notes,
                                    savedToday = true, isLoading = false,
                                )
                            }
                        } else {
                            updateState { copy(isLoading = false) }
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
            val s = currentState
            val log = SleepLog.create(ownerId = userId).copy(
                bedtimeHour = s.bedtimeHour, bedtimeMinute = s.bedtimeMinute,
                waketimeHour = s.waketimeHour, waketimeMinute = s.waketimeMinute,
                quality = s.quality, disturbances = s.disturbances.toList(),
                screenFreeLastHour = s.screenFreeLastHour, notes = s.notes,
            )
            when (repository.upsertSleepLog(log)) {
                is RequestState.Success -> {
                    updateState { copy(isSaving = false, savedToday = true) }
                    sendEffect(SleepContract.Effect.SavedSuccessfully)
                }
                is RequestState.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(SleepContract.Effect.Error("Errore nel salvataggio"))
                }
                else -> {}
            }
        }
    }
}
