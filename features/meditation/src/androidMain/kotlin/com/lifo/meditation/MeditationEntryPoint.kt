package com.lifo.meditation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the redesigned meditation flow.
 *
 * The session save Effect is silent (no Toast) — the OVERVIEW phase is the
 * user-facing confirmation. Errors are also silent in Phase 1; Phase 3 will
 * surface them via a non-blocking inline message in the Overview screen.
 *
 * `navigateBack` is called only when the user wants to exit the entire flow
 * (currently only relevant when handled by hardware back at the WELCOME phase
 * — the in-flow back navigation is internal via [MeditationContract.Intent]).
 */
@Composable
fun MeditationRouteContent(
    navigateBack: () -> Unit,
) {
    val viewModel: MeditationViewModel = koinViewModel(key = "meditation_vm")
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MeditationContract.Effect.SessionCompleted,
                MeditationContract.Effect.PlayBell -> MeditationBellPlayer.play()
                MeditationContract.Effect.SessionSaved -> Unit  // silent
                is MeditationContract.Effect.Error -> Unit       // silent in Phase 1
            }
        }
    }

    MeditationScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onExit = navigateBack,
    )
}
