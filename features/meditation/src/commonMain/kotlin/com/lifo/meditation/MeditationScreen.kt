package com.lifo.meditation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lifo.meditation.screens.MeditationConfigureScreen
import com.lifo.meditation.screens.MeditationOverviewScreen
import com.lifo.meditation.screens.MeditationScreeningScreen
import com.lifo.meditation.screens.MeditationSessionScreen
import com.lifo.meditation.screens.MeditationWelcomeScreen

/**
 * Top-level dispatcher for the redesigned 5-phase meditation flow.
 *
 * Routes to one of:
 * - [MeditationWelcomeScreen]   (initial)
 * - [MeditationScreeningScreen] (medical safety check)
 * - [MeditationConfigureScreen] (duration / goal / experience / audio / technique)
 * - [MeditationSessionScreen]   (active breath session)
 * - [MeditationOverviewScreen]  (post-session reflection)
 *
 * Phase transitions use a horizontal slide + fade, except SESSION which uses
 * a pure fade — the breath visual is intentionally minimal and centered.
 *
 * The screen is intentionally thin: all state mutation flows through
 * [MeditationContract.Intent] handled by [MeditationViewModel].
 *
 * @param onExit invoked when user wants to leave the flow entirely
 *               (e.g. hardware back from WELCOME). Currently wired only as a
 *               hook for the entry point; Phase 2 will add a BackHandler.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun MeditationScreen(
    state: MeditationContract.State,
    onIntent: (MeditationContract.Intent) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onUpgrade: () -> Unit = {},
) {
    AnimatedContent(
        targetState = state.phase,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            // SESSION enters/exits with pure fade — the breath visual is the protagonist
            val isSession = targetState == MeditationContract.SessionPhase.SESSION ||
                initialState == MeditationContract.SessionPhase.SESSION
            if (isSession) {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(600)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
            } else {
                (fadeIn() + slideInHorizontally { it / 4 }) togetherWith
                    (fadeOut() + slideOutHorizontally { -it / 4 })
            }
        },
        label = "meditation_phase",
    ) { phase ->
        when (phase) {
            MeditationContract.SessionPhase.WELCOME -> MeditationWelcomeScreen(
                onBegin = { onIntent(MeditationContract.Intent.NavigateToScreening) },
            )

            MeditationContract.SessionPhase.SCREENING -> MeditationScreeningScreen(
                selectedRisks = state.risks,
                onToggleRisk = { onIntent(MeditationContract.Intent.ToggleRiskFlag(it)) },
                onClearAll = { onIntent(MeditationContract.Intent.ClearAllRisks) },
                onBack = { onIntent(MeditationContract.Intent.NavigateBackFromScreening) },
                onContinue = { onIntent(MeditationContract.Intent.NavigateToConfigure) },
            )

            MeditationContract.SessionPhase.CONFIGURE -> MeditationConfigureScreen(
                state = state,
                onBack = { onIntent(MeditationContract.Intent.NavigateBackFromConfigure) },
                onSetDuration = { onIntent(MeditationContract.Intent.SetDuration(it)) },
                onSetGoal = { onIntent(MeditationContract.Intent.SetGoal(it)) },
                onSetExperience = { onIntent(MeditationContract.Intent.SetExperience(it)) },
                onSetAudio = { onIntent(MeditationContract.Intent.SetAudio(it)) },
                onSetTechniqueOverride = { onIntent(MeditationContract.Intent.SetTechniqueOverride(it)) },
                onRedoScreening = { onIntent(MeditationContract.Intent.NavigateBackFromConfigure) },
                onBegin = { onIntent(MeditationContract.Intent.StartSession) },
            )

            MeditationContract.SessionPhase.SESSION -> {
                val runtime = state.session
                if (runtime != null) {
                    MeditationSessionScreen(
                        runtime = runtime,
                        showStopDialog = state.showStopDialog,
                        audio = state.config.audio,
                        onPauseToggle = {
                            onIntent(
                                if (runtime.isPaused) MeditationContract.Intent.ResumeSession
                                else MeditationContract.Intent.PauseSession
                            )
                        },
                        onRequestStop = { onIntent(MeditationContract.Intent.RequestStopSession) },
                        onConfirmStop = { onIntent(MeditationContract.Intent.ConfirmStopSession) },
                        onDismissStopDialog = { onIntent(MeditationContract.Intent.DismissStopDialog) },
                    )
                }
            }

            MeditationContract.SessionPhase.OVERVIEW -> {
                val runtime = state.session
                if (runtime != null) {
                    MeditationOverviewScreen(
                        runtime = runtime,
                        onDifferent = { onIntent(MeditationContract.Intent.NavigateDifferentFromOverview) },
                        onRedo = { onIntent(MeditationContract.Intent.NavigateRedoFromOverview) },
                        onUpgrade = onUpgrade,
                    )
                }
            }
        }
    }
}
