package com.lifo.ui.components.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.math.ln

/**
 * Navigation destination model
 */
open class NavigationDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val hasNews: Boolean = false,
    val badge: String? = null
) {
    object Home : NavigationDestination(
        route = "home_screen",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object History : NavigationDestination(
        route = "history_screen",
        label = "Activity",
        selectedIcon = Icons.Filled.Notifications,
        unselectedIcon = Icons.Outlined.Notifications
    )

    object Settings : NavigationDestination(
        route = "settings_screen",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    object Write : NavigationDestination(
        route = "write_screen",
        label = "Write",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    )

    object Profile : NavigationDestination(
        route = "profile_screen",
        label = "Profilo",
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics
    )

    object Humanoid : NavigationDestination(
        route = "humanoid_screen",
        label = "Avatar",
        selectedIcon = Icons.Filled.Face,
        unselectedIcon = Icons.Outlined.Face
    )

    object Feed : NavigationDestination(
        route = "feed_screen",
        label = "Feed",
        selectedIcon = Icons.Filled.DynamicFeed,
        unselectedIcon = Icons.Outlined.DynamicFeed
    )

    object Journal : NavigationDestination(
        route = "journal_home_screen",
        label = "Journal",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    )

    object AIChat : NavigationDestination(
        route = "chat_screen",
        label = "AI Chat",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
    )

    object Community : NavigationDestination(
        route = "community_screen",
        label = "Community",
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups
    )

    object Journey : NavigationDestination(
        route = "profile_screen",
        label = "Percorso",
        selectedIcon = Icons.Filled.TrendingUp,
        unselectedIcon = Icons.Outlined.TrendingUp
    )
}

/**
 * Material 3 Navigation Bar per Calmify
 */
@Composable
fun CalmifyNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
    destinations: List<NavigationDestination> = listOf(
        NavigationDestination.Home,
        NavigationDestination.Write,
        NavigationDestination.Profile
    )
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp
    ) {
        destinations.forEach { destination ->
            // Check if the current destination is selected
            val selected = currentDestination?.hierarchy?.any { navDestination ->
                navDestination.route == destination.route ||
                        navDestination.route?.startsWith(destination.route) == true
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        // Pop up to the start destination to avoid building up a back stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (destination.badge != null) {
                                Badge {
                                    Text(
                                        text = destination.badge,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            } else if (destination.hasNews) {
                                Badge(
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = null
                        )
                    }
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Alternative style con animazioni personalizzate (opzionale)
 */
@Composable
fun CalmifyNavigationBarAnimated(
    navController: NavController,
    modifier: Modifier = Modifier,
    destinations: List<NavigationDestination> = listOf(
        NavigationDestination.Home,
        NavigationDestination.Write,
        NavigationDestination.Profile
    )
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(80.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route == destination.route
                } == true

                NavigationBarItemAnimated(
                    selected = selected,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    label = destination.label,
                    badge = destination.badge,
                    hasNews = destination.hasNews,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Animated navigation item
 */
@Composable
private fun NavigationBarItemAnimated(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    badge: String?,
    hasNews: Boolean,
    modifier: Modifier = Modifier
) {
    val animationProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "NavigationItemAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    bounded = false,
                    radius = 32.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            .clearAndSetSemantics {
                contentDescription = label
            },
        contentAlignment = Alignment.Center
    ) {
        // Background pill for selected state
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = 0.9f + (0.1f * animationProgress)
                            scaleY = 0.9f + (0.1f * animationProgress)
                        },
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // Badge
                if (badge != null) {
                    Badge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (hasNews) {
                    // Notification dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = 0.dp)
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    )
                }
            }

            // Animated label
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Extension per creare elevation colors
 */
@Composable
fun ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    return if (this.surface == this.surfaceVariant) {
        this.surface
    } else {
        val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
        this.primary.copy(alpha = alpha).compositeOver(this.surface)
    }
}

/**
 * Utility function per color composition
 */
fun Color.compositeOver(background: Color): Color {
    val fg = this
    val bg = background

    val a = fg.alpha + bg.alpha * (1 - fg.alpha)
    val r = (fg.red * fg.alpha + bg.red * bg.alpha * (1 - fg.alpha)) / a
    val g = (fg.green * fg.alpha + bg.green * bg.alpha * (1 - fg.alpha)) / a
    val b = (fg.blue * fg.alpha + bg.blue * bg.alpha * (1 - fg.alpha)) / a

    return Color(r, g, b, a)
}

/**
 * NavigationBar Material 3 conforme alle specifiche ufficiali Android
 *
 * Implementazione standard seguendo:
 * - Material Design 3 Navigation Bar specifications
 * - Android Developer official guidelines
 * - NavigationBar è PERSISTENTE (no scroll behavior per spec M3)
 * - WindowInsets handling con NavigationBarDefaults
 * - Per 3-5 destinazioni principali (spec M3)
 *
 * Riferimenti ufficiali:
 * - https://developer.android.com/develop/ui/compose/components/navigation-bar
 * - https://m3.material.io/components/navigation-bar
 *
 * @param navController Controller per la navigazione
 * @param modifier Modificatore esterno
 * @param destinations Lista di destinazioni (max 5 per spec M3)
 */
@Composable
fun CalmifyBottomAppBar(
    navController: NavController,
    modifier: Modifier = Modifier,
    destinations: List<NavigationDestination> = listOf(
        NavigationDestination.Home,
        NavigationDestination.History,
        NavigationDestination.Profile
    )
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // NavigationBar standard M3 - persistente e integrata nel Scaffold
    NavigationBar(
        modifier = modifier,
        windowInsets = NavigationBarDefaults.windowInsets, // WindowInsets ufficiali M3
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        destinations.forEach { destination ->
            // Determina se la destinazione è selezionata
            val selected = currentDestination?.hierarchy?.any { navDestination ->
                navDestination.route == destination.route ||
                        navDestination.route?.startsWith(destination.route) == true
            } == true

            // NavigationBarItem standard M3 - garantisce accessibilità e layout corretto
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        // Pop fino alla start destination per back stack pulito
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    // Badge per notifiche/contatori
                    BadgedBox(
                        badge = {
                            when {
                                destination.badge != null -> {
                                    Badge {
                                        Text(
                                            text = destination.badge,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                destination.hasNews -> {
                                    Badge(modifier = Modifier.size(6.dp))
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = destination.label
                        )
                    }
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}
