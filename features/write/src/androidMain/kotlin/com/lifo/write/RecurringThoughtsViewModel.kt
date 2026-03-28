package com.lifo.write

import com.lifo.util.auth.AuthProvider
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.RecurringThoughtRepository
import kotlinx.coroutines.launch

class RecurringThoughtsViewModel(
    private val repository: RecurringThoughtRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<RecurringThoughtsContract.Intent, RecurringThoughtsContract.State, RecurringThoughtsContract.Effect>(
    RecurringThoughtsContract.State()
) {

    init {
        onIntent(RecurringThoughtsContract.Intent.Load)
    }

    override fun handleIntent(intent: RecurringThoughtsContract.Intent) {
        when (intent) {
            is RecurringThoughtsContract.Intent.Load -> load()
            is RecurringThoughtsContract.Intent.SelectThought -> updateState { copy(selectedThought = intent.thought) }
            is RecurringThoughtsContract.Intent.ReframeThought -> reframe(intent.thoughtId)
            is RecurringThoughtsContract.Intent.ResolveThought -> resolve(intent.thoughtId)
            is RecurringThoughtsContract.Intent.DeleteThought -> delete(intent.thoughtId)
        }
    }

    private fun load() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            repository.getThoughts(userId).collect { thoughts ->
                updateState { copy(thoughts = thoughts, isLoading = false) }
            }
        }
    }

    private fun reframe(thoughtId: String) {
        val thought = state.value.thoughts.find { it.id == thoughtId } ?: return
        sendEffect(RecurringThoughtsContract.Effect.NavigateToReframe(thought.theme))
    }

    private fun resolve(thoughtId: String) {
        scope.launch {
            try { repository.resolveThought(thoughtId) } catch (_: Exception) { }
        }
    }

    private fun delete(thoughtId: String) {
        scope.launch {
            try {
                repository.deleteThought(thoughtId)
                updateState { copy(selectedThought = null) }
            } catch (_: Exception) { }
        }
    }
}
