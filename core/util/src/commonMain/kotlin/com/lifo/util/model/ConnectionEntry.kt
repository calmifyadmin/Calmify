package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ConnectionEntry — "Le Tue Connessioni" relational gratitude & service acts.
 */
@OptIn(ExperimentalUuidApi::class)
data class ConnectionEntry(
    val id: String = Uuid.random().toString(),
    val ownerId: String = "",
    val dayKey: String = "",
    val timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val type: ConnectionType = ConnectionType.GRATITUDE,
    val personName: String = "",
    val description: String = "",
    val expressed: Boolean = false,
) {
    companion object {
        fun create(ownerId: String, type: ConnectionType): ConnectionEntry {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val localDate = now.toLocalDateTime(tz).date
            return ConnectionEntry(
                ownerId = ownerId,
                dayKey = localDate.toString(),
                timestampMillis = now.toEpochMilliseconds(),
                type = type,
            )
        }
    }
}

enum class ConnectionType(val displayName: String) {
    GRATITUDE("Gratitudine relazionale"),
    SERVICE("Atto di servizio"),
    QUALITY_TIME("Tempo di qualita'"),
}

/**
 * Monthly relationship reflection.
 */
data class RelationshipReflection(
    val id: String = "",
    val ownerId: String = "",
    val monthKey: String = "",
    val timestampMillis: Long = 0L,
    val nurturingRelationships: List<String> = emptyList(),
    val drainingRelationships: List<String> = emptyList(),
    val intention: String = "",
)
