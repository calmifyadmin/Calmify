package com.lifo.ui.components.stepper

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Stepper Component
 *
 * Features:
 * - Fluid animations with spring physics
 * - Morphing shapes between steps
 * - Expressive colors and typography
 * - Progress indication with check marks
 * - Collapsing behavior on scroll (labels hide, circles shrink)
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ExpressiveStepper(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    stepLabels: List<String> = emptyList(),
    collapsed: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 0 until totalSteps) {
            // Step indicator
            StepIndicator(
                stepNumber = step + 1,
                isActive = step == currentStep,
                isCompleted = step < currentStep,
                label = stepLabels.getOrNull(step),
                collapsed = collapsed,
                modifier = Modifier.weight(1f)
            )

            // Connector line between steps (except last)
            if (step < totalSteps - 1) {
                StepConnector(
                    isCompleted = step < currentStep,
                    modifier = Modifier.width(24.dp)
                )
            }
        }
    }
}

/**
 * Individual step indicator with animations and collapsing support
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun StepIndicator(
    stepNumber: Int,
    isActive: Boolean,
    isCompleted: Boolean,
    label: String?,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.primary
            isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "background_color"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.onPrimary
            isActive -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "content_color"
    )

    // Scale animation: active step is larger when expanded, all smaller when collapsed
    val scale by animateFloatAsState(
        targetValue = when {
            collapsed -> 0.75f // All circles shrink when collapsed
            isActive -> 1.1f   // Active step larger when expanded
            else -> 1f         // Others normal when expanded
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Circle size animation for collapsed state
    val circleSize by animateDpAsState(
        targetValue = if (collapsed) 36.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "circle_size"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Step circle
        Box(
            modifier = Modifier
                .size(circleSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isCompleted,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                },
                label = "step_content"
            ) { completed ->
                if (completed) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = contentColor,
                        modifier = Modifier.size(if (collapsed) 18.dp else 24.dp)
                    )
                } else {
                    Text(
                        text = stepNumber.toString(),
                        style = if (collapsed) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor
                    )
                }
            }
        }

        // Step label with animated visibility - fades out when collapsed
        AnimatedVisibility(
            visible = !collapsed && label != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Connector line between steps
 */
@Composable
private fun StepConnector(
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (isCompleted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 300),
        label = "connector_color"
    )

    Box(
        modifier = modifier
            .height(2.dp)
            .background(color)
    )
}

/**
 * Expressive progress bar for linear progression
 */
@Composable
fun ExpressiveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Text(
            text = "${(animatedProgress * 100).toInt()}% Complete",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
