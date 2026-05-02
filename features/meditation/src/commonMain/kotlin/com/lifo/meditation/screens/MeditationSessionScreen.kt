package com.lifo.meditation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.meditation.MeditationContract
import com.lifo.ui.i18n.Strings
import com.lifo.ui.i18n.coachRes
import com.lifo.ui.i18n.nameRes
import com.lifo.ui.i18n.shortRes
import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationAudio
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 1 Session screen — baseline functional surface.
 *
 * **Phase 1 scope** (this file):
 * - Working state-driven UI: phase label + remaining timer + linear progress + breath visual + cue + coach line + Stop/Pause
 * - Sub-phase aware: cue word + coach copy change between SETTLING / PRACTICE / INTEGRATION
 * - Stop confirmation [AlertDialog] (M3) — Phase 2 will replace with [ModalBottomSheet]
 * - Pause/Resume + Stop intent wiring
 * - All strings from `Strings.Meditation.*` (zero hardcoded copy)
 *
 * **Phase 2 polish (deferred)**:
 * - Replace ambient circle with full design pacer (halo + outer ring + mid ring + circle, per-segment scale animation with smoothstep, cue overlay following the breath rhythm)
 * - Per-segment cue rotation tied to the BreathingPattern timing (currently the cue updates per sub-phase, not per breath segment)
 * - Coach line rotation every 12s (currently shows static first coach line per sub-phase)
 * - Keyboard shortcuts (ESC = stop, SPACE = pause)
 * - Reduced-motion path
 * - Stop confirmation as ModalBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
internal fun MeditationSessionScreen(
    runtime: MeditationContract.SessionRuntime,
    showStopDialog: Boolean,
    audio: MeditationAudio,
    onPauseToggle: () -> Unit,
    onRequestStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onDismissStopDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Top bar: phase label + remaining time ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = phaseLabel(runtime, technique = runtime.technique),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = formatTimer(runtime.remainingSeconds),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                    color = colorScheme.onSurfaceVariant,
                )
            }

            // ── Linear progress ─────────────────────────────────────────
            val progressFraction = if (runtime.totalActiveSeconds > 0) {
                (runtime.elapsedSeconds.toFloat() / runtime.totalActiveSeconds).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceContainerHighest,
            )

            // ── Stage: pacer + cue + coach ──────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .semantics { hideFromAccessibility() },
                    contentAlignment = Alignment.Center,
                ) {
                    AmbientPacer(
                        active = runtime.subPhase == MeditationContract.SubPhase.PRACTICE && runtime.technique.hasPattern && !runtime.isPaused,
                    )
                    PacerCueOverlay(runtime)
                }

                Spacer(Modifier.height(28.dp))

                // Coach line — Phase 1: static per-sub-phase first line; Phase 2 will rotate
                AnimatedContent(
                    targetState = coachLineFor(runtime),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(400)))
                    },
                    label = "coach",
                ) { lineRes ->
                    Text(
                        text = lineRes?.let { stringResource(it) } ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            letterSpacing = 0.25.sp,
                        ),
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .animateContentSize(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // ── Bottom bar: Stop / meta / Pause ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onRequestStop,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.StopCircle,
                        contentDescription = stringResource(Strings.Meditation.Session.a11yStop),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Strings.Meditation.Session.stopButton).uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
                    )
                }

                Text(
                    text = sessionMeta(runtime, audio),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )

                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHigh),
                ) {
                    Icon(
                        imageVector = if (runtime.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = stringResource(
                            if (runtime.isPaused) Strings.Meditation.Session.a11yResume
                            else Strings.Meditation.Session.a11yPause
                        ),
                        modifier = Modifier.size(22.dp),
                        tint = colorScheme.onSurface,
                    )
                }
            }
        }
    }

    // ── Stop confirmation dialog (Phase 1: AlertDialog; Phase 2: BottomSheet) ─
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = onDismissStopDialog,
            title = {
                Text(
                    text = stringResource(Strings.Meditation.Stop.title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = stringResource(Strings.Meditation.Stop.body),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        letterSpacing = 0.15.sp,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(Strings.Meditation.Stop.end),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissStopDialog) {
                    Text(
                        text = stringResource(Strings.Meditation.Stop.keep),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        )
    }
}

// ── Sub-components ───────────────────────────────────────────────────────

/**
 * Phase 1 placeholder pacer — slow ambient pulse circle (5.5s in/out cycle).
 * Phase 2 replaces this with the full halo + 2 rings + circle from the design.
 */
@Composable
private fun AmbientPacer(active: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "sessionPacer")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 5500 else 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val opacity = if (active) 1f else 0.6f

    Box(
        modifier = Modifier.size(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Halo
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    scaleX = 0.85f + 0.25f * scale
                    scaleY = 0.85f + 0.25f * scale
                    alpha = opacity
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = 0.22f),
                            colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        radius = 380f,
                    )
                )
        )
        // Outer ring
        Box(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    scaleX = 0.65f + 0.45f * scale
                    scaleY = 0.65f + 0.45f * scale
                    alpha = opacity
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.08f))
        )
        // Mid ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = 0.55f + 0.55f * scale
                    scaleY = 0.55f + 0.55f * scale
                    alpha = opacity
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f))
        )
        // Inner circle
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = opacity
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colorScheme.primaryContainer,
                            colorScheme.primary,
                        ),
                    )
                )
        )
    }
}

