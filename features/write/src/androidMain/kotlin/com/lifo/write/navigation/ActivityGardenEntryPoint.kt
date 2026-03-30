package com.lifo.write.navigation

import androidx.compose.runtime.Composable
import com.lifo.write.ActivityGardenScreen

@Composable
fun ActivityGardenRouteContent(
    onBackPressed: () -> Unit = {},
    onMenuClicked: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onWriteClick: () -> Unit = {},
    onBrainDumpClick: () -> Unit = {},
    onGratitudeClick: () -> Unit = {},
    onMeditationClick: () -> Unit = {},
    onReframeClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    onRecurringThoughtsClick: () -> Unit = {},
    onEnergyClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    onMovementClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onValuesClick: () -> Unit = {},
    onIkigaiClick: () -> Unit = {},
    onAweClick: () -> Unit = {},
    onSilenceClick: () -> Unit = {},
    onConnectionClick: () -> Unit = {},
    onInspirationClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {},
    onEnvironmentClick: () -> Unit = {},
) {
    ActivityGardenScreen(
        onBackPressed = onBackPressed,
        onMenuClicked = onMenuClicked,
        onNotificationsClick = onNotificationsClick,
        onWriteClick = onWriteClick,
        onBrainDumpClick = onBrainDumpClick,
        onGratitudeClick = onGratitudeClick,
        onMeditationClick = onMeditationClick,
        onReframeClick = onReframeClick,
        onBlockClick = onBlockClick,
        onRecurringThoughtsClick = onRecurringThoughtsClick,
        onEnergyClick = onEnergyClick,
        onSleepClick = onSleepClick,
        onMovementClick = onMovementClick,
        onDashboardClick = onDashboardClick,
        onValuesClick = onValuesClick,
        onIkigaiClick = onIkigaiClick,
        onAweClick = onAweClick,
        onSilenceClick = onSilenceClick,
        onConnectionClick = onConnectionClick,
        onInspirationClick = onInspirationClick,
        onHabitsClick = onHabitsClick,
        onEnvironmentClick = onEnvironmentClick,
    )
}
