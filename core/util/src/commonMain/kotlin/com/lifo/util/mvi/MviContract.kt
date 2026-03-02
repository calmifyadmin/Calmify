package com.lifo.util.mvi

/**
 * Marker interfaces for MVI contracts.
 *
 * Each ViewModel should define a sealed interface for each of these
 * inside a companion Contract object:
 *
 * ```kotlin
 * object HomeContract {
 *     sealed interface Intent : MviContract.Intent { ... }
 *     data class State(...) : MviContract.State
 *     sealed interface Effect : MviContract.Effect { ... }
 * }
 * ```
 */
interface MviContract {
    /** User actions / UI events dispatched to the ViewModel. */
    interface Intent

    /** Immutable representation of the screen's UI state. */
    interface State

    /** One-shot side effects (navigation, snackbar, toast, etc.). */
    interface Effect
}
