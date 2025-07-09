package com.lifo.chat.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifo.chat.presentation.screen.ChatScreen
import com.lifo.util.Screen

fun NavGraphBuilder.chatRoute(
    navigateBack: () -> Unit,
    navigateToWriteWithContent: (String) -> Unit
) {
    composable(
        route = Screen.Chat.route,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        ChatScreen(
            navigateBack = navigateBack,
            navigateToWriteWithContent = navigateToWriteWithContent
        )
    }

    // Route with session ID (for future use)
    composable(
        route = "${Screen.Chat.route}/{sessionId}",
        arguments = listOf(
            navArgument("sessionId") {
                type = NavType.StringType
            }
        ),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        ChatScreen(
            navigateBack = navigateBack,
            navigateToWriteWithContent = navigateToWriteWithContent
        )
    }
}

fun NavController.navigateToChat(sessionId: String? = null) {
    val route = if (sessionId != null) {
        "${Screen.Chat.route}/$sessionId"
    } else {
        Screen.Chat.route
    }

    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}