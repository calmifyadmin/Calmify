package com.lifo.ui.components.coaching

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkState — hoisted state for the entire overlay sequence
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Hoisted state for a coach-mark sequence.
 *
 * Usage:
 * ```
 * val coachState = rememberCoachMarkState(ScreenTutorials.home)
 *
 * Box(Modifier.fillMaxSize()) {
 *     // Content — attach targets via Modifier.coachMarkTarget(coachState, key)
 *     GreetingRow(modifier = Modifier.coachMarkTarget(coachState, CoachMarkKeys.HOME_GREETING))
 *
 *     CoachMarkOverlay(state = coachState, onFinished = { manager.markSeen(ScreenTutorials.KEY_HOME) })
 * }
 * ```
 */
class CoachMarkState(val steps: List<CoachMarkStep>) {

    /** Index of the currently active step, or -1 when hidden. */
    var currentIndex by mutableStateOf(-1)
        private set

    /** Registered target bounds keyed by [CoachMarkKeys] value. */
    private val targets = mutableMapOf<String, Rect>()

    /** Target position for positioning the card (from onGloballyPositioned) */
    private val targetPositions = mutableMapOf<String, Offset>()

    /** BringIntoViewRequester for each target to auto-scroll when coach mark shows */
    private val bringIntoViewRequesters = mutableMapOf<String, BringIntoViewRequester>()

    /** Whether the overlay is currently visible. */
    val isVisible: Boolean get() = currentIndex >= 0

    /** Current step data, or null when not visible. */
    val currentStep: CoachMarkStep? get() = steps.getOrNull(currentIndex)

    /** Spotlight rect for the current step, or null if no targetKey / not yet measured. */
    val currentSpotlight: Rect? get() = currentStep?.targetKey?.let { targets[it] }

    /** Card position offset for the current step */
    val currentCardPosition: Offset? get() = currentStep?.targetKey?.let { targetPositions[it] }

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

    internal fun registerTarget(key: String, bounds: Rect) {
        targets[key] = bounds
    }

    internal fun registerTargetPosition(key: String, coords: LayoutCoordinates) {
        targetPositions[key] = coords.boundsInRoot().bottomCenter
    }

    internal fun registerBringIntoViewRequester(key: String, requester: BringIntoViewRequester) {
        bringIntoViewRequesters[key] = requester
    }

    internal fun getBringIntoViewRequester(key: String): BringIntoViewRequester? {
        return bringIntoViewRequesters[key]
    }
}

@Composable
fun rememberCoachMarkState(steps: List<CoachMarkStep>): CoachMarkState =
    remember(steps) { CoachMarkState(steps) }

// ─────────────────────────────────────────────────────────────────────────────
// Modifier extension — registers element bounds
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Attach this to any composable to register it as a spotlight target.
 *
 * @param state  The [CoachMarkState] that drives the overlay.
 * @param key    One of the [CoachMarkKeys] constants.
 * @param padding Extra space (dp) added around the measured bounds for the spotlight ring.
 */
fun Modifier.coachMarkTarget(
    state: CoachMarkState,
    key: String,
    padding: Float = 12f,
): Modifier = this.onGloballyPositioned { coords: LayoutCoordinates ->
    val bounds = coords.boundsInRoot()
    state.registerTarget(
        key,
        Rect(
            left   = bounds.left   - padding,
            top    = bounds.top    - padding,
            right  = bounds.right  + padding,
            bottom = bounds.bottom + padding,
        )
    )
    // Store position for card positioning
    state.registerTargetPosition(key, coords)
}

/**
 * Wraps a composable that has coach mark targets with BringIntoViewRequester for auto-scroll.
 * Use this at the target's parent level.
 */
