package com.lifo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Calmify font family — single family across the entire app.
 *
 * The CSS port at `design/biosignal/calmify.css` declares Roboto Flex (variable font)
 * for M3 Expressive parity. Until R1.4 bundles the VF binary into
 * `composeResources/font/`, this is a `FontFamily.SansSerif` placeholder
 * (renders Roboto on Android, San Francisco on iOS, system sans on Desktop/Web).
 *
 * **R1.4 will swap this for `FontFamily(Font(Res.font.RobotoFlex_VariableFont, …))`**.
 * No callsite should reference platform-specific font families directly.
 */
val CalmifyFontFamily: FontFamily = FontFamily.SansSerif

// ============================================================================
// CALMIFY M3 TYPE SCALE — 13 styles, 1:1 with calmify.css
// ----------------------------------------------------------------------------
// Display:  400 / large=57/64, medium=45/52, small=36/44      (splash, hero, big numbers)
// Headline: 600 / large=32/40, medium=28/36, small=24/32      (section titles — M3
//                                                              default is 400, Calmify
//                                                              overrides to 600 SemiBold)
// Title:    500 / large=22/28, medium=16/24, small=14/20      (card / dialog titles)
// Body:     400 / large=16/24, medium=14/20, small=12/16      (paragraphs)
// Label:    500 / large=14/20, medium=12/16, small=11/16      (buttons, chips, tabs)
//
// Letter-spacing values match M3 spec exactly.
// ============================================================================

val CalmifyTypography = Typography(
    // ---- DISPLAY ----
    displayLarge = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ---- HEADLINE (Calmify customizes to SemiBold) ----
    headlineLarge = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ---- TITLE ----
    titleLarge = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // ---- BODY ----
    bodyLarge = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // ---- LABEL ----
    labelLarge = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = CalmifyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Backward-compat alias. Existing callsites reference `Typography`.
 * Theme.kt was already using `MaterialTheme.colorScheme` consistently;
 * Typography was never wired into the theme (M3 fell back to defaults).
 * This alias preserves the public name while pointing at the new full scale.
 */
val Typography: Typography = CalmifyTypography
