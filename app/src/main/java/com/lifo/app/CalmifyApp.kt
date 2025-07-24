package com.lifo.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lifo.app.navigation.*
import com.lifo.auth.navigation.authenticationRoute
import com.lifo.chat.navigation.chatRoute
import com.lifo.home.navigation.homeRoute
import com.lifo.ui.components.navigation.CalmifyNavigationBar
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.Screen
import com.lifo.write.navigation.writeRoute

/**
 * Composable principale dell'app con Navigation Bar Material 3
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CalmifyApp(
    startDestination: String,
    onDataLoaded: () -> Unit = {}
) {
    val navigationState = rememberNavigationState()
    val shouldShowBottomBar = navigationState.shouldShowBottomBar

    Scaffold(
        bottomBar = {
            // Animazione per mostrare/nascondere la navigation bar
            AnimatedVisibility(
                visible = shouldShowBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                // Usa direttamente CalmifyNavigationBar
                CalmifyNavigationBar(
                    navController = navigationState.navController,
                    destinations = listOf(
                        NavigationDestination.Home,
                        NavigationDestination.Write,
                        NavigationDestination.Profile
                    )
                )
            }
        },
        contentWindowInsets = WindowInsets(0.dp)

    ) { paddingValues ->
        // Navigation Host con padding per la bottom bar
        CalmifyNavHost(
            navController = navigationState.navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
            onDataLoaded = onDataLoaded
        )
    }
}

/**
 * Navigation Host principale
 */
@Composable
private fun CalmifyNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onDataLoaded: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Authentication
        authenticationRoute(
            navigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Authentication.route) {
                        inclusive = true
                    }
                }
            },
            onDataLoaded = onDataLoaded
        )

        // Home
        homeRoute(
            navController = navController,
            navigateToWrite = {
                navController.navigate(Screen.Write.route)
            },
            navigateToWriteWithArgs = { diaryId ->
                navController.navigate("${Screen.Write.route}/$diaryId")
            },
            navigateToAuth = {
                navController.navigate(Screen.Authentication.route) {
                    popUpTo(Screen.Home.route) {
                        inclusive = true
                    }
                }
            },
            navigateToChat = {
                navController.navigate(Screen.Chat.route)
            },
            onDataLoaded = onDataLoaded,
        )

        // Write
        writeRoute(
            navigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            }
        )
        // In the NavHost, after the writeRoute, add:
        chatRoute(
            navigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            },
            navigateToWriteWithContent = { content ->
                // Navigate to write screen with pre-filled content
                // This would require updating the Write feature to accept initial content
                navController.navigate(Screen.Write.route)
            }
        )



        // Profile
        composable(route = "profile_screen") {
            ProfileScreen(
                navController = navController
            )
        }
    }
}