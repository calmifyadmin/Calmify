package com.lifo.ui.components.biosignal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

/**
 * Mini heart-rate line chart — Phase 5.3 atom for the Meditation outro card
 * (and any future inline HR surface).
 *
 * 1:1 with the `.hr-chart` SVG in `design/biosignal/Calmify BioContextual Cards.html`:
 *
 * - 84dp tall plot area, accent-colored Catmull-Rom-ish smoothed line
 * - Fill under the line at 12% accent alpha (signals "ease" without anxiety)
 * - Two dotted horizontal bands at p10 / p90 of the rendered range (typical-range hints)
 * - Subtle start dot (fg-subtle) + accent end dot
 * - 3 Y-axis labels (max / mid / min bpm) on the left
 * - 3 X-axis labels (start / mid / end clock) on the bottom
 *
 * Per `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`: no targets, no percentile rankings,
 * no "above normal" warnings. The chart shows the trajectory of THIS session only —
 * the user reads meaning, the app doesn't impose it.
 */
@Composable
fun BioMiniHrChart(
    points: List<HrChartPoint>,
    a11yLabel: String,
    modifier: Modifier = Modifier,
    bandLow: Int? = null,
    bandHigh: Int? = null,
) {
    if (points.size < 2) return
    val accent = MaterialTheme.colorScheme.primary
    val bandTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val startDotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val axisTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val xAxisStyle = axisTextStyle.copy(fontFamily = FontFamily.Default)

    val minBpm = points.minOf { it.bpm }
    val maxBpm = points.maxOf { it.bpm }
    val midBpm = (minBpm + maxBpm) / 2
    val firstClock = points.first().clockLabel
    val lastClock = points.last().clockLabel
    val midClock = points[points.size / 2].clockLabel

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 10.dp)
            .semantics { contentDescription = a11yLabel },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CalmifySpacing.md),
        ) {
            // Y-axis labels — max / mid / min stacked
            Column(
                modifier = Modifier
                    .height(84.dp + 18.dp)
                    .padding(top = 4.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = maxBpm.toString(), style = axisTextStyle)
                Text(text = midBpm.toString(), style = axisTextStyle)
                Text(text = minBpm.toString(), style = axisTextStyle)
            }

            // Plot column (chart + X-axis labels)
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(84.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val rangeSpan = (maxBpm - minBpm).coerceAtLeast(1)

                        // Dotted typical-range bands (CSS lines y=32 and y=56 of viewBox 200x80,
                        // i.e. 40% and 70% of plot height — used here for hi/lo bands)
                        val bandHighY = bandHigh?.let { mapY(it, minBpm, rangeSpan, h) } ?: (h * 0.4f)
                        val bandLowY = bandLow?.let { mapY(it, minBpm, rangeSpan, h) } ?: (h * 0.7f)
                        val dashed = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
                        drawLine(
                            color = bandTint,
                            start = Offset(0f, bandHighY),
                            end = Offset(w, bandHighY),
                            strokeWidth = 1f,
                            pathEffect = dashed,
                        )
                        drawLine(
                            color = bandTint,
                            start = Offset(0f, bandLowY),
                            end = Offset(w, bandLowY),
                            strokeWidth = 1f,
                            pathEffect = dashed,
                        )

                        // Build the smoothed line path
                        val pointOffsets: List<Offset> = points.mapIndexed { index, p ->
                            val x = if (points.size == 1) 0f else w * index / (points.size - 1f)
                            val y = mapY(p.bpm, minBpm, rangeSpan, h)
                            Offset(x, y)
                        }
                        val linePath = buildSmoothPath(pointOffsets)

                        // Fill under the line
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(pointOffsets.last().x, h)
                            lineTo(pointOffsets.first().x, h)
                            close()
                        }
                        drawPath(fillPath, color = accent.copy(alpha = 0.12f))

                        // The line itself
                        drawPath(
                            linePath,
                            color = accent,
                            style = Stroke(
                                width = 2.5f.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )

                        // Start + end dots
                        drawCircle(
                            color = startDotColor,
                            radius = 3f.dp.toPx(),
                            center = pointOffsets.first(),
                        )
                        drawCircle(
                            color = accent,
                            radius = 3.5f.dp.toPx(),
                            center = pointOffsets.last(),
                        )
                    }

                    // End-point bpm pill (top-right of plot)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(CalmifyRadius.pill))
                            .background(accent)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = points.last().bpm.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = onAccent,
                            ),
                        )
                    }
                }
                // X-axis labels — start / mid / end
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(firstClock, style = xAxisStyle)
                    Text(midClock, style = xAxisStyle)
                    Text(lastClock, style = xAxisStyle)
                }
            }
        }
    }
}

/**
 * Single plotted point for [BioMiniHrChart].
 *
 * @property bpm        instantaneous heart-rate value (Y)
 * @property clockLabel pre-formatted X-axis label (e.g. "0:00", "5:00", "10:00")
 */
data class HrChartPoint(
    val bpm: Int,
    val clockLabel: String,
)

private fun mapY(bpm: Int, minBpm: Int, rangeSpan: Int, h: Float): Float {
    // Invert: higher bpm renders nearer the top.
    val t = (bpm - minBpm).toFloat() / rangeSpan
    return h - t * h
}

/** Catmull-Rom-style cubic-bezier smoothing — same visual character as the SVG sample. */
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
        // Catmull-Rom tension 0.5 -> Bezier
        val c1 = Offset(
            x = p1.x + (p2.x - p0.x) / 6f,
            y = p1.y + (p2.y - p0.y) / 6f,
        )
        val c2 = Offset(
            x = p2.x - (p3.x - p1.x) / 6f,
            y = p2.y - (p3.y - p1.y) / 6f,
        )
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
}
