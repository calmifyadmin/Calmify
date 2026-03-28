package com.lifo.util.model

/**
 * AweEntry — "Momenti di Meraviglia" journal entry.
 *
 * Captures moments of awe, wonder, and connection with nature/beauty.
 */
data class AweEntry(
    val id: String = "",
    val ownerId: String = "",
    val description: String = "",
    val context: String = "",
    val photoUrl: String? = null,
    val timestampMillis: Long = 0L,
    val dayKey: String = "",
)
