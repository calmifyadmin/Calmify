package com.lifo.chat.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lifo.chat.presentation.screen.ChatScreen
import com.lifo.chat.presentation.screen.LiveChatScreen
import com.lifo.chat.presentation.screen.GeminiLiveChatScreen
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
            sessionId = sessionId
        )
    }

    // LiveChat route
    composable(
        route = Screen.LiveChat.route,
        enterTransition = {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        },
        exitTransition = {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        },
        popEnterTransition = {
            slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        },
        popExitTransition = {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        }
    ) {
        LiveChatScreen(
            onBackClicked = navigateBack
        )
    }

    // Gemini LiveChat route (POC)
    composable(
        route = Screen.GeminiLiveChat.route,
        enterTransition = {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        },
        exitTransition = {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        },
        popEnterTransition = {
            slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(500)
            ) + fadeIn(animationSpec = tween(500))
        },
        popExitTransition = {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(500)
            ) + fadeOut(animationSpec = tween(500))
        }
    ) {
        GeminiLiveChatScreen(
            onBackClicked = navigateBack
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

fun NavController.navigateToLiveChat() {
    navigate(Screen.LiveChat.route) {
        launchSingleTop = true
        restoreState = true
    }
}

fun NavController.navigateToGeminiLiveChat() {
    navigate(Screen.GeminiLiveChat.route) {
        launchSingleTop = true
        restoreState = true
    }
}