package com.lifo.write.navigation

import androidx.compose.runtime.Composable
import com.lifo.write.JournalHomeScreen

/**
 * Public entry point composable for the Journal Home screen.
 * Used by DecomposeApp to render the JournalHome destination.
 */
@Composable
fun JournalHomeRouteContent(
    onWriteClick: () -> Unit,
    onBrainDumpClick: () -> Unit = {},
    onGratitudeClick: () -> Unit = {},
    onDiaryClick: (String) -> Unit,
    onInsightClick: (String) -> Unit,
    onMenuClicked: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onEnergyClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {},
    onMeditationClick: () -> Unit = {},
    onMovementClick: () -> Unit = {},
    onAllActivitiesClick: () -> Unit = {},
) {
    JournalHomeScreen(
        onWriteClick = onWriteClick,
        onBrainDumpClick = onBrainDumpClick,
        onGratitudeClick = onGratitudeClick,
        onDiaryClick = onDiaryClick,
        onInsightClick = onInsightClick,
        onMenuClicked = onMenuClicked,
        onNotificationsClick = onNotificationsClick,
        onEnergyClick = onEnergyClick,
        onSleepClick = onSleepClick,
        onHabitsClick = onHabitsClick,
        onMeditationClick = onMeditationClick,
        onMovementClick = onMovementClick,
        onAllActivitiesClick = onAllActivitiesClick,
    )
}
