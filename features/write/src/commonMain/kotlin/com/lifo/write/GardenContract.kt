package com.lifo.write

import com.lifo.util.model.IkigaiExploration
import com.lifo.util.mvi.MviContract

object GardenContract {

    enum class Category(val label: String) {
        SCRITTURA("Scrittura"),
        MENTE("Mente"),
        CORPO("Corpo"),
        SPIRITO("Spirito"),
        ABITUDINI("Abitudini"),
    }

    enum class Difficulty(val label: String) {
        FACILE("Facile"),
        MEDIO("Medio"),
        AVANZATO("Avanzato"),
    }

    data class ActivityInfo(
        val id: String,
        val name: String,
        val description: String,
        val longDescription: String,
        val benefits: List<String>,
        val estimatedMinutes: Int,
        val difficulty: Difficulty,
        val category: Category,
    )

    enum class IkigaiCircle(val displayName: String) {
        PASSION("Passione"),
        TALENT("Talento"),
        MISSION("Missione"),
        PROFESSION("Professione"),
    }

    data class State(
        val isLoading: Boolean = true,
        val exploredIds: Set<String> = emptySet(),
        val favoriteIds: Set<String> = emptySet(),
        val expandedId: String? = null,
        val ikigaiExploration: IkigaiExploration? = null,
        val selectedIkigaiCircle: IkigaiCircle? = null,
        val ikigaiInput: String = "",
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class ToggleExpand(val activityId: String) : Intent
        data class ToggleFavorite(val activityId: String) : Intent
        data class MarkExplored(val activityId: String) : Intent
        data class SelectIkigaiCircle(val circle: IkigaiCircle) : Intent
        data class UpdateIkigaiInput(val text: String) : Intent
        data object AddIkigaiItem : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data class NavigateTo(val activityId: String) : Effect
    }
}
