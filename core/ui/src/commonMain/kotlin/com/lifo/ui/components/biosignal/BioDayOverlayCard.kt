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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioRangeHint
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import org.jetbrains.compose.resources.stringResource

/**
 * Bio overlay for a specific day — Phase 9.2.3 (2026-05-17) re-engineered atom.
 *
 * Renders the bio context of a single past day (sleep + morning HR + steps)
 * inside DiaryDetailScreen so the user can answer "what was happening in my
 * body when I wrote this?" — the question Claude Design's mockup never
 * surfaced because it kept bio in its own silos.
 *
 * Each metric is a slim row with icon + value + optional baseline-hint suffix
 * ("close to your usual" / "below" / "above"). The whole atom is silence-by-
 * default: if [data] has no metrics, the composable returns immediately.
 *
 * Per `memory/feedback_calmify_values.md` dogma #3: observational, not
 * prescriptive. We never say "you slept too little to journal well" — we
 * say "you slept 6h 12m that night."
 */
@Composable
fun BioDayOverlayCard(
    data: BioDayOverlay,
    modifier: Modifier = Modifier,
) {
    if (!data.hasAny) return
    val cs = MaterialTheme.colorScheme
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
        Column(verticalArrangement = Arrangement.spacedBy(CalmifySpacing.md)) {
            // Eyebrow: "QUEL GIORNO" / "THAT DAY"
            Text(
                text = stringResource(Strings.BioDayOverlay.eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 1.2.sp,
                ),
                color = cs.primary,
            )
            // Each row
            data.sleepMinutes?.let { mins ->
                BioOverlayRow(
                    icon = Icons.Outlined.Bedtime,
                    label = stringResource(Strings.BioDayOverlay.labelSleep),
                    value = stringResource(
                        Strings.BioDayOverlay.valueSleep,
                        mins / 60, mins % 60,
                    ),
                    hint = data.sleepHint,
                )
            }
            data.morningHrBpm?.let { bpm ->
                BioOverlayRow(
                    icon = Icons.Outlined.Favorite,
                    label = stringResource(Strings.BioDayOverlay.labelHr),
                    value = stringResource(Strings.BioDayOverlay.valueHr, bpm),
                    hint = data.hrHint,
                )
            }
            data.steps?.let { steps ->
                BioOverlayRow(
                    icon = Icons.Outlined.DirectionsWalk,
                    label = stringResource(Strings.BioDayOverlay.labelSteps),
                    value = stringResource(Strings.BioDayOverlay.valueSteps, steps),
                    hint = data.stepsHint,
                )
            }
            // Confidence footer (only when at least one real source)
            data.source?.let { source ->
                Spacer(Modifier.height(2.dp))
                BioConfidenceFooter(level = data.confidence, source = source)
            }
        }
    }
}

@Composable
private fun BioOverlayRow(
    icon: ImageVector,
    label: String,
    value: String,
    hint: BioRangeHint?,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(CalmifyRadius.pill))
                .background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(
                text = stringResource(hintLabel(hint)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = when (hint) {
                    BioRangeHint.WITHIN -> cs.primary.copy(alpha = 0.85f)
                    BioRangeHint.BELOW -> cs.onSurfaceVariant
                    BioRangeHint.ABOVE -> cs.onSurfaceVariant
                },
            )
        }
    }
}

private fun hintLabel(hint: BioRangeHint) = when (hint) {
    BioRangeHint.WITHIN -> Strings.BioDayOverlay.hintWithin
    BioRangeHint.BELOW -> Strings.BioDayOverlay.hintBelow
    BioRangeHint.ABOVE -> Strings.BioDayOverlay.hintAbove
}

/**
 * Folded bio data for a single calendar day, ready for [BioDayOverlayCard].
 *
 * Built by a host-side use case (e.g. `GetDiaryDayBioOverlayUseCase` in
 * `:features:write/domain/usecase/`) from the local sample store.
 */
data class BioDayOverlay(
    val sleepMinutes: Int? = null,
    val morningHrBpm: Int? = null,
    val steps: Int? = null,
    val sleepHint: BioRangeHint? = null,
    val hrHint: BioRangeHint? = null,
    val stepsHint: BioRangeHint? = null,
    val confidence: ConfidenceLevel = ConfidenceLevel.LOW,
    val source: BioSignalSource? = null,
) {
    val hasAny: Boolean get() = sleepMinutes != null || morningHrBpm != null || steps != null
}
