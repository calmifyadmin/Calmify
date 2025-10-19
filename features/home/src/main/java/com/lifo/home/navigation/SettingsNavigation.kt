package com.lifo.home.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.lifo.home.SettingsScreen
import com.lifo.util.Screen

/**
 * Navigation route for Settings screen
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.settingsRoute(
    onMenuClicked: () -> Unit,
    bottomBarScrollBehavior: BottomAppBarScrollBehavior
) {
    composable(
        route = Screen.Settings.route,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideInHorizontally(
                        initialOffsetX = { it / 4 },
                        animationSpec = tween(300)
                    )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(300)
                    )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(300)
                    )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(300)
                    )
        }
    ) {
        SettingsScreen(
            onMenuClicked = onMenuClicked,
            bottomBarScrollBehavior = bottomBarScrollBehavior
        )
    }
}
