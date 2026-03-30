package com.lifo.write

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.lifo.ui.components.CalmifyTopBar
import com.lifo.ui.components.graphics.GraphEdge
import com.lifo.ui.components.graphics.GraphNode
import com.lifo.ui.components.graphics.InteractiveNodeGraph
import com.lifo.util.formatDecimal
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.Trend
import com.lifo.util.model.getWeekLabelFull
import kotlinx.coroutines.launch

/**
 * Profile chart data passed from DecomposeApp (avoids cross-feature dependency).
 */
data class PercorsoChartData(
    val moodLine: List<Float> = emptyList(),
    val weekLabels: List<String> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PercorsoScreen(
    state: PercorsoContract.State,
    onIntent: (PercorsoContract.Intent) -> Unit,
    onMenuClicked: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    // Profile data (from ProfileViewModel, passed through DecomposeApp)
    latestProfile: PsychologicalProfile? = null,
    chartData: PercorsoChartData = PercorsoChartData(),
    profileLoading: Boolean = false,
    onProfileRetry: () -> Unit = {},
    // Tool navigation
    onMeditationClick: () -> Unit = {},
    onReframeClick: () -> Unit = {},
    onBlockClick: () -> Unit = {},
    onRecurringThoughtsClick: () -> Unit = {},
    onEnergyCheckInClick: () -> Unit = {},
    onSleepLogClick: () -> Unit = {},
    onMovementClick: () -> Unit = {},
    onValuesClick: () -> Unit = {},
    onIkigaiClick: () -> Unit = {},
    onAweClick: () -> Unit = {},
    onSilenceClick: () -> Unit = {},
    onConnectionClick: () -> Unit = {},
    onInspirationClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {},
    onEnvironmentClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onIntent(PercorsoContract.Intent.Load) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    // Section progress
    val menteProgress = if (state.mente.items.isEmpty()) 0f
        else state.mente.items.map { it.progress }.average().toFloat()
    val corpoProgress = if (state.corpo.items.isEmpty()) 0f
        else state.corpo.items.map { it.progress }.average().toFloat()
    val spiritoProgress = if (state.spirito.items.isEmpty()) 0f
        else state.spirito.items.map { it.progress }.average().toFloat()
    val abitudiniProgress = if (state.abitudini.items.isEmpty()) 0f
        else state.abitudini.items.map { it.progress }.average().toFloat()

    fun nodeSize(progress: Float) = 22f + progress * 18f

    // Section card indices shift based on whether profile hero is shown
    val sectionStartIndex = if (latestProfile != null) 3 else 2

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CalmifyTopBar(title = "Il Mio Percorso", scrollBehavior = scrollBehavior)
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        ) {
            // ── Hero: Mood Score (from Profile) or Ring Progress ──
            if (latestProfile != null) {
                item(key = "hero_score") {
                    HeroScoreCard(latestProfile)
                }
            } else if (!profileLoading) {
                // Fallback: show ring progress when no profile data
                item(key = "ring_progress") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RingProgress(
                                progress = state.overallProgress,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(120.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Progresso Settimanale",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── Weekly Reflection (from Profile) ──
            if (latestProfile != null) {
                item(key = "weekly_reflection") {
                    WeeklyReflectionCard(latestProfile, chartData)
                }
            }

            // ── Interactive Node Graph ──
            item(key = "node_graph") {
                val tertiary = MaterialTheme.colorScheme.tertiary
                val secondary = MaterialTheme.colorScheme.secondary
                val error = MaterialTheme.colorScheme.error
                val primary = MaterialTheme.colorScheme.primary

                val graphNodes = remember(menteProgress, corpoProgress, spiritoProgress, abitudiniProgress, tertiary, secondary, error, primary) {
                    listOf(
                        GraphNode(id = "mente",     label = "Mente",     color = tertiary,  size = nodeSize(menteProgress),     group = "mind"),
                        GraphNode(id = "corpo",     label = "Corpo",     color = secondary, size = nodeSize(corpoProgress),     group = "body"),
                        GraphNode(id = "spirito",   label = "Spirito",   color = error,     size = nodeSize(spiritoProgress),   group = "spirit"),
                        GraphNode(id = "abitudini", label = "Abitudini", color = primary,   size = nodeSize(abitudiniProgress), group = "habits"),
                    )
                }
                val graphEdges = remember {
                    listOf(
                        GraphEdge(fromId = "mente",     toId = "corpo",     weight = 1f),
                        GraphEdge(fromId = "mente",     toId = "spirito",   weight = 1f),
                        GraphEdge(fromId = "mente",     toId = "abitudini", weight = 1f),
                        GraphEdge(fromId = "corpo",     toId = "spirito",   weight = 1f),
                        GraphEdge(fromId = "corpo",     toId = "abitudini", weight = 1f),
                        GraphEdge(fromId = "spirito",   toId = "abitudini", weight = 1f),
                    )
                }

                val sectionIndexMap = remember(sectionStartIndex) {
                    mapOf(
                        "mente" to sectionStartIndex,
                        "corpo" to sectionStartIndex + 1,
                        "spirito" to sectionStartIndex + 2,
                        "abitudini" to sectionStartIndex + 3,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Mappa del Percorso",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        InteractiveNodeGraph(
                            nodes = graphNodes,
                            edges = graphEdges,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            selectedNodeId = selectedNodeId,
                            onNodeTap = { node ->
                                selectedNodeId = node.id
                                val targetIndex = sectionIndexMap[node.id] ?: return@InteractiveNodeGraph
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            },
                        )
                    }
                }
            }

            // ── Section Cards (Mente / Corpo / Spirito / Abitudini) ──
            item(key = "section_mente") {
                SectionCard(
                    section = state.mente,
                    icon = Icons.Default.Psychology,
                    color = MaterialTheme.colorScheme.primary,
                    highlighted = selectedNodeId == "mente",
                    tools = listOf(
                        PercorsoToolAction("Meditazione", Icons.Outlined.SelfImprovement, onMeditationClick),
                        PercorsoToolAction("Reframing", Icons.Outlined.Psychology, onReframeClick),
                        PercorsoToolAction("Blocchi", Icons.Outlined.Extension, onBlockClick),
                        PercorsoToolAction("Pensieri", Icons.Outlined.BubbleChart, onRecurringThoughtsClick),
                    ),
                )
            }
            item(key = "section_corpo") {
                SectionCard(
                    section = state.corpo,
                    icon = Icons.Default.FitnessCenter,
                    color = MaterialTheme.colorScheme.tertiary,
                    highlighted = selectedNodeId == "corpo",
                    tools = listOf(
                        PercorsoToolAction("Energia", Icons.Outlined.BatteryChargingFull, onEnergyCheckInClick),
                        PercorsoToolAction("Sonno", Icons.Outlined.Bedtime, onSleepLogClick),
                        PercorsoToolAction("Movimento", Icons.AutoMirrored.Outlined.DirectionsRun, onMovementClick),
                        PercorsoToolAction("Dashboard", Icons.Outlined.Terrain, onDashboardClick),
                    ),
                )
            }
            item(key = "section_spirito") {
                SectionCard(
                    section = state.spirito,
                    icon = Icons.Default.SelfImprovement,
                    color = MaterialTheme.colorScheme.secondary,
                    highlighted = selectedNodeId == "spirito",
                    tools = listOf(
                        PercorsoToolAction("Valori", Icons.Outlined.Explore, onValuesClick),
                        PercorsoToolAction("Ikigai", Icons.Outlined.Interests, onIkigaiClick),
                        PercorsoToolAction("Awe", Icons.Outlined.Park, onAweClick),
                        PercorsoToolAction("Silenzio", Icons.Outlined.SelfImprovement, onSilenceClick),
                        PercorsoToolAction("Connessioni", Icons.Outlined.People, onConnectionClick),
                        PercorsoToolAction("Ispirazione", Icons.Outlined.FormatQuote, onInspirationClick),
                    ),
                )
            }
            item(key = "section_abitudini") {
                SectionCard(
                    section = state.abitudini,
                    icon = Icons.Default.CheckCircle,
                    color = MaterialTheme.colorScheme.error,
                    highlighted = selectedNodeId == "abitudini",
                    tools = listOf(
                        PercorsoToolAction("Abitudini", Icons.Outlined.CheckCircle, onHabitsClick),
                        PercorsoToolAction("Ambiente", Icons.Outlined.Spa, onEnvironmentClick),
                    ),
                )
            }

            // ── Journey Chart (from Profile) ──
            if (latestProfile != null && chartData.moodLine.size >= 2) {
                item(key = "journey_chart") {
                    JourneyLineCard(chartData)
                }
            }

            // ── Resilience Gauge (from Profile) ──
            if (latestProfile != null) {
                item(key = "resilience") {
                    ResilienceGaugeCard(latestProfile)
                }
            }

            // ── Stats Grid (from Profile) ──
            if (latestProfile != null) {
                item(key = "stats_grid") {
                    StatsGrid(latestProfile)
                }
            }

            // ── Data Quality Footer (from Profile) ──
            if (latestProfile != null) {
                item(key = "data_quality") {
                    DataQualityFooter(latestProfile)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Ring Progress (existing)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun RingProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ring_progress",
    )
    val trackColor = color.copy(alpha = 0.15f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
// Hero Score Card (adapted from ProfileDashboard)
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

            Row(verticalAlignment = Alignment.Bottom) {
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
                    shape = RoundedCornerShape(20.dp),
                    color = moodColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = moodLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
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
                    shape = RoundedCornerShape(20.dp),
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
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = trendColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            MoodGradientBar(score = profile.moodBaseline, modifier = Modifier.fillMaxWidth())
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
// Weekly Reflection Card (adapted from ProfileDashboard)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun WeeklyReflectionCard(profile: PsychologicalProfile, chartData: PercorsoChartData) {
    val reflection = remember(profile) { buildWeeklyReflection(profile) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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
                    shape = RoundedCornerShape(12.dp),
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
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

private fun buildWeeklyReflection(profile: PsychologicalProfile): String {
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
// Journey Line Card (adapted from ProfileDashboard)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun JourneyLineCard(chartData: PercorsoChartData) {
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
        shape = RoundedCornerShape(24.dp),
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
                        shape = RoundedCornerShape(20.dp),
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

                    for (gv in listOf(0f, 2.5f, 5f, 7.5f, 10f)) {
                        val y = toY(gv)
                        drawLine(
                            gridColor, Offset(0f, y), Offset(w, y), 1f,
                            pathEffect = if (gv == 5f) null else PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                    }

                    val vc = (points.size * progress).toInt().coerceAtLeast(2)

                    val fillPath = Path().apply {
                        moveTo(toX(0), toY(points[0]))
                        for (i in 1 until vc) lineTo(toX(i), toY(points[i]))
                        lineTo(toX(vc - 1), h)
                        lineTo(toX(0), h)
                        close()
                    }
                    drawPath(fillPath, Brush.verticalGradient(listOf(fillColor, Color.Transparent)))

                    val linePath = Path().apply {
                        moveTo(toX(0), toY(points[0]))
                        for (i in 1 until vc) lineTo(toX(i), toY(points[i]))
                    }
                    drawPath(linePath, lineColor, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    for (i in 0 until vc) {
                        val cx = toX(i)
                        val cy = toY(points[i])
                        val isLast = i == vc - 1
                        drawCircle(Color.White, if (isLast) 10f else 7f, Offset(cx, cy))
                        drawCircle(lineColor, if (isLast) 7f else 5f, Offset(cx, cy))
                    }
                }
            }

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
// Resilience Gauge Card (adapted from ProfileDashboard)
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
        shape = RoundedCornerShape(24.dp),
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

            val density = LocalDensity.current
            Box(
                modifier = Modifier.size(160.dp, 90.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = with(density) { 12.dp.toPx() }
                    val arcSize = Size(size.width - strokeW, (size.height - strokeW / 2) * 2)
                    val topLeft = Offset(strokeW / 2, strokeW / 2)

                    drawArc(
                        color = colorScheme.surfaceContainerHighest,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
                    shape = RoundedCornerShape(20.dp),
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
// Stats Grid (adapted from ProfileDashboard)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun StatsGrid(profile: PsychologicalProfile) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            icon = Icons.AutoMirrored.Filled.ShowChart
        )
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
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
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
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
// Data Quality Footer (adapted from ProfileDashboard)
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
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = confColor.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = confColor
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// Section Card + Tool Action (existing)
// ══════════════════════════════════════════════════════════════════════

private data class PercorsoToolAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCard(
    section: PercorsoContract.SectionSummary,
    icon: ImageVector,
    color: Color,
    highlighted: Boolean = false,
    tools: List<PercorsoToolAction> = emptyList(),
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow,
        border = if (highlighted) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(16.dp))

            section.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        item.value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp),
                    )
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            if (tools.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tools.forEach { tool ->
                        FilledTonalButton(
                            onClick = tool.onClick,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        ) {
                            Icon(tool.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tool.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
