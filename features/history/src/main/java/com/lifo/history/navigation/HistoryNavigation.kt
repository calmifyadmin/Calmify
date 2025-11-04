package com.lifo.history.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.lifo.history.ChatHistoryFullScreen
import com.lifo.history.DiaryHistoryFullScreen
import com.lifo.history.HistoryScreen
import com.lifo.history.HistoryViewModel
import com.lifo.util.Screen
import com.lifo.util.model.HomeContentItem
import kotlinx.coroutines.launch

/**
 * Navigation route for the History screen
 */
@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.historyRoute(
    navController: NavHostController,
    navigateToWriteWithArgs: (String) -> Unit,
    navigateToExistingChat: (String) -> Unit,
    navigateToInsight: (String) -> Unit,
    onMenuClicked: () -> Unit
) {
    // Main History screen
    composable(
        route = Screen.History.route,
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
        val viewModel: HistoryViewModel = hiltViewModel()

        HistoryScreen(
            onMenuClicked = onMenuClicked,
            onChatClick = { chat ->
                navigateToExistingChat(chat.id)
            },
            onDiaryClick = { diary ->
                navigateToWriteWithArgs(diary.id)
            },
            onChatHistoryHeaderClick = {
                navController.navigate(Screen.ChatHistoryFull.route)
            },
            onDiaryHistoryHeaderClick = {
                navController.navigate(Screen.DiaryHistoryFull.route)
            },
            viewModel = viewModel
        )
    }

    // Full Chat History screen
    composable(
        route = Screen.ChatHistoryFull.route,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
        }
    ) {
        val viewModel: HistoryViewModel = hiltViewModel()

        ChatHistoryFullScreen(
            onBackClick = {
                navController.popBackStack()
            },
            onChatClick = { chat ->
                navigateToExistingChat(chat.id)
            },
            viewModel = viewModel
        )
    }

    // Full Diary History screen
    composable(
        route = Screen.DiaryHistoryFull.route,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
        }
    ) {
        val viewModel: HistoryViewModel = hiltViewModel()

        DiaryHistoryFullScreen(
            onBackClick = {
                navController.popBackStack()
            },
            onDiaryClick = { diary ->
                navigateToWriteWithArgs(diary.id)
            },
            onInsightClick = { diaryId ->
                navigateToInsight(diaryId)
            },
            viewModel = viewModel
        )
    }
}
