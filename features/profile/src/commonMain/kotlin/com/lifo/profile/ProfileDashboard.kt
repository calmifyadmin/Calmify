package com.lifo.profile

import com.lifo.util.formatDecimal
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.Trend
import com.lifo.util.model.getWeekLabelFull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDashboard(
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Il Mio Percorso") }
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

// ══════════════════════════════════════════════════════════════════════
// States
// ══════════════════════════════════════════════════════════════════════

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
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Qualcosa e' andato storto",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Il tuo percorso inizia qui",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Scrivi almeno 3 diari questa settimana per sbloccare il tuo profilo psicologico",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
// Success State
// ══════════════════════════════════════════════════════════════════════

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Score Card
        HeroScoreCard(latestProfile)

        // 2. Weekly Reflection
        WeeklyReflectionCard(latestProfile, chartData)

        // 3. Journey Chart
        if (chartData.moodLine.size >= 2) {
            JourneyLineCard(chartData)
        }

        // 4. Resilience Gauge
        ResilienceGaugeCard(latestProfile)

        // 5. Stats Grid
        StatsGrid(latestProfile)

        // 6. Data quality
        DataQualityFooter(latestProfile)

        Spacer(Modifier.height(80.dp))
    }
}

// ══════════════════════════════════════════════════════════════════════
// Hero Score Card
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun HeroScoreCard(profile: PsychologicalProfile) {
    val colorScheme = MaterialTheme.colorScheme
    val moodColor = getMoodColor(profile.moodBaseline)
    val trend = profile.getMoodTrendEnum()

    val animatedMood = remember { Animatable(0f) }
    LaunchedEffect(profile.moodBaseline) {
        animatedMood.animateTo(
            profile.moodBaseline,
            tween(1200, easing = FastOutSlowInEasing)
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            moodColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Umore medio",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant
            )

            // Big animated score
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formatDecimal(1, animatedMood.value),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 64.sp,
                        letterSpacing = (-2).sp
                    ),
                    color = moodColor
                )
                Text(
                    text = "/10",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Mood label + trend
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val moodLabel = when {
                    profile.moodBaseline >= 8f -> "Eccellente"
                    profile.moodBaseline >= 6.5f -> "Buono"
                    profile.moodBaseline >= 5f -> "Nella media"
                    profile.moodBaseline >= 3f -> "Sotto la media"
                    else -> "Difficile"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = moodColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = moodLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = moodColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                val trendIcon = when (trend) {
                    Trend.IMPROVING -> Icons.AutoMirrored.Filled.TrendingUp
                    Trend.DECLINING -> Icons.AutoMirrored.Filled.TrendingDown
                    else -> Icons.AutoMirrored.Filled.TrendingFlat
                }
                val trendColor = when (trend) {
                    Trend.IMPROVING -> Color(0xFF4CAF50)
                    Trend.DECLINING -> Color(0xFFEF5350)
                    else -> colorScheme.onSurfaceVariant
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = trendColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = trendIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = trendColor
                        )
                        Text(
                            text = trend.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = trendColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Mood gradient bar
            MoodGradientBar(
                score = profile.moodBaseline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MoodGradientBar(score: Float, modifier: Modifier = Modifier) {
    val animFraction = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animFraction.animateTo(
            (score / 10f).coerceIn(0f, 1f),
            tween(1400, easing = FastOutSlowInEasing)
        )
    }

    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val gradientColors = listOf(
        Color(0xFFEF5350), Color(0xFFFF7043), Color(0xFFFFCA28),
        Color(0xFF66BB6A), Color(0xFF4CAF50)
    )

    Box(modifier = modifier.height(16.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(
                color = colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                cornerRadius = r
            )
            val fw = size.width * animFraction.value
            if (fw > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(gradientColors),
                    cornerRadius = r,
                    size = Size(fw, size.height)
                )
            }

            // Indicator
            val indicatorR = with(density) { 5.dp.toPx() }
            val cx = (size.width - indicatorR * 2) * animFraction.value + indicatorR
            drawCircle(Color.White, indicatorR + with(density) { 1.dp.toPx() }, Offset(cx, size.height / 2f))
            drawCircle(
                interpolateGradient(gradientColors, animFraction.value),
                indicatorR,
                Offset(cx, size.height / 2f)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Weekly Reflection Card
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun WeeklyReflectionCard(profile: PsychologicalProfile, chartData: ChartData) {
    val reflection = remember(profile) { buildWeeklyReflection(profile, chartData) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = "La tua settimana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = reflection,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
        }
    }
}

private fun buildWeeklyReflection(profile: PsychologicalProfile, chartData: ChartData): String {
    val mood = profile.moodBaseline
    val stress = profile.stressBaseline
    val trend = profile.getMoodTrendEnum()
    val resilience = profile.resilienceIndex
    val diaryCount = profile.diaryCount

    val sb = StringBuilder()

    when {
        mood >= 7f -> sb.append("Questa settimana hai mostrato un umore positivo e stabile. ")
        mood >= 5f -> sb.append("Una settimana equilibrata, con alti e bassi. ")
        else -> sb.append("Non e' stata una settimana facile — e va bene cosi'. ")
    }

    when {
        stress >= 7f -> sb.append("Lo stress e' stato alto. Prenditi cura di te. ")
        stress >= 4f -> sb.append("Lo stress c'e' stato ma l'hai gestito. ")
        else -> sb.append("I livelli di stress sono rimasti bassi — ottimo segnale. ")
    }

    when (trend) {
        Trend.IMPROVING -> sb.append("Il trend e' in miglioramento rispetto alle settimane precedenti.")
        Trend.DECLINING -> sb.append("Il trend mostra un calo — niente panico, e' informazione utile.")
        Trend.STABLE -> sb.append("Il tuo percorso e' costante, e la costanza ha valore.")
        Trend.INSUFFICIENT_DATA -> sb.append("Scrivi di piu' per avere un quadro completo.")
    }

    if (resilience >= 0.7f) {
        sb.append(" La tua resilienza e' solida — ti riprendi bene dopo le difficolta'.")
    }

    if (diaryCount >= 5) {
        sb.append(" $diaryCount diari questa settimana: stai costruendo un'abitudine importante.")
    }

    return sb.toString()
}

// ══════════════════════════════════════════════════════════════════════
// Journey Line Card
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun JourneyLineCard(chartData: ChartData) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(chartData) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Il tuo percorso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val latest = chartData.moodLine.lastOrNull()
                if (latest != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = formatDecimal(1, latest),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("10", "5", "0").forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            fontSize = 10.sp
                        )
                    }
                }

                val progress = animProgress.value
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp)
                ) {
                    val points = chartData.moodLine
                    if (points.size < 2) return@Canvas

                    val w = size.width
                    val h = size.height
                    val pad = 8f
                    val drawH = h - pad * 2
                    val drawW = w - pad * 2
                    val spacing = drawW / (points.size - 1)

                    fun toY(v: Float) = pad + drawH * (1 - (v / 10f).coerceIn(0f, 1f))
                    fun toX(i: Int) = pad + spacing * i

                    // Grid
                    for (gv in listOf(0f, 2.5f, 5f, 7.5f, 10f)) {
                        val y = toY(gv)
                        drawLine(
                            gridColor, Offset(0f, y), Offset(w, y), 1f,
                            pathEffect = if (gv == 5f) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                    }

                    val vc = (points.size * progress).toInt().coerceAtLeast(2)

                    // Fill
                    val fillPath = Path().apply {
                        moveTo(toX(0), toY(points[0]))
                        for (i in 1 until vc) lineTo(toX(i), toY(points[i]))
                        lineTo(toX(vc - 1), h)
                        lineTo(toX(0), h)
                        close()
                    }
                    drawPath(fillPath, Brush.verticalGradient(listOf(fillColor, Color.Transparent)))

                    // Line
                    val linePath = Path().apply {
                        moveTo(toX(0), toY(points[0]))
                        for (i in 1 until vc) lineTo(toX(i), toY(points[i]))
                    }
                    drawPath(linePath, lineColor, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // Dots
                    for (i in 0 until vc) {
                        val cx = toX(i)
                        val cy = toY(points[i])
                        val isLast = i == vc - 1
                        drawCircle(Color.White, if (isLast) 10f else 7f, Offset(cx, cy))
                        drawCircle(lineColor, if (isLast) 7f else 5f, Offset(cx, cy))
                    }
                }
            }

            // Week labels
            if (chartData.weekLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    chartData.weekLabels.forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Resilience Gauge Card
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun ResilienceGaugeCard(profile: PsychologicalProfile) {
    val colorScheme = MaterialTheme.colorScheme
    val resiliencePercent = (profile.resilienceIndex * 100).toInt()

    val animSweep = remember { Animatable(0f) }
    LaunchedEffect(profile.resilienceIndex) {
        animSweep.animateTo(
            profile.resilienceIndex,
            tween(1200, easing = FastOutSlowInEasing)
        )
    }

    val arcColor = when {
        profile.resilienceIndex >= 0.7f -> Color(0xFF4CAF50)
        profile.resilienceIndex >= 0.4f -> Color(0xFFFFCA28)
        else -> Color(0xFFEF5350)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Resilienza",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            // Semi-circle gauge
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .size(160.dp, 90.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = with(density) { 12.dp.toPx() }
                    val arcSize = Size(size.width - strokeW, (size.height - strokeW / 2) * 2)
                    val topLeft = Offset(strokeW / 2, strokeW / 2)

                    // Track
                    drawArc(
                        color = colorScheme.surfaceContainerHighest,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                    // Fill
                    drawArc(
                        color = arcColor,
                        startAngle = 180f,
                        sweepAngle = 180f * animSweep.value,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = "$resiliencePercent%",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = arcColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            val resLabel = when {
                profile.resilienceIndex >= 0.7f -> "Ti riprendi bene dopo le difficolta'"
                profile.resilienceIndex >= 0.4f -> "Hai margine di miglioramento"
                else -> "Lavora sulla gestione dello stress"
            }
            Text(
                text = resLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (profile.recoverySpeed > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Recupero medio: ${profile.recoverySpeed.toInt()} giorni",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Stats Grid
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun StatsGrid(profile: PsychologicalProfile) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stress card
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Stress",
            value = formatDecimal(1, profile.stressBaseline),
            subtitle = when {
                profile.stressBaseline >= 7f -> "Alto"
                profile.stressBaseline >= 4f -> "Medio"
                else -> "Basso"
            },
            color = when {
                profile.stressBaseline >= 7f -> Color(0xFFEF5350)
                profile.stressBaseline >= 4f -> Color(0xFFFFCA28)
                else -> Color(0xFF4CAF50)
            },
            icon = Icons.Default.Whatshot
        )

        // Volatility card
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Variabilita'",
            value = if (profile.moodVolatility > 0) formatDecimal(1, profile.moodVolatility) else "—",
            subtitle = when {
                profile.moodVolatility >= 3f -> "Alta"
                profile.moodVolatility >= 1.5f -> "Media"
                profile.moodVolatility > 0f -> "Bassa"
                else -> ""
            },
            color = colorScheme.tertiary,
            icon = Icons.Default.ShowChart
        )

        // Diary count
        StatCard(
            modifier = Modifier.weight(1f),
            label = "Diari",
            value = "${profile.diaryCount}",
            subtitle = "questa settimana",
            color = colorScheme.primary,
            icon = Icons.Default.EditNote
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Data Quality Footer
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun DataQualityFooter(profile: PsychologicalProfile) {
    val colorScheme = MaterialTheme.colorScheme
    val confColor = when {
        profile.confidence >= 0.7f -> Color(0xFF4CAF50)
        profile.confidence >= 0.4f -> colorScheme.onSurfaceVariant
        else -> colorScheme.error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.onSurfaceVariant
                )
                Text(
                    text = profile.getWeekLabelFull(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(confColor)
                )
                Text(
                    text = "Affidabilita' ${(profile.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = confColor
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Utils
// ══════════════════════════════════════════════════════════════════════

private fun getMoodColor(mood: Float): Color = when {
    mood >= 8f -> Color(0xFF4CAF50)
    mood >= 6f -> Color(0xFF66BB6A)
    mood >= 4f -> Color(0xFFFFCA28)
    mood >= 2f -> Color(0xFFFF7043)
    else -> Color(0xFFEF5350)
}

private fun interpolateGradient(colors: List<Color>, fraction: Float): Color {
    if (colors.isEmpty()) return Color.Gray
    if (fraction <= 0f) return colors.first()
    if (fraction >= 1f) return colors.last()
    val sf = fraction * (colors.size - 1)
    val i = sf.toInt().coerceIn(0, colors.size - 2)
    val lf = sf - i
    val a = colors[i]; val b = colors[i + 1]
    return Color(
        a.red + (b.red - a.red) * lf,
        a.green + (b.green - a.green) * lf,
        a.blue + (b.blue - a.blue) * lf
    )
}
