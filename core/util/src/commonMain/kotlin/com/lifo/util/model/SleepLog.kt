package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * SleepLog — daily sleep tracking.
 *
 * Tracks bedtime, waketime, quality, and disturbance factors.
 * One entry per day (dayKey-based).
 */
@OptIn(ExperimentalUuidApi::class)
data class SleepLog(
    val id: String = Uuid.random().toString(),
    val ownerId: String = "",
    val dayKey: String = "",
    val timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val timezone: String = "",
    val bedtimeHour: Int = 23,        // 0-23
    val bedtimeMinute: Int = 0,       // 0-59
    val waketimeHour: Int = 7,        // 0-23
    val waketimeMinute: Int = 0,      // 0-59
    val quality: Int = 3,             // 1-5 perceived quality
    val disturbances: List<SleepDisturbance> = emptyList(),
    val screenFreeLastHour: Boolean = false,
    val notes: String = "",
) {
    val sleepHours: Float
        get() {
            val bedMinutes = bedtimeHour * 60 + bedtimeMinute
            val wakeMinutes = waketimeHour * 60 + waketimeMinute
            val diff = if (wakeMinutes > bedMinutes) {
                wakeMinutes - bedMinutes
            } else {
                (24 * 60 - bedMinutes) + wakeMinutes
            }
            return diff / 60f
        }

    companion object {
        fun create(ownerId: String): SleepLog {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val localDate = now.toLocalDateTime(tz).date
            return SleepLog(
                ownerId = ownerId,
                dayKey = localDate.toString(),
                timestampMillis = now.toEpochMilliseconds(),
                timezone = tz.id
            )
        }
    }
}

enum class SleepDisturbance(val displayName: String) {
    RUMORE("Rumore"),
    LUCE("Luce"),
    TEMPERATURA("Temperatura"),
    STRESS("Stress/Pensieri"),
    DOLORE("Dolore fisico"),
    CAFFEINA("Caffeina"),
    SCHERMO("Schermi prima di dormire"),
    ALTRO("Altro"),
}
