package com.lifo.meditation

import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationAudio
import com.lifo.util.model.MeditationExperience
import com.lifo.util.model.MeditationGoal
import com.lifo.util.model.MeditationRiskFlag
import com.lifo.util.model.MeditationSession
import com.lifo.util.mvi.MviContract

/**
 * MVI contract for the redesigned 5-phase meditation flow:
 * `WELCOME → SCREENING → CONFIGURE → SESSION → OVERVIEW`.
 *
 * Inside [SessionPhase.SESSION], the runtime progresses through three
 * sub-phases: settling (15%) → practice (70%) → integration (15%).
 *
 * Risk screening is **enforcing**, not advisory: any flag toggled on sets
 * `restricted = true` and locks the technique to gentle-track only
 * ([BreathingPattern.isGentle]).
 */
object MeditationContract {

    // ── Intents ─────────────────────────────────────────────────────────

    sealed interface Intent : MviContract.Intent {
        // Phase navigation
        data object NavigateToScreening : Intent       // Welcome → Screening
        data object NavigateToConfigure : Intent       // Screening → Configure
        data object NavigateBackFromScreening : Intent // Screening → Welcome
        data object NavigateBackFromConfigure : Intent // Configure → Screening
        data object NavigateRedoFromOverview : Intent      // Overview → Session (same config)
        data object NavigateDifferentFromOverview : Intent // Overview → Configure

        // Screening
        data class ToggleRiskFlag(val flag: MeditationRiskFlag) : Intent
        data object ClearAllRisks : Intent

        // Configure
        data class SetDuration(val minutes: Int) : Intent
        data class SetGoal(val goal: MeditationGoal) : Intent
        data class SetExperience(val exp: MeditationExperience) : Intent
        data class SetAudio(val audio: MeditationAudio) : Intent
        /** Pass `null` to reset to auto-resolved technique (clears override). */
        data class SetTechniqueOverride(val pattern: BreathingPattern?) : Intent

        // Session lifecycle
        data object StartSession : Intent
        data object PauseSession : Intent
        data object ResumeSession : Intent
        data object RequestStopSession : Intent     // shows confirmation modal
        data object DismissStopDialog : Intent
        data object ConfirmStopSession : Intent     // user confirmed → finalize as stopped
        /** Auto-fired by ViewModel ticker when elapsed >= total active seconds. */
        data object SessionAutoComplete : Intent

        // Stats
        data object LoadStats : Intent
        data object RetryLoadStats : Intent
    }

    // ── State ───────────────────────────────────────────────────────────

