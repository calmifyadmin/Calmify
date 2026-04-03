package com.lifo.history

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.lifo.ui.components.CalmifyTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onMenuClicked: () -> Unit
) {
    CalmifyTopBar(
        title = "Activity",
        onMenuClick = onMenuClicked,
        scrollBehavior = scrollBehavior,
    )
}
