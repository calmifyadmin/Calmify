package com.lifo.ui.components.graphics

import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * EmotionalColorEngine — Mood-aware dynamic color palettes.
 *
 * Maps emotional states to harmonious color sets using HSL-based
 * interpolation for perceptually smooth transitions.
 */
object EmotionalColorEngine {

    enum class Mood(val hue: Float, val saturation: Float, val lightness: Float) {
        GIOIOSO(45f, 0.85f, 0.55f),
        SERENO(180f, 0.50f, 0.60f),
        ENERGICO(15f, 0.90f, 0.55f),
        RIFLESSIVO(230f, 0.40f, 0.50f),
        MALINCONICO(250f, 0.30f, 0.40f),
        ANSIOSO(30f, 0.60f, 0.45f),
        ARRABBIATO(0f, 0.80f, 0.45f),
        GRATO(140f, 0.60f, 0.50f),
        NEUTRO(210f, 0.15f, 0.55f),
    }

    fun moodToColor(mood: Mood): Color {
        return hslToColor(mood.hue, mood.saturation, mood.lightness)
    }

    /**
     * Interpolate between two mood colors (0f = from, 1f = to).
     */
    fun interpolateMoods(from: Mood, to: Mood, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        // Interpolate hue via shortest arc
        var dh = to.hue - from.hue
        if (dh > 180f) dh -= 360f
        if (dh < -180f) dh += 360f
        val h = (from.hue + dh * f + 360f) % 360f
        val s = from.saturation + (to.saturation - from.saturation) * f
        val l = from.lightness + (to.lightness - from.lightness) * f
        return hslToColor(h, s, l)
    }

    data class MoodPalette(
        val primary: Color,
        val secondary: Color,
        val accent: Color,
        val backgroundTint: Color,
    )

    fun generatePalette(mood: Mood): MoodPalette {
        val primary = hslToColor(mood.hue, mood.saturation, mood.lightness)
        val secondary = hslToColor((mood.hue + 30f) % 360f, mood.saturation * 0.7f, (mood.lightness * 1.1f).coerceAtMost(1f))
        val accent = hslToColor((mood.hue + 180f) % 360f, 0.9f, 0.55f)
        val bg = hslToColor(mood.hue, 0.15f, 0.92f)
        return MoodPalette(primary, secondary, accent, bg)
    }

    fun scoreToMood(score: Int): Mood = when (score) {
        1 -> Mood.ARRABBIATO
        2 -> Mood.ANSIOSO
        3 -> Mood.MALINCONICO
        4 -> Mood.RIFLESSIVO
        5 -> Mood.NEUTRO
        6 -> Mood.SERENO
        7 -> Mood.GRATO
        8 -> Mood.ENERGICO
        9 -> Mood.GIOIOSO
        10 -> Mood.GIOIOSO
        else -> Mood.NEUTRO
    }

    /**
     * HSL to RGB Color conversion (pure Kotlin, no platform deps).
     */
    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
        )
    }
}
