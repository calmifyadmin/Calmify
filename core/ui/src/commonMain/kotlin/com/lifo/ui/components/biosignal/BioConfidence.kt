package com.lifo.ui.components.biosignal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import org.jetbrains.compose.resources.stringResource

/**
 * Bio-signal confidence atoms — onestà radicale.
 *
 * Per `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` Decision 2 (DataConfidence always
 * visible) and `memory/feedback_calmify_values.md` dogma #4 (data sovereignty +
 * transparency). Every bio metric surfaced in UI must show source + level.
 *
 * Two surfaces — both pulled 1:1 from `design/biosignal/Calmify BioContextual Cards.html`:
 *
 * - [BioConfidenceChip] — compact pill used inline next to narrative chips
 *   (Home Today narrative card). Just the icon + level word — visual punctuation,
 *   not a full disclosure. Tapping the parent card surfaces the full provenance.
 *
 * - [BioConfidenceFooter] — full "From {device} · {level} confidence" footer
 *   used at the bottom of journal banner / meditation slab / insight correlation
 *   card. Includes a 3-segment bar visualisation of [ConfidenceLevel].
 */

// ── Compact chip ──────────────────────────────────────────────────────────────

@Composable
fun BioConfidenceChip(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(Strings.BioConfidence.levelLabel(level))
    val a11y = stringResource(Strings.BioConfidence.chipA11y, text)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CalmifyRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(13.dp).width(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ── Full footer (3-segment bar + device + level) ──────────────────────────────

@Composable
fun BioConfidenceFooter(
    level: ConfidenceLevel,
    source: BioSignalSource,
    modifier: Modifier = Modifier,
) {
    val deviceLabel = source.deviceName.ifBlank { stringResource(Strings.BioConfidence.unknownDevice) }
    val levelLabel = stringResource(Strings.BioConfidence.levelLabel(level))
    val text = stringResource(Strings.BioConfidence.footerTemplate, deviceLabel, levelLabel)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
    ) {
        ConfidenceSegmentBar(level = level)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun ConfidenceSegmentBar(level: ConfidenceLevel) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val filledCount = when (level) {
        ConfidenceLevel.LOW -> 1
        ConfidenceLevel.MEDIUM -> 2
        ConfidenceLevel.HIGH -> 3
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .width(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filledCount) active else inactive),
            )
        }
    }
}
