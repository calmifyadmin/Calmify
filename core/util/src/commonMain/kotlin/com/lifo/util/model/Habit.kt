package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Habit — a tracked daily/recurring habit.
 *
 * "La disciplina non e' forza di volonta' — e' design."
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class Habit(
    val id: String = Uuid.random().toString(),
    val ownerId: String = "",
    val name: String = "",
    val description: String = "",
    val category: HabitCategory = HabitCategory.CRESCITA,
    val anchorHabit: String? = null,
    val minimumAction: String = "",
    val targetFrequency: HabitFrequency = HabitFrequency.DAILY,
    val reminderTime: String? = null,
    val createdAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val isActive: Boolean = true,
)

/**
 * HabitCompletion — a single completion record for a habit on a day.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class HabitCompletion(
    val id: String = Uuid.random().toString(),
    val habitId: String = "",
    val ownerId: String = "",
    val completedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val dayKey: String = "",
    val note: String? = null,
)

@Serializable
enum class HabitCategory(val displayName: String) {
    MENTE("Mente"),
    CORPO("Corpo"),
    SPIRITO("Spirito"),
    RELAZIONI("Relazioni"),
    CRESCITA("Crescita"),
}

@Serializable
enum class HabitFrequency(val displayName: String) {
    DAILY("Ogni giorno"),
    WEEKDAYS("Giorni feriali"),
    WEEKENDS("Weekend"),
    THREE_TIMES_WEEK("3 volte/settimana"),
}
