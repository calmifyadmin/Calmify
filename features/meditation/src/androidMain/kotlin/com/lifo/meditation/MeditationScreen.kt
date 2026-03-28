package com.lifo.meditation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeditationScreen(
    state: MeditationContract.State,
    onIntent: (MeditationContract.Intent) -> Unit,
    onBackPressed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (state.phase) {
                                MeditationContract.SessionPhase.SETUP -> "Meditazione"
                                MeditationContract.SessionPhase.ACTIVE -> if (state.selectedType == MeditationType.BREATHING) "Respirazione" else "Meditazione"
                                MeditationContract.SessionPhase.COMPLETED -> "Sessione completata"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when (state.phase) {
                                MeditationContract.SessionPhase.SETUP -> "Configura la tua pratica"
                                MeditationContract.SessionPhase.ACTIVE -> "In corso..."
                                MeditationContract.SessionPhase.COMPLETED -> "Ben fatto!"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.phase == MeditationContract.SessionPhase.ACTIVE) {
                            onIntent(MeditationContract.Intent.StopSession)
                        } else {
                            onBackPressed()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.phase,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 3 })
            },
            label = "phase",
        ) { currentPhase ->
            when (currentPhase) {
                MeditationContract.SessionPhase.SETUP -> SetupContent(state, onIntent)
                MeditationContract.SessionPhase.ACTIVE -> ActiveContent(state, onIntent)
                MeditationContract.SessionPhase.COMPLETED -> CompletedContent(state, onIntent, onBackPressed)
            }
        }
    }
}

// ==================== SETUP PHASE ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupContent(state: MeditationContract.State, onIntent: (MeditationContract.Intent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Stats summary
        if (state.sessionCount > 0) {
            AnimatedSection(0) {
                MeditationSummaryCard(
                    totalMinutes = state.totalMinutes,
                    sessionCount = state.sessionCount
                )
            }
        }

        // Type selection — tappable icons grid
        AnimatedSection(1) {
            TypeSelectorCard(
                selectedType = state.selectedType,
                onTypeSelected = { onIntent(MeditationContract.Intent.SelectType(it)) }
            )
        }

        // Breathing pattern (only for breathing type)
        AnimatedVisibility(visible = state.selectedType == MeditationType.BREATHING) {
            AnimatedSection(2) {
                PatternSelectorCard(
                    selectedPattern = state.selectedPattern,
                    onPatternSelected = { onIntent(MeditationContract.Intent.SelectBreathingPattern(it)) }
                )
            }
        }

        // Duration — chips
        AnimatedSection(3) {
            DurationSelectorCard(
                selectedSeconds = state.selectedDurationSeconds,
                onSelect = { onIntent(MeditationContract.Intent.SelectDuration(it)) }
            )
        }

        // Start button
        Button(
            onClick = { onIntent(MeditationContract.Intent.StartSession) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Inizia la pratica", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ==================== MEDITATION SUMMARY ====================

@Composable
private fun MeditationSummaryCard(totalMinutes: Int, sessionCount: Int) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.SelfImprovement,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$totalMinutes",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = colorScheme.onPrimaryContainer
                )
                Text(
                    "minuti totali",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Surface(
                modifier = Modifier
                    .width(1.dp)
                    .height(60.dp),
                color = colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            ) {}

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$sessionCount",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = colorScheme.onPrimaryContainer
                )
                Text(
                    "sessioni",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ==================== TYPE SELECTOR ====================

@Composable
private fun TypeSelectorCard(
    selectedType: MeditationType,
    onTypeSelected: (MeditationType) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Che tipo di pratica vuoi fare?",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MeditationType.entries.forEach { type ->
                    val isSelected = type == selectedType
                    val icon = type.icon()
                    val label = type.displayName

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTypeSelected(type) }
                            .padding(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected)
                                colorScheme.primaryContainer
                            else
                                colorScheme.surfaceContainerHigh,
                            border = if (isSelected)
                                BorderStroke(2.dp, colorScheme.primary)
                            else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(28.dp),
                                    tint = if (isSelected)
                                        colorScheme.primary
                                    else
                                        colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected)
                                colorScheme.primary
                            else
                                colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            type.subtitle(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ==================== PATTERN SELECTOR ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternSelectorCard(
    selectedPattern: BreathingPattern,
    onPatternSelected: (BreathingPattern) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Air,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = colorScheme.primary
                )
                Text(
                    "Quale pattern respiratorio?",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurface
                )
            }

            BreathingPattern.entries.forEach { pattern ->
                val isSelected = pattern == selectedPattern

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPatternSelected(pattern) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected)
                        colorScheme.primaryContainer
                    else
                        colorScheme.surfaceContainerHigh,
                    border = if (isSelected)
                        BorderStroke(1.5.dp, colorScheme.primary)
                    else
                        BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pattern.displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                patternTimingLabel(pattern),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected)
                                    colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== DURATION SELECTOR ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSelectorCard(selectedSeconds: Int, onSelect: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val durations = listOf(180 to "3 min", 300 to "5 min", 600 to "10 min", 900 to "15 min", 1200 to "20 min")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Per quanto tempo?",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durations.forEach { (seconds, label) ->
                    val isSelected = selectedSeconds == seconds

                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(seconds) },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(36.dp)
                    )
                }
            }

            // Duration hint
            val minutes = selectedSeconds / 60
            Text(
                text = when {
                    minutes <= 3 -> "Perfetto per iniziare"
                    minutes <= 10 -> "Un buon equilibrio"
                    else -> "Per praticanti esperti"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ==================== ACTIVE PHASE ====================

@Composable
private fun ActiveContent(state: MeditationContract.State, onIntent: (MeditationContract.Intent) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Timer display — expressive
        val remaining = state.selectedDurationSeconds - state.elapsedSeconds
        val minutes = remaining / 60
        val seconds = remaining % 60

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("%02d", minutes),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp
                    ),
                    color = colorScheme.onSurface
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = String.format("%02d", seconds),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp
                    ),
                    color = colorScheme.onSurface
                )
            }
        }

        // Breathing / meditation circle (center)
        if (state.selectedType == MeditationType.BREATHING) {
            BreathingCircle(
                phase = state.breathingPhase,
                progress = state.breathingPhaseProgress,
                isPaused = state.isPaused,
                modifier = Modifier.size(280.dp),
            )
        } else {
            SimpleMeditationCircle(
                isPaused = state.isPaused,
                modifier = Modifier.size(280.dp),
            )
        }

        // Controls — pill-shaped
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            // Stop button
            Surface(
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onIntent(MeditationContract.Intent.StopSession) },
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Termina",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pause/Resume button
            Surface(
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        if (state.isPaused) onIntent(MeditationContract.Intent.ResumeSession)
                        else onIntent(MeditationContract.Intent.PauseSession)
                    },
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (state.isPaused) "Riprendi" else "Pausa",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ==================== BREATHING CIRCLE ====================

