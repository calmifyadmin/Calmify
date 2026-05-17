package com.lifo.biocontext.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrator that runs all pattern detection use cases in parallel and
 * returns the non-null ones — Phase 9.2.2 Pattern Feed entry point.
 *
 * Each detector self-gates honesty (returns null when data is too sparse or
 * effect size is below the meaningfulness floor) so the result list naturally
 * stays short — the feed shows only what the data actually supports.
 *
 * Sorted by [BioPattern.Kind.ordinal] so the order is stable across refreshes.
 */
class GetAllBioPatternsUseCase(
    private val sleepMood: DetectSleepMoodPatternUseCase,
    private val hrvTrend: DetectHrvTrendPatternUseCase,
    private val sleepDrift: DetectSleepDriftPatternUseCase,
) {
    suspend operator fun invoke(): List<BioPattern> = coroutineScope {
        val a = async { runCatching { sleepMood() }.getOrNull() }
        val b = async { runCatching { hrvTrend() }.getOrNull() }
        val c = async { runCatching { sleepDrift() }.getOrNull() }
        listOfNotNull(a.await(), b.await(), c.await()).sortedBy { it.kind.ordinal }
    }
}
