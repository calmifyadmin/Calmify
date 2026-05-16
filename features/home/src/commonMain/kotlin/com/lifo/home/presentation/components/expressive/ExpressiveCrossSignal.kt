package com.lifo.home.presentation.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.domain.usecase.CrossSignalPattern
import com.lifo.ui.components.biosignal.Bar
import com.lifo.ui.components.biosignal.BarRow
import com.lifo.ui.components.biosignal.BarStyle
import com.lifo.ui.components.biosignal.BioConfidenceFooter
import com.lifo.ui.components.biosignal.BioCorrelationBars
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.formatDecimal
import org.jetbrains.compose.resources.stringResource

/**
 * Home "Cross-signal pattern" card — Phase 5.4 (Card 4), PRO-only.
 *
 * Renders the meditation × HRV correlation detected by
 * [com.lifo.home.domain.usecase.GetCrossSignalPatternUseCase]. The card sits
 * in Home below the community preview until an Insight pattern feed exists
 * (see commit message for the migration plan).
 *
 * 1:1 layout grammar with `.corr` in
 * `design/biosignal/Calmify BioContextual Cards.html` lines 1229–1306:
 * accent-glyph header + title + PRO chip + dismiss × + meta + pattern label +
 * narrative + [BioCorrelationBars] + [BioConfidenceFooter].
 *
 * Per `memory/feedback_biosignal_plan_as_compass.md`: observation only,
 * never causation. The "Coincidence? Maybe" framing is baked into the narrative
 * string and intentional.
 */
@Composable
internal fun ExpressiveCrossSignal(
    pattern: CrossSignalPattern,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val dismissA11y = stringResource(Strings.BioCrossSignal.dismissA11y)
    val barsA11y = stringResource(Strings.BioCrossSignal.barsA11y, pattern.windowWeeks)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.xxl))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(CalmifyRadius.xxl),
            )
            .padding(CalmifySpacing.lg),
    ) {
        Column {
            // ── Header ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(Strings.BioCrossSignal.cardTitle),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CalmifyRadius.pill))
                                .background(accent.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = stringResource(Strings.BioProLock.proChip),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = accent,
                            )
                        }
                    }
                    Text(
                        text = stringResource(Strings.BioCrossSignal.cardMeta, pattern.windowWeeks),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .clickable(onClick = onDismiss)
                        .semantics { contentDescription = dismissA11y },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Pattern label ─────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Strings.BioCrossSignal.patternLabel),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                ),
                color = accent,
            )

            // ── Narrative ─────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    Strings.BioCrossSignal.narrative,
                    pattern.highMeditationThreshold,
                    pattern.liftPercent,
                    pattern.windowWeeks,
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // ── Correlation bars ──────────────────────────────────────────
            Spacer(Modifier.height(14.dp))
            BioCorrelationBars(
                rows = listOf(
                    BarRow(
                        label = stringResource(Strings.BioCrossSignal.rowMedLabel),
                        sublabel = stringResource(Strings.BioCrossSignal.rowMedSublabel),
                        bars = pattern.medBars.map { Bar(it.value, it.isHi) },
                        valueLabel = stringResource(
                            Strings.BioCrossSignal.rowMedValue,
                            formatDecimal(1, pattern.sessionsPerWeekAvg),
                        ),
                        deltaLabel = stringResource(Strings.BioCrossSignal.rowMedDelta),
                        style = BarStyle.MED,
                    ),
                    BarRow(
                        label = stringResource(Strings.BioCrossSignal.rowHrvLabel),
                        sublabel = stringResource(Strings.BioCrossSignal.rowHrvSublabel),
                        bars = pattern.hrvBars.map { Bar(it.value, it.isHi) },
                        valueLabel = stringResource(
                            Strings.BioCrossSignal.rowHrvValue,
                            pattern.hrvAvgMillis.toInt(),
                        ),
                        deltaLabel = stringResource(
                            Strings.BioCrossSignal.rowHrvDelta,
                            pattern.liftPercent,
                        ),
                        style = BarStyle.HRV,
                    ),
                ),
                a11yLabel = barsA11y,
            )

            // ── Confidence footer ─────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            BioConfidenceFooter(
                level = pattern.confidence,
                source = pattern.source,
            )
        }
    }
}
