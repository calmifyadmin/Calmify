package com.lifo.ui.components.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.math.ln

/**
 * Navigation destination model
 */
sealed class NavigationDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val hasNews: Boolean = false,
    val badge: String? = null
) {
    object Home : NavigationDestination(
        route = "home_screen", // Match your actual route
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Reports : NavigationDestination(
        route = "reports_screen", // Match your actual route
        label = "Reports",
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
        badge = "3" // Example badge
    )

    object Personal : NavigationDestination(
        route = "personal_screen", // Match your actual route
        label = "Personal",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        hasNews = true // Example notification dot
    )
}

/**
 * Enterprise-grade Navigation Bar following Google's Material Design 3
 */
@Composable
fun GoogleStyleNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
    destinations: List<NavigationDestination> = listOf(
        NavigationDestination.Home,
        NavigationDestination.Reports,
        NavigationDestination.Personal
    )
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Debug: Log current route
    LaunchedEffect(currentDestination) {
        println("Current route: ${currentDestination?.route}")
    }

    // Always show the navigation bar
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
                // For now, check if this is the home route
                val selected = when (destination) {
                    is NavigationDestination.Home -> currentDestination?.route == "home_screen"
                    else -> false
                }

                GoogleNavigationBarItem(
                    selected = selected,
                    onClick = {
                        // For now, only navigate to home
                        if (destination is NavigationDestination.Home) {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
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
 * Individual navigation item with Google-style animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    badge: String?,
    hasNews: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
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
                indication = rememberRipple(
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
 * Extension to create elevation colors like Google apps
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
 * Utility function for color composition
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