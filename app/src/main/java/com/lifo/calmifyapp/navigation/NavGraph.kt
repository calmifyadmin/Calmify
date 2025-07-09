package com.lifo.calmifyapp.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lifo.auth.navigation.authenticationRoute
import com.lifo.chat.navigation.chatRoute
import com.lifo.home.navigation.homeRoute
import com.lifo.ui.components.navigation.GoogleStyleNavigationBar
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.Screen
import com.lifo.write.navigation.writeRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun SetupNavGraph(
    startDestination: String,
    navController: NavHostController,
    onDataLoaded: () -> Unit
) {
    // Navigation state management - using separate saveable states instead of data class
    var isNavigating by rememberSaveable { mutableStateOf(false) }
    var currentRouteState by rememberSaveable { mutableStateOf<String?>(null) }
    var previousRouteState by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Track current route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Update navigation state when route changes
    LaunchedEffect(currentRoute) {
        if (currentRoute != currentRouteState) {
            previousRouteState = currentRouteState
            currentRouteState = currentRoute
        }
    }

    // Determine if we should show the navigation bar
    val shouldShowNavigationBar = false

    // Data loading state management
    val dataLoadedState = rememberSaveable { mutableStateOf(false) }
    val safeOnDataLoaded: () -> Unit = {
        if (!dataLoadedState.value) {
            dataLoadedState.value = true
            Log.d("Navigation", "Data loaded - first time only")
            onDataLoaded()
        }
    }

    // Safe navigation wrapper
    val safeNavigate: (String, () -> Unit) -> Unit = { route, navigationAction ->
        if (!isNavigating && currentRoute != route) {
            isNavigating = true
            coroutineScope.launch {
                try {
                    navigationAction()
                    delay(300) // Allow animation to complete
                    isNavigating = false
                } catch (e: Exception) {
                    Log.e("Navigation", "Error navigating to $route", e)
                    isNavigating = false
                }
            }
        }
    }

    // Main navigation scaffold
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Animated navigation bar
            AnimatedVisibility(
                visible = shouldShowNavigationBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut()
            ) {
                GoogleStyleNavigationBar(
                    navController = navController,
                    destinations = listOf(
                        NavigationDestination.Home,
                        NavigationDestination.Reports,
                        NavigationDestination.Personal
                    )
                )
            }
        }
    ) { paddingValues ->
        // Navigation host with animations
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (shouldShowNavigationBar) paddingValues.calculateBottomPadding() else 0.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    fadeIn(animationSpec = tween(300)) +
                            slideInHorizontally(
                                initialOffsetX = { it / 3 },
                                animationSpec = tween(300)
                            )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(300)) +
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = tween(300)
                            )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300)) +
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(300)
                            )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(300)) +
                            slideOutHorizontally(
                                targetOffsetX = { it / 3 },
                                animationSpec = tween(300)
                            )
                }
            ) {
                authenticationRoute(
                    navigateToHome = {
                        safeNavigate(Screen.Home.route) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Authentication.route) { inclusive = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onDataLoaded = safeOnDataLoaded
                )

                homeRoute(
                    navController = navController,
                    navigateToWrite = {
                        safeNavigate(Screen.Write.route) {
                            navController.navigate(Screen.Write.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    navigateToWriteWithArgs = { diaryId ->
                        val writeRoute = Screen.Write.passDiaryId(diaryId = diaryId)
                        safeNavigate(writeRoute) {
                            navController.navigate(writeRoute) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    navigateToAuth = {
                        safeNavigate(Screen.Authentication.route) {
                            navController.navigate(Screen.Authentication.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onDataLoaded = safeOnDataLoaded,
                    navigateToChat = {
                        safeNavigate(Screen.Chat.route) {
                            navController.navigate(Screen.Chat.route) {
                                popUpTo(Screen.Chat.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                )

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
            }
        }
    }

    // Navigation listener for debugging
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            Log.d("Navigation", "Navigated to: ${destination.route}")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }
}

// Custom enter/exit animations for specific routes
@OptIn(ExperimentalAnimationApi::class)
private fun getEnterTransition(route: String?): EnterTransition {
    return when (route) {
        Screen.Write.route -> {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn()
        }
        else -> {
            fadeIn(animationSpec = tween(300)) +
                    scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(300)
                    )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
private fun getExitTransition(route: String?): ExitTransition {
    return when (route) {
        Screen.Write.route -> {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut()
        }
        else -> {
            fadeOut(animationSpec = tween(300)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(300)
                    )
        }
    }
}