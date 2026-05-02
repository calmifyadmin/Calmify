package com.lifo.write

import com.lifo.ui.i18n.Strings
import com.lifo.util.mvi.MviContract
import org.jetbrains.compose.resources.StringResource

object PercorsoContract {

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
    }

    data class SectionSummary(
        val titleRes: StringResource,
        val items: List<SectionItem>,
    )

    /**
     * Stat row inside a section card (e.g. "Meditazione  0 sessioni  ▓░░").
     * `valueRes` is a parameterized format like `%1$d sessioni`; `valueArg` is
     * substituted at render. `valueOverride` (if set) wins over valueRes — used
     * for non-count strings like "Da scoprire" or "8.0h".
     */
    data class SectionItem(
        val labelRes: StringResource,
        val valueRes: StringResource,
        val valueArg: Int = 0,
        val valueOverride: String? = null,
        val progress: Float = 0f,
    )

    data class State(
        val mente: SectionSummary = SectionSummary(Strings.Percorso.pillarMind, emptyList()),
        val corpo: SectionSummary = SectionSummary(Strings.Percorso.pillarBody, emptyList()),
        val spirito: SectionSummary = SectionSummary(Strings.Percorso.pillarSpirit, emptyList()),
        val abitudini: SectionSummary = SectionSummary(Strings.Percorso.pillarHabits, emptyList()),
        val overallProgress: Float = 0f,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect
}
