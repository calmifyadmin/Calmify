package com.lifo.ui.tutorial

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

/**
 * A single step in a coach-mark tutorial sequence.
 */
data class CoachMarkStep(
    val title: String,
    val description: String,
)

/**
 * Full-screen semi-transparent overlay with a bottom-anchored tooltip card.
 * Manages its own step state internally.
 *
 * Usage — wrap your Scaffold in a Box and place CoachMark as a sibling:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     Scaffold(...) { ... }
 *     CoachMark(visible = show, steps = ..., onComplete = { show = false })
 * }
 * ```
 */
@Composable
fun CoachMark(
    visible: Boolean,
    steps: List<CoachMarkStep>,
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete,
) {
    if (steps.isEmpty()) return

    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    // Reset step whenever the CoachMark becomes visible again
    LaunchedEffect(visible) {
        if (visible) currentStep = 0
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(300)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* consume touches — prevent click-through */ },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(200)))
                },
                label = "coachmark_step",
            ) { step ->
                val item = steps[step]
                val isLast = step == steps.lastIndex

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp)
                        .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.xl),
                    shape = RoundedCornerShape(CalmifyRadius.xxl),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(CalmifySpacing.xl),
                        verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
                    ) {
                        // ── Step dots ─────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            steps.forEachIndexed { i, _ ->
                                val isActive = i == step
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isActive) 24.dp else 8.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                    label = "dot_width_$i",
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(8.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                )
                            }
                        }

                        Spacer(Modifier.height(CalmifySpacing.sm))

                        // ── Step label ────────────────────────────────────────
                        Text(
                            text = "${step + 1} / ${steps.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // ── Title ─────────────────────────────────────────────
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 28.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // ── Description ───────────────────────────────────────
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 24.sp,
                        )

                        Spacer(Modifier.height(CalmifySpacing.sm))

                        // ── Buttons ───────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onSkip) {
                                Text(
                                    text = "Salta",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = {
                                    if (isLast) onComplete()
                                    else currentStep++
                                },
                                shape = RoundedCornerShape(CalmifyRadius.lg),
                            ) {
                                Text(text = if (isLast) "Capito!" else "Avanti")
                            }
                        }
                    }
                }
            }
        }
    }
}