@Composable
private fun BreathingCircle(
    phase: MeditationContract.BreathingPhase,
    progress: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    // Direct scale from progress — no animation wrapper needed since
    // progress already updates smoothly from the ViewModel ticker.
    // Only the pause/resume transition is animated.
    val breathingScale = when {
        phase == MeditationContract.BreathingPhase.INHALE -> 0.5f + 0.5f * progress
        phase == MeditationContract.BreathingPhase.HOLD_IN -> 1.0f
        phase == MeditationContract.BreathingPhase.EXHALE -> 1.0f - 0.5f * progress
        phase == MeditationContract.BreathingPhase.HOLD_OUT -> 0.5f
        else -> 0.6f
    }

    val pauseWeight by animateFloatAsState(
        targetValue = if (isPaused) 1f else 0f,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "pauseWeight",
    )

    val scale = breathingScale * (1f - pauseWeight) + 0.6f * pauseWeight

    val phaseColor = when (phase) {
        MeditationContract.BreathingPhase.INHALE -> Color(0xFF64B5F6)
        MeditationContract.BreathingPhase.HOLD_IN -> Color(0xFF9575CD)
        MeditationContract.BreathingPhase.EXHALE -> Color(0xFF4DB6AC)
        MeditationContract.BreathingPhase.HOLD_OUT -> Color(0xFF9575CD)
    }

    val outerAlpha by animateFloatAsState(
        targetValue = if (phase == MeditationContract.BreathingPhase.INHALE || phase == MeditationContract.BreathingPhase.EXHALE) 0.3f else 0.15f,
        animationSpec = tween(500),
        label = "alpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val particleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particleRotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2

            // Wave rings
            for (i in 0..2) {
                val waveScale = scale - i * 0.05f
                val waveAlpha = (outerAlpha - i * 0.05f).coerceAtLeast(0.05f)
                drawCircle(
                    color = phaseColor.copy(alpha = waveAlpha),
                    radius = maxRadius * waveScale,
                    center = center,
                )
            }

            // Inner solid circle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(phaseColor.copy(alpha = 0.8f), phaseColor.copy(alpha = 0.4f)),
                    center = center,
                    radius = maxRadius * scale * 0.6f,
                ),
                radius = maxRadius * scale * 0.6f,
                center = center,
            )

            // Orbiting particles
            drawParticles(center, maxRadius * scale, particleRotation, phaseColor)
        }

        Spacer(Modifier.height(16.dp))

        // Phase label in pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = phaseColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = phase.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = phaseColor,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

