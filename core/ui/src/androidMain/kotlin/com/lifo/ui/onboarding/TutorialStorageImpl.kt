package com.lifo.ui.onboarding

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "calmify_tutorial_prefs"
private const val KEY_SEEN   = "seen_tutorial_keys"

/**
 * Android implementation of [TutorialStorage] backed by plain SharedPreferences.
 *
 * Not encrypted — tutorial flags are not sensitive data.
 * Registered in [com.lifo.calmifyapp.di.KoinModules] as a singleton.
 */
class TutorialStorageImpl(context: Context) : TutorialStorage {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadSeenKeys(): Set<String> =
        prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()

    override fun saveSeenKeys(keys: Set<String>) {
        prefs.edit { putStringSet(KEY_SEEN, keys) }
    }
}
