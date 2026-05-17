package com.lifo.biocontext.domain

import com.lifo.ui.components.biosignal.BioBaselineDrift
import com.lifo.util.model.BioSignalDataType
import com.lifo.util.preview.BioPreviewProvider
import com.lifo.util.repository.BioSignalRepository

/**
 * Compute baseline drift over a 60-day comparison window — Phase 9.2.5 (2026-05-17).
 *
 * Returns one drift card per type that has BOTH a current baseline AND a
 * historical snapshot from at least [COMPARISON_DAYS] days ago. Cold-start
 * users see nothing — silence by design, per dogma #3.
 *
 * Re-engineering: only possible because Phase 9.2.5's BioBaselineHistory
 * SQLDelight table snapshots every recompute. Before that, baselines were
 * overwritten and the slow-drift signal was invisible.
 */
class GetBaselineDriftUseCase(
    private val repository: BioSignalRepository,
    private val preview: BioPreviewProvider,
) {
    suspend operator fun invoke(): List<BioBaselineDrift> {
        val results = mutableListOf<BioBaselineDrift>()
        for (type in PRIORITY_TYPES) {
            val current = repository.getBaseline(type) ?: continue
            if (current.p50 <= 0.0) continue
            val historical = repository.getBaselineDaysAgo(type, COMPARISON_DAYS)
                ?: continue
            if (historical.p50 <= 0.0) continue
            val deltaPercent = (((current.p50 - historical.p50) / historical.p50) * 100.0).toInt()
            // Skip tiny drifts; surface only meaningful shifts.
            if (kotlin.math.abs(deltaPercent) < MEANINGFUL_DRIFT_PERCENT) continue
            results += BioBaselineDrift(
                type = type,
                currentMedian = current.p50,
                comparedToDaysAgo = COMPARISON_DAYS,
                deltaPercent = deltaPercent,
            )
        }
        if (results.isEmpty() && preview.enabled) {
            return listOf(
                BioBaselineDrift(
                    type = BioSignalDataType.HRV,
                    currentMedian = 42.0,
                    comparedToDaysAgo = 60,
                    deltaPercent = 12,
                ),
            )
        }
        return results
    }

    companion object {
        /** Types shown on the drift surface, in priority order. */
        private val PRIORITY_TYPES = listOf(
            BioSignalDataType.HRV,
            BioSignalDataType.RESTING_HEART_RATE,
            BioSignalDataType.SLEEP,
        )
        private const val COMPARISON_DAYS = 60
        private const val MEANINGFUL_DRIFT_PERCENT = 4
    }
}
