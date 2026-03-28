package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.MovementLog
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.MovementRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MovementViewModel(
    private val repository: MovementRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<MovementContract.Intent, MovementContract.State, MovementContract.Effect>(
    MovementContract.State()
) {

    init {
        onIntent(MovementContract.Intent.LoadToday)
    }

    override fun handleIntent(intent: MovementContract.Intent) {
        when (intent) {
            is MovementContract.Intent.LoadToday -> loadData()
            is MovementContract.Intent.SetMovementType -> updateState { copy(movementType = intent.type) }
            is MovementContract.Intent.SetDuration -> updateState { copy(durationMinutes = intent.minutes) }
            is MovementContract.Intent.SetFeeling -> updateState { copy(feeling = intent.feeling) }
            is MovementContract.Intent.SetNote -> updateState { copy(note = intent.note) }
            is MovementContract.Intent.Save -> save()
        }
    }

    private fun loadData() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getRecentLogs(userId, 7).collect { logs ->
                updateState { copy(recentLogs = logs, weeklyCount = logs.size) }
            }
        }
    }

    private fun save() {
        val userId = authProvider.currentUserId ?: return
        val s = state.value
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayKey = "${local.year}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}"

        scope.launch {
            updateState { copy(isSaving = true) }
            val log = MovementLog(
                ownerId = userId,
                timestampMillis = now.toEpochMilliseconds(),
                dayKey = dayKey,
                movementType = s.movementType,
                durationMinutes = s.durationMinutes,
                feelingAfter = s.feeling,
                note = s.note,
            )
            repository.saveLog(log)
                .onSuccess { sendEffect(MovementContract.Effect.SavedSuccessfully) }
                .onFailure { sendEffect(MovementContract.Effect.Error(it.message ?: "Errore")) }
            updateState { copy(isSaving = false) }
        }
    }
}
