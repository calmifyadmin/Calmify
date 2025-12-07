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
import androidx.navigation.compose.composable
import com.lifo.app.presentation.screen.AvatarLiveChatScreen
import com.lifo.util.Screen

/**
 * Avatar Live Chat Navigation
 *
 * Defines navigation routes for the Avatar Live Chat screen integration.
 * This screen combines Gemini Live API with the VRM avatar for real-time
 * voice conversation with synchronized lip-sync and emotions.
 *
 * Route: Screen.AvatarLiveChat.route
 */
@RequiresApi(Build.VERSION_CODES.O)
fun NavGraphBuilder.avatarLiveChatRoute(navigateBack: () -> Unit) {
    composable(
        route = Screen.AvatarLiveChat.route,
        enterTransition = {
            fadeIn(tween(400)) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(400)
            )
        },
        exitTransition = {
            fadeOut(tween(300)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(300)
            )
        }
    ) {
        AvatarLiveChatScreen(onClose = navigateBack)
    }
}

/**
 * Navigation extension for NavController.
 *
 * Navigate to Avatar Live Chat screen.
 */
fun NavController.navigateToAvatarLiveChat() {
    navigate(Screen.AvatarLiveChat.route) {
        launchSingleTop = true
        restoreState = true
    }
}
