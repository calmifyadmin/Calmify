package com.lifo.write

import com.lifo.util.model.IkigaiExploration
import com.lifo.util.mvi.MviContract

object IkigaiContract {

    enum class Circle(val displayName: String, val prompt: String) {
        PASSION("Passione", "Cosa fai quando il tempo scompare?"),
        TALENT("Talento", "In cosa sei naturalmente bravo? Cosa gli altri ti riconoscono?"),
        MISSION("Missione", "Di cosa ha bisogno il mondo intorno a te?"),
        PROFESSION("Professione", "Per cosa le persone ti pagherebbero?"),
    }

    data class State(
        val isLoading: Boolean = true,
        val exploration: IkigaiExploration? = null,
        val selectedCircle: Circle = Circle.PASSION,
        val currentInput: String = "",
        val isSaving: Boolean = false,
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class SelectCircle(val circle: Circle) : Intent
        data class UpdateInput(val text: String) : Intent
        data object AddItem : Intent
        data class RemoveItem(val circle: Circle, val index: Int) : Intent
        data object Save : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data object Saved : Effect
    }
}
