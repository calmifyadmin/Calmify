package com.lifo.util.model

/**
 * IkigaiExploration — "Il Tuo Ikigai" guided exploration.
 *
 * 4 circles:
 * - Passion: what you love doing
 * - Talent: what you're naturally good at
 * - Mission: what the world needs
 * - Profession: what you could be paid for
 */
data class IkigaiExploration(
    val id: String = "",
    val ownerId: String = "",
    val passionItems: List<String> = emptyList(),
    val talentItems: List<String> = emptyList(),
    val missionItems: List<String> = emptyList(),
    val professionItems: List<String> = emptyList(),
    val aiInsight: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)
