package com.lifo.biocontext

import com.lifo.biocontext.domain.GetAllBioPatternsUseCase
import com.lifo.util.mvi.MviViewModel
import kotlinx.coroutines.launch

class BioPatternFeedViewModel(
    private val getAllPatterns: GetAllBioPatternsUseCase,
) : MviViewModel<BioPatternFeedContract.Intent, BioPatternFeedContract.State, BioPatternFeedContract.Effect>(
    BioPatternFeedContract.State()
) {

    init { refresh() }

    override fun handleIntent(intent: BioPatternFeedContract.Intent) {
        when (intent) {
            BioPatternFeedContract.Intent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            updateState { copy(isLoading = true) }
            val list = runCatching { getAllPatterns() }.getOrDefault(emptyList())
            updateState { copy(isLoading = false, patterns = list) }
        }
    }
}
