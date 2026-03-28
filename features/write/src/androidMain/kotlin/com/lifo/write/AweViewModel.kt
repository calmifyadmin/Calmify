package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.AweEntry
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.AweRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class AweViewModel(
    private val repository: AweRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<AweContract.Intent, AweContract.State, AweContract.Effect>(
    AweContract.State()
) {

    init {
        onIntent(AweContract.Intent.Load)
    }

    override fun handleIntent(intent: AweContract.Intent) {
        when (intent) {
            is AweContract.Intent.Load -> load()
            is AweContract.Intent.UpdateDescription -> updateState { copy(description = intent.text) }
            is AweContract.Intent.UpdateContext -> updateState { copy(context = intent.text) }
            is AweContract.Intent.Save -> save()
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        val weekIndex = (Clock.System.now().toEpochMilliseconds() / (7L * 24 * 60 * 60 * 1000)).toInt()
        val promptIndex = weekIndex % AweContract.weeklyPrompts.size
        val challengeIndex = weekIndex % AweContract.weeklyChallenges.size

        updateState {
            copy(
                currentPrompt = AweContract.weeklyPrompts[promptIndex],
                currentChallenge = AweContract.weeklyChallenges[challengeIndex],
            )
        }

        scope.launch {
            repository.getEntries(userId).collect { entries ->
                updateState { copy(entries = entries, isLoading = false) }
            }
        }
    }

    private fun save() {
        val userId = authProvider.currentUserId ?: return
        val desc = state.value.description.trim()
        if (desc.isBlank()) return

        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dayKey = "${local.year}-${local.monthNumber.toString().padStart(2, '0')}-${local.dayOfMonth.toString().padStart(2, '0')}"

        val entry = AweEntry(
            ownerId = userId,
            description = desc,
            context = state.value.context.trim(),
            timestampMillis = now.toEpochMilliseconds(),
            dayKey = dayKey,
        )

        scope.launch {
            updateState { copy(isSaving = true) }
            repository.saveEntry(entry)
                .onSuccess {
                    updateState { copy(description = "", context = "", isSaving = false) }
                    sendEffect(AweContract.Effect.Saved)
                }
                .onFailure {
                    updateState { copy(isSaving = false) }
                }
        }
    }
}
