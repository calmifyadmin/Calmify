package com.lifo.util.model

import kotlinx.serialization.Serializable

/**
 * RecurringThought — a thought pattern detected in diary entries.
 *
 * Tracks frequency, type (limiting/empowering/neutral),
 * and optional 90-day tracking post-reframing.
 */
@Serializable
data class RecurringThought(
    val id: String = "",
    val ownerId: String = "",
    val theme: String = "",
    val type: ThoughtType = ThoughtType.NEUTRAL,
    val occurrences: Int = 1,
    val firstSeenMillis: Long = 0L,
    val lastSeenMillis: Long = 0L,
    val reframedAtMillis: Long? = null,
    val reframeId: String? = null,
    val occurrencesPostReframe: Int = 0,
    val isResolved: Boolean = false,
)

@Serializable
enum class ThoughtType(val displayName: String) {
    LIMITING("Limitante"),
    EMPOWERING("Potenziante"),
    NEUTRAL("Neutro"),
}
