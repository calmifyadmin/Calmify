package com.lifo.home.util

import com.lifo.home.domain.model.TimePeriod
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Date Formatting Utilities for Home Screen
 * Italian-focused formatters with Material3 design patterns
 */
object DateFormatters {

    // ==================== LOCALE ====================

    private val italianLocale = Locale.ITALIAN

    // ==================== FORMATTERS ====================

    // Full date: "24 Dicembre 2025"
    val fullDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.LONG)
        .withLocale(italianLocale)

    // Medium date: "24 dic 2025"
    val mediumDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(italianLocale)

    // Short date: "24/12/25"
    val shortDateFormatter: DateTimeFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.SHORT)
        .withLocale(italianLocale)

    // Time only: "14:30"
    val timeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(italianLocale)

    // Day and month: "24 dic"
    val dayMonthFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("d MMM", italianLocale)

    // Month only: "Dicembre"
    val monthFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMMM", italianLocale)

    // Short month: "Dic"
    val shortMonthFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MMM", italianLocale)

    // Day name: "Martedì"
    val dayNameFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("EEEE", italianLocale)

    // Short day name: "Mar"
    val shortDayNameFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("EEE", italianLocale)

    // Day letter: "M" (for chart labels)
    val dayLetterFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("E", italianLocale)

    // ==================== RELATIVE TIME FORMATTING ====================

    /**
     * Get relative time string: "2 ore fa", "Ieri", "3 giorni fa"
     */
    fun getRelativeTime(dateTime: ZonedDateTime): String {
        val now = ZonedDateTime.now()
        val duration = Duration.between(dateTime, now)

        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()

        return when {
            minutes < 1 -> "Ora"
            minutes < 60 -> "$minutes ${if (minutes == 1L) "minuto" else "minuti"} fa"
            hours < 24 -> "$hours ${if (hours == 1L) "ora" else "ore"} fa"
            days < 1 -> "Oggi"
            days < 2 -> "Ieri"
            days < 7 -> "$days giorni fa"
            days < 14 -> "Settimana scorsa"
            days < 30 -> "${days / 7} settimane fa"
            days < 60 -> "Mese scorso"
            days < 365 -> "${days / 30} mesi fa"
            else -> "${days / 365} ${if (days / 365 == 1L) "anno" else "anni"} fa"
        }
    }

    /**
     * Get time of day greeting
     */
    fun getTimeOfDayGreeting(): String {
        val hour = LocalTime.now().hour
        return when {
            hour < 5 -> "Buonanotte"
            hour < 12 -> "Buongiorno"
            hour < 18 -> "Buon pomeriggio"
            hour < 22 -> "Buonasera"
            else -> "Buonanotte"
        }
    }

    // ==================== TIME PERIOD HELPERS ====================

    /**
     * Get TimePeriod for a given date
     */
    fun getTimePeriod(dateTime: ZonedDateTime): TimePeriod {
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val targetDate = dateTime.toLocalDate()
        val daysDiff = ChronoUnit.DAYS.between(targetDate, today)

        return when {
            daysDiff == 0L -> TimePeriod.TODAY
            daysDiff == 1L -> TimePeriod.YESTERDAY
            daysDiff < 7 -> TimePeriod.THIS_WEEK
            daysDiff < 14 -> TimePeriod.LAST_WEEK
            daysDiff < 30 -> TimePeriod.THIS_MONTH
            else -> TimePeriod.OLDER
        }
    }

    /**
     * Format date for section headers
     */
    fun formatSectionHeader(period: TimePeriod, date: ZonedDateTime? = null): String {
        return when (period) {
            TimePeriod.TODAY -> "Oggi"
            TimePeriod.YESTERDAY -> "Ieri"
            TimePeriod.THIS_WEEK -> "Questa settimana"
            TimePeriod.LAST_WEEK -> "Settimana scorsa"
            TimePeriod.THIS_MONTH -> "Questo mese"
            TimePeriod.OLDER -> date?.let { monthFormatter.format(it) } ?: "Più vecchi"
        }
    }

    // ==================== WEEK HELPERS ====================

    /**
     * Get week range string: "16 - 22 Dic"
     */
    fun getWeekRangeString(weekOffset: Int = 0): String {
        val now = LocalDate.now()
        val currentDayOfWeek = now.dayOfWeek.value // 1 = Monday
        val daysFromMonday = currentDayOfWeek - 1

        val monday = now.minusDays(daysFromMonday.toLong()).plusWeeks(weekOffset.toLong())
        val sunday = monday.plusDays(6)

        val mondayStr = dayMonthFormatter.format(monday)
        val sundayStr = dayMonthFormatter.format(sunday)

        return "$mondayStr - $sundayStr"
    }

    /**
     * Get week label for navigation
     */
    fun getWeekLabel(weekOffset: Int): String {
        return when {
            weekOffset == 0 -> "Questa settimana"
            weekOffset == -1 -> "Settimana scorsa"
            weekOffset < 0 -> "${-weekOffset} settimane fa"
            weekOffset == 1 -> "Prossima settimana"
            else -> "Tra $weekOffset settimane"
        }
    }

    // ==================== CHART LABEL HELPERS ====================

    /**
     * Get day label for chart (M, T, W, T, F, S, S)
     */
    fun getDayLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "L" // Lunedì
            2 -> "M" // Martedì
            3 -> "M" // Mercoledì
            4 -> "G" // Giovedì
            5 -> "V" // Venerdì
            6 -> "S" // Sabato
            7 -> "D" // Domenica
            else -> "?"
        }
    }

    /**
     * Get full day name in Italian
     */
    fun getFullDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Lunedì"
            2 -> "Martedì"
            3 -> "Mercoledì"
            4 -> "Giovedì"
            5 -> "Venerdì"
            6 -> "Sabato"
            7 -> "Domenica"
            else -> "?"
        }
    }

    /**
     * Get month labels for wellbeing chart (Gen, Feb, Mar, ...)
     */
    fun getMonthLabels(months: Int = 4): List<String> {
        val now = LocalDate.now()
        return (0 until months).map { offset ->
            shortMonthFormatter.format(now.minusMonths((months - 1 - offset).toLong()))
        }
    }

    // ==================== STREAK HELPERS ====================

    /**
     * Format streak message
     */
    fun formatStreakMessage(days: Int): String {
        return when {
            days == 0 -> "Inizia il tuo streak oggi!"
            days == 1 -> "1 giorno di streak"
            else -> "$days giorni di streak"
        }
    }

    /**
     * Format days since message
     */
    fun formatDaysSince(days: Int): String {
        return when {
            days == 0 -> "Oggi"
            days == 1 -> "Ieri"
            days < 7 -> "$days giorni fa"
            days == 7 -> "1 settimana fa"
            days < 30 -> "${days / 7} settimane fa"
            else -> "${days / 30} ${if (days / 30 == 1) "mese" else "mesi"} fa"
        }
    }

    // ==================== SNAPSHOT HELPERS ====================

    /**
     * Format last snapshot date for display
     */
    fun formatLastSnapshotDate(days: Int): String {
        return when {
            days == 0 -> "Ultimo: oggi"
            days == 1 -> "Ultimo: ieri"
            else -> "Ultimo: ${days}g fa"
        }
    }

    /**
     * Check if snapshot is due (>= 7 days since last)
     */
    fun isSnapshotDue(daysSinceLastSnapshot: Int): Boolean {
        return daysSinceLastSnapshot >= 7
    }
}
