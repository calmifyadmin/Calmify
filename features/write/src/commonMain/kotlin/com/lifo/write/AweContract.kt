package com.lifo.write

import com.lifo.util.model.AweEntry
import com.lifo.util.mvi.MviContract

object AweContract {

    val weeklyPrompts = listOf(
        "Questa settimana, hai avuto un momento di meraviglia? Un tramonto, un cielo stellato, qualcosa di bello che ti ha fermato?",
        "Hai notato qualcosa di piccolo ma straordinario nella natura oggi?",
        "Quando e' stata l'ultima volta che hai sentito un senso di vastita'?",
        "C'e' stato un momento questa settimana in cui ti sei sentito parte di qualcosa di piu' grande?",
    )

    val weeklyChallenges = listOf(
        "Passa 20 minuti nella natura senza telefono. Poi raccontami.",
        "Guarda il cielo per 5 minuti, senza fare nient'altro.",
        "Trova qualcosa di bello nel tuo quartiere che non avevi mai notato.",
        "Ascolta i suoni della natura per 10 minuti.",
    )

    data class State(
        val isLoading: Boolean = true,
        val entries: List<AweEntry> = emptyList(),
        val description: String = "",
        val context: String = "",
        val isSaving: Boolean = false,
        val currentPrompt: String = weeklyPrompts[0],
        val currentChallenge: String = weeklyChallenges[0],
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
        data class UpdateDescription(val text: String) : Intent
        data class UpdateContext(val text: String) : Intent
        data object Save : Intent
    }

    sealed interface Effect : MviContract.Effect {
        data object Saved : Effect
    }
}
