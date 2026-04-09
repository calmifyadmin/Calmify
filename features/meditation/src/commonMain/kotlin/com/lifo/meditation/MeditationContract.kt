package com.lifo.meditation

import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationSession
import com.lifo.util.model.MeditationType
import com.lifo.util.mvi.MviContract

object MeditationContract {

    sealed interface Intent : MviContract.Intent {
        // Setup
        data class SelectType(val type: MeditationType) : Intent
        data class SelectBreathingPattern(val pattern: BreathingPattern) : Intent
        data class SelectDuration(val seconds: Int) : Intent

        // Timer
        data object StartSession : Intent
        data object PauseSession : Intent
        data object ResumeSession : Intent
        data object StopSession : Intent

        // Post-session
        data class SetPostNote(val note: String) : Intent
        data object SaveSession : Intent
        data object DiscardSession : Intent

        // Stats
        data object LoadStats : Intent
        data object RetryLoadStats : Intent
    }

    data class State(
        // Setup
        val selectedType: MeditationType = MeditationType.BREATHING,
        val selectedPattern: BreathingPattern = BreathingPattern.BOX_BREATHING,
        val selectedDurationSeconds: Int = 300,

        // Timer
        val phase: SessionPhase = SessionPhase.SETUP,
        val elapsedSeconds: Int = 0,
        val isPaused: Boolean = false,

        // Breathing animation
        val breathingPhase: BreathingPhase = BreathingPhase.INHALE,
        val breathingPhaseProgress: Float = 0f,

        // Post-session
        val postNote: String = "",

        // Stats
        val totalMinutes: Int = 0,
        val sessionCount: Int = 0,
        val recentSessions: List<MeditationSession> = emptyList(),

        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : MviContract.State

    enum class SessionPhase { SETUP, ACTIVE, COMPLETED }

    enum class BreathingPhase(val label: String) {
        INHALE("Inspira..."),
        HOLD_IN("Trattieni..."),
        EXHALE("Espira..."),
        HOLD_OUT("Trattieni...");
    }

    sealed interface Effect : MviContract.Effect {
        data object SessionCompleted : Effect
        data object SessionSaved : Effect
        data class Error(val message: String) : Effect
        data object PlayBell : Effect
    }
}
