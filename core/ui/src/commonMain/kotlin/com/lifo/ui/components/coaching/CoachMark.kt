package com.lifo.ui.components.coaching

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.modifier.ModifierLocalConsumer
import androidx.compose.ui.modifier.ModifierLocalReadScope
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import androidx.compose.ui.semantics.semantics
import com.svenjacobs.reveal.Reveal
import com.svenjacobs.reveal.RevealCanvas
import com.svenjacobs.reveal.RevealCanvasState
import com.svenjacobs.reveal.RevealOverlayArrangement
import com.svenjacobs.reveal.RevealState
import com.svenjacobs.reveal.rememberRevealCanvasState
import com.svenjacobs.reveal.rememberRevealState
import com.svenjacobs.reveal.revealable

// ─────────────────────────────────────────────────────────────────────────────
// CompositionLocal — propagates Reveal state without explicit parameters
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provides the [RevealCanvasState] to all descendant [CoachMarkOverlay] composables.
 * Set by [RevealCanvasWrapper] at app level.
 */
val LocalRevealCanvasState = staticCompositionLocalOf<RevealCanvasState?> { null }

/**
 * Provides the [RevealState] to elements marked with [coachMarkTarget].
 * Set by [CoachMarkOverlay] internally for reveal functionality.
 */
val LocalRevealState = staticCompositionLocalOf<RevealState?> { null }

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkState — hoisted state for the entire overlay sequence
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Hoisted state for a coach-mark sequence using Reveal library.
 *
 * Usage in screen:
 * ```
 * val coachState = rememberCoachMarkState(ScreenTutorials.home)
 *
 * LaunchedEffect(Unit) {
 *     if (onboardingManager.shouldShowTutorial(ScreenTutorials.KEY_HOME)) {
 *         coachState.start()
 *     }
 * }
 *
 * CoachMarkOverlay(state = coachState, onFinished = { ... }) {
 *     Box(Modifier.fillMaxSize()) {
 *         GreetingRow(modifier = Modifier.coachMarkTarget(coachState, CoachMarkKeys.HOME_GREETING))
 *         // ... other content with targets
 *     }
 * }
 * ```
 */
class CoachMarkState(val steps: List<CoachMarkStep>) {

    /** Index of the currently active step, or -1 when hidden. */
    var currentIndex by mutableStateOf(-1)
        private set

    /** Current step data, or null when not visible. */
    val currentStep: CoachMarkStep? get() = steps.getOrNull(currentIndex)

    val isVisible: Boolean get() = currentIndex >= 0
    val totalSteps: Int get() = steps.size
    val stepDisplay: String get() = "${currentIndex + 1} di $totalSteps"

    fun start() {
        currentIndex = 0
    }

    fun advance() {
        if (currentIndex < steps.lastIndex) currentIndex++ else hide()
    }

    fun hide() {
        currentIndex = -1
    }
}

@Composable
fun rememberCoachMarkState(steps: List<CoachMarkStep>): CoachMarkState =
    remember(steps) { CoachMarkState(steps) }

// ─────────────────────────────────────────────────────────────────────────────
// Modifier extension — marks element as spotlight target (Reveal)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Attach this to any composable to register it as a spotlight target.
 * Uses Modifier.composed() to read RevealState from CompositionLocal
 * and apply the revealable() modifier from the Reveal library.
 *
 * @param state  The [CoachMarkState] that drives the overlay.
 * @param key    One of the [CoachMarkKeys] constants.
 */
