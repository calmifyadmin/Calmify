package com.lifo.util.model

import kotlinx.serialization.Serializable

/**
 * AweEntry — "Momenti di Meraviglia" journal entry.
 *
 * Captures moments of awe, wonder, and connection with nature/beauty.
 */
@Serializable
data class AweEntry(
    val id: String = "",
    val ownerId: String = "",
    val description: String = "",
    val context: String = "",
    val photoUrl: String? = null,
    val timestampMillis: Long = 0L,
    val dayKey: String = "",
)
