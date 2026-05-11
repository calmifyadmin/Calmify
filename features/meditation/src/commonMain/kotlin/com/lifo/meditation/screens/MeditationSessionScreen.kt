package com.lifo.meditation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.meditation.MeditationContract
import com.lifo.ui.accessibility.isReducedMotionEnabled
import com.lifo.ui.i18n.Strings
import com.lifo.ui.i18n.coachRes
import com.lifo.ui.i18n.cueRes
import com.lifo.ui.i18n.nameRes
import com.lifo.ui.i18n.shortRes
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BreathSegmentKind
import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationAudio
import kotlin.math.ceil
import kotlin.math.max
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Phase 2 Session screen — production-grade pacer + per-segment cue + coach rotation.
 *
 * Anatomy:
 * - Top: phase label ("SETTLING · COHERENT", etc.) + remaining timer (MM:SS)
 * - Linear progress (elapsed / totalActive)
 * - Stage: [BreathingPacer] (halo + outer ring + mid ring + circle) + cue overlay (word + count)
 * - Coach line (AnimatedContent fade, rotates every 12s during PRACTICE)
 * - Bottom: Stop OutlinedButton + meta text + Pause/Resume IconButton
 *
 * The pacer drives off `runtime.currentSegmentIndex` (from the contract). On each
 * segment boundary, an [Animatable] snaps to the segment's start scale and animates
 * to its end scale over `segment.seconds` with the design's cubic-bezier(.4,0,.2,1)
 * easing, replicating the CSS transition used in the Claude Design source.
 *
 * Stop confirmation uses [ModalBottomSheet] (Phase 1 used [AlertDialog]).
 *
 * Coach rotation:
 * - SETTLING / INTEGRATION: 3 lines, advanced linearly with elapsed within the sub-phase
 * - PRACTICE: 3 lines per technique, advanced every 12s
 *
 * Phase 3 will add: TTS (Sherpa-ONNX), reduced-motion clamping, full TalkBack
 * pass with focus order verification, locale-specific screenshot regression.
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
    val reducedMotion = isReducedMotionEnabled()

    // Keyboard shortcut focus target (ESC opens stop modal, SPACE toggles pause).
    // Matters for hardware keyboards (Bluetooth, tablet docks, Chromebook) and
    // for the Level 3 desktop/web ports — no behavior change on phone-only.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> {
                        if (!showStopDialog) onRequestStop()
                        true
                    }
                    Key.Spacebar -> {
                        if (!showStopDialog) onPauseToggle()
                        true
                    }
                    else -> false
                }
            },
        color = colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Top bar: phase label + remaining time ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CalmifySpacing.xl) // was 20.dp → xl (24)
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
            val progressFraction = if (runtime.totalActiveMillis > 0L) {
                (runtime.elapsedMillis.toFloat() / runtime.totalActiveMillis).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CalmifySpacing.xl) // was 20.dp → xl (24)
                    .height(3.dp)
                    .clip(RoundedCornerShape(CalmifyRadius.pill)),                                  // was 999.dp ✓
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceContainerHighest,
            )

            // ── Stage: pacer + cue + coach ──────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = CalmifySpacing.xl),                 // was 20.dp → xl (24)
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Pacer geometry is decorative — TalkBack reads cue word + count
                    // from the PacerCueOverlay's liveRegion semantics instead.
                    Box(modifier = Modifier.semantics { hideFromAccessibility() }) {
                        BreathingPacer(runtime = runtime, reducedMotion = reducedMotion)
                    }
                    PacerCueOverlay(runtime = runtime)
                }

                Spacer(Modifier.height(28.dp))

                CoachLine(runtime = runtime)
            }

            // ── Bottom bar: Stop / meta / Pause ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = CalmifySpacing.xl, vertical = CalmifySpacing.lg), // was 20+16 → xl+lg
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onRequestStop,
                    shape = RoundedCornerShape(CalmifyRadius.pill), // was 999.dp ✓
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

    // ── Stop confirmation modal bottom sheet ────────────────────────────
    if (showStopDialog) {
        StopConfirmationSheet(
            onConfirm = onConfirmStop,
            onDismiss = onDismissStopDialog,
        )
    }
}

