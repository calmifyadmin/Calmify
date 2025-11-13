package com.lifo.humanoid.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.lifo.humanoid.presentation.HumanoidScreen

/**
 * Navigation route for Humanoid feature
 */
const val HUMANOID_ROUTE = "humanoid_screen"

/**
 * Extension function to add Humanoid navigation to the app's NavGraph.
 *
 * Usage in app module's NavGraph.kt:
 * ```
 * navController.navigate(HUMANOID_ROUTE)
 * ```
 *
 * @param navigateBack Callback to navigate back from the screen
 */
fun NavGraphBuilder.humanoidRoute(
    navigateBack: () -> Unit
) {
    composable(route = HUMANOID_ROUTE) {
        HumanoidScreen(navigateBack = navigateBack)
    }
}
