package com.lifo.util.model

/**
 * ValuesDiscovery — "La Tua Bussola" guided values exploration.
 *
 * 4-step journey:
 * 1. "I Momenti Vivi" — 3 moments of authentic aliveness
 * 2. "Cosa Ti Indigna" — what angers you reveals values
 * 3. "La Domanda Finale" — end-of-life regret reflection
 * 4. "I Tuoi Valori" — AI synthesis + user confirmation
 */
data class ValuesDiscovery(
    val id: String = "",
    val ownerId: String = "",
    val completedSteps: Int = 0,
    val aliveMoments: List<String> = emptyList(),
    val indignationTopics: List<String> = emptyList(),
    val finalReflection: String = "",
    val discoveredValues: List<String> = emptyList(),
    val confirmedValues: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val lastReviewMillis: Long? = null,
    val nextReviewMillis: Long? = null,
)
