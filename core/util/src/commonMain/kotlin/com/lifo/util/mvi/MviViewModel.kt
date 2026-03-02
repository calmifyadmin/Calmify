package com.lifo.util.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base ViewModel implementing the Model-View-Intent pattern.
 *
 * KMP-compatible: uses a plain [CoroutineScope] instead of AndroidX ViewModel.
 * Lifecycle management is handled by koin-compose-viewmodel 4.1.x which
 * calls [onCleared] automatically on all platforms.
 *
 * @param Intent  Sealed interface representing user actions (implements [MviContract.Intent]).
 * @param State   Immutable data class representing UI state (implements [MviContract.State]).
 * @param Effect  Sealed interface representing one-shot side effects (implements [MviContract.Effect]).
 * @param initialState The initial UI state emitted before any intent is processed.
 */
abstract class MviViewModel<Intent : MviContract.Intent, State : MviContract.State, Effect : MviContract.Effect>(
    initialState: State
) : PlatformViewModel() {

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(initialState)

    /** Observable UI state. Collect in the UI layer with `collectAsState()`. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** Current snapshot of the state — useful inside `handleIntent` without collecting. */
    protected val currentState: State get() = _state.value

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)

    /** One-shot side effects. Collect in a `LaunchedEffect` with `effects.collect { ... }`. */
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    /**
     * Entry point for the UI to dispatch user actions.
     * Delegates to the subclass [handleIntent] implementation.
     */
    fun onIntent(intent: Intent) {
        handleIntent(intent)
    }

    /**
     * Process an incoming [intent] and update state or emit effects accordingly.
     * Subclasses must implement this to wire each intent to the appropriate business logic.
     */
    protected abstract fun handleIntent(intent: Intent)

    /**
     * Atomically update the UI state using a reducer function.
     *
     * ```kotlin
     * updateState { copy(isLoading = true) }
     * ```
     */
    protected fun updateState(reducer: State.() -> State) {
        _state.update(reducer)
    }

    /**
     * Emit a one-shot side effect to the UI.
     * Effects are delivered exactly once via a buffered [Channel].
     *
     * ```kotlin
     * sendEffect(HomeContract.Effect.NavigateToDetail(diaryId))
     * ```
     */
    protected fun sendEffect(effect: Effect) {
        scope.launch {
            _effects.send(effect)
        }
    }

    /**
     * Called when this ViewModel is no longer used and will be destroyed.
     * Cancels the [scope] to prevent coroutine leaks.
     * koin-compose-viewmodel calls this automatically on all platforms.
     */
    override fun onCleared() {
        super.onCleared()
        scope.cancel()
    }
}