@Composable
fun CoachMarkTargetWithScroll(
    state: CoachMarkState,
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Register the requester when this composable is created
    LaunchedEffect(key) {
        state.registerBringIntoViewRequester(key, bringIntoViewRequester)
    }

    content(
        modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .coachMarkTarget(state, key)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkOverlay — the full-screen overlay composable
// ─────────────────────────────────────────────────────────────────────────────

private val OverlayColor = Color(0xCC000000)   // 80 % black, same feel as M3 scrim
private val SpotlightColor = Color(0x00000000) // transparent cut-out

/**
 * Full-screen coach-mark overlay. Place this at the top of a [Box] that contains the screen content.
 *
 * @param state      Drives visibility, current step, and spotlight position.
 * @param onFinished Called when the user completes or skips the entire sequence.
 */
@Composable
fun CoachMarkOverlay(
    state: CoachMarkState,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible     = state.isVisible,
        enter       = fadeIn(),
        exit        = fadeOut(),
        modifier    = modifier,
    ) {
        val spotlight = state.currentSpotlight
        val cardPosition = state.currentCardPosition
        val density = LocalDensity.current

        // Auto-scroll target into view when coach mark becomes visible
        LaunchedEffect(state.currentStep?.targetKey) {
            state.currentStep?.targetKey?.let { targetKey ->
                state.getBringIntoViewRequester(targetKey)?.bringIntoView()
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Consume all touches so nothing behind fires
                .clickable(
                    indication          = null,
                    interactionSource   = remember { MutableInteractionSource() },
                    onClick             = { /* block pass-through */ }
                )
        ) {
            val screenHeightPx = constraints.maxHeight.toFloat()

            // ── Dark overlay with optional spotlight cutout ──────────────────
            if (spotlight != null) {
                SpotlightCanvas(rect = spotlight)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OverlayColor)
                )
            }

            // ── Tooltip card positioned near the target ────────────────────────
            state.currentStep?.let { step ->
                AnimatedVisibility(
                    visible  = state.isVisible,
                    enter    = fadeIn() + slideInVertically { it / 2 },
                    exit     = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    if (cardPosition != null) {
                        // Position card relative to target bottom-center
                        val cardPadding = 16.dp
                        val startPadding = with(density) { cardPosition.x.toDp() - 100.dp }
                        val topPadding = with(density) { cardPosition.y.toDp() + cardPadding }

                        // Check if card will be in bottom area (below 70% of screen height)
                        val screenHeightDp = with(density) { screenHeightPx.toDp() }
                        val isCardInBottomArea = with(density) { cardPosition.y.toDp() > screenHeightDp * 0.7f }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = startPadding, top = topPadding)
                                .then(if (isCardInBottomArea) Modifier.navigationBarsPadding() else Modifier),
                        ) {
                            CoachMarkCard(
                                step        = step,
                                stepDisplay = state.stepDisplay,
                                isLastStep  = state.currentIndex == state.totalSteps - 1,
                                onNext      = { state.advance() },
                                onSkip      = {
                                    state.hide()
                                    onFinished()
                                },
                            )
                        }
                    } else {
                        // Fallback: position at bottom center if no position yet
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            CoachMarkCard(
                                step        = step,
                                stepDisplay = state.stepDisplay,
                                isLastStep  = state.currentIndex == state.totalSteps - 1,
                                onNext      = { state.advance() },
                                onSkip      = {
                                    state.hide()
                                    onFinished()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SpotlightCanvas — dark background + transparent rounded rect cutout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotlightCanvas(rect: Rect) {
    val cornerRadius = 16f

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        // 1. Solid dark overlay
        drawRect(color = OverlayColor)

        // 2. Cut out the spotlight area
        drawRoundRect(
            color        = SpotlightColor,
            topLeft      = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
            size         = rect.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            blendMode    = BlendMode.Clear,
        )

        // 3. Subtle pulsing ring around the spotlight
        drawRoundRect(
            color        = Color(0xFF4CAF7D).copy(alpha = 0.4f),
            topLeft      = androidx.compose.ui.geometry.Offset(rect.left - 4f, rect.top - 4f),
            size         = rect.size.copy(
                width  = rect.width + 8f,
                height = rect.height + 8f,
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius + 4f),
            style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkCard — tooltip card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoachMarkCard(
    step: CoachMarkStep,
    stepDisplay: String,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(horizontal = CalmifySpacing.lg),
        shape  = RoundedCornerShape(CalmifyRadius.xxl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
