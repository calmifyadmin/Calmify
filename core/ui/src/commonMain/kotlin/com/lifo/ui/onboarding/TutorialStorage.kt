package com.lifo.ui.onboarding

/**
 * Platform-neutral interface for persisting which tutorials have been seen.
 *
 * Implement this in androidMain using SharedPreferences (see [TutorialStorageImpl]).
 * Stub implementations for desktop/iOS can return empty sets and no-op saves.
 */
interface TutorialStorage {
    fun loadSeenKeys(): Set<String>
    fun saveSeenKeys(keys: Set<String>)
}
