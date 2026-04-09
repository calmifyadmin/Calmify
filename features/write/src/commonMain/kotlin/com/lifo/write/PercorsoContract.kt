package com.lifo.write

import com.lifo.util.mvi.MviContract

object PercorsoContract {

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
    }

    data class SectionSummary(
        val title: String,
        val items: List<SectionItem>,
    )

    data class SectionItem(
        val label: String,
        val value: String,
        val progress: Float = 0f,
    )

    data class State(
        val mente: SectionSummary = SectionSummary("Mente", emptyList()),
        val corpo: SectionSummary = SectionSummary("Corpo", emptyList()),
        val spirito: SectionSummary = SectionSummary("Spirito", emptyList()),
        val abitudini: SectionSummary = SectionSummary("Abitudini", emptyList()),
        val overallProgress: Float = 0f,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect
}
