package com.lifo.write

import com.lifo.util.mvi.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SilenceViewModel : MviViewModel<SilenceContract.Intent, SilenceContract.State, SilenceContract.Effect>(
    SilenceContract.State()
) {

    private var timerJob: Job? = null

    override fun handleIntent(intent: SilenceContract.Intent) {
        when (intent) {
            is SilenceContract.Intent.SetDuration -> updateState { copy(duration = intent.duration) }
            is SilenceContract.Intent.StartSilence -> startSilence()
            is SilenceContract.Intent.StopSilence -> stopSilence()
            is SilenceContract.Intent.UpdateJournal -> updateState { copy(journalText = intent.text) }
            is SilenceContract.Intent.SaveJournal -> saveJournal()
        }
    }

    private fun startSilence() {
        val totalSeconds = state.value.duration.minutes * 60
        updateState { copy(phase = SilenceContract.Phase.ACTIVE, remainingSeconds = totalSeconds) }
        sendEffect(SilenceContract.Effect.PlayBell)

        timerJob?.cancel()
        timerJob = scope.launch {
            while (state.value.remainingSeconds > 0 && state.value.phase == SilenceContract.Phase.ACTIVE) {
                delay(1000L)
                updateState { copy(remainingSeconds = remainingSeconds - 1) }
            }
            if (state.value.phase == SilenceContract.Phase.ACTIVE) {
                sendEffect(SilenceContract.Effect.PlayBell)
                updateState { copy(phase = SilenceContract.Phase.JOURNAL) }
                sendEffect(SilenceContract.Effect.SilenceCompleted)
            }
        }
    }

    private fun stopSilence() {
        timerJob?.cancel()
        updateState { copy(phase = SilenceContract.Phase.JOURNAL) }
    }

    private fun saveJournal() {
        updateState { copy(isSaving = false) }
        sendEffect(SilenceContract.Effect.JournalSaved)
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
