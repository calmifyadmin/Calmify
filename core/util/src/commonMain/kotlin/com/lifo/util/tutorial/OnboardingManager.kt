package com.lifo.util.tutorial

/**
 * Tracks which coach-mark tutorials the user has already seen.
 * Interface lives in commonMain; the Android implementation uses SharedPreferences.
 */
interface OnboardingManager {
    fun isTutorialSeen(key: String): Boolean
    fun markTutorialSeen(key: String)
}

/**
 * Stable keys for each tutorial flow.
 */
object TutorialKey {
    const val HOME      = "coach_home"
    const val PERCORSO  = "coach_percorso"
    const val CHAT      = "coach_chat"
    const val DIARIO    = "coach_diario"
}
