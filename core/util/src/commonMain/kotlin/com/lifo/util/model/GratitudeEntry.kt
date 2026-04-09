package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GratitudeEntry — "3 Cose Belle" daily gratitude practice.
 *
 * Each entry captures up to 3 things the user is grateful for today.
 * Simple, quick, no pressure.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class GratitudeEntry(
    val id: String = Uuid.random().toString(),
    val ownerId: String = "",
    val dayKey: String = "",
    val timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val timezone: String = "",
    val item1: String = "",
    val item2: String = "",
    val item3: String = "",
    val category1: GratitudeCategory = GratitudeCategory.ALTRO,
    val category2: GratitudeCategory = GratitudeCategory.ALTRO,
    val category3: GratitudeCategory = GratitudeCategory.ALTRO,
) {
    val items: List<String>
        get() = listOf(item1, item2, item3).filter { it.isNotBlank() }

    val itemCount: Int
        get() = items.size

    companion object {
        fun create(ownerId: String): GratitudeEntry {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val localDate = now.toLocalDateTime(tz).date
            return GratitudeEntry(
                ownerId = ownerId,
                dayKey = localDate.toString(),
                timestampMillis = now.toEpochMilliseconds(),
                timezone = tz.id
            )
        }
    }
}

@Serializable
enum class GratitudeCategory(val displayName: String) {
    PERSONE("Persone"),
    NATURA("Natura"),
    LAVORO("Lavoro"),
    SALUTE("Salute"),
    SE_STESSI("Se stessi"),
    CREATIVITA("Creativita'"),
    ALTRO("Altro"),
}
