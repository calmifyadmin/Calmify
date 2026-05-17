package com.lifo.biocontext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SentimentSatisfiedAlt
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.biocontext.domain.BioPattern
import com.lifo.ui.components.biosignal.Bar
import com.lifo.ui.components.biosignal.BarRow
import com.lifo.ui.components.biosignal.BarStyle
import com.lifo.ui.components.biosignal.BioConfidenceFooter
import com.lifo.ui.components.biosignal.BioCorrelationBars
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bio Pattern Feed — Phase 9.2.2 (2026-05-17).
 *
 * Re-engineered surface: aggregates *all* honest cross-signal correlations
 * the app can detect. Each pattern is a card that uses [BioCorrelationBars]
 * for the visual + a one-line observational narrative + a confidence footer.
 *
 * Silence-by-default: when no pattern crosses its meaningfulness floor, the
 * screen shows an empty state explaining what's needed to surface more
 * observations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BioPatternFeedScreen(
    state: BioPatternFeedContract.State,
    onIntent: (BioPatternFeedContract.Intent) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(Strings.BioPatternFeed.topbarTitle)
    val backA11y = stringResource(Strings.Action.back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
                .padding(horizontal = CalmifySpacing.lg, vertical = CalmifySpacing.md),
            verticalArrangement = Arrangement.spacedBy(CalmifySpacing.lg),
        ) {
            FeedIntro()

            when {
                state.isLoading -> LoadingBlock()
                state.isEmpty -> EmptyBlock()
                else -> state.patterns.forEach { pattern -> PatternCard(pattern) }
            }
        }
    }
}

