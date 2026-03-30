package com.lifo.write.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lifo.write.GardenScreen
import com.lifo.write.GardenViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GardenRouteContent(
    onActivityClick: (String) -> Unit,
    onMenuClicked: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
) {
    val viewModel: GardenViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    GardenScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onActivityClick = onActivityClick,
        onMenuClicked = onMenuClicked,
        unreadNotificationCount = unreadNotificationCount,
        onNotificationsClick = onNotificationsClick,
    )
}
