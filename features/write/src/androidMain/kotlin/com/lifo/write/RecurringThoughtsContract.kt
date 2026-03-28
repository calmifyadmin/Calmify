package com.lifo.write

import com.lifo.util.model.RecurringThought
import com.lifo.util.mvi.MviContract

object RecurringThoughtsContract {

    data class State(
        val isLoading: Boolean = true,
        val thoughts: List<RecurringThought> = emptyList(),
        val selectedThought: RecurringThought? = null,
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class SelectThought(val thought: RecurringThought?) : Intent
        data class ReframeThought(val thoughtId: String) : Intent
        data class ResolveThought(val thoughtId: String) : Intent
        data class DeleteThought(val thoughtId: String) : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data class NavigateToReframe(val thoughtTheme: String) : Effect
    }
}
