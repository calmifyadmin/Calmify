package com.lifo.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Unified top bar for all main tab screens.
 *
 * - Title: headlineSmall SemiBold
 * - Navigation icon: hamburger menu only when [onMenuClick] is provided (Home only)
 * - Scroll behavior: pass [scrollBehavior] to enable enterAlways hide-on-scroll (YouTube-style).
 *   The caller must also apply `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to the Scaffold.
 * - containerColor: Transparent (inherits from Scaffold background)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalmifyTopBar(
    title: String,
    onMenuClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}