    data class State(
        val phase: SessionPhase = SessionPhase.WELCOME,
        val risks: Set<MeditationRiskFlag> = emptySet(),
        val config: SessionConfig = SessionConfig(),
        val session: SessionRuntime? = null,
        val showStopDialog: Boolean = false,
        // Stats
        val totalMinutes: Int = 0,
        val sessionCount: Int = 0,
        val recentSessions: List<MeditationSession> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : MviContract.State {

        /** True when any risk flag is set → forces gentle-track lock. */
        val restricted: Boolean get() = risks.isNotEmpty()

        /** Resolves the auto-recommended technique. Override (when allowed) wins. */
        val effectiveTechnique: BreathingPattern get() = resolveTechnique(this)

        /** True if the user can override the auto-resolved technique on Configure. */
        val techniqueOverridable: Boolean
            get() = !restricted && config.experience != MeditationExperience.FIRST
    }

    /** Configuration captured on the Configure screen, persisted across the session. */
    data class SessionConfig(
        val duration: Int = 5,                                 // minutes
        val goal: MeditationGoal = MeditationGoal.STRESS,
        val experience: MeditationExperience = MeditationExperience.OCCASIONAL,
        val audio: MeditationAudio = MeditationAudio.CHIMES,
        val techniqueOverride: BreathingPattern? = null,
    )

    /**
     * Live session state. Created when entering [SessionPhase.SESSION],
     * cleared when transitioning to [SessionPhase.OVERVIEW].
     */
    data class SessionRuntime(
        val technique: BreathingPattern,
        val durationSeconds: Int,
        val elapsedSeconds: Int = 0,
        val isPaused: Boolean = false,
        val cyclesCompleted: Int = 0,
        val stopped: Boolean = false,
    ) {
        /** Settling sub-phase length: max(20s, 15% of duration). */
        val settleSeconds: Int get() = maxOf(20, (durationSeconds * 0.15f).toInt())

        /** Integration sub-phase length: max(20s, 15% of duration). */
        val integrateSeconds: Int get() = maxOf(20, (durationSeconds * 0.15f).toInt())

        /** Practice sub-phase length: total minus settle minus integrate. */
        val practiceSeconds: Int get() = (durationSeconds - settleSeconds - integrateSeconds).coerceAtLeast(0)

        /**
         * Practice phase capped by technique's `cycleCap` (e.g. 4-7-8 limited to 4 cycles).
         * For uncapped techniques returns [practiceSeconds].
         */
        val practiceCapSeconds: Int
            get() {
                val cap = technique.cycleCap ?: return practiceSeconds
                if (!technique.hasPattern) return practiceSeconds
                val maxBySafety = (cap * technique.totalCycleSeconds).toInt()
                return minOf(practiceSeconds, maxBySafety)
            }

        val totalActiveSeconds: Int get() = settleSeconds + practiceCapSeconds + integrateSeconds

        val subPhase: SubPhase
            get() = when {
                elapsedSeconds < settleSeconds -> SubPhase.SETTLING
                elapsedSeconds >= settleSeconds + practiceCapSeconds -> SubPhase.INTEGRATION
                else -> SubPhase.PRACTICE
            }

        /** Seconds remaining before auto-complete. Clamped to >= 0. */
        val remainingSeconds: Int get() = (totalActiveSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    // ── Phase + sub-phase enums ─────────────────────────────────────────

    enum class SessionPhase {
        WELCOME, SCREENING, CONFIGURE, SESSION, OVERVIEW;
    }

    /** Sub-phase within [SessionPhase.SESSION]. Drives coach copy + pacer activation. */
    enum class SubPhase { SETTLING, PRACTICE, INTEGRATION }

    // ── Effects (one-shot) ──────────────────────────────────────────────

    sealed interface Effect : MviContract.Effect {
        data object SessionCompleted : Effect
        data object SessionSaved : Effect
        data class Error(val message: String) : Effect
        /** Emitted at sub-phase boundaries (settling → practice → integration). */
        data object PlayBell : Effect
    }
}

/**
 * Resolves the recommended [BreathingPattern] from the current state.
 *
 * Priority:
 * 1. **Restricted (any risk flag)**: locked to [BreathingPattern.BELLY_NATURAL]
 *    (override ignored — UI must surface this clearly).
 * 2. **First-time**: locked to [BreathingPattern.COHERENT] regardless of goal
 *    (override ignored — `requiresExperience = true` techniques are inelegible).
 * 3. **User override (when overridable)**: respected if non-null.
 * 4. **Auto from goal**: maps goal → default technique (with sleep fallback to
 *    EXTENDED_EXHALE if 4-7-8 is ineligible — currently never since eligibility
 *    is gated by FIRST already, but kept as defensive fallback).
 */
private fun resolveTechnique(state: MeditationContract.State): BreathingPattern {
    if (state.restricted) return BreathingPattern.BELLY_NATURAL
    if (state.config.experience == com.lifo.util.model.MeditationExperience.FIRST) {
        return BreathingPattern.COHERENT
    }
    state.config.techniqueOverride?.let { override ->
        // Override allowed if technique is not gated behind experience requirement
        if (!override.requiresExperience || state.config.experience != com.lifo.util.model.MeditationExperience.FIRST) {
            return override
        }
    }
    return when (state.config.goal) {
        com.lifo.util.model.MeditationGoal.STRESS -> BreathingPattern.COHERENT
        com.lifo.util.model.MeditationGoal.ANXIETY -> BreathingPattern.EXTENDED_EXHALE
        com.lifo.util.model.MeditationGoal.SLEEP -> {
            // 4-7-8 is the design's first choice for sleep; falls back to extended exhale
            // if for any reason RELAXATION_478 isn't eligible (defensive — currently
            // unreachable since FIRST is gated above).
            BreathingPattern.RELAXATION_478
        }
        com.lifo.util.model.MeditationGoal.FOCUS -> BreathingPattern.BOX_BREATHING
        com.lifo.util.model.MeditationGoal.GROUNDING -> BreathingPattern.BODY_SCAN_NATURAL
    }
}
