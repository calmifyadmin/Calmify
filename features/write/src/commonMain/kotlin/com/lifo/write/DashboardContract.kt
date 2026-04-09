package com.lifo.write

import com.lifo.util.mvi.MviContract

object DashboardContract {

    data class MetricSummary(
        val label: String,
        val value: String,
        val progress: Float, // 0f..1f
        val trend: String = "", // e.g. "+0.3" or "-1.2"
    )

    data class State(
        val isLoading: Boolean = true,
        val avgSleepHours: Float = 0f,
        val avgEnergy: Float = 0f,
        val avgWaterGlasses: Float = 0f,
        val movementDays: Int = 0,
        val totalDays: Int = 7,
        val terrainLevel: String = "—",
        val aiNarrative: String = "",
        val correlationInsight: String = "",
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent {
        data object Load : Intent
    }

    sealed interface Effect : MviContract.Effect
}
