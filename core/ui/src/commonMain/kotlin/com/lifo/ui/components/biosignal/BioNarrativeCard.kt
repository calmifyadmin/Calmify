package com.lifo.ui.components.biosignal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.i18n.Strings
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing
import com.lifo.util.model.BioSignalSource
import com.lifo.util.model.ConfidenceLevel
import org.jetbrains.compose.resources.stringResource

/**
 * AI narrative card — Phase 8.2 atom for the PRO-tier weekly insight surface.
 *
 * 1:1 grammar with `design/biosignal/Calmify BioNarrativeCard.html` (lines
 * 149-330, `.bnc`):
 * - Accent-glyph header + title + PRO chip + freshness sub-label
 * - Single multi-line narrative paragraph (4-6 lines)
 * - Sources chip row (which signals fed the narrative)
 * - BioConfidenceFooter (source + level)
 *
 * Per `memory/feedback_biosignal_plan_as_compass.md` hard rules from the bio
 * framing doc:
 * - Never diagnostic ("You are stressed")
 * - Always observational ("It seems that…", "The pattern suggests…")
 * - Never prescriptive ("You should…")
 * - Always with implicit opt-out ("The pattern is not a verdict")
 *
 * The narrative copy is the host's responsibility — this atom only renders.
 * In Phase 8.2 the copy is built from a local template that uses the user's
 * baselines + recent samples; in Phase 8.3/8.4 the same atom backs the real
 * Gemini-generated narrative (the contract stays stable).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BioNarrativeCard(
    title: String,
    freshness: String,
    narrative: String,
    sources: List<BioSource>,
    confidenceLevel: ConfidenceLevel,
    confidenceSource: BioSignalSource,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val proLabel = stringResource(Strings.BioProLock.proChip)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.xxl))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(CalmifyRadius.xxl),
            )
            .padding(CalmifySpacing.lg + 2.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ── Header ────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
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
                                text = proLabel,
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
                        text = freshness,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // ── Narrative paragraph ───────────────────────────────────────
            Text(
                text = narrative,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // ── Sources chip row ──────────────────────────────────────────
            if (sources.isNotEmpty()) {
                val sourcesA11y = sources.joinToString(", ") { it.label }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { contentDescription = sourcesA11y },
                ) {
                    sources.forEach { source -> SourceChip(source) }
                }
            }

            // ── Confidence footer ─────────────────────────────────────────
            Spacer(Modifier.height(2.dp))
            BioConfidenceFooter(
                level = confidenceLevel,
                source = confidenceSource,
            )
        }
    }
}

@Composable
private fun SourceChip(source: BioSource) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CalmifyRadius.pill))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = source.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Source descriptor for [BioNarrativeCard.sources] — icon + localized label. */
data class BioSource(
    val label: String,
    val icon: ImageVector,
)
