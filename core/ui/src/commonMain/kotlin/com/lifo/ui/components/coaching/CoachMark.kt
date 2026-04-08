@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkState — hoisted state for the entire overlay sequence
// ─────────────────────────────────────────────────────────────────────────────

class CoachMarkState(val steps: List<CoachMarkStep>) {

    var currentIndex by mutableStateOf(-1)
        private set

    private val targets = mutableStateMapOf<String, Rect>()
    private val targetPositions = mutableStateMapOf<String, Offset>()

    /** Top Y coordinate (px) of each registered target */
    private val targetTops = mutableStateMapOf<String, Float>()

    private val bringIntoViewRequesters = mutableMapOf<String, BringIntoViewRequester>()

    val isVisible: Boolean get() = currentIndex >= 0
    val currentStep: CoachMarkStep? get() = steps.getOrNull(currentIndex)
    val currentSpotlight: Rect? get() = currentStep?.targetKey?.let { targets[it] }
    val currentCardPosition: Offset? get() = currentStep?.targetKey?.let { targetPositions[it] }
    val totalSteps: Int get() = steps.size
    val stepDisplay: String get() = "${currentIndex + 1} di $totalSteps"

    fun start() { currentIndex = 0 }
    fun advance() { if (currentIndex < steps.lastIndex) currentIndex++ else hide() }
    fun hide() { currentIndex = -1 }

    internal fun registerTarget(key: String, bounds: Rect) {
        targets[key] = bounds
    }

    internal fun registerTargetPosition(key: String, coords: LayoutCoordinates) {
        val bounds = coords.boundsInRoot()
        targetPositions[key] = bounds.bottomCenter
        targetTops[key] = bounds.top
    }

    internal fun registerBringIntoViewRequester(key: String, requester: BringIntoViewRequester) {
        bringIntoViewRequesters[key] = requester
    }

    internal fun getBringIntoViewRequester(key: String): BringIntoViewRequester? =
        bringIntoViewRequesters[key]

    /** Returns the top Y (px) of the target, or null if not yet measured. */
    fun getTargetTop(key: String): Float? = targetTops[key]
}

@Composable
fun rememberCoachMarkState(steps: List<CoachMarkStep>): CoachMarkState =
    remember(steps) { CoachMarkState(steps) }

// ─────────────────────────────────────────────────────────────────────────────
// Modifier extension
// ─────────────────────────────────────────────────────────────────────────────

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
    state.registerTargetPosition(key, coords)
}

