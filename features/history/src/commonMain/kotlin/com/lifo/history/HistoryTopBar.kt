package com.lifo.history

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.lifo.ui.components.CalmifyTopBar
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onMenuClicked: () -> Unit
) {
    CalmifyTopBar(
        title = stringResource(Res.string.history_title),
        onMenuClick = onMenuClicked,
        scrollBehavior = scrollBehavior,
    )
}
