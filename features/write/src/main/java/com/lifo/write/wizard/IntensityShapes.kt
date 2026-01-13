package com.lifo.write.wizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive Intensity Shapes
 *
 * Shapes che comunicano l'intensita' emotiva (0-10) attraverso la geometria.
 * Sostituiscono le emoji con forme espressive.
 *
 * Scala di Intensita':
 * - 0-2: Forme molto morbide, quasi circolari (calma, pacatezza)
 * - 3-4: Forme leggermente ondulate (tranquillita' con movimento)
 * - 5-6: Forme bilanciate con pulsazione (equilibrio dinamico)
 * - 7-8: Forme radianti con punte morbide (energia crescente)
 * - 9-10: Forme esplosive/stellari (massima intensita')
 */
object IntensityShapes {

    /**
     * Restituisce la Shape appropriata per un livello di intensita' (0-10)
     */
    fun getShapeForIntensity(intensity: Int): Shape {
        return when (intensity.coerceIn(0, 10)) {
            0 -> CircleShape()
            1 -> SoftOvalShape(squish = 0.05f)
            2 -> SoftOvalShape(squish = 0.1f)
            3 -> GentleWaveShape(waves = 3, amplitude = 0.05f)
            4 -> GentleWaveShape(waves = 4, amplitude = 0.08f)
            5 -> PulsingBlobShape(lobes = 4, lobeFactor = 0.1f)
            6 -> PulsingBlobShape(lobes = 5, lobeFactor = 0.15f)
            7 -> SoftStarShape(points = 5, innerRadius = 0.75f)
            8 -> SoftStarShape(points = 6, innerRadius = 0.7f)
            9 -> RadiantBurstShape(points = 8, innerRadius = 0.6f)
            10 -> ExplosionShape(points = 12, innerRadius = 0.5f)
            else -> CircleShape()
        }
    }

    /**
     * Colori per ogni livello di intensita'
     * Gradiente da blu calmo a rosso intenso
     */
    fun getColorForIntensity(intensity: Int): Color {
        return when (intensity.coerceIn(0, 10)) {
            0 -> Color(0xFF90CAF9)   // Blu molto chiaro
            1 -> Color(0xFF81D4FA)   // Azzurro chiaro
            2 -> Color(0xFF80DEEA)   // Ciano chiaro
            3 -> Color(0xFF80CBC4)   // Teal chiaro
            4 -> Color(0xFFA5D6A7)   // Verde chiaro
            5 -> Color(0xFFE6EE9C)   // Lime chiaro
            6 -> Color(0xFFFFE082)   // Ambra chiaro
            7 -> Color(0xFFFFCC80)   // Arancione chiaro
            8 -> Color(0xFFFFAB91)   // Arancione profondo
            9 -> Color(0xFFEF9A9A)   // Rosso chiaro
            10 -> Color(0xFFF48FB1)  // Rosa intenso
            else -> Color(0xFFE6EE9C)
        }
    }

    /**
     * Colore di sfondo (versione piu' chiara) per ogni intensita'
     */
    fun getBackgroundColorForIntensity(intensity: Int): Color {
        return getColorForIntensity(intensity).copy(alpha = 0.2f)
    }

    // ==================== SHAPE DEFINITIONS ====================

    /**
     * Cerchio perfetto - Intensita' 0 (massima calma)
     */
    class CircleShape : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY)

            path.addOval(
                androidx.compose.ui.geometry.Rect(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius
                )
            )
            return Outline.Generic(path)
        }
    }

    /**
     * Ovale morbido - Intensita' 1-2 (calma con leggero movimento)
     */
    class SoftOvalShape(private val squish: Float = 0.1f) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radiusX = min(centerX, centerY) * (1 + squish)
            val radiusY = min(centerX, centerY) * (1 - squish)

            path.addOval(
                androidx.compose.ui.geometry.Rect(
                    centerX - radiusX,
                    centerY - radiusY,
                    centerX + radiusX,
                    centerY + radiusY
                )
            )
            return Outline.Generic(path)
        }
    }

    /**
     * Forma con onde gentili - Intensita' 3-4 (tranquillita' dinamica)
     */
    class GentleWaveShape(
        private val waves: Int = 4,
        private val amplitude: Float = 0.08f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY) * 0.9f

            val points = waves * 30
            for (i in 0 until points) {
                val angle = (i * 2 * PI / points).toFloat()
                val waveOffset = amplitude * sin(angle * waves).toFloat()
                val r = radius * (1 + waveOffset)
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    /**
     * Blob pulsante - Intensita' 5-6 (equilibrio dinamico)
     */
    class PulsingBlobShape(
        private val lobes: Int = 4,
        private val lobeFactor: Float = 0.15f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY) * 0.9f

            val points = lobes * 20
            for (i in 0 until points) {
                val angle = (i * 2 * PI / points).toFloat()
                val lobeAngle = angle * lobes
                val r = radius * (1 + lobeFactor * sin(lobeAngle).toFloat())
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    /**
     * Stella morbida - Intensita' 7-8 (energia crescente)
     */
    class SoftStarShape(
        private val points: Int = 5,
        private val innerRadius: Float = 0.7f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = min(centerX, centerY) * 0.9f
            val innerRadiusValue = outerRadius * innerRadius

            val totalPoints = points * 2
            for (i in 0 until totalPoints) {
                val angle = (i * PI / points - PI / 2).toFloat()
                val radius = if (i % 2 == 0) outerRadius else innerRadiusValue
                val x = centerX + radius * cos(angle)
                val y = centerY + radius * sin(angle)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    /**
     * Burst radiante - Intensita' 9 (alta energia)
     */
    class RadiantBurstShape(
        private val points: Int = 8,
        private val innerRadius: Float = 0.6f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = min(centerX, centerY) * 0.9f
            val innerRadiusValue = outerRadius * innerRadius

            val totalPoints = points * 2
            for (i in 0 until totalPoints) {
                val angle = (i * PI / points - PI / 2).toFloat()
                val radius = if (i % 2 == 0) outerRadius else innerRadiusValue
                val x = centerX + radius * cos(angle)
                val y = centerY + radius * sin(angle)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    /**
     * Esplosione - Intensita' 10 (massima intensita')
     */
    class ExplosionShape(
        private val points: Int = 12,
        private val innerRadius: Float = 0.5f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = min(centerX, centerY) * 0.95f
            val innerRadiusValue = outerRadius * innerRadius

            val totalPoints = points * 2
            for (i in 0 until totalPoints) {
                val angle = (i * PI / points - PI / 2).toFloat()
                val radius = if (i % 2 == 0) outerRadius else innerRadiusValue
                val x = centerX + radius * cos(angle)
                val y = centerY + radius * sin(angle)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            return Outline.Generic(path)
        }
    }
}

// ==================== DRAWING UTILITIES ====================

/**
 * Disegna la forma di intensita' nel Canvas
 */
fun DrawScope.drawIntensityShape(
    intensity: Int,
    color: Color,
    center: Offset,
    radius: Float
) {
    val path = createIntensityPath(intensity, center, radius)
    drawPath(path, color)
}

/**
 * Crea il Path per una specifica intensita'
 */
private fun createIntensityPath(intensity: Int, center: Offset, radius: Float): Path {
    return when (intensity.coerceIn(0, 10)) {
        0, 1, 2 -> createCirclePath(center, radius)
        3, 4 -> createWavePath(center, radius, waves = intensity, amplitude = 0.05f + intensity * 0.01f)
        5, 6 -> createBlobPath(center, radius, lobes = intensity - 1, lobeFactor = 0.1f + (intensity - 5) * 0.05f)
        7, 8 -> createStarPath(center, radius, points = intensity - 2, innerRadius = 0.8f - (intensity - 7) * 0.05f)
        9, 10 -> createBurstPath(center, radius, points = intensity + 2, innerRadius = 0.6f - (intensity - 9) * 0.1f)
        else -> createCirclePath(center, radius)
    }
}

private fun createCirclePath(center: Offset, radius: Float): Path {
    val path = Path()
    val points = 60
    for (i in 0 until points) {
        val angle = (i * 2 * PI / points).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createWavePath(center: Offset, radius: Float, waves: Int, amplitude: Float): Path {
    val path = Path()
    val points = waves * 30
    for (i in 0 until points) {
        val angle = (i * 2 * PI / points).toFloat()
        val r = radius * (1 + amplitude * sin(angle * waves))
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createBlobPath(center: Offset, radius: Float, lobes: Int, lobeFactor: Float): Path {
    val path = Path()
    val points = lobes * 20
    for (i in 0 until points) {
        val angle = (i * 2 * PI / points).toFloat()
        val r = radius * (1 + lobeFactor * sin(angle * lobes))
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createStarPath(center: Offset, radius: Float, points: Int, innerRadius: Float): Path {
    val path = Path()
    val innerRadiusValue = radius * innerRadius
    val totalPoints = points * 2
    for (i in 0 until totalPoints) {
        val angle = (i * PI / points - PI / 2).toFloat()
        val r = if (i % 2 == 0) radius else innerRadiusValue
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createBurstPath(center: Offset, radius: Float, points: Int, innerRadius: Float): Path {
    val path = Path()
    val innerRadiusValue = radius * innerRadius
    val totalPoints = points * 2
    for (i in 0 until totalPoints) {
        val angle = (i * PI / points - PI / 2).toFloat()
        val r = if (i % 2 == 0) radius else innerRadiusValue
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