private fun DrawScope.drawParticles(
    center: Offset,
    radius: Float,
    rotation: Float,
    color: Color,
) {
    val particleCount = 12
    for (i in 0 until particleCount) {
        val angle = (rotation + i * (360f / particleCount)) * PI.toFloat() / 180f
        val px = center.x + radius * cos(angle)
        val py = center.y + radius * sin(angle)
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = 4f,
            center = Offset(px, py),
        )
    }
}

@Composable
private fun SimpleMeditationCircle(
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meditation")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val effectivePulse = if (isPaused) 0.7f else pulse
    val color = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.minDimension / 2

            drawCircle(
                color = color.copy(alpha = 0.1f),
                radius = maxRadius * (effectivePulse + 0.05f),
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0.2f)),
                    center = center,
                    radius = maxRadius * effectivePulse,
                ),
                radius = maxRadius * effectivePulse,
                center = center,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Label in pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.12f)
        ) {
            Text(
                text = "Respira naturalmente...",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = color,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

// ==================== COMPLETED PHASE ====================

@Composable
private fun CompletedContent(
    state: MeditationContract.State,
    onIntent: (MeditationContract.Intent) -> Unit,
    onBackPressed: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val completedMinutes = state.elapsedSeconds / 60
    val completedSeconds = state.elapsedSeconds % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Icon in expressive container
        AnimatedSection(0) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.SelfImprovement,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        AnimatedSection(1) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Ottimo lavoro!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = colorScheme.onSurface
                )
                Text(
                    "${completedMinutes}m ${completedSeconds}s di pratica",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // Session summary card
        AnimatedSection(2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.selectedType.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Tipo",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp),
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    ) {}
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${completedMinutes}:${String.format("%02d", completedSeconds)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Durata",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Note card
        AnimatedSection(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Come ti senti dopo la pratica?",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = state.postNote,
                        onValueChange = { onIntent(MeditationContract.Intent.SetPostNote(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Scrivi le tue sensazioni... (opzionale)") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Save button
        Button(
            onClick = { onIntent(MeditationContract.Intent.SaveSession) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Salva sessione", style = MaterialTheme.typography.labelLarge)
            }
        }

        TextButton(onClick = {
            onIntent(MeditationContract.Intent.DiscardSession)
            onBackPressed()
        }) {
            Text("Scarta e torna indietro")
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ==================== ANIMATED SECTION ====================

@Composable
private fun AnimatedSection(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 80L); visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 }
    ) {
        content()
    }
}

// ==================== UTILS ====================

@Composable
private fun MeditationType.icon(): ImageVector = when (this) {
    MeditationType.TIMER -> Icons.Outlined.SelfImprovement
    MeditationType.BREATHING -> Icons.Outlined.Air
    MeditationType.BODY_SCAN -> Icons.Outlined.Accessibility
}

private fun MeditationType.subtitle(): String = when (this) {
    MeditationType.TIMER -> "Silenzio"
    MeditationType.BREATHING -> "Guidato"
    MeditationType.BODY_SCAN -> "Corporeo"
}

private fun patternTimingLabel(pattern: BreathingPattern): String = buildString {
    append("${pattern.inhaleSeconds}s inspira")
    if (pattern.holdInSeconds > 0) append(" - ${pattern.holdInSeconds}s trattieni")
    append(" - ${pattern.exhaleSeconds}s espira")
    if (pattern.holdOutSeconds > 0) append(" - ${pattern.holdOutSeconds}s trattieni")
}
