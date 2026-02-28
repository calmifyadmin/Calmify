package com.lifo.chat.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifo.chat.presentation.screen.ChatScreen
import com.lifo.chat.presentation.screen.LiveScreen
import com.lifo.util.Screen

fun NavGraphBuilder.chatRoute(
    navigateBack: () -> Unit,
    navigateToWriteWithContent: (String) -> Unit,
    navigateToLiveScreen: () -> Unit
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
            navigateToWriteWithContent = navigateToWriteWithContent,
            navigateToLiveScreen = navigateToLiveScreen
        )
    }

    // Route with session ID for existing sessions
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
    ) { backStackEntry ->
        val sessionId = backStackEntry.arguments?.getString("sessionId")
        ChatScreen(
            navigateBack = navigateBack,
            navigateToWriteWithContent = navigateToWriteWithContent,
            navigateToLiveScreen = navigateToLiveScreen,
            sessionId = sessionId
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

/**
 * Navigate to dedicated Live Chat screen
 */
fun NavController.navigateToLiveChat() {
    navigate(Screen.LiveChat.route) {
        launchSingleTop = true
    }
}

/**
 * Add Live Chat route to navigation graph
 *
 * @param navigateBack Callback to navigate back
 * @param avatarContent Optional composable slot for the 3D avatar (from features/humanoid)
 */
fun NavGraphBuilder.liveRoute(
    navigateBack: () -> Unit,
    avatarContent: (@Composable () -> Unit)? = null
) {
    composable(
        route = Screen.LiveChat.route,
        enterTransition = {
            fadeIn(animationSpec = tween(500)) +
            scaleIn(initialScale = 0.9f, animationSpec = tween(500))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
            scaleOut(targetScale = 0.9f, animationSpec = tween(300))
        }
    ) {
        LiveScreen(
            onClose = navigateBack,
            showAvatar = avatarContent != null,
            avatarContent = avatarContent
        )
    }
}

