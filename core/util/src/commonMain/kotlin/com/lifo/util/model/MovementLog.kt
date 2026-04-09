package com.lifo.util.model

import kotlinx.serialization.Serializable

/**
 * A movement/exercise log entry.
 * Simple — not a fitness tracker, just awareness.
 */
@Serializable
data class MovementLog(
    val id: String = "",
    val ownerId: String = "",
    val timestampMillis: Long = 0L,
    val dayKey: String = "",
    val movementType: MovementType = MovementType.CAMMINATA,
    val durationMinutes: Int = 20,
    val feelingAfter: PostMovementFeeling = PostMovementFeeling.MEGLIO,
    val note: String = "",
)

@Serializable
enum class PostMovementFeeling(val displayName: String) {
    MEGLIO("Meglio"),
    UGUALE("Uguale"),
    PEGGIO("Peggio"),
}
