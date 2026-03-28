package com.lifo.write

import com.lifo.util.model.MovementLog
import com.lifo.util.model.MovementType
import com.lifo.util.model.PostMovementFeeling
import com.lifo.util.mvi.MviContract

object MovementContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadToday : Intent
        data class SetMovementType(val type: MovementType) : Intent
        data class SetDuration(val minutes: Int) : Intent
        data class SetFeeling(val feeling: PostMovementFeeling) : Intent
        data class SetNote(val note: String) : Intent
        data object Save : Intent
    }

    data class State(
        val movementType: MovementType = MovementType.CAMMINATA,
        val durationMinutes: Int = 20,
        val feeling: PostMovementFeeling = PostMovementFeeling.MEGLIO,
        val note: String = "",
        val recentLogs: List<MovementLog> = emptyList(),
        val weeklyCount: Int = 0,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object SavedSuccessfully : Effect
        data class Error(val message: String) : Effect
    }
}