fun Modifier.coachMarkTarget(
    state: CoachMarkState,
    key: String,
): Modifier = composed {
    val revealState = LocalRevealState.current
    if (revealState != null) {
        this.revealable(key = key as Any, state = revealState)
    } else {
        this
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkOverlay — Reveal-based coach mark overlay with content wrapping
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Coach-mark overlay using Reveal library.
 * Wraps the screen content to enable spotlight on target elements.
 *
 * Requires [RevealCanvasWrapper] to be set up at app level.
 *
 * The [RevealCanvasState] is automatically obtained from [LocalRevealCanvasState]
 * (no explicit parameter needed).
 *
 * @param state       Drives visibility, current step, and spotlight position.
 * @param onFinished  Called when the user completes or skips the entire sequence.
 * @param modifier    Applied to the Reveal composable.
 * @param content     The screen content with .coachMarkTarget() elements.
 */
@Composable
fun CoachMarkOverlay(
    state: CoachMarkState,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val revealCanvasState = LocalRevealCanvasState.current ?: run {
        // Fallback: if no RevealCanvasWrapper, just render content without overlay
        content()
        return
    }

    val revealState = rememberRevealState()

    // Sync CoachMarkState with Reveal using LaunchedEffect (reveal/hide are suspend functions)
    LaunchedEffect(state.currentStep?.targetKey) {
        if (state.isVisible && state.currentStep != null) {
            try {
                revealState.reveal(state.currentStep!!.targetKey as Any)
            } catch (e: IllegalArgumentException) {
                // Target may not be rendered yet, try again after delay
                kotlinx.coroutines.delay(500)
                try {
                    revealState.reveal(state.currentStep!!.targetKey as Any)
                } catch (e2: Exception) {
                    // Target still not found, silently continue
                }
            }
        } else {
            revealState.hide()
        }
    }

    Reveal(
        revealCanvasState = revealCanvasState,
        revealState = revealState,
        onRevealableClick = { /* No action on target click */ },
        onOverlayClick = { /* Overlay is non-interactive */ },
        modifier = modifier,
        overlayArrangement = RevealOverlayArrangement.Bottom,
        overlayContent = { key ->
            state.steps.find { it.targetKey == key }?.let { step ->
                Surface(
                    modifier = Modifier
                        .padding(CalmifySpacing.lg),
                    shape = RoundedCornerShape(CalmifyRadius.xxl),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                ) {
                    CoachMarkCard(
                        step = step,
                        stepDisplay = state.stepDisplay,
                        isLastStep = state.currentIndex == state.totalSteps - 1,
                        onNext = { state.advance() },
                        onSkip = {
                            state.hide()
                            onFinished()
                        },
                    )
                }
            }
        },
    ) {
        // Provide RevealState to all descendant coachMarkTarget modifiers
        CompositionLocalProvider(LocalRevealState provides revealState) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkCard — tooltip card with step progress and actions
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CoachMarkCard(
    step: CoachMarkStep,
    stepDisplay: String,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.padding(CalmifySpacing.xl)) {

        // ── Header row: step indicator + skip ────────────────────────────
        Row(
            modifier            = Modifier.fillMaxWidth(),
            verticalAlignment   = Alignment.CenterVertically,
        ) {
            StepDots(
                total   = stepDisplay.substringAfter("di").trim().toIntOrNull() ?: 1,
                current = stepDisplay.substringBefore("di").trim().toIntOrNull()?.minus(1) ?: 0,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSkip) {
                Text(
                    text  = "Salta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(CalmifySpacing.md))

        // ── Title ─────────────────────────────────────────────────────────
        Text(
            text       = step.title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(CalmifySpacing.sm))

        // ── Description ───────────────────────────────────────────────────
        Text(
            text  = step.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(CalmifySpacing.xl))

        // ── Advance button ────────────────────────────────────────────────
        Button(
            onClick   = onNext,
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(CalmifyRadius.lg),
            colors    = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text       = if (isLastStep) "Capito!" else step.buttonText,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StepDots — small progress indicator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepDots(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier          = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val isActive = index == current
            val dotWidth by animateDpAsState(
                targetValue   = if (isActive) 20.dp else 6.dp,
                animationSpec = spring(),
                label         = "dotWidth",
            )
            Surface(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(width = dotWidth, height = 6.dp)
                    .clip(CircleShape),
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
                content = {},
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RevealCanvasWrapper — wraps app content with RevealCanvas + CompositionLocal
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps your app content with [RevealCanvas] and provides [LocalRevealCanvasState].
 * Use at the **root level** of your app (in DecomposeApp or MainActivity).
 *
 * Usage:
 * ```
 * RevealCanvasWrapper {
 *     YourAppContent()
 * }
 * ```
 */
@Composable
fun RevealCanvasWrapper(
    content: @Composable () -> Unit,
) {
    val revealCanvasState = rememberRevealCanvasState()
    CompositionLocalProvider(LocalRevealCanvasState provides revealCanvasState) {
        RevealCanvas(
            revealCanvasState = revealCanvasState,
        ) {
            content()
        }
    }
}
