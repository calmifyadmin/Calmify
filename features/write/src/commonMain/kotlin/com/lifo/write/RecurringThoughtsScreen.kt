package com.lifo.write

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.RecurringThought
import com.lifo.util.model.ThoughtType
import kotlin.math.*
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringThoughtsScreen(
    state: RecurringThoughtsContract.State,
    onIntent: (RecurringThoughtsContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
    onNavigateToReframe: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.recurring_thoughts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.thoughts.isEmpty()) {
            EmptyThoughtsView(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Summary card
            val limiting = state.thoughts.count { it.type == ThoughtType.LIMITING }
            val empowering = state.thoughts.count { it.type == ThoughtType.EMPOWERING }
            SummaryCard(total = state.thoughts.size, limiting = limiting, empowering = empowering)

            // Bubble cloud visualization
            Text(
                "Mappa dei Pensieri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            BubbleCloud(
                thoughts = state.thoughts,
                onThoughtTapped = { onIntent(RecurringThoughtsContract.Intent.SelectThought(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )

            // Selected thought detail
            AnimatedVisibility(
                visible = state.selectedThought != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                state.selectedThought?.let { thought ->
                    ThoughtDetailCard(
                        thought = thought,
                        onReframe = { onIntent(RecurringThoughtsContract.Intent.ReframeThought(thought.id)) },
                        onResolve = { onIntent(RecurringThoughtsContract.Intent.ResolveThought(thought.id)) },
                        onDismiss = { onIntent(RecurringThoughtsContract.Intent.SelectThought(null)) },
                    )
                }
            }

            // 90-day tracker for reframed thoughts
            val reframed = state.thoughts.filter { it.reframedAtMillis != null }
            if (reframed.isNotEmpty()) {
                Text(
                    "Tracker 90 Giorni",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                reframed.forEach { thought ->
                    ReframeTrackerCard(thought = thought)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Handle effects
    LaunchedEffect(Unit) {
        // Effects handled via onNavigateToReframe callback
    }
}

@Composable
private fun EmptyThoughtsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("\uD83E\uDDE0", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Nessun pensiero ricorrente rilevato",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Continua a scrivere nel diario. I temi ricorrenti verranno rilevati automaticamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SummaryCard(total: Int, limiting: Int, empowering: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn("Totali", total.toString(), MaterialTheme.colorScheme.onSurface)
            StatColumn("\uD83D\uDD34 Limitanti", limiting.toString(), MaterialTheme.colorScheme.error)
            StatColumn("\uD83D\uDFE2 Potenzianti", empowering.toString(), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// === BUBBLE CLOUD ===

private data class BubbleData(
    val thought: RecurringThought,
    val center: Offset,
    val radius: Float,
)

@Composable
private fun BubbleCloud(
    thoughts: List<RecurringThought>,
    onThoughtTapped: (RecurringThought) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val limitingColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val empoweringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val neutralColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textColor = MaterialTheme.colorScheme.onSurface

    // Animate floating offset
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )

    var bubbles by remember(thoughts) { mutableStateOf(emptyList<BubbleData>()) }

    Canvas(
        modifier = modifier
            .pointerInput(thoughts) {
                detectTapGestures { tapOffset ->
                    val tapped = bubbles.find { bubble ->
                        val dx = tapOffset.x - bubble.center.x
                        val dy = tapOffset.y - bubble.center.y
                        sqrt(dx * dx + dy * dy) <= bubble.radius
                    }
                    tapped?.let { onThoughtTapped(it.thought) }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val maxOccurrences = thoughts.maxOfOrNull { it.occurrences } ?: 1
        val minRadius = 24.dp.toPx()
        val maxRadius = 56.dp.toPx()

        // Simple circle packing — place bubbles avoiding overlap
        val placed = mutableListOf<BubbleData>()
        val sorted = thoughts.sortedByDescending { it.occurrences }

        for (thought in sorted) {
            val ratio = thought.occurrences.toFloat() / maxOccurrences
            val radius = minRadius + (maxRadius - minRadius) * ratio

            // Find a position that doesn't overlap
            var bestCenter = Offset(w / 2f, h / 2f)
            var found = false

            // Spiral outward from center to find a free spot
            for (attempt in 0..200) {
                val angle = attempt * 0.5f
                val dist = attempt * 3f
                val cx = (w / 2f + cos(angle) * dist).coerceIn(radius, w - radius)
                val cy = (h / 2f + sin(angle) * dist).coerceIn(radius, h - radius)
                val candidate = Offset(cx, cy)

                val overlaps = placed.any { existing ->
                    val dx = candidate.x - existing.center.x
                    val dy = candidate.y - existing.center.y
                    sqrt(dx * dx + dy * dy) < (radius + existing.radius + 4.dp.toPx())
                }

                if (!overlaps) {
                    bestCenter = candidate
                    found = true
                    break
                }
            }

            if (!found) {
                // Fallback: place it anyway with a random offset
                bestCenter = Offset(
                    (radius + (w - 2 * radius) * (placed.size.toFloat() / sorted.size)).coerceIn(radius, w - radius),
                    (radius + (h - 2 * radius) * ((placed.size % 3).toFloat() / 3f)).coerceIn(radius, h - radius),
                )
            }

            placed.add(BubbleData(thought, bestCenter, radius))
        }

        bubbles = placed

        // Draw bubbles
        for (bubble in placed) {
            val floatDy = sin(floatOffset * PI.toFloat() * 2f + bubble.center.x * 0.01f) * 3.dp.toPx()
            val adjustedCenter = Offset(bubble.center.x, bubble.center.y + floatDy)

            val color = when (bubble.thought.type) {
                ThoughtType.LIMITING -> limitingColor
                ThoughtType.EMPOWERING -> empoweringColor
                ThoughtType.NEUTRAL -> neutralColor
            }

            // Draw circle
            drawCircle(
                color = color,
                radius = bubble.radius,
                center = adjustedCenter,
            )

            // Draw border
            drawCircle(
                color = color.copy(alpha = 0.9f),
                radius = bubble.radius,
                center = adjustedCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )

            // Draw label
            val label = if (bubble.thought.theme.length > 12) {
                bubble.thought.theme.take(11) + "..."
            } else {
                bubble.thought.theme
            }
            val textResult = textMeasurer.measure(
                text = AnnotatedString(label),
                style = TextStyle(
                    fontSize = (10 + (bubble.radius / maxRadius * 4)).sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
            )
            drawText(
                textLayoutResult = textResult,
                topLeft = Offset(
                    adjustedCenter.x - textResult.size.width / 2f,
                    adjustedCenter.y - textResult.size.height / 2f,
                ),
            )
        }
    }
}

@Composable
private fun ThoughtDetailCard(
    thought: RecurringThought,
    onReframe: () -> Unit,
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    thought.theme,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", modifier = Modifier.size(18.dp))
                }
            }

            Text(
                "Rilevato ${thought.occurrences} volte",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (thought.reframedAtMillis != null) {
                Text(
                    "Reframing applicato — occorrenze post: ${thought.occurrencesPostReframe}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (thought.type == ThoughtType.LIMITING && thought.reframedAtMillis == null) {
                    Button(
                        onClick = onReframe,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.recurring_thoughts_reframing))
                    }
                }
                OutlinedButton(
                    onClick = onResolve,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.recurring_thoughts_resolved))
                }
            }
        }
    }
}

@Composable
private fun ReframeTrackerCard(thought: RecurringThought) {
    val reframedAt = thought.reframedAtMillis ?: return
    val now = System.currentTimeMillis()
    val daysSinceReframe = ((now - reframedAt) / (1000L * 60 * 60 * 24)).toInt()
    val progress = (daysSinceReframe / 90f).coerceIn(0f, 1f)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    thought.theme,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Giorno $daysSinceReframe/90",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                "Ricomparse dopo reframing: ${thought.occurrencesPostReframe}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
