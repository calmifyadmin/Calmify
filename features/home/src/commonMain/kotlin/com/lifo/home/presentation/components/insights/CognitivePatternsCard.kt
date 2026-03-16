package com.lifo.home.presentation.components.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.CognitivePatternSummary
import com.lifo.home.domain.model.PatternSentiment
import com.lifo.home.domain.model.TrendDirection
import com.lifo.home.util.EmotionAwareColors

/**
 * Cognitive Patterns Card - Timeline of identified CBT patterns
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  Pattern Cognitivi                    Questa    │
 * │                                       settimana │
 * │                                                 │
 * │  ● Pensiero catastrofico          3 occorrenze │
 * │    └─ "Tendenza a immaginare il peggio"        │
 * │                                                 │
 * │  ● Generalizzazione               2 occorrenze │
 * │    └─ "Estendere singoli eventi a tutto"       │
 * │                                                 │
 * │  ● Pensiero positivo              5 occorrenze │
 * │    └─ "Riconoscimento dei progressi"           │
 * │                                                 │
 * │  [Scopri di più sui tuoi pattern →]            │
 * └─────────────────────────────────────────────────┘
 */
@Composable
fun CognitivePatternsCard(
    patterns: List<CognitivePatternSummary>,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisiblePatterns: Int = 3
) {
    // Entrance animation
    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    var showAllPatterns by remember { mutableStateOf(false) }
    val visiblePatterns = if (showAllPatterns) patterns else patterns.take(maxVisiblePatterns)

    // Determine border color based on predominant sentiment
    val borderColor = when {
        patterns.count { it.sentiment == PatternSentiment.ADAPTIVE } >
                patterns.count { it.sentiment == PatternSentiment.MALADAPTIVE } ->
            EmotionAwareColors.PatternColors.adaptive

        patterns.count { it.sentiment == PatternSentiment.MALADAPTIVE } >
                patterns.count { it.sentiment == PatternSentiment.ADAPTIVE } ->
            EmotionAwareColors.PatternColors.maladaptive

        else -> EmotionAwareColors.PatternColors.neutral
    }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterTransition.value
                translationY = (1f - enterTransition.value) * 30f
            },
        shape = RoundedCornerShape(24.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pattern Cognitivi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Questa settimana",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pattern timeline
            if (patterns.isEmpty()) {
                EmptyPatternsState()
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    visiblePatterns.forEachIndexed { index, pattern ->
                        PatternTimelineItem(
                            pattern = pattern,
                            index = index
                        )
                    }
                }

                // Show more/less button
                if (patterns.size > maxVisiblePatterns) {
                    TextButton(
                        onClick = { showAllPatterns = !showAllPatterns },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (showAllPatterns) "Mostra meno" else "Mostra tutti (${patterns.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Learn more button
            TextButton(
                onClick = onLearnMore,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Scopri di più sui tuoi pattern →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PatternTimelineItem(
    pattern: CognitivePatternSummary,
    index: Int
) {
    // Staggered entrance
    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 100).toLong())
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
    }

    val patternColor = when (pattern.sentiment) {
        PatternSentiment.ADAPTIVE -> EmotionAwareColors.PatternColors.adaptive
        PatternSentiment.MALADAPTIVE -> EmotionAwareColors.PatternColors.maladaptive
        PatternSentiment.NEUTRAL -> EmotionAwareColors.PatternColors.neutral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterTransition.value
                translationX = (1f - enterTransition.value) * 20f
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline dot
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(patternColor)
        )

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Pattern name and trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pattern.patternName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Trend icon
                Icon(
                    imageVector = when (pattern.trend) {
                        TrendDirection.UP -> Icons.AutoMirrored.Filled.TrendingUp
                        TrendDirection.DOWN -> Icons.AutoMirrored.Filled.TrendingDown
                        TrendDirection.STABLE -> Icons.AutoMirrored.Filled.TrendingFlat
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when (pattern.sentiment) {
                        PatternSentiment.ADAPTIVE ->
                            if (pattern.trend == TrendDirection.UP) patternColor else MaterialTheme.colorScheme.onSurfaceVariant

                        PatternSentiment.MALADAPTIVE ->
                            if (pattern.trend == TrendDirection.DOWN) EmotionAwareColors.positiveLight else patternColor

                        PatternSentiment.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Description
            Text(
                text = pattern.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Occurrences badge
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = patternColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = "${pattern.occurrences}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = patternColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyPatternsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Shape-based icon instead of emoji (M3 Expressive)
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(
            text = "Nessun pattern rilevato questa settimana",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Continua a scrivere per scoprire i tuoi schemi di pensiero",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact pattern indicator for dashboard
 */
@Composable
fun CompactPatternIndicator(
    adaptiveCount: Int,
    maladaptiveCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PatternCountBadge(
            count = adaptiveCount,
            label = "Positivi",
            color = EmotionAwareColors.PatternColors.adaptive
        )
        PatternCountBadge(
            count = maladaptiveCount,
            label = "Da migliorare",
            color = EmotionAwareColors.PatternColors.maladaptive
        )
    }
}

@Composable
private fun PatternCountBadge(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
