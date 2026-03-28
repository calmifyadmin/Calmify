package com.lifo.ui.components.graphics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HabitHeatMap — GitHub-style calendar heat map for habit completions.
 *
 * Shows a grid of cells (7 rows = days of week, columns = weeks).
 * Color intensity represents completion level for that day.
 */
data class HeatMapDay(
    val dayKey: String,  // "2026-03-23"
    val value: Float,    // 0f..1f (0 = no completion, 1 = fully done)
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun HabitHeatMap(
    days: List<HeatMapDay>,
    modifier: Modifier = Modifier,
    weeks: Int = 13,
    baseColor: Color = Color.Unspecified,
    emptyColor: Color = Color.Unspecified,
) {
    val resolvedBase = if (baseColor == Color.Unspecified) MaterialTheme.colorScheme.primary else baseColor
    val resolvedEmpty = if (emptyColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else emptyColor
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val dayLabels = listOf("L", "M", "M", "G", "V", "S", "D")

    // Build lookup map
    val dayMap = days.associateBy { it.dayKey }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((7 * 16 + 20).dp)
    ) {
        val cellSize = 12.dp.toPx()
        val gap = 3.dp.toPx()
        val labelWidth = 16.dp.toPx()
        val topPadding = 4.dp.toPx()

        // Day labels (Mon-Sun)
        dayLabels.forEachIndexed { i, label ->
            if (i % 2 == 0) { // Show every other label to save space
                val textResult = textMeasurer.measure(
                    AnnotatedString(label),
                    style = TextStyle(fontSize = 9.sp, color = labelColor),
                )
                drawText(
                    textResult,
                    topLeft = Offset(0f, topPadding + i * (cellSize + gap) + (cellSize - textResult.size.height) / 2f),
                )
            }
        }

        // Grid cells
        // We render `weeks` columns x 7 rows
        // Most recent day is bottom-right, going backwards
        val totalDays = weeks * 7
        for (week in 0 until weeks) {
            for (dow in 0 until 7) {
                val dayIndex = totalDays - 1 - (week * 7 + (6 - dow))
                // Calculate day key from index
                val dayEntry = if (dayIndex in days.indices) days.getOrNull(dayIndex) else null
                val value = dayEntry?.value ?: 0f

                val color = if (value <= 0f) {
                    resolvedEmpty
                } else {
                    resolvedBase.copy(alpha = 0.2f + 0.8f * value.coerceIn(0f, 1f))
                }

                val x = labelWidth + (weeks - 1 - week) * (cellSize + gap)
                val y = topPadding + dow * (cellSize + gap)

                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cellSize, cellSize),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}
