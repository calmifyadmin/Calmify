package com.lifo.write

import com.lifo.util.mvi.MviContract

object SilenceContract {

    enum class SilenceDuration(val minutes: Int, val label: String) {
        TEN(10, "10 min"),
        TWENTY(20, "20 min"),
        THIRTY(30, "30 min"),
    }

    enum class Phase {
        SETUP,
        ACTIVE,
        JOURNAL,
    }

    data class State(
        val phase: Phase = Phase.SETUP,
        val duration: SilenceDuration = SilenceDuration.TEN,
        val remainingSeconds: Int = 0,
        val journalText: String = "",
        val isSaving: Boolean = false,
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data class SetDuration(val duration: SilenceDuration) : Intent
        data object StartSilence : Intent
        data object StopSilence : Intent
        data class UpdateJournal(val text: String) : Intent
        data object SaveJournal : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data object PlayBell : Effect
        data object SilenceCompleted : Effect
        data object JournalSaved : Effect
    }
}
