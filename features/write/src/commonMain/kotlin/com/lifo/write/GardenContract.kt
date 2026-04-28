package com.lifo.write

import com.lifo.ui.i18n.Strings
import com.lifo.util.model.IkigaiExploration
import com.lifo.util.mvi.MviContract
import org.jetbrains.compose.resources.StringResource

object GardenContract {

    enum class Category(val labelRes: StringResource) {
        SCRITTURA(Strings.Garden.categoryWriting),
        MENTE(Strings.Garden.categoryMind),
        CORPO(Strings.Garden.categoryBody),
        SPIRITO(Strings.Garden.categorySpirit),
        ABITUDINI(Strings.Garden.categoryHabits),
    }

    enum class Difficulty(val labelRes: StringResource) {
        FACILE(Strings.Garden.difficultyEasy),
        MEDIO(Strings.Garden.difficultyMedium),
        AVANZATO(Strings.Garden.difficultyAdvanced),
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

    enum class IkigaiCircle(val displayNameRes: StringResource) {
        PASSION(Strings.Garden.ikigaiPassion),
        TALENT(Strings.Garden.ikigaiTalent),
        MISSION(Strings.Garden.ikigaiMission),
        PROFESSION(Strings.Garden.ikigaiProfession),
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
