package com.lifo.home.util

import com.lifo.ui.i18n.Strings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource

/**
 * Date utilities for the Home screen.
 *
 * History: this object previously held ~10 hardcoded-Italian date/time helpers
 * (formatFullDate, formatMediumDate, getRelativeTime, formatSectionHeader, etc.)
 * — all dead code as of i18n Phase J Tier 3 cleanup. Removed per CLAUDE.md rule 12.
 *
 * Surviving API: `getTimeOfDayGreetingRes()` — returns a localized greeting StringResource
 * resolved by the caller via `stringResource(...)` in a @Composable scope.
 */
object DateFormatters {

    /**
     * Get time of day greeting as a StringResource based on the current local hour.
     * Caller resolves with `stringResource(DateFormatters.getTimeOfDayGreetingRes())`.
     */
    fun getTimeOfDayGreetingRes(): StringResource {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return when {
            hour < 5 -> Strings.DateTime.greetingNight
            hour < 12 -> Strings.DateTime.greetingMorning
            hour < 18 -> Strings.DateTime.greetingAfternoon
            hour < 22 -> Strings.DateTime.greetingEvening
            else -> Strings.DateTime.greetingNight
        }
    }
}
