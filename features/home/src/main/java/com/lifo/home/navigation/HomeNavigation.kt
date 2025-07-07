package com.lifo.home.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.lifo.home.HomeScreen
import com.lifo.home.HomeViewModel
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.util.Constants.APP_ID
import com.lifo.util.Screen
import com.lifo.util.model.RequestState
import io.realm.kotlin.mongodb.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun NavGraphBuilder.homeRoute(
    navController: NavHostController,
    navigateToWrite: () -> Unit,
    navigateToWriteWithArgs: (String) -> Unit,
    navigateToAuth: () -> Unit,
    onDataLoaded: () -> Unit
) {
    composable(
        route = Screen.Home.route,
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
        // Get ViewModel scoped to this route
        val viewModel: HomeViewModel = hiltViewModel()

        // Collect states using lifecycle-aware collection
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val diaries by viewModel.diaries

        // Dialog states - preserved across configuration changes
        var signOutDialogOpened by rememberSaveable { mutableStateOf(false) }
        var deleteAllDialogOpened by rememberSaveable { mutableStateOf(false) }

        // Drawer state
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // Track if we've called onDataLoaded
        val hasCalledOnDataLoaded = rememberSaveable { mutableStateOf(false) }

        // Handle initial data loading
        LaunchedEffect(Unit) {
            // Only reload if we don't have data
            if (diaries is RequestState.Idle) {
                Log.d("HomeRoute", "Initial load - fetching diaries")
                viewModel.loadDiaries()
            }
        }

        // Call onDataLoaded when appropriate
        LaunchedEffect(diaries) {
            if (!hasCalledOnDataLoaded.value &&
                diaries !is RequestState.Loading &&
                diaries !is RequestState.Idle) {
                hasCalledOnDataLoaded.value = true
                Log.d("HomeRoute", "Data ready - calling onDataLoaded")
                onDataLoaded()
            }
        }

        // Home Screen with all necessary parameters
        HomeScreen(
            diaries = diaries,
            navController = navController,
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
                        Log.e("HomeRoute", "Error toggling drawer", e)
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
            userProfileImageUrl = viewModel.getUserPhotoUrl()
        )

        // Enhanced Sign Out Dialog
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
                            // Show loading toast
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Signing out...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            // Sign out in IO context
                            withContext(Dispatchers.IO) {
                                val user = App.create(APP_ID).currentUser
                                user?.logOut()
                            }

                            // Navigate to auth on main thread
                            withContext(Dispatchers.Main) {
                                navigateToAuth()
                            }
                        } catch (e: Exception) {
                            Log.e("HomeRoute", "Error signing out", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Failed to sign out. Please try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        // Enhanced Delete All Dialog
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
                            Toast.makeText(
                                context,
                                "All diaries deleted successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            scope.launch {
                                try {
                                    if (!drawerState.isAnimationRunning) {
                                        drawerState.close()
                                    }
                                } catch (e: Exception) {
                                    Log.e("HomeRoute", "Error closing drawer", e)
                                }
                            }
                        },
                        onError = { error ->
                            val errorMessage = when {
                                error.message?.contains("No Internet Connection") == true ->
                                    "Please check your internet connection and try again."
                                else -> error.message ?: "Failed to delete diaries"
                            }

                            Toast.makeText(
                                context,
                                errorMessage,
                                Toast.LENGTH_LONG
                            ).show()

                            scope.launch {
                                try {
                                    if (!drawerState.isAnimationRunning) {
                                        drawerState.close()
                                    }
                                } catch (e: Exception) {
                                    Log.e("HomeRoute", "Error closing drawer", e)
                                }
                            }
                        }
                    )
                }
            )
        }
    }
}