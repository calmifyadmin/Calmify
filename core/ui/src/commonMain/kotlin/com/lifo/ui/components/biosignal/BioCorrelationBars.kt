package com.lifo.ui.components.biosignal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

/**
 * Cross-signal correlation bars — Phase 5.4 atom (Card 4).
 *
 * 1:1 with `.corr-mini` in `design/biosignal/Calmify BioContextual Cards.html`:
 * two rows of equal-length bar series (typically 6 weeks), each row with a
 * left-aligned label block, the bar series in the middle, and a right-aligned
 * value with delta annotation.
 *
 * Renders entirely with Compose primitives (no Canvas) — each bar is a clipped
 * Box with proportional height based on `value / rowMax`. Bars in [BarRow.bars]
 * marked `isHi = true` are rendered in the accent tint (vs the subtle resting
 * tint for non-hi bars) — encoding "weeks where the signal was above its own
 * average". This matches the CSS `.bars.med .b.hi` / `.bars.hrv .b.hi` rules.
 */
@Composable
fun BioCorrelationBars(
    rows: List<BarRow>,
    a11yLabel: String,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = a11yLabel },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { row ->
            CorrelationBarRow(row)
        }
    }
}

@Composable
private fun CorrelationBarRow(row: BarRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Label block (86dp fixed width per CSS) ───────────────────────────
        Column(modifier = Modifier.width(86.dp)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.sublabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Bars ──────────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).height(28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val maxValue = row.bars.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
                row.bars.forEach { bar ->
                    val heightFraction = (bar.value / maxValue).coerceIn(0.08f, 1f)
                    val barColor = if (bar.isHi) {
                        when (row.style) {
                            BarStyle.MED -> MaterialTheme.colorScheme.primary
                            BarStyle.HRV -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        }
                    } else {
                        when (row.style) {
                            BarStyle.MED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
                            BarStyle.HRV -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 6.dp)
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor),
                    )
                }
            }
        }

        // ── Value + delta block (38dp per CSS) ───────────────────────────────
        Column(
            modifier = Modifier.width(40.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = row.valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (row.deltaLabel != null) {
                Text(
                    text = row.deltaLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * @property label    left-aligned subtle row title (e.g. "Meditation")
 * @property sublabel left-aligned bold caption (e.g. "sessions / week")
 * @property bars     equal-length series (6 weeks recommended); height is value-relative
 * @property valueLabel right-aligned mono value (e.g. "3.7" or "41 ms")
 * @property deltaLabel right-aligned accent delta (e.g. "avg" or "+15%"); null = omitted
 * @property style    visual tint preset — MED uses solid accent, HRV uses a softer accent
 */
data class BarRow(
    val label: String,
    val sublabel: String,
    val bars: List<Bar>,
    val valueLabel: String,
    val deltaLabel: String? = null,
    val style: BarStyle = BarStyle.MED,
)

data class Bar(val value: Float, val isHi: Boolean = false)

enum class BarStyle { MED, HRV }
