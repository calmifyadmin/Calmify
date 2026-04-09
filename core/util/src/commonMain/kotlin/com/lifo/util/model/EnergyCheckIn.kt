package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * EnergyCheckIn — "Come Sta il Tuo Corpo?" daily physical check-in.
 *
 * Quick 30-second body check: energy, sleep, water, movement, meals.
 * One entry per day (dayKey-based, same pattern as GratitudeEntry).
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class EnergyCheckIn(
    val id: String = Uuid.random().toString(),
    val ownerId: String = "",
    val dayKey: String = "",
    val timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val timezone: String = "",
    val energyLevel: Int = 5,           // 1-10 perceived energy
    val sleepHours: Float = 7f,         // hours of sleep last night
    val waterGlasses: Int = 0,          // glasses of water today
    val didMovement: Boolean = false,   // did any physical activity?
    val movementType: MovementType = MovementType.NESSUNO,
    val regularMeals: Boolean = true,   // ate regular meals?
) {
    companion object {
        fun create(ownerId: String): EnergyCheckIn {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val localDate = now.toLocalDateTime(tz).date
            return EnergyCheckIn(
                ownerId = ownerId,
                dayKey = localDate.toString(),
                timestampMillis = now.toEpochMilliseconds(),
                timezone = tz.id
            )
        }
    }
}

@Serializable
enum class MovementType(val displayName: String) {
    NESSUNO("Nessuno"),
    CAMMINATA("Camminata"),
    CORSA("Corsa"),
    PALESTRA("Palestra"),
    SPORT("Sport"),
    STRETCHING("Stretching"),
    YOGA("Yoga"),
    BICI("Bici"),
    NUOTO("Nuoto"),
    ALTRO("Altro"),
}
