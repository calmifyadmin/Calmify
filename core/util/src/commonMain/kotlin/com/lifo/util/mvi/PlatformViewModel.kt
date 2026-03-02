package com.lifo.util.mvi

/**
 * Platform-specific ViewModel base class.
 *
 * - Android: actual typealias to `androidx.lifecycle.ViewModel` (required by Koin's viewModel DSL)
 * - Desktop/iOS: empty abstract class
 */
expect abstract class PlatformViewModel() {
    protected open fun onCleared()
}
