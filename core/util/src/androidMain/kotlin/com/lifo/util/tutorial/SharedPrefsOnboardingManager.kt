package com.lifo.util.tutorial

import android.content.Context

/**
 * SharedPreferences-backed implementation.
 * Stores each tutorial key as a Boolean flag under "calmify_tutorials".
 */
class SharedPrefsOnboardingManager(context: Context) : OnboardingManager {

    private val prefs = context.getSharedPreferences("calmify_tutorials", Context.MODE_PRIVATE)

    override fun isTutorialSeen(key: String): Boolean = prefs.getBoolean(key, false)

    override fun markTutorialSeen(key: String) {
        prefs.edit().putBoolean(key, true).apply()
    }
}
