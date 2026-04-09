package com.lifo.home.util

import com.lifo.home.domain.model.TimePeriod
import kotlinx.datetime.*

/**
 * Date Formatting Utilities for Home Screen
 * Italian-focused formatters with manual formatting (KMP-safe, no java.time)
 */
object DateFormatters {

    // ==================== ITALIAN MONTH / DAY NAMES ====================

    private val italianMonthsFull = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )

    private val italianMonthsShort = listOf(
        "Gen", "Feb", "Mar", "Apr", "Mag", "Giu",
        "Lug", "Ago", "Set", "Ott", "Nov", "Dic"
    )

    private val italianMonthsAbbrev = listOf(
        "gen", "feb", "mar", "apr", "mag", "giu",
        "lug", "ago", "set", "ott", "nov", "dic"
    )

    // ==================== FORMAT FUNCTIONS ====================

    /** Full date: "24 Dicembre 2025" */
    fun formatFullDate(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.dayOfMonth} ${italianMonthsFull[dt.monthNumber - 1]} ${dt.year}"
    }

    /** Medium date: "24 dic 2025" */
    fun formatMediumDate(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.dayOfMonth} ${italianMonthsAbbrev[dt.monthNumber - 1]} ${dt.year}"
    }

    /** Short date: "24/12/25" */
    fun formatShortDate(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val day = dt.dayOfMonth.toString().padStart(2, '0')
        val month = dt.monthNumber.toString().padStart(2, '0')
        val year = (dt.year % 100).toString().padStart(2, '0')
        return "$day/$month/$year"
    }

    /** Time only: "14:30" */
    fun formatTime(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val h = dt.hour.toString().padStart(2, '0')
        val m = dt.minute.toString().padStart(2, '0')
        return "$h:$m"
    }

    /** Day and month: "24 dic" */
    fun formatDayMonth(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.dayOfMonth} ${italianMonthsAbbrev[dt.monthNumber - 1]}"
    }

    /** Day and month from LocalDate: "24 dic" */
    fun formatDayMonth(date: LocalDate): String {
        return "${date.dayOfMonth} ${italianMonthsAbbrev[date.monthNumber - 1]}"
    }

    /** Month full name: "Dicembre" */
    fun formatMonth(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return italianMonthsFull[dt.monthNumber - 1]
    }

    /** Short month: "Dic" */
    fun formatShortMonth(date: LocalDate): String {
        return italianMonthsShort[date.monthNumber - 1]
    }

    // ==================== RELATIVE TIME FORMATTING ====================

    /**
     * Get relative time string: "2 ore fa", "Ieri", "3 giorni fa"
     */
    fun getRelativeTime(dateTime: Instant): String {
        val now = Clock.System.now()
        val duration = now - dateTime
        val totalMinutes = duration.inWholeMinutes
        val totalHours = duration.inWholeHours
        val totalDays = duration.inWholeDays

        return when {
            totalMinutes < 1 -> "Ora"
            totalMinutes < 60 -> "$totalMinutes ${if (totalMinutes == 1L) "minuto" else "minuti"} fa"
            totalHours < 24 -> "$totalHours ${if (totalHours == 1L) "ora" else "ore"} fa"
            totalDays < 1 -> "Oggi"
            totalDays < 2 -> "Ieri"
            totalDays < 7 -> "$totalDays giorni fa"
            totalDays < 14 -> "Settimana scorsa"
            totalDays < 30 -> "${totalDays / 7} settimane fa"
            totalDays < 60 -> "Mese scorso"
            totalDays < 365 -> "${totalDays / 30} mesi fa"
            else -> "${totalDays / 365} ${if (totalDays / 365 == 1L) "anno" else "anni"} fa"
        }
    }

    /**
     * Get time of day greeting
     */
    fun getTimeOfDayGreeting(): String {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
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
    fun getTimePeriod(dateTime: Instant): TimePeriod {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val targetDate = dateTime.toLocalDateTime(tz).date
        val daysDiff = targetDate.daysUntil(today).toLong()

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
    fun formatSectionHeader(period: TimePeriod, date: Instant? = null): String {
        return when (period) {
            TimePeriod.TODAY -> "Oggi"
            TimePeriod.YESTERDAY -> "Ieri"
            TimePeriod.THIS_WEEK -> "Questa settimana"
            TimePeriod.LAST_WEEK -> "Settimana scorsa"
            TimePeriod.THIS_MONTH -> "Questo mese"
            TimePeriod.OLDER -> date?.let { formatMonth(it) } ?: "Più vecchi"
        }
    }

    // ==================== WEEK HELPERS ====================

    /**
     * Get week range string: "16 - 22 Dic"
     */
    fun getWeekRangeString(weekOffset: Int = 0): String {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val currentDayOfWeek = today.dayOfWeek.isoDayNumber // 1 = Monday
        val daysFromMonday = currentDayOfWeek - 1

        val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
            .plus(weekOffset * 7, DateTimeUnit.DAY)
        val sunday = monday.plus(6, DateTimeUnit.DAY)

        val mondayStr = formatDayMonth(monday)
        val sundayStr = formatDayMonth(sunday)

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
     * Get day label for chart (L, M, M, G, V, S, D)
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
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return (0 until months).map { offset ->
            val targetDate = today.minus((months - 1 - offset), DateTimeUnit.MONTH)
            formatShortMonth(targetDate)
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
