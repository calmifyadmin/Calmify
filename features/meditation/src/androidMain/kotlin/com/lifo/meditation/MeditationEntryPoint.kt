package com.lifo.meditation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lifo.meditation.voice.MeditationVoicePlayer
import com.lifo.util.model.MeditationAudio
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the redesigned meditation flow.
 *
 * Audio handling — gated on [MeditationAudio]:
 * - SILENT: no audio output (visual + cue text only)
 * - CHIMES: bell tone at sub-phase boundaries + session-complete
 * - VOICE: bell tone + ducked voice utterances (cue per breath segment + coach
 *   line on first display per session — see [MeditationVoicePlayer]).
 *
 * The voice player is created on session entry and released on disposal (the
 * Compose lifecycle owns the player's lifetime — when the user navigates away
 * from the meditation flow, the player + audio focus are torn down).
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
    navigateToSubscription: () -> Unit = {},
) {
    val viewModel: MeditationViewModel = koinViewModel(key = "meditation_vm")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val voicePlayer = remember(context) { MeditationVoicePlayer(context.applicationContext) }
    DisposableEffect(voicePlayer) {
        onDispose { voicePlayer.release() }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            // Read live audio mode at emission time — user may have re-entered
            // Configure between sessions; stale closure capture is unsafe.
            val audio = viewModel.state.value.config.audio
            when (effect) {
                MeditationContract.Effect.SessionCompleted,
                MeditationContract.Effect.PlayBell -> {
                    if (audio != MeditationAudio.SILENT) {
                        MeditationBellPlayer.play()
                    }
                }
                is MeditationContract.Effect.Speak -> {
                    if (audio == MeditationAudio.VOICE) {
                        voicePlayer.play(effect.utterance, currentLocaleTag())
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
        onUpgrade = navigateToSubscription,
    )
}

/**
 * Returns the current per-app locale's primary language tag (`it`, `en`, `ja`,
 * etc.). Used to resolve the voice asset path. Falls back to `en` for any
 * unrecognized language so the voice player's EN fallback path always matches.
 */
private fun currentLocaleTag(): String {
    val tag = Locale.getDefault().language.lowercase()
    return tag.ifEmpty { "en" }
}
