package com.lifo.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== CALMIFY PALETTE ====================
// Design philosophy: warm neutral canvas, green only as signature accent.
// Green = organic sage (not neon). Surfaces = warm gray (zero green tint).
// Result: green appears ONLY on buttons, FAB, toggles, selected nav, chart accents.

// Light Theme — Organic Sage accent, warm neutral canvas
val md_theme_light_primary = Color(0xFF2E7D55)           // deep sage — confident, not loud
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFB8F0D0)  // light sage wash
val md_theme_light_onPrimaryContainer = Color(0xFF002112)
val md_theme_light_secondary = Color(0xFF5A5A5A)      // warm gray — no green
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE4DFDA) // warm stone
val md_theme_light_onSecondaryContainer = Color(0xFF1B1B18)
val md_theme_light_tertiary = Color(0xFF4D6A78)           // muted blue-slate
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFD0E8F2)
val md_theme_light_onTertiaryContainer = Color(0xFF082232)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFAFAFA)
val md_theme_light_onBackground = Color(0xFF1A1A1A)
val md_theme_light_surface = Color(0xFFFAFAFA)
val md_theme_light_onSurface = Color(0xFF1A1A1A)
val md_theme_light_surfaceVariant = Color(0xFFE2E2E2)
val md_theme_light_onSurfaceVariant = Color(0xFF454545)
val md_theme_light_outline = Color(0xFF757575)
val md_theme_light_inverseOnSurface = Color(0xFFF0F0F0)
val md_theme_light_inverseSurface = Color(0xFF303030)
val md_theme_light_inversePrimary = Color(0xFFBDBDBD)
val md_theme_light_shadow = Color(0xFF000000)
val md_theme_light_surfaceTint = Color(0xFF2E7D55)
val md_theme_light_outlineVariant = Color(0xFFCBC7C0)
val md_theme_light_scrim = Color(0xFF000000)

// Surface container colors - Light (warm neutral)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF5F5F5)
val md_theme_light_surfaceContainer = Color(0xFFEFEFEF)
val md_theme_light_surfaceContainerHigh = Color(0xFFE9E9E9)
val md_theme_light_surfaceContainerHighest = Color(0xFFE3E3E3)

// Dark Theme — Organic Sage accent, warm dark neutrals
val md_theme_dark_primary = Color(0xFF6DD4A0)             // calm sage — visible but not neon
val md_theme_dark_onPrimary = Color(0xFF003822)
val md_theme_dark_primaryContainer = Color(0xFF005233)    // deep forest
val md_theme_dark_onPrimaryContainer = Color(0xFF8AF2BE)
val md_theme_dark_secondary = Color(0xFFC6BFB8)           // warm stone
val md_theme_dark_onSecondary = Color(0xFF2A2A2A)
val md_theme_dark_secondaryContainer = Color(0xFF404040) // warm brown-gray
val md_theme_dark_onSecondaryContainer = Color(0xFFE2DBD4)
val md_theme_dark_tertiary = Color(0xFFA4C8D8)            // quiet blue
val md_theme_dark_onTertiary = Color(0xFF0A3544)
val md_theme_dark_tertiaryContainer = Color(0xFF254C5B)
val md_theme_dark_onTertiaryContainer = Color(0xFFC0E8F5)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF121212)          // true dark
val md_theme_dark_onBackground = Color(0xFFE3E1DE)        // warm white
val md_theme_dark_surface = Color(0xFF121212)
val md_theme_dark_onSurface = Color(0xFFE3E1DE)
val md_theme_dark_surfaceVariant = Color(0xFF282828)
val md_theme_dark_onSurfaceVariant = Color(0xFFC4C4C4)
val md_theme_dark_outline = Color(0xFF8E8E8E)
val md_theme_dark_inverseOnSurface = Color(0xFF2C2C2C)
val md_theme_dark_inverseSurface = Color(0xFFE3E1DE)
val md_theme_dark_inversePrimary = Color(0xFF2E7D55)
val md_theme_dark_shadow = Color(0xFF000000)
val md_theme_dark_surfaceTint = Color(0xFF6DD4A0)
val md_theme_dark_outlineVariant = Color(0xFF464543)
val md_theme_dark_scrim = Color(0xFF000000)

// Surface container colors - Dark (warm neutral)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0F0F0F)
val md_theme_dark_surfaceContainerLow = Color(0xFF1A1A1A)
val md_theme_dark_surfaceContainer = Color(0xFF1E1E1E)
val md_theme_dark_surfaceContainerHigh = Color(0xFF252525)
val md_theme_dark_surfaceContainerHighest = Color(0xFF2E2E2E)

// Seed
val seed = Color(0xFF4CAF7D)

// Sage tonal family — for data visualization (charts, chips, badges)
val SageBright = Color(0xFF8AF2BE)    // Tone 90 — light emphasis
val Sage = Color(0xFF6DD4A0)          // Tone 70 — primary data color
val SageMedium = Color(0xFF4CAF7D)    // Tone 50 — medium
val SageDim = Color(0xFF2E7D55)       // Tone 35 — subdued
val SageSoft = Color(0xFF7BB89A)      // Desaturated — secondary data color

// Legacy mood colors (used by non-dashboard features)
val NeutralColor = Color(0xFF78909C)
val HappyColor = Color(0xFFFFEE58)
val RomanticColor = Color(0xFFEC407A)
val CalmColor = Color(0xFF42A5F5)
val TenseColor = Color(0xFFFF7043)
val LonelyColor = Color(0xFF8D6E63)
val MysteriousColor = Color(0xFF26A69A)
val AngryColor = Color(0xFFEF5350)
val AwfulColor = Color(0xFF66BB6A)
val SurprisedColor = Color(0xFF29B6F6)
val DepressedColor = Color(0xFFBDBDBD)
val DisappointedColor = Color(0xFFAB47BC)
val ShamefulColor = Color(0xFF7E57C2)
val HumorousColor = Color(0xFFFFCA28)
val SuspiciousColor = Color(0xFFD4E157)
val BoredColor = Color(0xFF26C6DA)
