package com.lifo.meditation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.meditation.domain.SessionHrSummary
import com.lifo.ui.components.biosignal.BioConfidenceFooter
import com.lifo.ui.components.biosignal.BioMiniHrChart
import com.lifo.ui.components.biosignal.BioProLock
import com.lifo.ui.components.biosignal.HrChartPoint
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Meditation outro bio card (Phase 5.3, Card 2) — wraps the `:core:ui`
 * biosignal atoms into the layout from
 * `design/biosignal/Calmify BioContextual Cards.html` lines 985–1049
 * (`.bio-medi`).
 *
 * Anatomy (top→bottom):
 * 1. Header — small accent-tinted glyph + "Your body followed" + "during this session"
 * 2. Narrative — single sentence; "drop" or "stable" variant chosen from [SessionHrSummary]
 * 3. Mini HR chart — [BioMiniHrChart] (Compose Canvas line, accent fill, dotted bands)
 * 4. PRO gate — [BioProLock] for the HRV layer (only when [isPro] is false)
 * 5. Confidence footer — [BioConfidenceFooter] (3-segment bar + device + level)
 *
 * Silence-by-default contract: callers must omit this composable entirely when
 * `summary == null`. Per Decision 2, no fake/placeholder data is ever shown.
 */
@Composable
internal fun MeditationBioCard(
    summary: SessionHrSummary,
    isPro: Boolean,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val narrative = if (summary.isMeaningfulDrop) {
        stringResource(Strings.BioMeditation.narrativeDrop, summary.startBpm, summary.endBpm)
    } else {
        stringResource(Strings.BioMeditation.narrativeStable, (summary.startBpm + summary.endBpm) / 2)
    }
    val chartA11y = stringResource(Strings.BioMeditation.chartA11y, summary.startBpm, summary.endBpm)
    val hrvGateCopy = stringResource(Strings.BioMeditation.hrvGateCopy)

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
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MonitorHeart,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Text(
                    text = stringResource(Strings.BioMeditation.cardTitle),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Strings.BioMeditation.cardScope),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Narrative ─────────────────────────────────────────────────
            Spacer(Modifier.height(6.dp))
            Text(
                text = narrative,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // ── Mini HR chart ─────────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            BioMiniHrChart(
                points = summary.points.toChartPoints(),
                a11yLabel = chartA11y,
            )

            // ── PRO gate (FREE only) ──────────────────────────────────────
            if (!isPro) {
                Spacer(Modifier.height(12.dp))
                BioProLock(
                    copy = hrvGateCopy,
                    onUpgrade = onUpgrade,
                )
            }

            // ── Confidence footer ─────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            BioConfidenceFooter(
                level = summary.confidence,
                source = summary.source,
            )
        }
    }
}

@Composable
private fun List<com.lifo.meditation.domain.HrPoint>.toChartPoints(): List<HrChartPoint> {
    val clockTemplate = Strings.BioMeditation.clockMinSec
    return map { p ->
        val mins = p.elapsedSeconds / 60
        val rem = p.elapsedSeconds % 60
        HrChartPoint(
            bpm = p.bpm,
            clockLabel = stringResource(clockTemplate, mins, rem),
        )
    }
}
