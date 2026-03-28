package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ThoughtReframe — CBT Lite "Laboratorio dei Pensieri"
 *
 * Captures the 3-step cognitive reframing process:
 * 1. Original negative thought
 * 2. Socratic questioning (evidence for/against)
 * 3. Reframed, credible alternative
 */
@OptIn(ExperimentalUuidApi::class)
data class ThoughtReframe(
    val id: String = Uuid.random().toString(),
    val ownerId: String = "",
    val timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val originalThought: String = "",
    val evidenceFor: String = "",
    val evidenceAgainst: String = "",
    val friendPerspective: String = "",
    val reframedThought: String = "",
    val category: ThoughtCategory = ThoughtCategory.ALTRO,
)

enum class ThoughtCategory(val displayName: String) {
    AUTOSTIMA("Autostima"),
    LAVORO("Lavoro"),
    RELAZIONI("Relazioni"),
    FUTURO("Futuro"),
    SALUTE("Salute"),
    ALTRO("Altro"),
}
