package com.lifo.util.model

import kotlinx.serialization.Serializable

/**
 * A recorded mental block — what was blocking, type detected, resolution used.
 */
@Serializable
data class Block(
    val id: String = "",
    val ownerId: String = "",
    val timestampMillis: Long = 0L,
    val description: String = "",
    val type: BlockType = BlockType.UNKNOWN,
    val resolution: BlockResolution? = null,
    val resolutionNote: String = "",
    val isResolved: Boolean = false,
    val resolvedAtMillis: Long? = null,
)

@Serializable
enum class BlockType(val displayName: String, val suggestion: String) {
    FEAR_OF_FAILURE(
        "Paura del fallimento",
        "Qual e' la cosa piu' piccola che puoi fare ORA, anche imperfettamente?",
    ),
    OVERLOAD(
        "Sovraccarico mentale",
        "Hai troppo in testa. Facciamo un brain dump: scrivi TUTTO quello che ti gira in mente.",
    ),
    LIMITING_BELIEF(
        "Credenza limitante",
        "Quella e' una storia, non un fatto. Riformuliamo questo pensiero insieme.",
    ),
    CREATIVE_BLOCK(
        "Blocco creativo",
        "Quando la mente si arena, muovi il corpo. 20 minuti di camminata e poi ne riparliamo.",
    ),
    UNKNOWN(
        "Blocco generico",
        "Descrivimi cosa sta succedendo. Non devi analizzare, solo raccontare.",
    ),
}

@Serializable
enum class BlockResolution(val displayName: String) {
    BRAIN_DUMP("Brain Dump"),
    REFRAMING("Riformulazione"),
    MOVEMENT("Movimento"),
    MEDITATION("Meditazione"),
    JOURNALING("Scrittura libera"),
    TALKED_TO_SOMEONE("Ne ho parlato"),
    RESOLVED_ITSELF("Si e' risolto"),
    OTHER("Altro"),
}
