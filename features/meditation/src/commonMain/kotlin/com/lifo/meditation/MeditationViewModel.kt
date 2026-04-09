package com.lifo.meditation

import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationType
import com.lifo.util.model.MeditationSession
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.auth.AuthProvider
import com.lifo.util.repository.MeditationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MeditationViewModel(
    private val repository: MeditationRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<MeditationContract.Intent, MeditationContract.State, MeditationContract.Effect>(
    MeditationContract.State()
) {

    private var timerJob: Job? = null

    init {
        onIntent(MeditationContract.Intent.LoadStats)
    }

    override fun handleIntent(intent: MeditationContract.Intent) {
        when (intent) {
            is MeditationContract.Intent.SelectType -> updateState { copy(selectedType = intent.type) }
            is MeditationContract.Intent.SelectBreathingPattern -> updateState { copy(selectedPattern = intent.pattern) }
            is MeditationContract.Intent.SelectDuration -> updateState { copy(selectedDurationSeconds = intent.seconds) }

            is MeditationContract.Intent.StartSession -> startSession()
            is MeditationContract.Intent.PauseSession -> pauseSession()
            is MeditationContract.Intent.ResumeSession -> resumeSession()
            is MeditationContract.Intent.StopSession -> stopSession()

            is MeditationContract.Intent.SetPostNote -> updateState { copy(postNote = intent.note) }
            is MeditationContract.Intent.SaveSession -> saveSession()
            is MeditationContract.Intent.DiscardSession -> {
                updateState { MeditationContract.State() }
                onIntent(MeditationContract.Intent.LoadStats)
            }

            is MeditationContract.Intent.LoadStats -> loadStats()
            is MeditationContract.Intent.RetryLoadStats -> {
                updateState { copy(errorMessage = null) }
                loadStats()
            }
        }
    }

    private fun startSession() {
        updateState {
            copy(
                phase = MeditationContract.SessionPhase.ACTIVE,
                elapsedSeconds = 0,
                isPaused = false,
                breathingPhase = MeditationContract.BreathingPhase.INHALE,
                breathingPhaseProgress = 0f,
            )
        }
        sendEffect(MeditationContract.Effect.PlayBell)
        startTimer()
    }

    private fun pauseSession() {
        timerJob?.cancel()
        updateState { copy(isPaused = true) }
    }

    private fun resumeSession() {
        updateState { copy(isPaused = false) }
        startTimer()
    }

    private fun stopSession() {
        timerJob?.cancel()
        val s = state.value
        if (s.elapsedSeconds >= 30) {
            sendEffect(MeditationContract.Effect.PlayBell)
            updateState { copy(phase = MeditationContract.SessionPhase.COMPLETED) }
        } else {
            updateState { MeditationContract.State() }
            onIntent(MeditationContract.Intent.LoadStats)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000L)
                val s = state.value
                val newElapsed = s.elapsedSeconds + 1

                if (newElapsed >= s.selectedDurationSeconds) {
                    updateState { copy(elapsedSeconds = newElapsed, phase = MeditationContract.SessionPhase.COMPLETED) }
                    sendEffect(MeditationContract.Effect.PlayBell)
                    sendEffect(MeditationContract.Effect.SessionCompleted)
                    break
                }

                val (breathPhase, breathProgress) = if (s.selectedType == MeditationType.BREATHING) {
                    calculateBreathingPhase(newElapsed, s.selectedPattern)
                } else {
                    s.breathingPhase to 0f
                }

                updateState {
                    copy(
                        elapsedSeconds = newElapsed,
                        breathingPhase = breathPhase,
                        breathingPhaseProgress = breathProgress,
                    )
                }
            }
        }
    }

    private fun calculateBreathingPhase(
        elapsedSeconds: Int,
        pattern: BreathingPattern,
    ): Pair<MeditationContract.BreathingPhase, Float> {
        val cyclePosition = elapsedSeconds % pattern.totalCycleSeconds
        var accumulated = 0

        // Inhale
        accumulated += pattern.inhaleSeconds
        if (cyclePosition < accumulated) {
            val phaseElapsed = cyclePosition - (accumulated - pattern.inhaleSeconds)
            return MeditationContract.BreathingPhase.INHALE to (phaseElapsed.toFloat() / pattern.inhaleSeconds)
        }

        // Hold In
        if (pattern.holdInSeconds > 0) {
            accumulated += pattern.holdInSeconds
            if (cyclePosition < accumulated) {
                val phaseElapsed = cyclePosition - (accumulated - pattern.holdInSeconds)
                return MeditationContract.BreathingPhase.HOLD_IN to (phaseElapsed.toFloat() / pattern.holdInSeconds)
            }
        }

        // Exhale
        accumulated += pattern.exhaleSeconds
        if (cyclePosition < accumulated) {
            val phaseElapsed = cyclePosition - (accumulated - pattern.exhaleSeconds)
            return MeditationContract.BreathingPhase.EXHALE to (phaseElapsed.toFloat() / pattern.exhaleSeconds)
        }

        // Hold Out
        if (pattern.holdOutSeconds > 0) {
            val phaseElapsed = cyclePosition - accumulated
            return MeditationContract.BreathingPhase.HOLD_OUT to (phaseElapsed.toFloat() / pattern.holdOutSeconds)
        }

        return MeditationContract.BreathingPhase.INHALE to 0f
    }

    private fun saveSession() {
        val s = state.value
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            updateState { copy(isLoading = true) }
            @OptIn(ExperimentalUuidApi::class)
            val session = MeditationSession(
                id = Uuid.random().toString(),
                ownerId = userId,
                timestampMillis = com.lifo.util.currentTimeMillis(),
                type = s.selectedType,
                breathingPattern = if (s.selectedType == MeditationType.BREATHING) s.selectedPattern else null,
                durationSeconds = s.selectedDurationSeconds,
                completedSeconds = s.elapsedSeconds,
                postNote = s.postNote,
            )
            repository.saveSession(session)
                .onSuccess {
                    sendEffect(MeditationContract.Effect.SessionSaved)
                    updateState { MeditationContract.State() }
                    onIntent(MeditationContract.Intent.LoadStats)
                }
                .onFailure { sendEffect(MeditationContract.Effect.Error(it.message ?: "Errore nel salvataggio")) }
            updateState { copy(isLoading = false) }
        }
    }

    private fun loadStats() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            try {
                val totalMin = repository.getTotalMinutes(userId)
                val count = repository.getSessionCount(userId)
                updateState { copy(totalMinutes = totalMin, sessionCount = count, errorMessage = null) }
            } catch (e: Exception) {
                updateState { copy(errorMessage = "Impossibile caricare i dati. Verifica la connessione.") }
            }
        }
        scope.launch {
            try {
                repository.getRecentSessions(userId).collect { sessions ->
                    updateState { copy(recentSessions = sessions) }
                }
            } catch (e: Exception) {
                updateState { copy(errorMessage = "Impossibile caricare i dati. Verifica la connessione.") }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