@Composable
private fun PacerCueOverlay(runtime: MeditationContract.SessionRuntime) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(cueWordFor(runtime)),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
            color = colorScheme.onSurface,
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

@Composable
private fun phaseLabel(
    runtime: MeditationContract.SessionRuntime,
    technique: BreathingPattern,
): String {
    val phase = stringResource(
        when (runtime.subPhase) {
            MeditationContract.SubPhase.SETTLING -> Strings.Meditation.Session.phaseSettling
            MeditationContract.SubPhase.PRACTICE -> Strings.Meditation.Session.phasePractice
            MeditationContract.SubPhase.INTEGRATION -> Strings.Meditation.Session.phaseIntegration
        }
    )
    val techName = stringResource(technique.nameRes)
    return "${phase.uppercase()} · $techName"
}

@Composable
private fun sessionMeta(
    runtime: MeditationContract.SessionRuntime,
    audio: MeditationAudio,
): String {
    if (runtime.isPaused) {
        return stringResource(Strings.Meditation.Session.paused)
    }
    val short = stringResource(runtime.technique.shortRes)
    return when (audio) {
        MeditationAudio.SILENT -> short
        MeditationAudio.VOICE -> stringResource(
            Strings.Meditation.Session.metaTemplate,
            short,
            stringResource(Strings.Meditation.Session.audioVoice),
        )
        MeditationAudio.CHIMES -> stringResource(
            Strings.Meditation.Session.metaTemplate,
            short,
            stringResource(Strings.Meditation.Session.audioChimes),
        )
    }
}

private fun cueWordFor(runtime: MeditationContract.SessionRuntime): StringResource = when (runtime.subPhase) {
    MeditationContract.SubPhase.SETTLING -> Strings.Meditation.Cue.arrive
    MeditationContract.SubPhase.INTEGRATION -> Strings.Meditation.Cue.release
    MeditationContract.SubPhase.PRACTICE -> {
        // Phase 1: static "Breathe" cue. Phase 2 will rotate per breath segment
        // (Breathe in / Hold / Breathe out) tied to the BreathingPattern timing.
        Strings.Meditation.Cue.breathe
    }
}

private fun coachLineFor(runtime: MeditationContract.SessionRuntime): StringResource? = when (runtime.subPhase) {
    MeditationContract.SubPhase.SETTLING -> Strings.Meditation.SettleCoach.line1
    MeditationContract.SubPhase.INTEGRATION -> Strings.Meditation.IntegrateCoach.line1
    MeditationContract.SubPhase.PRACTICE -> runtime.technique.coachRes(0)
}

private fun formatTimer(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
