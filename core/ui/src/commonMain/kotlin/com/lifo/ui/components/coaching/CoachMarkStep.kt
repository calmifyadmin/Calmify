package com.lifo.ui.components.coaching

/**
 * A single step in a coach mark tutorial sequence.
 *
 * @param title         Short, warm title shown in the tooltip card.
 * @param description   One or two sentences explaining the feature.
 * @param buttonText    Label for the advance button (last step uses "Capito!").
 * @param targetKey     Identifies which element to spotlight; null = centered card with no spotlight.
 */
data class CoachMarkStep(
    val title: String,
    val description: String,
    val buttonText: String = "Avanti",
    val targetKey: String? = null,
)

/** Keys used to identify coach-mark target elements via [Modifier.coachMarkTarget]. */
object CoachMarkKeys {
    // Home
    const val HOME_GREETING    = "home_greeting"
    const val HOME_QUICK_ACTIONS = "home_quick_actions"
    const val HOME_MOOD        = "home_mood"
    const val HOME_AVATAR      = "home_avatar"

    // Il Tuo Percorso (PercorsoScreen / WellbeingSnapshot)
    const val PERCORSO_PATTERNS   = "percorso_patterns"
    const val PERCORSO_TREND      = "percorso_trend"
    const val PERCORSO_SUGGESTIONS = "percorso_suggestions"

    // Chat AI
    const val CHAT_INTRO   = "chat_intro"
    const val CHAT_VOICE   = "chat_voice"

    // Diario / Write
    const val WRITE_EDITOR = "write_editor"
    const val WRITE_MOOD   = "write_mood"
}
