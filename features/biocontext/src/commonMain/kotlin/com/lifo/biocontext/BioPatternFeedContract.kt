package com.lifo.biocontext

import com.lifo.biocontext.domain.BioPattern
import com.lifo.util.mvi.MviContract

/**
 * MVI contract for the Bio Pattern Feed (Phase 9.2.2, 2026-05-17).
 *
 * Aggregate surface for cross-signal correlations the user couldn't see
 * before — sleep × mood (cross-feature), HRV trend (vs 60d ago), and sleep
 * drift. Each pattern is silence-by-default: if the data isn't there or the
 * effect is too small to mean anything, it doesn't render.
 */
object BioPatternFeedContract {

    sealed interface Intent : MviContract.Intent {
        data object Refresh : Intent
    }

    sealed interface Effect : MviContract.Effect

    data class State(
        val isLoading: Boolean = true,
        val patterns: List<BioPattern> = emptyList(),
    ) : MviContract.State {
        val isEmpty: Boolean get() = !isLoading && patterns.isEmpty()
    }
}
