package com.lifo.ui.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages which screen tutorials have already been shown to the user.
 *
 * The actual persistence is handled by [TutorialStorage], which has a
 * platform-specific implementation (SharedPreferences on Android).
 *
 * Usage — in a screen composable:
 * ```
 * val manager = koinInject<OnboardingManager>()
 * val shouldShow = manager.shouldShowTutorial(ScreenTutorials.KEY_HOME)
 *
 * val coachState = rememberCoachMarkState(ScreenTutorials.home())
 * LaunchedEffect(shouldShow) {
 *     if (shouldShow) coachState.start()
 * }
 * CoachMarkOverlay(state = coachState, onFinished = { manager.markTutorialSeen(ScreenTutorials.KEY_HOME) })
 * ```
 */
class OnboardingManager(private val storage: TutorialStorage) {

    private val _seenKeys = MutableStateFlow<Set<String>>(emptySet())
    val seenKeys: StateFlow<Set<String>> = _seenKeys.asStateFlow()

    init {
        _seenKeys.value = storage.loadSeenKeys()
    }

    /** Returns `true` if the tutorial for [screenKey] has NOT been shown yet. */
    fun shouldShowTutorial(screenKey: String): Boolean = screenKey !in _seenKeys.value

    /** Persists [screenKey] as seen so it won't show again. */
    fun markTutorialSeen(screenKey: String) {
        val updated = _seenKeys.value + screenKey
        _seenKeys.value = updated
        storage.saveSeenKeys(updated)
    }

    /** Clears all seen keys — useful in dev/debug or account-reset flows. */
    fun resetAll() {
        _seenKeys.value = emptySet()
        storage.saveSeenKeys(emptySet())
    }
}
