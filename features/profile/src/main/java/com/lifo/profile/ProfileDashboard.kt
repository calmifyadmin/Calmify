package com.lifo.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.Trend
import com.lifo.util.model.getWeekLabelFull
import kotlin.math.abs
import kotlin.math.pow

/**
 * ProfileDashboard - Main screen for psychological profile visualization
 *
 * Week 7 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5 (Week 7)
 *
 * Features:
 * - 4-week rolling trend chart (stress & mood baselines)
 * - Resilience index card
 * - Mood trend indicators
 * - Data quality footer
 * - Empty state handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDashboard(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Il Tuo Profilo Psicologico") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> LoadingState()
                is ProfileUiState.Success -> SuccessState(
                    profiles = state.profiles,
                    chartData = state.chartData
                )
                is ProfileUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = { viewModel.refresh() }
                )
                is ProfileUiState.Empty -> EmptyState()
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Errore",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Riprova")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Non hai ancora abbastanza dati",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scrivi almeno 3 diari questa settimana per vedere il tuo profilo psicologico",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuccessState(
    profiles: List<PsychologicalProfile>,
    chartData: ChartData
) {
    val latestProfile = profiles.firstOrNull() ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Material 3 Expressive Weekly Chart Card - Fitbit style
        WeeklyExpressiveChart(chartData)

        // Resilience card
        ResilienceCard(latestProfile)

        // Trend indicators
        TrendIndicatorsRow(latestProfile)

        // Data quality footer
        DataQualityFooter(latestProfile)
    }
}

/**
 * Weekly Expressive Chart - Material 3 Expressive Design
 * Ispirato al design Fitbit con pillole verticali arrotondate
 * Adattato per dati psicologici settimanali (stress & mood)
 */
@Composable
private fun WeeklyExpressiveChart(chartData: ChartData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Navigation
            WeeklyChartHeader(chartData)

            // Main Stats
            WeeklyMainStats(chartData)

            // The Chart with Vertical Rounded Pills
            if (chartData.weekLabels.isNotEmpty() &&
                chartData.stressLine.isNotEmpty() &&
                chartData.moodLine.isNotEmpty()
            ) {
                VerticalPillsWeeklyChart(
                    chartData = chartData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(vertical = 12.dp)
                )
            } else {
                Text(
                    text = "Dati insufficienti per il grafico",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Secondary Stats Row
            if (chartData.stressLine.isNotEmpty() && chartData.moodLine.isNotEmpty()) {
                WeeklySecondaryStats(chartData)
            }
        }
    }
}

/**
 * Header con titolo e navigazione settimana
 */
