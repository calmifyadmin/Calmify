package com.lifo.write

import com.lifo.util.model.ValuesDiscovery
import com.lifo.util.mvi.MviContract

object ValuesContract {

    data class State(
        val isLoading: Boolean = true,
        val discovery: ValuesDiscovery? = null,
        val currentStep: Int = 0, // 0-3
        val currentInput: String = "",
        val isSaving: Boolean = false,
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class SetStep(val step: Int) : Intent
        data class UpdateInput(val text: String) : Intent
        data object AddCurrentInput : Intent
        data class RemoveItem(val index: Int) : Intent
        data class SetFinalReflection(val text: String) : Intent
        data class ConfirmValues(val values: List<String>) : Intent
        data object Save : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data object Saved : Effect
        data class Error(val message: String) : Effect
    }
}
