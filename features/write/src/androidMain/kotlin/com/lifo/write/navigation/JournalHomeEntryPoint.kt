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
    onDiaryClick: (String) -> Unit,
    onInsightClick: (String) -> Unit,
    onMenuClicked: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
) {
    JournalHomeScreen(
        onWriteClick = onWriteClick,
        onDiaryClick = onDiaryClick,
        onInsightClick = onInsightClick,
        onMenuClicked = onMenuClicked,
        onNotificationsClick = onNotificationsClick,
    )
}
