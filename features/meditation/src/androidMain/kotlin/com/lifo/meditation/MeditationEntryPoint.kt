package com.lifo.meditation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MeditationRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: MeditationViewModel = koinViewModel(key = "meditation_vm")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MeditationContract.Effect.SessionSaved -> {
                    Toast.makeText(context, "Sessione salvata!", Toast.LENGTH_SHORT).show()
                    navigateBack()
                }
                is MeditationContract.Effect.SessionCompleted -> {
                    // Bell sound would play here — for now just a toast
                }
                is MeditationContract.Effect.PlayBell -> {
                    // TODO: play bell sound asset
                }
                is MeditationContract.Effect.Error -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    MeditationScreen(
        state = state,
        onIntent = { viewModel.onIntent(it) },
        onBackPressed = navigateBack,
    )
}
