package com.lifo.home.navigation


import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.lifo.home.HomeScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.lifo.home.HomeContent
import com.lifo.home.HomeViewModel
import com.lifo.home.MissingPermissionsComponent
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.util.Constants.APP_ID
import com.lifo.util.Screen
import com.lifo.util.model.RequestState
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.ext.profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


fun NavGraphBuilder.homeRoute(
    navigateToWrite: () -> Unit,
    navigateToWriteWithArgs: (String) -> Unit,
    navigateToAuth: () -> Unit,
    onDataLoaded: () -> Unit,
    navigateToReport: () -> Unit
) {
    composable(route = Screen.Home.route) {
        val viewModel: HomeViewModel = hiltViewModel()
        val diaries by viewModel.diaries
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var signOutDialogOpened by remember { mutableStateOf(false) }
        var deleteAllDialogOpened by remember { mutableStateOf(false) }
        var reloadTrigger by remember { mutableStateOf(0) }
        LaunchedEffect(key1 = reloadTrigger) {
            viewModel.reloadDiaries()

        }

        // Gestione del caricamento dei dati e azioni post-caricamento
        if (diaries !is RequestState.Loading) {
            onDataLoaded()
        }
            HomeScreen(
                diaries = diaries,
                drawerState = drawerState,
                onMenuClicked = {
                    scope.launch {
                        drawerState.open()
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
                navigateToReport = navigateToReport
            )


        DisplayAlertDialog(
            title = "Sign Out",
            message = "Are you sure you want to Sign Out from your Google Account?",
            dialogOpened = signOutDialogOpened,
            onDialogClosed = { signOutDialogOpened = false },
            onYesClicked = {
                scope.launch(Dispatchers.IO) {
                    val user = App.create(APP_ID).currentUser
                    if (user != null) {
                        user.logOut()
                        withContext(Dispatchers.Main) {
                            navigateToAuth()
                        }
                    }
                }
            }
        )

        DisplayAlertDialog(
            title = "Delete All Diaries",
            message = "Are you sure you want to permanently delete all your diaries?",
            dialogOpened = deleteAllDialogOpened,
            onDialogClosed = { deleteAllDialogOpened = false },
            onYesClicked = {
                viewModel.deleteAllDiaries(
                    onSuccess = {
                        Toast.makeText(
                            context,
                            "All Diaries Deleted.",
                            Toast.LENGTH_SHORT
                        ).show()
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onError = {
                        Toast.makeText(
                            context,
                            if (it.message == "No Internet Connection.")
                                "We need an Internet Connection for this operation."
                            else it.message,
                            Toast.LENGTH_SHORT
                        ).show()
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        )
    }
}