// ── Pacer ────────────────────────────────────────────────────────────────

/**
 * The breathing pacer visual: 4 concentric layers (halo, outer ring, mid ring,
 * inner circle) that scale together. During [MeditationContract.SubPhase.PRACTICE]
 * with a pattern technique, the scale follows the per-segment target (0.55 ↔ 1.0)
 * with the design's cubic-bezier(.4,0,.2,1) easing over the segment's duration.
 *
 * For SETTLING / INTEGRATION or no-pattern techniques (BELLY_NATURAL / BODY_SCAN),
 * a slow ambient infinite pulse (4s cycle) replaces the per-segment animation, with
 * lower opacity so the visual remains present but recedes from focus.
 *
 * The implementation uses [Animatable] keyed on `runtime.currentSegmentIndex` and
 * `runtime.isPaused`: each segment boundary triggers `snapTo(start) → animateTo(end)`,
 * pause halts (stop()) the in-flight animation. This produces the same "breath that
 * pauses with you" UX as the design's CSS transitions.
 */
@Composable
private fun BreathingPacer(
    runtime: MeditationContract.SessionRuntime,
    reducedMotion: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val pacingActive = runtime.subPhase == MeditationContract.SubPhase.PRACTICE &&
        runtime.technique.hasPattern && runtime.currentSegment != null

    if (pacingActive) {
        ActivePacer(runtime = runtime, colorScheme = colorScheme, reducedMotion = reducedMotion)
    } else if (reducedMotion) {
        // Reduced-motion: no infinite ambient pulse — render a static mid-scale circle
        // so the visual still grounds the user but doesn't move.
        PacerLayers(scale = 0.55f, opacity = AMBIENT_OPACITY, colorScheme = colorScheme)
    } else {
        AmbientPulsePacer(colorScheme = colorScheme)
    }
}

@Composable
private fun ActivePacer(
    runtime: MeditationContract.SessionRuntime,
    colorScheme: androidx.compose.material3.ColorScheme,
    reducedMotion: Boolean,
) {
    val pacerEasing = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }
    val scale = remember { Animatable(SCALE_LOW) }

    LaunchedEffect(runtime.currentSegmentIndex, runtime.isPaused) {
        if (runtime.isPaused) {
            scale.stop()
            return@LaunchedEffect
        }
        val seg = runtime.currentSegment ?: return@LaunchedEffect
        val (start, end) = when (seg.kind) {
            BreathSegmentKind.INHALE -> SCALE_LOW to SCALE_HIGH
            BreathSegmentKind.EXHALE -> SCALE_HIGH to SCALE_LOW
            BreathSegmentKind.HOLD_IN -> SCALE_HIGH to SCALE_HIGH
            BreathSegmentKind.HOLD_OUT -> SCALE_LOW to SCALE_LOW
        }
        // Compensate for animation already in flight when the segment changes:
        // start from the current segment-progress position, not always the segment start.
        val intoMillis = runtime.intoSegmentMillis.toFloat()
        val totalMillis = (seg.seconds * 1000f).coerceAtLeast(1f)
        val progress = (intoMillis / totalMillis).coerceIn(0f, 1f)
        val resumeStart = start + (end - start) * progress
        scale.snapTo(resumeStart)
        if (start != end) {
            // Reduced motion: clamp every per-segment tween to a short fixed duration.
            // The breath rhythm is still conveyed by the cue word + count overlay.
            val durationMs = if (reducedMotion) {
                REDUCED_MOTION_TWEEN_MILLIS
            } else {
                (totalMillis - intoMillis).coerceAtLeast(0f).toInt()
            }
            if (durationMs > 0) {
                scale.animateTo(
                    targetValue = end,
                    animationSpec = tween(durationMillis = durationMs, easing = pacerEasing),
                )
            } else {
                scale.snapTo(end)
            }
        }
    }

    PacerLayers(scale = scale.value, opacity = 1f, colorScheme = colorScheme)
}

