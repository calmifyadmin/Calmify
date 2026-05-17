package com.lifo.biocontext

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.biocontext.BioTimelineContract.MarkerKind
import com.lifo.biocontext.BioTimelineContract.TimelineMarker
import com.lifo.biocontext.BioTimelineContract.TimelinePoint
import com.lifo.biocontext.BioTimelineContract.TimelineWindow
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Bio-Signal Timeline drill-down — Phase 9.2.1 (2026-05-17).
 *
 * Re-engineered surface: the Claude Design mockups treat each bio signal as a
 * stand-alone chip. The user told us "we have all this data — what could we
 * do with it?". This screen is the answer: a per-signal time-series view that
 * overlays journal entries + meditation sessions on the chart so the user
 * can *see* their interventions land on their physiology.
 *
 * Anatomy:
 *  - TopAppBar with the localized signal name + back
 *  - 7d / 30d / 90d window picker
 *  - Big line chart (Compose Canvas, 220dp tall):
 *      * dotted typical-range bands (baselineLow / baselineHigh)
 *      * smoothed signal line + filled gradient underneath
 *      * vertical overlay markers (journal = primary tint, meditation = tertiary)
 *  - Summary card: avg / min / max + honesty footer ("X / Y days tracked")
 *  - Empty state when there are fewer than 2 points
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioTimelineScreen(
    state: BioTimelineContract.State,
    onIntent: (BioTimelineContract.Intent) -> Unit,
    onBack: () -> Unit,
) {
    val signalLabel = stringResource(signalLabelRes(state.signal))
    val backA11y = stringResource(Strings.Action.back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = signalLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backA11y,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = CalmifySpacing.lg,
                    end = CalmifySpacing.lg,
                    top = CalmifySpacing.md,
                    bottom = CalmifySpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg),
        ) {
            WindowPicker(
                current = state.window,
                onSelect = { onIntent(BioTimelineContract.Intent.SetWindow(it)) },
            )

            when {
                state.isLoading -> LoadingBlock()
                state.isEmpty -> EmptyBlock(state.signal)
                else -> {
                    ChartCard(
                        points = state.points,
                        markers = state.markers,
                        baselineLow = state.baselineLow,
                        baselineHigh = state.baselineHigh,
                        signal = state.signal,
                    )
                    SummaryCard(state = state)
                    MarkersLegend(markers = state.markers)
                    HonestyFooter(daysTracked = state.daysTracked, window = state.window)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Window picker — 7d / 30d / 90d segmented control
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun WindowPicker(
    current: TimelineWindow,
    onSelect: (TimelineWindow) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TimelineWindow.entries.forEach { window ->
            val selected = window == current
            val label = stringResource(windowLabelRes(window))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CalmifyRadius.pill))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable(role = Role.Button, onClickLabel = label) { onSelect(window) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Chart card — Canvas line chart with overlay markers
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun ChartCard(
    points: List<TimelinePoint>,
    markers: List<TimelineMarker>,
    baselineLow: Double?,
    baselineHigh: Double?,
    signal: BioSignalDataType,
) {
    if (points.size < 2) return
    val accent = MaterialTheme.colorScheme.primary
    val fillTint = accent.copy(alpha = 0.12f)
    val bandTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
    val startDotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val journalTint = MaterialTheme.colorScheme.primary
    val meditationTint = MaterialTheme.colorScheme.tertiary
    val axisTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val minValue = minOf(points.minOf { it.value }, baselineLow ?: Double.POSITIVE_INFINITY)
    val maxValue = maxOf(points.maxOf { it.value }, baselineHigh ?: Double.NEGATIVE_INFINITY)
    val midValue = (minValue + maxValue) / 2

    val firstTs = points.first().timestampMillis
    val lastTs = points.last().timestampMillis
    val tsSpan = (lastTs - firstTs).coerceAtLeast(1L)

    val signalLabel = stringResource(signalLabelRes(signal))
    val chartA11y = stringResource(
        Strings.BioTimeline.chartA11y,
        signalLabel,
        formatValue(signal, minValue),
        formatValue(signal, maxValue),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(CalmifyRadius.lg),
            )
            .padding(CalmifySpacing.md)
            .clearAndSetSemantics { contentDescription = chartA11y },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md)) {
            // Y-axis labels (max / mid / min)
            Column(
                modifier = Modifier.height(220.dp).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatValue(signal, maxValue), style = axisTextStyle)
                Text(formatValue(signal, midValue), style = axisTextStyle)
                Text(formatValue(signal, minValue), style = axisTextStyle)
            }

            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val rangeSpan = (maxValue - minValue).coerceAtLeast(1.0)

                    // Dotted typical-range bands
                    val dashed = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
                    baselineHigh?.let { hi ->
                        val y = mapY(hi, minValue, rangeSpan, h)
                        drawLine(bandTint, Offset(0f, y), Offset(w, y), 1f, pathEffect = dashed)
                    }
                    baselineLow?.let { lo ->
                        val y = mapY(lo, minValue, rangeSpan, h)
                        drawLine(bandTint, Offset(0f, y), Offset(w, y), 1f, pathEffect = dashed)
                    }

                    // Build smoothed signal path
                    val offsets: List<Offset> = points.map { p ->
                        val x = w * (p.timestampMillis - firstTs).toFloat() / tsSpan
                        val y = mapY(p.value, minValue, rangeSpan, h)
                        Offset(x, y)
                    }
                    val linePath = buildSmoothPath(offsets)

                    // Fill under line
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(offsets.last().x, h)
                        lineTo(offsets.first().x, h)
                        close()
                    }
                    drawPath(fillPath, fillTint)

                    // Signal line
                    drawPath(
                        linePath,
                        accent,
                        style = Stroke(
                            width = 2.5f.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )

                    // Endpoints
                    drawCircle(
                        color = startDotColor,
                        radius = 3f.dp.toPx(),
                        center = offsets.first(),
                    )
                    drawCircle(accent, radius = 3.5f.dp.toPx(), center = offsets.last())

                    // Overlay markers — vertical lines at journal + meditation timestamps
                    markers.forEach { marker ->
                        val mx = w * (marker.timestampMillis - firstTs).toFloat() / tsSpan
                        if (mx in 0f..w) {
                            val color = when (marker.kind) {
                                MarkerKind.JOURNAL -> journalTint
                                MarkerKind.MEDITATION -> meditationTint
                            }
                            drawLine(
                                color = color.copy(alpha = 0.65f),
                                start = Offset(mx, 0f),
                                end = Offset(mx, h),
                                strokeWidth = 1.5f.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f),
                            )
                            drawCircle(color, radius = 4f.dp.toPx(), center = Offset(mx, 6.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Summary card — avg / min / max
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(state: BioTimelineContract.State) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(CalmifySpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatColumn(
            label = stringResource(Strings.BioTimeline.statAvg),
            value = formatValue(state.signal, state.averageValue),
        )
        StatColumn(
            label = stringResource(Strings.BioTimeline.statMin),
            value = formatValue(state.signal, state.minValue),
        )
        StatColumn(
            label = stringResource(Strings.BioTimeline.statMax),
            value = formatValue(state.signal, state.maxValue),
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Markers legend — only shows when markers exist
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun MarkersLegend(markers: List<TimelineMarker>) {
    if (markers.isEmpty()) return
    val journalCount = markers.count { it.kind == MarkerKind.JOURNAL }
    val meditationCount = markers.count { it.kind == MarkerKind.MEDITATION }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
    ) {
        if (journalCount > 0) {
            LegendDot(
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Outlined.EditNote,
                label = stringResource(Strings.BioTimeline.legendJournal, journalCount),
            )
        }
        if (meditationCount > 0) {
            LegendDot(
                color = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Outlined.SelfImprovement,
                label = stringResource(Strings.BioTimeline.legendMeditation, meditationCount),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(CalmifyRadius.pill))
                .background(color),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Honesty footer — "X / Y days tracked"
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun HonestyFooter(daysTracked: Int, window: TimelineWindow) {
    Text(
        text = stringResource(Strings.BioTimeline.daysTracked, daysTracked, window.days),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Loading + empty states
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun EmptyBlock(signal: BioSignalDataType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(CalmifySpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = signalIcon(signal),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(Strings.BioTimeline.emptyTitle),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Strings.BioTimeline.emptyBody),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Helpers — value formatting + per-type labels + smoothing
// ──────────────────────────────────────────────────────────────────────────

internal fun signalLabelRes(signal: BioSignalDataType): StringResource = when (signal) {
    BioSignalDataType.HEART_RATE -> Strings.BioTimeline.signalHeartRate
    BioSignalDataType.HRV -> Strings.BioTimeline.signalHrv
    BioSignalDataType.SLEEP -> Strings.BioTimeline.signalSleep
    BioSignalDataType.STEPS -> Strings.BioTimeline.signalSteps
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioTimeline.signalRestingHeartRate
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioTimeline.signalSpo2
    BioSignalDataType.ACTIVITY -> Strings.BioTimeline.signalActivity
}

private fun windowLabelRes(window: TimelineWindow): StringResource = when (window) {
    TimelineWindow.SEVEN_DAYS -> Strings.BioTimeline.window7d
    TimelineWindow.THIRTY_DAYS -> Strings.BioTimeline.window30d
    TimelineWindow.NINETY_DAYS -> Strings.BioTimeline.window90d
}

private fun signalIcon(signal: BioSignalDataType): ImageVector = when (signal) {
    BioSignalDataType.HEART_RATE -> Icons.Outlined.Favorite
    BioSignalDataType.HRV -> Icons.Outlined.GraphicEq
    BioSignalDataType.SLEEP -> Icons.Outlined.Bedtime
    BioSignalDataType.STEPS -> Icons.Outlined.DirectionsWalk
    BioSignalDataType.RESTING_HEART_RATE -> Icons.Outlined.MonitorHeart
    BioSignalDataType.OXYGEN_SATURATION -> Icons.Outlined.MonitorHeart
    BioSignalDataType.ACTIVITY -> Icons.Outlined.DirectionsRun
}

private fun formatValue(signal: BioSignalDataType, value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    return when (signal) {
        BioSignalDataType.HEART_RATE,
        BioSignalDataType.RESTING_HEART_RATE -> "${value.roundToInt()} bpm"
        BioSignalDataType.HRV -> "${value.roundToInt()} ms"
        BioSignalDataType.SLEEP -> {
            val mins = value.roundToInt()
            val h = mins / 60
            val m = mins % 60
            "${h}h ${m}m"
        }
        BioSignalDataType.STEPS -> "${value.roundToInt()}"
        BioSignalDataType.OXYGEN_SATURATION -> "${value.roundToInt()}%"
        BioSignalDataType.ACTIVITY -> "${value.roundToInt()} min"
    }
}

private fun mapY(value: Double, minValue: Double, rangeSpan: Double, h: Float): Float {
    val t = ((value - minValue) / rangeSpan).toFloat()
    return h - t * h
}

private fun buildSmoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    if (points.size < 3) {
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        return@apply
    }
    for (i in 0 until points.size - 1) {
        val p0 = if (i == 0) points[i] else points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
}