@Composable
private fun WeeklyChartHeader(chartData: ChartData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Questa settimana",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { /* TODO: Previous week */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Settimana precedente",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { /* TODO: Calendar picker */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Seleziona data",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { /* TODO: Next week */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Prossima settimana",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Statistiche principali in evidenza
 */
@Composable
private fun WeeklyMainStats(chartData: ChartData) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Calcola media settimanale combinata (mood - stress per benessere generale)
        val weeklyAverage = if (chartData.moodLine.isNotEmpty() && chartData.stressLine.isNotEmpty()) {
            val moodAvg = chartData.moodLine.average()
            val stressAvg = chartData.stressLine.average()
            // Benessere = mood alto + stress basso (normalizzato 0-10)
            ((moodAvg + (10 - stressAvg)) / 2).toFloat()
        } else 0f

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format("%.1f", weeklyAverage),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "benessere/giorno (avg)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Sotto-statistica
        val totalDays = chartData.weekLabels.size
        val totalScore = weeklyAverage * totalDays
        Text(
            text = "Hai totalizzato un punteggio di ${String.format("%.0f", totalScore)} su $totalDays giorni",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Statistiche secondarie in riga
 */
@Composable
private fun WeeklySecondaryStats(chartData: ChartData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Peak indicators
        val stressPeaks = chartData.peaks.size

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "15k",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Obiettivo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Vertical Pills Weekly Chart - Il grafico principale con pillole verticali
 * Ispirato al design Fitbit nell'immagine
 */
@Composable
private fun VerticalPillsWeeklyChart(
    chartData: ChartData,
    modifier: Modifier = Modifier
) {
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    // Animazione per l'apparizione delle pillole
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(chartData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Colori Material 3 Expressive
    val primaryPillColor = Color(0xFF6750A4) // Purple primary
    val secondaryPillColor = Color(0xFF7BCFFF) // Light blue/cyan
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val pillWidth = 32.dp.toPx()
                        val spacing = (size.width - 40.dp.toPx()) / chartData.weekLabels.size
                        val index = ((offset.x - 20.dp.toPx()) / spacing).toInt()
                            .coerceIn(0, chartData.weekLabels.size - 1)
                        selectedDayIndex = if (selectedDayIndex == index) null else index
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val horizontalPadding = 20.dp.toPx()
            val bottomPadding = 40.dp.toPx()
            val topPadding = 20.dp.toPx()

            val chartHeight = canvasHeight - topPadding - bottomPadding
            val dataPoints = chartData.weekLabels.size

            if (dataPoints == 0) return@Canvas

            val spacing = (canvasWidth - horizontalPadding * 2) / dataPoints
            val pillWidth = 32.dp.toPx().coerceAtMost(spacing * 0.7f)

            // Valori max per normalizzazione (scala 0-10)
            val maxValue = 10f

            // Disegna linee griglia orizzontali (5 linee per 0, 2.5, 5, 7.5, 10)
            for (i in 0..4) {
                val y = topPadding + (chartHeight / 4) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(horizontalPadding, y),
                    end = Offset(canvasWidth - horizontalPadding, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
                )
            }

            // Disegna le pillole per ogni giorno
            chartData.weekLabels.forEachIndexed { index, _ ->
                val centerX = horizontalPadding + spacing * index + spacing / 2

                // Valori normalizzati 0-10
                val stressValue = chartData.stressLine.getOrNull(index) ?: 0f
                val moodValue = chartData.moodLine.getOrNull(index) ?: 0f

                // Altezze animate
                val animatedStress = stressValue * animationProgress.value
                val animatedMood = moodValue * animationProgress.value

                // Converti valori in altezze pixel
                val stressHeight = (animatedStress / maxValue) * chartHeight
                val moodHeight = (animatedMood / maxValue) * chartHeight

                // Posizione Y baseline (bottom)
                val baselineY = canvasHeight - bottomPadding

                // Pillola PRIMARY (Mood - viola)
                val moodPillTop = baselineY - moodHeight
                val moodPillLeft = centerX - pillWidth - 2.dp.toPx()

                drawRoundRect(
                    color = primaryPillColor,
                    topLeft = Offset(moodPillLeft, moodPillTop),
                    size = Size(pillWidth, moodHeight),
                    cornerRadius = CornerRadius(pillWidth / 2, pillWidth / 2),
                    alpha = if (selectedDayIndex == index) 1f else 0.85f
                )

                // Pillola SECONDARY (Stress - cyan, invertito per visualizzazione)
                // Stress alto = pillola più bassa (dato che stress alto è negativo)
                val adjustedStressValue = (maxValue - stressValue) // Inverte la scala
                val adjustedStressHeight = (adjustedStressValue / maxValue) * chartHeight
                val stressPillTop = baselineY - adjustedStressHeight
                val stressPillLeft = centerX + 2.dp.toPx()

                drawRoundRect(
                    color = secondaryPillColor,
                    topLeft = Offset(stressPillLeft, stressPillTop),
                    size = Size(pillWidth, adjustedStressHeight),
                    cornerRadius = CornerRadius(pillWidth / 2, pillWidth / 2),
                    alpha = if (selectedDayIndex == index) 1f else 0.85f
                )

                // Checkmark indicator per giorni con achievement (esempio: mood > 7)
                if (moodValue > 7f) {
                    val checkmarkSize = 16.dp.toPx()
                    val checkmarkCenterX = centerX
                    val checkmarkCenterY = moodPillTop - checkmarkSize

                    // Cerchio verde con checkmark
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = checkmarkSize / 2,
                        center = Offset(checkmarkCenterX, checkmarkCenterY)
                    )

                    // Checkmark symbol (semplificato)
                    val checkStroke = 2.dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = Offset(checkmarkCenterX - 4.dp.toPx(), checkmarkCenterY),
                        end = Offset(checkmarkCenterX - 1.dp.toPx(), checkmarkCenterY + 3.dp.toPx()),
                        strokeWidth = checkStroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(checkmarkCenterX - 1.dp.toPx(), checkmarkCenterY + 3.dp.toPx()),
                        end = Offset(checkmarkCenterX + 5.dp.toPx(), checkmarkCenterY - 3.dp.toPx()),
                        strokeWidth = checkStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Labels dei giorni in basso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            chartData.weekLabels.forEachIndexed { index, label ->
                // Estrai solo la lettera del giorno (M, T, W, T, F, S, S)
                val dayLetter = when {
                    label.contains("Mon", ignoreCase = true) || label.startsWith("L") -> "M"
                    label.contains("Tue", ignoreCase = true) || label.startsWith("M") -> "T"
                    label.contains("Wed", ignoreCase = true) || label.contains("Mer") -> "W"
                    label.contains("Thu", ignoreCase = true) || label.startsWith("G") -> "T"
                    label.contains("Fri", ignoreCase = true) || label.startsWith("V") -> "F"
                    label.contains("Sat", ignoreCase = true) || label.startsWith("S") -> "S"
                    label.contains("Sun", ignoreCase = true) || label.startsWith("D") -> "S"
                    else -> label.take(1)
                }

                Text(
                    text = dayLetter,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedDayIndex == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedDayIndex == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Tooltip per giorno selezionato
        selectedDayIndex?.let { index ->
            if (index in chartData.weekLabels.indices) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = chartData.weekLabels[index],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(primaryPillColor)
                                )
                                Text(
                                    text = "Umore: ${String.format("%.1f", chartData.moodLine[index])}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(secondaryPillColor)
                                )
                                Text(
                                    text = "Stress: ${String.format("%.1f", chartData.stressLine[index])}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Resilience Card
 * Shows resilience index and recovery speed
 */
@Composable
private fun ResilienceCard(profile: PsychologicalProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resilienza",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(onClick = { /* TODO: Show info tooltip */ }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Resilience progress
            LinearProgressIndicator(
                progress = { profile.resilienceIndex },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(profile.resilienceIndex * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (profile.recoverySpeed > 0) {
                    Text(
                        text = "Recupero in ${profile.recoverySpeed.toInt()} giorni",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Trend Indicators Row
 * Shows mood trend with icon and color coding
 */
@Composable
private fun TrendIndicatorsRow(profile: PsychologicalProfile) {
    val trend = profile.getMoodTrendEnum()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (trend) {
                Trend.IMPROVING -> MaterialTheme.colorScheme.primaryContainer
                Trend.DECLINING -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Andamento Umore",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = trend.emoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Volatility indicator (optional)
            if (profile.moodVolatility > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Variabilità",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f", profile.moodVolatility),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Data Quality Footer
 * Shows data sources and confidence level
 */
@Composable
private fun DataQualityFooter(profile: PsychologicalProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = profile.getWeekLabelFull(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Basato su ${profile.diaryCount} diari",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Affidabilità: ${(profile.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (profile.confidence >= 0.7f) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        profile.confidence >= 0.7f -> MaterialTheme.colorScheme.primary
                        profile.confidence >= 0.4f -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Line Chart
 * Custom Canvas-based chart with smooth curves, gradients, and animations
 */
@Composable
private fun ExpressiveLineChart(
    chartData: ChartData,
    modifier: Modifier = Modifier
) {
    // Animation state for chart appearance
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(chartData) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val stressColor = MaterialTheme.colorScheme.error
    val moodColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val chartWidth = size.width
                        val padding = 40f
                        val dataPoints = chartData.weekLabels.size

                        if (dataPoints > 1) {
                            val pointSpacing = (chartWidth - padding * 2) / (dataPoints - 1)
                            val index = ((offset.x - padding) / pointSpacing)
                                .toInt()
                                .coerceIn(0, dataPoints - 1)
                            selectedIndex = if (selectedIndex == index) null else index
                        }
                    }
                }
        ) {
            val chartWidth = size.width
            val chartHeight = size.height
            val padding = 40f
            val topPadding = 20f
            val bottomPadding = 30f

            val dataPoints = chartData.stressLine.size
            if (dataPoints < 1) return@Canvas

            val drawWidth = chartWidth - padding * 2
            val drawHeight = chartHeight - topPadding - bottomPadding
            val pointSpacing = if (dataPoints > 1) {
                drawWidth / (dataPoints - 1)
            } else {
                0f // Single point will be centered
            }

            // Normalize data to 0-10 scale
            val maxValue = 10f
            val minValue = 0f

            // Draw horizontal grid lines
            for (i in 0..5) {
                val y = topPadding + (drawHeight / 5) * i
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(chartWidth - padding, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }

            // Helper function to convert data point to Y coordinate
            fun valueToY(value: Float): Float {
                val normalizedValue = (value - minValue) / (maxValue - minValue)
                return topPadding + drawHeight * (1 - normalizedValue)
            }

            // Helper function to get X coordinate for a point
            fun getPointX(index: Int): Float {
                return if (dataPoints == 1) {
                    chartWidth / 2 // Center single point
                } else {
                    padding + pointSpacing * index
                }
            }

            // Helper function to create smooth Bezier curve path
            fun createSmoothPath(dataLine: List<Float>): Path {
                val path = Path()
                val points = dataLine.mapIndexed { index, value ->
                    Offset(
                        x = getPointX(index),
                        y = valueToY(value)
                    )
                }

                if (points.isEmpty()) return path
                if (points.size == 1) {
                    // For single point, just move to it (no line to draw)
                    path.moveTo(points[0].x, points[0].y)
                    return path
                }

                path.moveTo(points[0].x, points[0].y)

                for (i in 0 until points.size - 1) {
                    val current = points[i]
                    val next = points[i + 1]

                    // Cubic Bezier control points for smooth curve
                    val controlPoint1 = Offset(
                        x = current.x + (next.x - current.x) / 3,
                        y = current.y
                    )
                    val controlPoint2 = Offset(
                        x = current.x + 2 * (next.x - current.x) / 3,
                        y = next.y
                    )

                    path.cubicTo(
                        controlPoint1.x, controlPoint1.y,
                        controlPoint2.x, controlPoint2.y,
                        next.x, next.y
                    )
                }

                return path
            }

            // Animate data
            val animatedStress = chartData.stressLine.map { it * animationProgress.value }
            val animatedMood = chartData.moodLine.map { it * animationProgress.value }

            // Only draw lines and gradients if we have multiple points
            if (dataPoints > 1) {
                // Draw stress line with gradient fill
                val stressPath = createSmoothPath(animatedStress)
                val stressGradientPath = Path().apply {
                    addPath(stressPath)
                    lineTo(chartWidth - padding, chartHeight - bottomPadding)
                    lineTo(padding, chartHeight - bottomPadding)
                    close()
                }

                drawPath(
                    path = stressGradientPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            stressColor.copy(alpha = 0.3f),
                            stressColor.copy(alpha = 0.05f)
                        ),
                        startY = topPadding,
                        endY = chartHeight - bottomPadding
                    )
                )

                drawPath(
                    path = stressPath,
                    color = stressColor,
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Draw mood line with gradient fill
                val moodPath = createSmoothPath(animatedMood)
                val moodGradientPath = Path().apply {
                    addPath(moodPath)
                    lineTo(chartWidth - padding, chartHeight - bottomPadding)
                    lineTo(padding, chartHeight - bottomPadding)
                    close()
                }

                drawPath(
                    path = moodGradientPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            moodColor.copy(alpha = 0.3f),
                            moodColor.copy(alpha = 0.05f)
                        ),
                        startY = topPadding,
                        endY = chartHeight - bottomPadding
                    )
                )

                drawPath(
                    path = moodPath,
                    color = moodColor,
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw data points
            animatedStress.forEachIndexed { index, value ->
                val x = getPointX(index)
                val y = valueToY(value)

                // Outer circle (glow effect)
                drawCircle(
                    color = stressColor.copy(alpha = 0.3f),
                    radius = if (selectedIndex == index) 16f else if (dataPoints == 1) 14f else 8f,
                    center = Offset(x, y)
                )
                // Inner circle
                drawCircle(
                    color = stressColor,
                    radius = if (selectedIndex == index) 10f else if (dataPoints == 1) 8f else 5f,
                    center = Offset(x, y)
                )
            }

            animatedMood.forEachIndexed { index, value ->
                val x = getPointX(index)
                val y = valueToY(value)

                // Outer circle (glow effect)
                drawCircle(
                    color = moodColor.copy(alpha = 0.3f),
                    radius = if (selectedIndex == index) 16f else if (dataPoints == 1) 14f else 8f,
                    center = Offset(x, y)
                )
                // Inner circle
                drawCircle(
                    color = moodColor,
                    radius = if (selectedIndex == index) 10f else if (dataPoints == 1) 8f else 5f,
                    center = Offset(x, y)
                )
            }

            // Draw peak markers
            chartData.peaks.forEach { peak ->
                if (peak.weekIndex in 0 until dataPoints) {
                    val x = getPointX(peak.weekIndex)
                    val y = topPadding - 5f

                    // Warning triangle marker
                    drawCircle(
                        color = stressColor.copy(alpha = 0.6f),
                        radius = 6f,
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Selected point tooltip
        selectedIndex?.let { index ->
            if (index in chartData.weekLabels.indices) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = chartData.weekLabels[index],
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(stressColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", chartData.stressLine[index]),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = stressColor
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(moodColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", chartData.moodLine[index]),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = moodColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Week labels at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 40.dp),
            horizontalArrangement = if (chartData.weekLabels.size == 1) {
                Arrangement.Center
            } else {
                Arrangement.SpaceBetween
            }
        ) {
            chartData.weekLabels.forEachIndexed { index, label ->
                Text(
                    text = if (chartData.weekLabels.size > 3 && index % 2 != 0 && selectedIndex != index) {
                        "" // Hide alternate labels if too many
                    } else {
                        label.take(3) // Show first 3 chars (e.g., "W1-")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedIndex == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Encouraging message for single data point
        if (chartData.weekLabels.size == 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ottimo inizio!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Continua a scrivere diari per vedere l'andamento",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Stat Card for displaying metrics
 */
@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
