package com.lifo.app.navigation

import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifo.util.Screen

/**
 * Stato di navigazione dell'app che gestisce la visibilità della bottom bar
 */
@Stable
class NavigationState(
    val navController: NavHostController
) {
    /**
     * Destinazioni principali per la navigation bar
     */
    val topLevelDestinations = listOf(
        TopLevelDestination.Home,
        TopLevelDestination.Write,
        TopLevelDestination.Profile
    )

    /**
     * Route corrente basata sul back stack
     */
    val currentRoute: String?
        @Composable get() = navController
            .currentBackStackEntryAsState()
            .value?.destination?.route

    /**
     * Determina se mostrare la bottom navigation bar
     */
    val shouldShowBottomBar: Boolean
        @Composable get() = when (currentRoute) {
            Screen.Chat.route,
            "${Screen.Chat.route}/{sessionId}",
            Screen.Authentication.route -> false
            else -> true
        }

    /**
     * Naviga verso una destinazione top-level
     */
    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            // Pop fino alla start destination per evitare stack complessi
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            // Evita copie multiple della stessa destinazione
            launchSingleTop = true
            // Ripristina lo stato se disponibile
            restoreState = true
        }
    }
}

/**
 * Destinazioni principali dell'app
 */
sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Home : TopLevelDestination(
        route = Screen.Home.route,
        label = "Home",
        selectedIcon = androidx.compose.material.icons.Icons.Filled.Home,
        unselectedIcon = androidx.compose.material.icons.Icons.Outlined.Home
    )

    object Write : TopLevelDestination(
        route = Screen.Write.route,
        label = "Write",
        selectedIcon = androidx.compose.material.icons.Icons.Filled.EditNote,
        unselectedIcon = androidx.compose.material.icons.Icons.Outlined.EditNote
    )

    object Profile : TopLevelDestination(
        route = "profile_screen", // Aggiungi questa route a Screen.kt se non esiste
        label = "Profile",
        selectedIcon = androidx.compose.material.icons.Icons.Filled.Person,
        unselectedIcon = androidx.compose.material.icons.Icons.Outlined.Person
    )
}

/**
 * Ricorda lo stato di navigazione
 */
@Composable
fun rememberNavigationState(
    navController: NavHostController = rememberNavController()
): NavigationState {
    return remember(navController) {
        NavigationState(navController)
    }
}