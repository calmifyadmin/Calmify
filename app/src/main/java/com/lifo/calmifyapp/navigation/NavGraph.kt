package com.lifo.calmifyapp.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import com.lifo.auth.navigation.authenticationRoute
import com.lifo.home.navigation.homeRoute
import com.lifo.util.Screen
import com.lifo.write.navigation.writeRoute

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun SetupNavGraph(
    startDestination: String,
    navController: NavHostController,
    onDataLoaded: () -> Unit
) {
    // Stato per gestire il debouncing dei click
    var lastNavigationTime by remember { mutableLongStateOf(0L) }
    val navigationDebounceTime = 300L // millisecondi

    // Funzione per la navigazione sicura con debouncing
    val navigateSafely: (String, NavOptions?) -> Unit = { route, navOptions ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationTime > navigationDebounceTime) {
            lastNavigationTime = currentTime
            try {
                if (navOptions != null) {
                    navController.navigate(route, navOptions)
                } else {
                    navController.navigate(route)
                }
            } catch (e: Exception) {
                Log.e("Navigation", "Error navigating to $route", e)
            }
        } else {
            Log.d("Navigation", "Navigation throttled to $route (too rapid)")
        }
    }

    // Funzione per il pop back stack sicuro
    val popBackStackSafely: () -> Unit = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigationTime > navigationDebounceTime) {
            lastNavigationTime = currentTime
            try {
                navController.popBackStack()
            } catch (e: Exception) {
                Log.e("Navigation", "Error during popBackStack", e)
            }
        } else {
            Log.d("Navigation", "PopBackStack throttled (too rapid)")
        }
    }
    // Error handling state for navigation issues
    var navigationError by remember { mutableStateOf<String?>(null) }

    // Show dialog if navigation error occurs
    if (navigationError != null) {
        AlertDialog(
            onDismissRequest = { navigationError = null },
            title = { Text("Navigation Error") },
            text = { Text(navigationError!!) },
            confirmButton = {
                Button(onClick = {
                    navigationError = null
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }) {
                    Text("Go Home")
                }
            }
        )
    }

    // Main navigation setup
    NavHost(
        startDestination = startDestination,
        navController = navController
    ) {
            authenticationRoute(
                navigateToHome = {
                    navigateSafely(Screen.Home.route,
                        navOptions {
                            popUpTo(Screen.Authentication.route) { inclusive = true }
                        }
                    )
                },
                onDataLoaded = onDataLoaded
            )
        homeRoute(
            navController = navController, // Passa il NavController globale
            navigateToWrite = {
                navigateSafely(Screen.Write.route, null)
            },
            navigateToWriteWithArgs = {
                navigateSafely(Screen.Write.passDiaryId(diaryId = it), null)
            },
            navigateToAuth = {
                navigateSafely(
                    Screen.Authentication.route,
                    navOptions { popUpTo(Screen.Home.route) { inclusive = true } }
                )
            },
            onDataLoaded = onDataLoaded,
            navigateToReport = {
                // Implementazione per la navigazione alle report
            }
        )

        writeRoute(
            navigateBack = popBackStackSafely
        )
    }

    // Navigation error handler
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            try {
                Log.d("Navigation", "Navigated to: ${destination.route}")
                // Any validation that needs to happen during navigation
            } catch (e: Exception) {
                Log.e("Navigation", "Error during navigation to ${destination.route}", e)
                navigationError = "Navigation error occurred. ${e.message}"
            }
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            Log.d("Navigation", "Navigation listener removed")
        }
    }
}