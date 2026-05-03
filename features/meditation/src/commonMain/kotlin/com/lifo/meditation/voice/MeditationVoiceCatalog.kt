package com.lifo.meditation.voice

import com.lifo.util.model.BreathSegmentKind
import com.lifo.util.model.BreathingPattern

/**
 * Identifies a voice utterance to play during a meditation session. Each
 * variant maps deterministically to an audio file path under
 * `features/meditation/src/androidMain/assets/meditation/voice/{lang}/{key}.mp3`.
 *
 * Voice playback is gated on `state.config.audio == MeditationAudio.VOICE`.
 *
 * The catalog is **closed** — every utterance the meditation flow will ever
 * need is enumerated here. Adding a new utterance requires adding a key here
 * AND generating the corresponding audio file (see
 * `scripts/generate-meditation-voice.py` + `assets/.../voice/README.md`).
 *
 * `assetKey` is the file stem (no extension) — the Android player resolves
 * to `meditation/voice/{lang}/{assetKey}.mp3`.
 */
sealed class VoiceUtterance {
    abstract val assetKey: String

    /** A breath cue spoken at the start of a segment ("Breathe in", "Hold", "Breathe out"). */
    data class Cue(val kind: BreathSegmentKind) : VoiceUtterance() {
        override val assetKey: String = when (kind) {
            BreathSegmentKind.INHALE -> "cue_breathe_in"
            BreathSegmentKind.EXHALE -> "cue_breathe_out"
            BreathSegmentKind.HOLD_IN, BreathSegmentKind.HOLD_OUT -> "cue_hold"
        }
    }

    /** A sub-phase opener cue ("Arrive" entering settle, "Release" entering integrate). */
    data class SubPhaseCue(val key: SubPhaseCueKey) : VoiceUtterance() {
        override val assetKey: String = when (key) {
            SubPhaseCueKey.ARRIVE -> "cue_arrive"
            SubPhaseCueKey.RELEASE -> "cue_release"
        }
    }

    /** A settle-phase coach line (3 lines, played in order as the settle phase progresses). */
    data class SettleCoach(val idx: Int) : VoiceUtterance() {
        override val assetKey: String = "settle_coach_${(idx + 1).coerceIn(1, 3)}"
    }

    /** An integrate-phase coach line (3 lines, played in order). */
    data class IntegrateCoach(val idx: Int) : VoiceUtterance() {
        override val assetKey: String = "integrate_coach_${(idx + 1).coerceIn(1, 3)}"
    }

    /** A technique-specific coach line (3 lines per technique, spoken once each per session). */
    data class PracticeCoach(val technique: BreathingPattern, val idx: Int) : VoiceUtterance() {
        override val assetKey: String = "${technique.name.lowercase()}_coach_${(idx + 1).coerceIn(1, 3)}"
    }
}

enum class SubPhaseCueKey { ARRIVE, RELEASE }

/**
 * Stable identifier for a coach-line "playback slot" within a session.
 * Used by the ViewModel to enforce the **speak-once-per-session** policy:
 * each slot is added to a `Set<CoachKey>` after the first utterance is
 * emitted; subsequent visual rotations do not re-emit voice.
 */
sealed class CoachKey {
    data class Settle(val idx: Int) : CoachKey()
    data class Integrate(val idx: Int) : CoachKey()
    data class Practice(val technique: BreathingPattern, val idx: Int) : CoachKey()

    fun toUtterance(): VoiceUtterance = when (this) {
        is Settle -> VoiceUtterance.SettleCoach(idx)
        is Integrate -> VoiceUtterance.IntegrateCoach(idx)
        is Practice -> VoiceUtterance.PracticeCoach(technique, idx)
    }
}
