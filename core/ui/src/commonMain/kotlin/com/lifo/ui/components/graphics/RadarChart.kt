package com.lifo.ui.components.graphics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * RadarChart — Spider/web chart for wellbeing dimensions.
 *
 * Draws concentric polygons as guide rings, then fills the data polygon.
 * Each axis represents a dimension (e.g., SDT: Autonomy, Competence, Relatedness).
 */
data class RadarAxis(
    val label: String,
    val value: Float, // 0f..1f normalized
    val color: Color = Color.Unspecified,
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun RadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    fillColor: Color = Color.Unspecified,
    strokeColor: Color = Color.Unspecified,
    gridColor: Color = Color.Gray.copy(alpha = 0.2f),
    gridLevels: Int = 5,
) {
    val resolvedFill = if (fillColor == Color.Unspecified) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else fillColor
    val resolvedStroke = if (strokeColor == Color.Unspecified) MaterialTheme.colorScheme.primary else strokeColor
    val labelColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        if (axes.size < 3) return@Canvas

        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(size.width, size.height) / 2f - 40f
        val angleStep = 2.0 * PI / axes.size

        // Grid rings
        for (level in 1..gridLevels) {
            val r = radius * level / gridLevels
            val path = Path()
            for (i in axes.indices) {
                val angle = -PI / 2 + angleStep * i
                val x = centerX + (r * cos(angle)).toFloat()
                val y = centerY + (r * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, gridColor, style = Stroke(width = 1f))
        }

        // Axis lines
        for (i in axes.indices) {
            val angle = -PI / 2 + angleStep * i
            val endX = centerX + (radius * cos(angle)).toFloat()
            val endY = centerY + (radius * sin(angle)).toFloat()
            drawLine(gridColor, Offset(centerX, centerY), Offset(endX, endY), strokeWidth = 1f)
        }

        // Data polygon — fill
        val dataPath = Path()
        for (i in axes.indices) {
            val angle = -PI / 2 + angleStep * i
            val r = radius * axes[i].value.coerceIn(0f, 1f)
            val x = centerX + (r * cos(angle)).toFloat()
            val y = centerY + (r * sin(angle)).toFloat()
            if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        drawPath(dataPath, resolvedFill)
        drawPath(dataPath, resolvedStroke, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // Data points
        for (i in axes.indices) {
            val angle = -PI / 2 + angleStep * i
            val r = radius * axes[i].value.coerceIn(0f, 1f)
            val x = centerX + (r * cos(angle)).toFloat()
            val y = centerY + (r * sin(angle)).toFloat()
            val pointColor = if (axes[i].color != Color.Unspecified) axes[i].color else resolvedStroke
            drawCircle(pointColor, 5f, Offset(x, y))
        }

        // Labels
        for (i in axes.indices) {
            val angle = -PI / 2 + angleStep * i
            val labelRadius = radius + 20f
            val lx = centerX + (labelRadius * cos(angle)).toFloat()
            val ly = centerY + (labelRadius * sin(angle)).toFloat()
            val textResult = textMeasurer.measure(
                AnnotatedString(axes[i].label),
                style = TextStyle(fontSize = 11.sp, color = labelColor),
            )
            drawText(
                textResult,
                topLeft = Offset(
                    lx - textResult.size.width / 2f,
                    ly - textResult.size.height / 2f,
                ),
            )
        }
    }
}