@Composable
private fun FeedIntro() {
    Column {
        Text(
            text = stringResource(Strings.BioPatternFeed.lede),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun EmptyBlock() {
    com.lifo.ui.components.biosignal.BioEmptyState(
        icon = Icons.Outlined.AutoAwesome,
        title = stringResource(Strings.BioPatternFeed.emptyTitle),
        body = stringResource(Strings.BioPatternFeed.emptyBody),
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Generic pattern card shell — header + narrative + bars + confidence
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun PatternCard(pattern: BioPattern) {
    when (pattern) {
        is BioPattern.SleepMood -> SleepMoodCard(pattern)
        is BioPattern.HrvTrend -> HrvTrendCard(pattern)
        is BioPattern.SleepDrift -> SleepDriftCard(pattern)
    }
}

@Composable
private fun CardShell(
    icon: ImageVector,
    title: String,
    meta: String,
    patternLabel: String,
    narrative: String,
    rows: List<BarRow>,
    barsA11y: String,
    confidence: com.lifo.util.model.ConfidenceLevel,
    source: com.lifo.util.model.BioSignalSource,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.xxl))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, accent.copy(alpha = 0.14f), RoundedCornerShape(CalmifyRadius.xxl))
            .padding(CalmifySpacing.lg),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = patternLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = narrative,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(14.dp))
            BioCorrelationBars(rows = rows, a11yLabel = barsA11y)

            Spacer(Modifier.height(12.dp))
            BioConfidenceFooter(level = confidence, source = source)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Pattern-specific cards
// ──────────────────────────────────────────────────────────────────────────

@Composable
private fun SleepMoodCard(p: BioPattern.SleepMood) {
    val title = stringResource(Strings.BioPatternFeed.sleepMoodTitle)
    val meta = stringResource(Strings.BioPatternFeed.sleepMoodMeta, p.windowDays)
    val patternLabel = stringResource(Strings.BioPatternFeed.patternLabel)
    val narrative = stringResource(
        Strings.BioPatternFeed.sleepMoodNarrative,
        p.sleepHoursThreshold.roundToInt(),
        p.liftPercent,
    )
    val rowGoodLabel = stringResource(Strings.BioPatternFeed.sleepMoodRowGoodLabel)
    val rowGoodSub = stringResource(Strings.BioPatternFeed.sleepMoodRowGoodSub)
    val rowPoorLabel = stringResource(Strings.BioPatternFeed.sleepMoodRowPoorLabel)
    val rowPoorSub = stringResource(Strings.BioPatternFeed.sleepMoodRowPoorSub)
    val deltaLabel = stringResource(Strings.BioPatternFeed.sleepMoodDelta, p.liftPercent)
    val barsA11y = stringResource(Strings.BioPatternFeed.sleepMoodBarsA11y, p.liftPercent)
    val rows = listOf(
        BarRow(
            label = rowGoodLabel,
            sublabel = rowGoodSub,
            bars = makeBars(value = p.goodSleepNextDayMoodAvg.toFloat(), max = 10f, count = 6, hi = true),
            valueLabel = "${p.goodSleepNextDayMoodAvg.roundToInt()}",
            deltaLabel = deltaLabel,
            style = BarStyle.MED,
        ),
        BarRow(
            label = rowPoorLabel,
            sublabel = rowPoorSub,
            bars = makeBars(value = p.poorSleepNextDayMoodAvg.toFloat(), max = 10f, count = 6, hi = false),
            valueLabel = "${p.poorSleepNextDayMoodAvg.roundToInt()}",
            deltaLabel = null,
            style = BarStyle.HRV,
        ),
    )
    CardShell(
        icon = Icons.Outlined.SentimentSatisfiedAlt,
        title = title,
        meta = meta,
        patternLabel = patternLabel,
        narrative = narrative,
        rows = rows,
        barsA11y = barsA11y,
        confidence = p.confidence,
        source = p.source,
    )
}

@Composable
private fun HrvTrendCard(p: BioPattern.HrvTrend) {
    val title = stringResource(Strings.BioPatternFeed.hrvTrendTitle)
    val meta = stringResource(Strings.BioPatternFeed.hrvTrendMeta, p.daysAgo)
    val arrow = if (p.deltaPercent >= 0) "+" else ""
    val narrative = stringResource(
        Strings.BioPatternFeed.hrvTrendNarrative,
        arrow + p.deltaPercent,
        p.daysAgo,
    )
    val rowNowLabel = stringResource(Strings.BioPatternFeed.trendRowNow)
    val rowPastLabel = stringResource(Strings.BioPatternFeed.trendRowPast, p.daysAgo)
    val nowSub = stringResource(Strings.BioPatternFeed.hrvUnit)
    val rows = listOf(
        BarRow(
            label = rowNowLabel,
            sublabel = nowSub,
            bars = makeBars(value = p.currentMedianMs.toFloat(), max = (p.currentMedianMs + p.pastMedianMs).toFloat() / 2 * 1.4f, count = 6, hi = p.deltaPercent >= 0),
            valueLabel = "${p.currentMedianMs.roundToInt()}",
            deltaLabel = "$arrow${p.deltaPercent}%",
            style = BarStyle.MED,
        ),
        BarRow(
            label = rowPastLabel,
            sublabel = nowSub,
            bars = makeBars(value = p.pastMedianMs.toFloat(), max = (p.currentMedianMs + p.pastMedianMs).toFloat() / 2 * 1.4f, count = 6, hi = false),
            valueLabel = "${p.pastMedianMs.roundToInt()}",
            deltaLabel = null,
            style = BarStyle.HRV,
        ),
    )
    val barsA11y = stringResource(Strings.BioPatternFeed.hrvTrendBarsA11y, p.currentMedianMs.roundToInt(), p.pastMedianMs.roundToInt())
    CardShell(
        icon = Icons.Outlined.GraphicEq,
        title = title,
        meta = meta,
        patternLabel = stringResource(Strings.BioPatternFeed.trendLabel),
        narrative = narrative,
        rows = rows,
        barsA11y = barsA11y,
        confidence = p.confidence,
        source = p.source,
    )
}

@Composable
private fun SleepDriftCard(p: BioPattern.SleepDrift) {
    val title = stringResource(Strings.BioPatternFeed.sleepDriftTitle)
    val meta = stringResource(Strings.BioPatternFeed.sleepDriftMeta, p.daysAgo)
    val arrow = if (p.deltaPercent >= 0) "+" else ""
    val absDelta = abs(p.deltaPercent)
    val narrative = stringResource(
        if (p.deltaPercent >= 0) Strings.BioPatternFeed.sleepDriftUp
        else Strings.BioPatternFeed.sleepDriftDown,
        absDelta,
        p.daysAgo,
    )
    val rowNowLabel = stringResource(Strings.BioPatternFeed.trendRowNow)
    val rowPastLabel = stringResource(Strings.BioPatternFeed.trendRowPast, p.daysAgo)
    val sleepSub = stringResource(Strings.BioPatternFeed.sleepUnit)
    val rows = listOf(
        BarRow(
            label = rowNowLabel,
            sublabel = sleepSub,
            bars = makeBars(value = p.currentMedianMinutes.toFloat(), max = (p.currentMedianMinutes + p.pastMedianMinutes).toFloat() / 2 * 1.4f, count = 6, hi = p.deltaPercent >= 0),
            valueLabel = formatSleepHours(p.currentMedianMinutes),
            deltaLabel = "$arrow${p.deltaPercent}%",
            style = BarStyle.MED,
        ),
        BarRow(
            label = rowPastLabel,
            sublabel = sleepSub,
            bars = makeBars(value = p.pastMedianMinutes.toFloat(), max = (p.currentMedianMinutes + p.pastMedianMinutes).toFloat() / 2 * 1.4f, count = 6, hi = false),
            valueLabel = formatSleepHours(p.pastMedianMinutes),
            deltaLabel = null,
            style = BarStyle.HRV,
        ),
    )
    val barsA11y = stringResource(
        Strings.BioPatternFeed.sleepDriftBarsA11y,
        formatSleepHours(p.currentMedianMinutes),
        formatSleepHours(p.pastMedianMinutes),
    )
    CardShell(
        icon = Icons.Outlined.Bedtime,
        title = title,
        meta = meta,
        patternLabel = stringResource(Strings.BioPatternFeed.trendLabel),
        narrative = narrative,
        rows = rows,
        barsA11y = barsA11y,
        confidence = p.confidence,
        source = p.source,
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────

/**
 * Synthesizes a 6-bar series scaled around [value]/[max] — illustrative only.
 * The pattern cards present averages, not week-by-week data, so the bars carry
 * visual weight (showing the magnitude relative to peer rows), not extra signal.
 */
private fun makeBars(value: Float, max: Float, count: Int, hi: Boolean): List<Bar> {
    val safeMax = if (max > 0f) max else 1f
    val baseRatio = (value / safeMax).coerceIn(0.15f, 1f)
    return (0 until count).map { i ->
        // gentle wave around the base so the visual reads as variation, not flatline
        val mod = 1f + kotlin.math.sin(i * 0.7f) * 0.12f
        Bar(value = (baseRatio * mod * safeMax), isHi = hi)
    }
}

private fun formatSleepHours(minutes: Double): String {
    val mins = minutes.roundToInt()
    val h = mins / 60
    val m = mins % 60
    return "${h}h ${m}m"
}
