package com.lifo.chat.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
    navigateToLiveScreen: () -> Unit,
    navigateToAvatarLiveChat: (() -> Unit)? = null
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
            navigateToLiveScreen = navigateToLiveScreen,
            navigateToAvatarLiveChat = navigateToAvatarLiveChat
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
            navigateToAvatarLiveChat = navigateToAvatarLiveChat,
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
 */
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.liveRoute(
    navigateBack: () -> Unit
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
            onClose = navigateBack
        )
    }
}

// NOTE: Avatar Chat route moved to app module to avoid circular dependency
// See app/src/main/java/com/lifo/app/navigation/AvatarChatNavigation.kt