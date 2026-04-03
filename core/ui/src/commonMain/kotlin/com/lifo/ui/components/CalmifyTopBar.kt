package com.lifo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.back_cd
import com.lifo.ui.resources.menu_cd
import org.jetbrains.compose.resources.stringResource

/**
 * Unified top bar for all screens.
 *
 * - Title: headlineSmall SemiBold
 * - Subtitle: bodySmall onSurfaceVariant, shown below title when provided
 * - Navigation icon priority: back arrow ([onBackClick]) > hamburger menu ([onMenuClick]) > none
 * - Scroll behavior: pass [scrollBehavior] to enable enterAlways hide-on-scroll.
 *   The caller must also apply `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to the Scaffold.
 * - containerColor: Transparent (inherits from Scaffold background)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalmifyTopBar(
    title: String,
    onMenuClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            if (subtitle != null) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        navigationIcon = {
            when {
                onBackClick != null -> IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                }
                onMenuClick != null -> IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(Res.string.menu_cd))
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
