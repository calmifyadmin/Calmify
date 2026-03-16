package com.lifo.home.util

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils as AndroidColorUtils

/**
 * Color Utilities for Home Screen
 * Helper functions for color manipulation, contrast checking, and animations
 */
@Stable
object ColorUtils {

    // ==================== COLOR MANIPULATION ====================

    /**
     * Lighten a color by a given factor (0-1)
     */
    fun lighten(color: Color, factor: Float): Color {
        val hsl = FloatArray(3)
        AndroidColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[2] = (hsl[2] + factor).coerceIn(0f, 1f)
        return Color(AndroidColorUtils.HSLToColor(hsl))
    }

    /**
     * Darken a color by a given factor (0-1)
     */
    fun darken(color: Color, factor: Float): Color {
        val hsl = FloatArray(3)
        AndroidColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[2] = (hsl[2] - factor).coerceIn(0f, 1f)
        return Color(AndroidColorUtils.HSLToColor(hsl))
    }

    /**
     * Adjust saturation of a color
     */
    fun adjustSaturation(color: Color, factor: Float): Color {
        val hsl = FloatArray(3)
        AndroidColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = (hsl[1] * factor).coerceIn(0f, 1f)
        return Color(AndroidColorUtils.HSLToColor(hsl))
    }

    /**
     * Set alpha for a color
     */
    fun withAlpha(color: Color, alpha: Float): Color {
        return color.copy(alpha = alpha.coerceIn(0f, 1f))
    }

    /**
     * Blend two colors together
     */
    fun blend(color1: Color, color2: Color, ratio: Float): Color {
        val inverseRatio = 1 - ratio.coerceIn(0f, 1f)
        return Color(
            red = color1.red * inverseRatio + color2.red * ratio,
            green = color1.green * inverseRatio + color2.green * ratio,
            blue = color1.blue * inverseRatio + color2.blue * ratio,
            alpha = color1.alpha * inverseRatio + color2.alpha * ratio
        )
    }

    // ==================== CONTRAST & ACCESSIBILITY ====================

    /**
     * Calculate contrast ratio between two colors (WCAG)
     * Returns value between 1 and 21
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = calculateLuminance(foreground)
        val backgroundLuminance = calculateLuminance(background)

        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)

        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Calculate relative luminance of a color
     */
    private fun calculateLuminance(color: Color): Float {
        fun gammaCorrect(value: Float): Float {
            return if (value <= 0.03928f) value / 12.92f
            else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        val r = gammaCorrect(color.red)
        val g = gammaCorrect(color.green)
        val b = gammaCorrect(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /**
     * Check if contrast meets WCAG AA standard (4.5:1 for normal text)
     */
    fun meetsContrastAA(foreground: Color, background: Color): Boolean {
        return calculateContrastRatio(foreground, background) >= 4.5f
    }

    /**
     * Check if contrast meets WCAG AAA standard (7:1 for normal text)
     */
    fun meetsContrastAAA(foreground: Color, background: Color): Boolean {
        return calculateContrastRatio(foreground, background) >= 7f
    }

    /**
     * Get appropriate text color (black or white) for a background
     */
    fun getContrastingTextColor(background: Color): Color {
        val luminance = calculateLuminance(background)
        return if (luminance > 0.179f) Color.Black else Color.White
    }

    // ==================== MATERIAL3 HELPERS ====================

    /**
     * Get tonal variation of a color for Material3 surfaces
     */
    fun getTonalVariation(color: Color, tone: Int): Color {
        val hsl = FloatArray(3)
        AndroidColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[2] = tone / 100f
        return Color(AndroidColorUtils.HSLToColor(hsl))
    }

    /**
     * Create a surface container color from a primary color
     */
    fun createSurfaceContainer(primary: Color, isDark: Boolean): Color {
        return if (isDark) {
            blend(primary, Color.Black, 0.85f)
        } else {
            blend(primary, Color.White, 0.92f)
        }
    }

    /**
     * Get on-color (text/icon color) for a container
     */
    fun getOnContainerColor(containerColor: Color, scheme: ColorScheme): Color {
        val luminance = calculateLuminance(containerColor)
        return if (luminance > 0.4f) {
            scheme.onSurface
        } else {
            scheme.inverseOnSurface
        }
    }

    // ==================== ANIMATION HELPERS ====================

    /**
     * Animate color transition with Material3 timing
     */
    @Composable
    fun animateColor(
        targetValue: Color,
        label: String = "colorAnimation"
    ): State<Color> {
        return animateColorAsState(
            targetValue = targetValue,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            ),
            label = label
        )
    }

    // ==================== GRADIENT HELPERS ====================

    /**
     * Create a gradient stop list for smooth transitions
     */
    fun createGradientStops(
        startColor: Color,
        endColor: Color,
        steps: Int = 3
    ): List<Pair<Float, Color>> {
        return (0 until steps).map { index ->
            val fraction = index.toFloat() / (steps - 1)
            fraction to blend(startColor, endColor, fraction)
        }
    }

    /**
     * Create radial glow colors for badges/highlights
     */
    fun createGlowColors(baseColor: Color): List<Color> {
        return listOf(
            baseColor.copy(alpha = 0.6f),
            baseColor.copy(alpha = 0.3f),
            baseColor.copy(alpha = 0.1f),
            Color.Transparent
        )
    }

    // ==================== HEX CONVERSION ====================

    /**
     * Convert Color to hex string
     */
    fun toHexString(color: Color): String {
        val argb = color.toArgb()
        return String.format("#%08X", argb)
    }

    /**
     * Parse hex string to Color
     */
    fun fromHexString(hex: String): Color {
        val cleanHex = hex.removePrefix("#")
        val colorInt = cleanHex.toLongOrNull(16) ?: return Color.Gray
        return Color(colorInt.toInt())
    }

    // ==================== COLOR SCHEMES ====================

    /**
     * Get a harmonized color palette from a base color
     */
    fun getHarmonizedPalette(baseColor: Color): ColorPalette {
        val hsl = FloatArray(3)
        AndroidColorUtils.colorToHSL(baseColor.toArgb(), hsl)

        return ColorPalette(
            base = baseColor,
            light = getTonalVariation(baseColor, 90),
            dark = getTonalVariation(baseColor, 30),
            surface = getTonalVariation(baseColor, 95),
            onSurface = getTonalVariation(baseColor, 10),
            container = getTonalVariation(baseColor, 85),
            onContainer = getTonalVariation(baseColor, 20)
        )
    }

    data class ColorPalette(
        val base: Color,
        val light: Color,
        val dark: Color,
        val surface: Color,
        val onSurface: Color,
        val container: Color,
        val onContainer: Color
    )
}
