package com.lifo.biocontext

import com.lifo.util.model.BioSignalDataType
import com.lifo.util.mvi.MviContract

/**
 * MVI contract for [BioTimelineScreen] — full-screen per-signal drill-down
 * (Phase 9.2.1, 2026-05-17). Re-engineered surface: Claude Design's mockups
 * never proposed this. Taps on any contextual bio card land here.
 *
 * Anatomy (driven by [TimelineWindow]):
 *  - Top app bar with type name + back
 *  - Window picker (7d / 30d / 90d)
 *  - Big line chart with min/max axis labels + dotted typical-range bands
 *  - Vertical overlay markers for journal entries + meditation sessions
 *    layered on the chart (the cross-feature integration the mockup missed)
 *  - Summary card: avg / min / max / count in the window
 *  - "Days tracked / total" honesty footer
 */
object BioTimelineContract {

    sealed interface Intent : MviContract.Intent {
        data object Refresh : Intent
        data class SetWindow(val window: TimelineWindow) : Intent
    }

    /** No effects emitted by the timeline today — placeholder for future drill-downs. */
    sealed interface Effect : MviContract.Effect

    data class State(
        val signal: BioSignalDataType = BioSignalDataType.HEART_RATE,
        val window: TimelineWindow = TimelineWindow.THIRTY_DAYS,
        val points: List<TimelinePoint> = emptyList(),
        val markers: List<TimelineMarker> = emptyList(),
        val averageValue: Double = 0.0,
        val minValue: Double = 0.0,
        val maxValue: Double = 0.0,
        /** Days with at least one sample in the window. */
        val daysTracked: Int = 0,
        /** Universal-range hints derived from per-user baseline (null = cold start). */
        val baselineLow: Double? = null,
        val baselineHigh: Double? = null,
        val isLoading: Boolean = true,
        val isEmpty: Boolean = false,
    ) : MviContract.State

    enum class TimelineWindow(val days: Int) {
        SEVEN_DAYS(7),
        THIRTY_DAYS(30),
        NINETY_DAYS(90),
    }

    /** One point on the line — millis since epoch + value. */
    data class TimelinePoint(val timestampMillis: Long, val value: Double)

    /** Vertical marker overlaying the line — journal entry or meditation session. */
    data class TimelineMarker(
        val timestampMillis: Long,
        val kind: MarkerKind,
        val label: String,
    )

    enum class MarkerKind { JOURNAL, MEDITATION }
}
