package com.lifo.home.domain.model

import androidx.compose.runtime.Immutable
import com.lifo.util.model.BioRangeHint
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel

/**
 * Lightweight bio-signal summary surfaced inside the Home "Today" narrative card.
 *
 * Per `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` Phase 5 (wellness integration) and
 * `memory/feedback_biosignal_plan_as_compass.md`:
 * - Narrative-first (no scores, no targets, no streaks)
 * - Observational copy, never prescriptive
 * - Confidence + provenance always visible
 * - Card is dismissable; absence = silence, not error
 *
 * Built by walking the last 24h of local SQLDelight rows (via
 * [com.lifo.util.repository.BioSignalRepository.getRawSamples]) — no server
 * call, no Health Connect read, no blocking.
 *
 * `null` ⇒ no card rendered (silence-by-default per dogma #3 in
 * `memory/feedback_calmify_values.md`).
 */
@Immutable
data class HomeBioContext(
    /** Last completed sleep session length, if any. */
    val sleepDurationMinutes: Int? = null,

    /** Resting / latest morning HR sample (bpm). */
    val heartRateBpm: Int? = null,

    /** Cumulative steps today. */
    val stepsToday: Int? = null,

    /** Lowest confidence across the materials presented (honest by floor). */
    val confidenceFloor: ConfidenceLevel = ConfidenceLevel.LOW,

    /** Most-frequent source device across the materials presented. */
    val primarySource: BioSignalSource? = null,

    // ── Phase 6.2 — personalized framing (null = no baseline yet, cold start) ──

    /** Where today's sleep sits in the user's own trailing-30d distribution. */
    val sleepHint: BioRangeHint? = null,

    /** Where this morning's HR sits in the user's own trailing-30d distribution. */
    val heartRateHint: BioRangeHint? = null,

    /** Where today's steps sit in the user's own trailing-30d distribution. */
    val stepsHint: BioRangeHint? = null,
) {
    /** At least one metric is present — the card has something to say. */
    val hasSignal: Boolean
        get() = sleepDurationMinutes != null || heartRateBpm != null || stepsToday != null
}