@Composable
fun CoachMarkTargetWithScroll(
    state: CoachMarkState,
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
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
// CoachMarkOverlay
// ─────────────────────────────────────────────────────────────────────────────

private val OverlayColor = Color(0xCC000000)
private val SpotlightColor = Color(0x00000000)

@Composable
fun CoachMarkOverlay(
    state: CoachMarkState,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    bottomBarHeight: Dp = 80.dp,
) {
    AnimatedVisibility(
        visible  = state.isVisible,
        enter    = fadeIn(),
        exit     = fadeOut(),
        modifier = modifier,
    ) {
        val spotlight    = state.currentSpotlight
        val cardPosition = state.currentCardPosition
        val density      = LocalDensity.current

        LaunchedEffect(state.currentStep?.targetKey) {
            state.currentStep?.targetKey?.let { key ->
                state.getBringIntoViewRequester(key)?.bringIntoView()
            }
        }

        var actualCardHeight by remember { mutableStateOf(0.dp) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication        = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick           = { /* block pass-through */ }
                )
        ) {
            val screenHeightPx = constraints.maxHeight.toFloat()

            // ── Overlay / spotlight ──────────────────────────────────────────
            if (spotlight != null) {
                SpotlightCanvas(rect = spotlight)
            } else {
                Box(Modifier.fillMaxSize().background(OverlayColor))
            }

            // ── Tooltip card ─────────────────────────────────────────────────
            state.currentStep?.let { step ->
                val navBarHeight = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()

                AnimatedVisibility(
                    visible = state.isVisible,
                    enter   = fadeIn() + slideInVertically { it / 2 },
                    exit    = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    if (cardPosition != null) {
                        val gapDp       = 16.dp
                        val cardHeight  = if (actualCardHeight > 0.dp) actualCardHeight else 250.dp

                        // Convert to px for space calculations
                        val cardHeightPx    = with(density) { cardHeight.toPx() }
                        val navBarHeightPx  = with(density) { navBarHeight.toPx() }
                        val bottomBarPx     = with(density) { bottomBarHeight.toPx() }
                        val gapPx           = with(density) { gapDp.toPx() }
                        val usableBottomPx  = screenHeightPx - navBarHeightPx - bottomBarPx

                        val targetBotPx = cardPosition.y          // bottomCenter.y
                        val targetTopPx = step.targetKey
                            ?.let { state.getTargetTop(it) }
                            ?: (targetBotPx - with(density) { 56.dp.toPx() })

                        // Does the card fit below the target (above the nav bar)?
                        val spaceBelow = usableBottomPx - targetBotPx - gapPx
                        val fitsBelow  = spaceBelow >= cardHeightPx

                        val topPadding = with(density) {
                            if (fitsBelow) {
                                // Enough room below → place card under the target
                                (targetBotPx + gapPx).toDp()
                            } else {
                                // Not enough room below → place card ABOVE the target
                                (targetTopPx - cardHeightPx - gapPx)
                                    .coerceAtLeast(8f)
                                    .toDp()
                            }
                        }

                        val startPadding = with(density) {
                            (cardPosition.x.toDp() - 100.dp).coerceAtLeast(0.dp)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = startPadding, top = topPadding),
                        ) {
                            CoachMarkCard(
                                step        = step,
                                stepDisplay = state.stepDisplay,
                                isLastStep  = state.currentIndex == state.totalSteps - 1,
                                onNext      = { state.advance() },
                                onSkip      = { state.hide(); onFinished() },
                                modifier    = Modifier.onSizeChanged { size ->
                                    actualCardHeight = with(density) { size.height.toDp() }
                                }
                            )
                        }
                    } else {
                        // Fallback: no position yet → bottom-center above nav + bottom bar
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(bottom = bottomBarHeight),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            CoachMarkCard(
                                step        = step,
                                stepDisplay = state.stepDisplay,
                                isLastStep  = state.currentIndex == state.totalSteps - 1,
                                onNext      = { state.advance() },
                                onSkip      = { state.hide(); onFinished() },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SpotlightCanvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotlightCanvas(rect: Rect) {
    val cornerRadius = 16f
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        drawRect(color = OverlayColor)
        drawRoundRect(
            color        = SpotlightColor,
            topLeft      = androidx.compose.ui.geometry.Offset(rect.left, rect.top),
            size         = rect.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            blendMode    = BlendMode.Clear,
        )
        drawRoundRect(
            color        = Color(0xFF4CAF7D).copy(alpha = 0.4f),
            topLeft      = androidx.compose.ui.geometry.Offset(rect.left - 4f, rect.top - 4f),
            size         = rect.size.copy(width = rect.width + 8f, height = rect.height + 8f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius + 4f),
            style        = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CoachMarkCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoachMarkCard(
    step: CoachMarkStep,
    stepDisplay: String,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier  = modifier
            .fillMaxWidth(0.85f)
            .padding(horizontal = CalmifySpacing.lg),
        shape     = RoundedCornerShape(CalmifyRadius.xxl),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(modifier = Modifier.padding(CalmifySpacing.xl)) {

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDots(
                    total    = stepDisplay.substringAfter("di").trim().toIntOrNull() ?: 1,
                    current  = stepDisplay.substringBefore("di").trim().toIntOrNull()?.minus(1) ?: 0,
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

            Text(
                text       = step.title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(CalmifySpacing.sm))

            Text(
                text  = step.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(CalmifySpacing.xl))

            Button(
                onClick  = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(CalmifyRadius.lg),
                colors   = ButtonDefaults.buttonColors(
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
// StepDots
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
                color   = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
                content = {},
            )
        }
    }
}