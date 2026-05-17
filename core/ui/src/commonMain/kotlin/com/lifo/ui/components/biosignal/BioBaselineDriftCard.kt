package com.lifo.ui.components.biosignal

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingFlat
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalDataType
import org.jetbrains.compose.resources.stringResource

/**
 * Predictive baseline drift card — Phase 9.2.5 (2026-05-17) re-engineered atom.
 *
 * Surfaces how the user's typical range has SHIFTED over time:
 *
 *   "Your HRV median: 42 ms (+12% vs 60 days ago)"
 *
 * Re-engineering NOTE: this card is the predictive promise from
 * `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` ("la tua resting HR di base è 62 —
 * oggi 71, qualcosa lo agita") made concrete in our actual architecture.
 * We can ship it only because Phase 6.1 baselines + Phase 9.2.5 history
 * snapshots make the historical comparison cheap.
 *
 * Per dogma #3: observational, never prescriptive. The arrow says "+12%"
 * not "you should…" The user reads meaning, we just surface signal.
 *
 * Silence-by-default: if there's no historical snapshot to compare against
 * (cold start, <60 days of usage), the composable returns immediately.
 */
@Composable
fun BioBaselineDriftCard(
    data: BioBaselineDrift,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val arrowIcon = when {
        data.deltaPercent > 0 -> Icons.Outlined.TrendingUp
        data.deltaPercent < 0 -> Icons.Outlined.TrendingDown
        else -> Icons.Outlined.TrendingFlat
    }
    val arrowTint = when {
        data.deltaPercent > 0 -> cs.primary
        data.deltaPercent < 0 -> cs.onSurfaceVariant
        else -> cs.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.xxl))
            .background(cs.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = cs.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(CalmifyRadius.xxl),
            )
            .padding(CalmifySpacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.sm)) {
            Text(
                text = stringResource(Strings.BioBaselineDrift.eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 1.2.sp,
                ),
                color = cs.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        labelOf(data.type),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        Strings.BioBaselineDrift.currentValue,
                        formatValue(data.currentMedian, data.type),
                        unitOf(data.type),
                    ),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = cs.onSurface,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(CalmifyRadius.pill))
                        .background(arrowTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = arrowIcon,
                        contentDescription = null,
                        tint = arrowTint,
                        modifier = Modifier.size(13.dp),
                    )
                }
                val sign = if (data.deltaPercent >= 0) "+" else ""
                Text(
                    text = stringResource(
                        Strings.BioBaselineDrift.deltaTemplate,
                        sign,
                        data.deltaPercent,
                        data.comparedToDaysAgo,
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(Strings.BioBaselineDrift.fineprint),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private fun labelOf(type: BioSignalDataType) = when (type) {
    BioSignalDataType.HRV -> Strings.BioBaselineDrift.labelHrv
    BioSignalDataType.HEART_RATE,
    BioSignalDataType.RESTING_HEART_RATE -> Strings.BioBaselineDrift.labelRestingHr
    BioSignalDataType.SLEEP -> Strings.BioBaselineDrift.labelSleep
    BioSignalDataType.STEPS -> Strings.BioBaselineDrift.labelSteps
    BioSignalDataType.OXYGEN_SATURATION -> Strings.BioBaselineDrift.labelSpo2
    BioSignalDataType.ACTIVITY -> Strings.BioBaselineDrift.labelActivity
}

private fun unitOf(type: BioSignalDataType): String = when (type) {
    BioSignalDataType.HRV -> "ms"
    BioSignalDataType.HEART_RATE, BioSignalDataType.RESTING_HEART_RATE -> "bpm"
    BioSignalDataType.SLEEP, BioSignalDataType.ACTIVITY -> "min"
    BioSignalDataType.STEPS -> ""
    BioSignalDataType.OXYGEN_SATURATION -> "%"
}

private fun formatValue(value: Double, type: BioSignalDataType): String =
    when (type) {
        BioSignalDataType.STEPS -> value.toInt().toString()
        else -> value.toInt().toString()
    }

/**
 * Folded drift data for [BioBaselineDriftCard]. Built by a use case that
 * compares the user's current 30-day baseline median against a historical
 * snapshot from `bio_baseline_history`.
 */
data class BioBaselineDrift(
    val type: BioSignalDataType,
    val currentMedian: Double,
    val comparedToDaysAgo: Int,
    /** Signed % change. Positive = current is higher than the comparison snapshot. */
    val deltaPercent: Int,
)
