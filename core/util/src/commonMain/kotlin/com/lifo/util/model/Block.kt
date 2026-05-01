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

/**
 * Block taxonomy. Display labels + Eve coaching sentence resolved at UI layer
 * via `Strings.Block.X` (see `core/ui/.../i18n/Strings.kt`). Storing localizable
 * strings here would create an upward `core/util -> core/ui` dependency.
 */
@Serializable
enum class BlockType {
    FEAR_OF_FAILURE,
    OVERLOAD,
    LIMITING_BELIEF,
    CREATIVE_BLOCK,
    UNKNOWN,
}

/**
 * Resolution method tag. Stored canonical (`.name`); display is unused in UI today
 * (kept as enum for serialization compatibility with `Block.resolution`).
 */
@Serializable
enum class BlockResolution {
    BRAIN_DUMP,
    REFRAMING,
    MOVEMENT,
    MEDITATION,
    JOURNALING,
    TALKED_TO_SOMEONE,
    RESOLVED_ITSELF,
    OTHER,
}