@Composable
private fun AmbientPulsePacer(
    colorScheme: androidx.compose.material3.ColorScheme,
) {
    val transition = rememberInfiniteTransition(label = "ambientPacer")
    val scale by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambientScale",
    )
    PacerLayers(scale = scale, opacity = AMBIENT_OPACITY, colorScheme = colorScheme)
}

/**
 * 4-layer concentric pacer geometry — same proportions as the design's
 * `pacer-halo / pacer-ring-outer / pacer-ring-mid / pacer-circle`.
 *
 * Halo: full size, light radial gradient. Outer ring: 280dp, very subtle fill.
 * Mid ring: 200dp, soft fill. Inner circle: 130dp, sage radial gradient.
 *
 * Each layer multiplies the input scale through a per-layer offset so they
 * appear to breathe together but with slight phase offset, matching the design.
 */
@Composable
private fun PacerLayers(
    scale: Float,
    opacity: Float,
    colorScheme: androidx.compose.material3.ColorScheme,
) {
    Box(
        modifier = Modifier.size(320.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Halo (background glow)
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    val s = 0.85f + 0.25f * scale
                    scaleX = s; scaleY = s; alpha = opacity * 0.95f
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
                    val s = 0.65f + 0.45f * scale
                    scaleX = s; scaleY = s; alpha = opacity * 0.9f
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.08f))
        )
        // Mid ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    val s = 0.55f + 0.55f * scale
                    scaleX = s; scaleY = s; alpha = opacity * 0.95f
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f))
        )
        // Inner circle
        Box(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale; alpha = opacity
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
    val cue = cueWordFor(runtime)
    val count = cueCountFor(runtime)

    // The cue word is the primary semantic surface for screen readers — TalkBack
    // re-announces it on each segment boundary. The count is intentionally NOT
    // a live region (announcing "5, 4, 3, 2, 1" every breath would be hostile).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = cue,
            transitionSpec = {
                fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "cueWord",
        ) { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                ),
                color = colorScheme.onSurface,
            )
        }
        if (count != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        }
    }
}

// ── Coach line ───────────────────────────────────────────────────────────

/**
 * Coach line text with technique- and sub-phase-aware rotation.
 *
 * - SETTLING: 3 lines progress linearly with elapsed/settle (line index = elapsed/(settle/3))
 * - INTEGRATION: 3 lines progress linearly with into/integrate
 * - PRACTICE: 3 lines per technique rotate every 12s (mod 3)
 *
 * Transitions use 600ms fade — matches the design's `.coach-fade` CSS class.
 */
