package com.lifo.home.navigation

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState
import com.lifo.home.HomeScreen
import com.lifo.home.HomeViewModel
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.util.model.RequestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Public entry point composable for the Home feature.
 * Wraps the internal HomeScreen with ViewModel setup and dialog handling.
 * Used by DecomposeApp to render the home destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRouteContent(
    navigateToWrite: () -> Unit,
    navigateToWriteWithArgs: (String) -> Unit,
    navigateToAuth: () -> Unit,
    navigateToChat: () -> Unit,
    navigateToExistingChat: (String) -> Unit,
    navigateToLiveScreen: () -> Unit,
    navigateToWellbeingSnapshot: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    navigateToFeed: () -> Unit = {},
    navigateToThreadDetail: (String) -> Unit = {},
    navigateToSocialProfile: () -> Unit = {},
    onDataLoaded: () -> Unit,
    drawerState: DrawerState
) {
    val viewModel: HomeViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val diaries by viewModel.diaries

    var signOutDialogOpened by rememberSaveable { mutableStateOf(false) }
    var deleteAllDialogOpened by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val hasCalledOnDataLoaded = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (diaries is RequestState.Idle) {
            viewModel.loadDiaries()
        }
    }

    LaunchedEffect(diaries) {
        if (!hasCalledOnDataLoaded.value &&
            diaries !is RequestState.Loading &&
            diaries !is RequestState.Idle
        ) {
            hasCalledOnDataLoaded.value = true
            onDataLoaded()
        }
    }

    HomeScreen(
        diaries = diaries,
        navController = null,
        drawerState = drawerState,
        onMenuClicked = {
            scope.launch {
                try {
                    if (!drawerState.isAnimationRunning) {
                        if (drawerState.isClosed) {
                            drawerState.open()
                        } else {
                            drawerState.close()
                        }
                    }
                } catch (e: Exception) {
                    println("[HomeRoute] ERROR: Error toggling drawer: ${e.message}")
                }
            }
        },
        dateIsSelected = viewModel.dateIsSelected,
        onDateSelected = { viewModel.getDiaries(zonedDateTime = it) },
        onDateReset = { viewModel.getDiaries() },
        onSignOutClicked = { signOutDialogOpened = true },
        onDeleteAllClicked = { deleteAllDialogOpened = true },
        navigateToWrite = navigateToWrite,
        navigateToWriteWithArgs = navigateToWriteWithArgs,
        viewModel = viewModel,
        userProfileImageUrl = viewModel.getUserPhotoUrl(),
        navigateToChat = navigateToChat,
        navigateToExistingChat = navigateToExistingChat,
        navigateToLiveScreen = navigateToLiveScreen,
        navigateToWellbeingSnapshot = navigateToWellbeingSnapshot,
        onNotificationsClick = onNotificationsClick,
        navigateToFeed = navigateToFeed,
        navigateToThreadDetail = navigateToThreadDetail,
        navigateToSocialProfile = navigateToSocialProfile,
    )

    // Sign Out Dialog
    AnimatedVisibility(
        visible = signOutDialogOpened,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        DisplayAlertDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out from your Google Account? You'll need to sign in again to access your diaries.",
            dialogOpened = signOutDialogOpened,
            onDialogClosed = { signOutDialogOpened = false },
            onYesClicked = {
                signOutDialogOpened = false
                scope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Signing out...", Toast.LENGTH_SHORT).show()
                        }
                        withContext(Dispatchers.IO) {
                            viewModel.signOut()
                        }
                        withContext(Dispatchers.Main) {
                            navigateToAuth()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to sign out. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // Delete All Dialog
    AnimatedVisibility(
        visible = deleteAllDialogOpened,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        DisplayAlertDialog(
            title = "Delete All Diaries",
            message = "This action cannot be undone. All your diaries and associated images will be permanently deleted.",
            dialogOpened = deleteAllDialogOpened,
            onDialogClosed = { deleteAllDialogOpened = false },
            onYesClicked = {
                deleteAllDialogOpened = false
                viewModel.deleteAllDiaries(
                    onSuccess = {
                        Toast.makeText(context, "All diaries deleted successfully", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            try {
                                if (!drawerState.isAnimationRunning) {
                                    drawerState.close()
                                }
                            } catch (e: Exception) {
                                println("[HomeRoute] ERROR: Error closing drawer: ${e.message}")
                            }
                        }
                    },
                    onError = { error ->
                        val errorMessage = when {
                            error.message?.contains("No Internet Connection") == true ->
                                "Please check your internet connection and try again."
                            else -> error.message ?: "Failed to delete diaries"
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        scope.launch {
                            try {
                                if (!drawerState.isAnimationRunning) {
                                    drawerState.close()
                                }
                            } catch (e: Exception) {
                                println("[HomeRoute] ERROR: Error closing drawer: ${e.message}")
                            }
                        }
                    }
                )
            }
        )
    }
}
