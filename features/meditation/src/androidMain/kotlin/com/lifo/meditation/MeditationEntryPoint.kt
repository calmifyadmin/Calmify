package com.lifo.meditation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lifo.util.model.MeditationAudio
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the redesigned meditation flow.
 *
 * Audio handling — gated on [MeditationAudio]:
 * - SILENT: no audio output (visual + cue text only)
 * - CHIMES: bell tone at sub-phase boundaries + session-complete
 * - VOICE: bell tone + (Phase 3.B') TTS voice guidance for cue words and coach
 *   lines. Voice is not yet wired — see `.claude/MEDITATION_TTS_DECISION.md`.
 *
 * The session save Effect is silent (no Toast) — the OVERVIEW phase is the
 * user-facing confirmation. Errors are surfaced via inline message in Overview
 * (Phase 3 will wire that path; for now they remain silent).
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
                MeditationContract.Effect.PlayBell -> {
                    // Read the live audio mode at emission time (the user may have
                    // re-entered Configure between sessions; we should not rely on
                    // a stale closure capture).
                    if (viewModel.state.value.config.audio != MeditationAudio.SILENT) {
                        MeditationBellPlayer.play()
                    }
                }
                MeditationContract.Effect.SessionSaved -> Unit  // silent
                is MeditationContract.Effect.Error -> Unit       // surfaced via Overview (Phase 3)
            }
        }
    }

    MeditationScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onExit = navigateBack,
    )
}