@Composable
private fun CoachLine(runtime: MeditationContract.SessionRuntime) {
    val colorScheme = MaterialTheme.colorScheme
    val coachRes = currentCoachLine(runtime)

    AnimatedContent(
        targetState = coachRes,
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
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun currentCoachLine(runtime: MeditationContract.SessionRuntime): StringResource? {
    return when (runtime.subPhase) {
        MeditationContract.SubPhase.SETTLING -> {
            val idx = settleCoachIndex(runtime)
            when (idx) {
                0 -> Strings.Meditation.SettleCoach.line1
                1 -> Strings.Meditation.SettleCoach.line2
                else -> Strings.Meditation.SettleCoach.line3
            }
        }
        MeditationContract.SubPhase.INTEGRATION -> {
            val idx = integrateCoachIndex(runtime)
            when (idx) {
                0 -> Strings.Meditation.IntegrateCoach.line1
                1 -> Strings.Meditation.IntegrateCoach.line2
                else -> Strings.Meditation.IntegrateCoach.line3
            }
        }
        MeditationContract.SubPhase.PRACTICE -> practiceCoachRes(runtime)
    }
}

@Composable
private fun practiceCoachRes(runtime: MeditationContract.SessionRuntime): StringResource? {
    // Tick once per 12s rotation, but recompose every second so the index advances
    // smoothly even if the VM tick lands mid-rotation. The wall-clock here is the
    // VM elapsedMillis which already accounts for pause.
    val practiceMillis = max(0L, runtime.practiceElapsedMillis)
    val rotationIdx = ((practiceMillis / COACH_ROTATION_MILLIS).toInt()) % COACH_LINES_PER_TECHNIQUE
    return runtime.technique.coachRes(rotationIdx)
}

private fun settleCoachIndex(runtime: MeditationContract.SessionRuntime): Int {
    val settleSec = runtime.settleSeconds.coerceAtLeast(1)
    val sliceSec = settleSec / SETTLE_LINES.toFloat()
    val elapsedSec = runtime.elapsedSeconds.coerceAtLeast(0)
    return (elapsedSec / sliceSec).toInt().coerceIn(0, SETTLE_LINES - 1)
}

private fun integrateCoachIndex(runtime: MeditationContract.SessionRuntime): Int {
    val intoIntegrateSec = (runtime.elapsedSeconds - runtime.settleSeconds - runtime.practiceCapSeconds)
        .coerceAtLeast(0)
    val sliceSec = runtime.integrateSeconds.coerceAtLeast(1) / INTEGRATE_LINES.toFloat()
    return (intoIntegrateSec / sliceSec).toInt().coerceIn(0, INTEGRATE_LINES - 1)
}

// ── Stop confirmation modal sheet ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopConfirmationSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerHigh,
        contentColor = colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Strings.Meditation.Stop.title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Strings.Meditation.Stop.body),
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    letterSpacing = 0.15.sp,
                ),
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CalmifySpacing.xl))                    // was 24.dp ✓
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md), // was 12.dp ✓
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),           // CTA height (= xxxl)
                ) {
                    Text(
                        text = stringResource(Strings.Meditation.Stop.keep),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(CalmifyRadius.pill), // was 999.dp ✓
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
            }
            Spacer(Modifier.height(16.dp))
        }
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

private fun cueWordFor(runtime: MeditationContract.SessionRuntime): StringResource {
    return when (runtime.subPhase) {
        MeditationContract.SubPhase.SETTLING -> Strings.Meditation.Cue.arrive
        MeditationContract.SubPhase.INTEGRATION -> Strings.Meditation.Cue.release
        MeditationContract.SubPhase.PRACTICE -> {
            val seg = runtime.currentSegment
            if (seg != null) seg.kind.cueRes else Strings.Meditation.Cue.breathe
        }
    }
}

/**
 * Remaining whole seconds in the current breath segment, shown beneath the cue word.
 * Returns null when there is nothing to count (no pacer, paused, or outside PRACTICE).
 */
private fun cueCountFor(runtime: MeditationContract.SessionRuntime): Int? {
    if (runtime.isPaused) return null
    if (runtime.subPhase != MeditationContract.SubPhase.PRACTICE) return null
    val seg = runtime.currentSegment ?: return null
    val totalMillis = (seg.seconds * 1000f).toLong()
    val remainingMillis = (totalMillis - runtime.intoSegmentMillis).coerceAtLeast(0L)
    return ceil(remainingMillis / 1000f).toInt().coerceAtLeast(1)
}

private fun formatTimer(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

// ── Constants ────────────────────────────────────────────────────────────

/** Smallest pacer scale (~exhale rest position). Matches design `0.55`. */
private const val SCALE_LOW = 0.55f

/** Largest pacer scale (~peak inhale). Matches design `1.0`. */
private const val SCALE_HIGH = 1.0f

/** Opacity multiplier for ambient (settling/integration/no-pattern) state. */
private const val AMBIENT_OPACITY = 0.6f

/** Coach lines available per technique (3 in the design). */
private const val COACH_LINES_PER_TECHNIQUE = 3

/** Settling sub-phase coach lines (3 in the design). */
private const val SETTLE_LINES = 3

/** Integration sub-phase coach lines (3 in the design). */
private const val INTEGRATE_LINES = 3

/** Practice coach line rotation cadence — design uses 12s. */
private const val COACH_ROTATION_MILLIS = 12_000L

/**
 * Per-segment scale tween duration when the user has enabled reduced motion.
 * Short enough to feel snappy, long enough to remain perceptually a transition
 * (rather than a teleport). 200ms is the WCAG-compatible safe minimum.
 */
private const val REDUCED_MOTION_TWEEN_MILLIS = 200
