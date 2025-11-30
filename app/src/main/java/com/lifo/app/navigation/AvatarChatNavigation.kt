package com.lifo.app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifo.app.presentation.screen.AvatarChatScreen
import com.lifo.util.Screen

/**
 * Avatar Chat Navigation
 *
 * Defines navigation routes for the Avatar Chat screen integration.
 *
 * Supports two routes:
 * 1. New session: Screen.AvatarChat.route
 * 2. Existing session: Screen.AvatarChat.route/{sessionId}
 */
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.avatarChatRoute(navigateBack: () -> Unit) {
    composable(
        route = Screen.AvatarChat.route,
        enterTransition = { fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)) },
        exitTransition = { fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)) }
    ) {
        AvatarChatScreen(navigateBack = navigateBack)
    }

    composable(
        route = "${Screen.AvatarChat.route}/{sessionId}",
        arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        enterTransition = { fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)) },
        exitTransition = { fadeOut(tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300)) }
    ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getString("sessionId")
        AvatarChatScreen(navigateBack = navigateBack, sessionId = sessionId)
    }
}

/**
 * Navigation extension for NavController.
 *
 * Navigate to Avatar Chat with optional session ID.
 */
fun NavController.navigateToAvatarChat(sessionId: String? = null) {
    val route = sessionId?.let { "${Screen.AvatarChat.route}/$it" }
        ?: Screen.AvatarChat.route

    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}
