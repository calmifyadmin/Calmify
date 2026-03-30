package com.lifo.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== CALMIFY PALETTE — OBSIDIAN EDITION ====================
// Design philosophy: pure neutral canvas (zero color tint on any surface),
// ONE accent = seed sage #4CAF7D, used ONLY on:
//   buttons, FAB, toggles ON, selected nav, active chips, chart primary line.
// Everything else is achromatic gray — like Threads, YouTube, Instagram dark.

// ── ACCENT (seed) ────────────────────────────────────────────────────────────
val SeedSage         = Color(0xFF4CAF7D)   // the ONLY color in the app
val SeedSageLight    = Color(0xFF6DCA9A)   // lighter variant (light theme primary)
val SeedSageDark     = Color(0xFF3D9268)   // darker variant (pressed states)
val SeedSageContainer = Color(0xFF0D3D22)  // dark primaryContainer (dark theme)
val SeedSageContainerLight = Color(0xFFCFF0DF) // light primaryContainer


// ==================== CALMIFY PALETTE — OBSIDIAN EDITION ====================
// Design philosophy: pure grayscale canvas, zero color noise.
// Seed (#4CAF7D) = the ONE accent. Appears ONLY on:
//   primary buttons, FAB, active toggle, selected nav indicator,
//   chart accent line, progress bar, active chip border.
// Everything else: neutral gray, black, white.
// Inspired by: Threads, YouTube, Instagram dark-mode clarity.

// ─────────────────────────────────────────────
// SEED (single accent — do not add more colors)
// ─────────────────────────────────────────────
val seed = Color(0xFF4CAF7D)

// Accent tonal ramp derived from seed (for charts / badges only)
val AccentBright  = Color(0xFFA8F0CB)   // tone 90 — light tag fill
val AccentDefault = Color(0xFF4CAF7D)   // tone 55 — primary accent  ← USE THIS
val AccentDim     = Color(0xFF2E7D55)   // tone 35 — pressed state
val AccentSubtle  = Color(0xFF1A4A34)   // tone 20 — tinted container (dark only)

// ─────────────────────────────────────────────
// LIGHT THEME — crisp white / slate gray
// ─────────────────────────────────────────────

// Primary = seed accent (buttons, FAB, toggles, active selection)
val md_theme_light_primary             = Color(0xFF2E7D55)  // deep accent — confident
val md_theme_light_onPrimary           = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer    = Color(0xFFD6F5E5)  // very light tint — chips / banners
val md_theme_light_onPrimaryContainer  = Color(0xFF00391E)

// Secondary = pure neutral — NO color tint
val md_theme_light_secondary           = Color(0xFF5C5C5C)
val md_theme_light_onSecondary         = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer  = Color(0xFFEBEBEB)
val md_theme_light_onSecondaryContainer= Color(0xFF1A1A1A)

// Tertiary = neutral-blue-gray (captions, timestamps) — barely visible
val md_theme_light_tertiary            = Color(0xFF5E6673)
val md_theme_light_onTertiary          = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer   = Color(0xFFE2E4EA)
val md_theme_light_onTertiaryContainer = Color(0xFF191D24)

// Error
val md_theme_light_error               = Color(0xFFBA1A1A)
val md_theme_light_errorContainer      = Color(0xFFFFDAD6)
val md_theme_light_onError             = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer    = Color(0xFF410002)

// Canvas — pure white, no warm offset
val md_theme_light_background          = Color(0xFFFFFFFF)
val md_theme_light_onBackground        = Color(0xFF111111)
val md_theme_light_surface             = Color(0xFFFFFFFF)
val md_theme_light_onSurface           = Color(0xFF111111)
val md_theme_light_surfaceVariant      = Color(0xFFE5E5E5)
val md_theme_light_onSurfaceVariant    = Color(0xFF444444)

// Borders & overlays
val md_theme_light_outline             = Color(0xFF8A8A8A)
val md_theme_light_outlineVariant      = Color(0xFFD0D0D0)
val md_theme_light_scrim               = Color(0xFF000000)
val md_theme_light_shadow              = Color(0xFF000000)

// Inverse
val md_theme_light_inverseSurface      = Color(0xFF1E1E1E)
val md_theme_light_inverseOnSurface    = Color(0xFFF2F2F2)
val md_theme_light_inversePrimary      = Color(0xFF6DD4A0)
val md_theme_light_surfaceTint         = Color(0xFF2E7D55)

// Surface containers — pure gray ladder
val md_theme_light_surfaceContainerLowest  = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow     = Color(0xFFF7F7F7)
val md_theme_light_surfaceContainer        = Color(0xFFF0F0F0)
val md_theme_light_surfaceContainerHigh    = Color(0xFFE8E8E8)
val md_theme_light_surfaceContainerHighest = Color(0xFFE0E0E0)

// ─────────────────────────────────────────────
// DARK THEME — obsidian black / pure gray
// Reference: Threads dark, YouTube dark, IG dark
// ─────────────────────────────────────────────

// Primary = seed accent (only CTA / active states glow green)
val md_theme_dark_primary              = Color(0xFF4CAF7D)  // ← seed, full brightness
val md_theme_dark_onPrimary            = Color(0xFF00391E)
val md_theme_dark_primaryContainer     = Color(0xFF1A4A34)  // subtle tinted container
val md_theme_dark_onPrimaryContainer   = Color(0xFFA8F0CB)

// Secondary = warm-neutral-free gray
val md_theme_dark_secondary            = Color(0xFFAAAAAA)
val md_theme_dark_onSecondary          = Color(0xFF1A1A1A)
val md_theme_dark_secondaryContainer   = Color(0xFF2E2E2E)
val md_theme_dark_onSecondaryContainer = Color(0xFFDDDDDD)

// Tertiary = muted blue-gray (metadata, captions)
val md_theme_dark_tertiary             = Color(0xFF8A96A6)
val md_theme_dark_onTertiary           = Color(0xFF0D1520)
val md_theme_dark_tertiaryContainer    = Color(0xFF1E2A35)
val md_theme_dark_onTertiaryContainer  = Color(0xFFBDCAD8)

// Error
val md_theme_dark_error                = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer       = Color(0xFF93000A)
val md_theme_dark_onError              = Color(0xFF690005)
val md_theme_dark_onErrorContainer     = Color(0xFFFFDAD6)

// Canvas — true obsidian, no tint whatsoever
val md_theme_dark_background           = Color(0xFF0F0F0F)  // YouTube/Threads base
val md_theme_dark_onBackground         = Color(0xFFEEEEEE)
val md_theme_dark_surface              = Color(0xFF0F0F0F)
val md_theme_dark_onSurface            = Color(0xFFEEEEEE)
val md_theme_dark_surfaceVariant       = Color(0xFF2A2A2A)
val md_theme_dark_onSurfaceVariant     = Color(0xFFB0B0B0)

// Borders & overlays
val md_theme_dark_outline              = Color(0xFF5C5C5C)
val md_theme_dark_outlineVariant       = Color(0xFF383838)
val md_theme_dark_scrim                = Color(0xFF000000)
val md_theme_dark_shadow               = Color(0xFF000000)

// Inverse
val md_theme_dark_inverseSurface       = Color(0xFFEEEEEE)
val md_theme_dark_inverseOnSurface     = Color(0xFF1A1A1A)
val md_theme_dark_inversePrimary       = Color(0xFF2E7D55)
val md_theme_dark_surfaceTint          = Color(0xFF4CAF7D)

// Surface containers — pure gray ladder (no green contamination)
val md_theme_dark_surfaceContainerLowest  = Color(0xFF090909)
val md_theme_dark_surfaceContainerLow     = Color(0xFF141414)
val md_theme_dark_surfaceContainer        = Color(0xFF1A1A1A)  // card base (Threads-like)
val md_theme_dark_surfaceContainerHigh    = Color(0xFF222222)
val md_theme_dark_surfaceContainerHighest = Color(0xFF2E2E2E)


// ── ACCENT TONAL FAMILY — for charts, chips, badges ──────────────────────────
// Only these are derived from the seed; used sparingly for data viz.
val SageBright  = Color(0xFFB0F0D0)   // Tone 90 — very light, primaryContainer on dark
val Sage        = SeedSage            // Tone 50 — THE accent, primary data color
val SageDim     = SeedSageDark        // Tone 35 — pressed / secondary chart line
val SageAlpha12 = Color(0x1F4CAF7D)   // 12% alpha — selected row tint, ripple

// Aliases per compatibilità — mappati sui nuovi token
val SageMedium  = SeedSage            // era #4CAF7D → rimane il seed
val SageSoft    = SeedSageDark        // era desaturato → ora SageDim (#3D9268)

// ── LEGACY MOOD COLORS (unchanged — used by non-dashboard features) ───────────
val NeutralColor      = Color(0xFF78909C)
val HappyColor        = Color(0xFFFFEE58)
val RomanticColor     = Color(0xFFEC407A)
val CalmColor         = Color(0xFF42A5F5)
val TenseColor        = Color(0xFFFF7043)
val LonelyColor       = Color(0xFF8D6E63)
val MysteriousColor   = Color(0xFF26A69A)
val AngryColor        = Color(0xFFEF5350)
val AwfulColor        = Color(0xFF66BB6A)
val SurprisedColor    = Color(0xFF29B6F6)
val DepressedColor    = Color(0xFFBDBDBD)
val DisappointedColor = Color(0xFFAB47BC)
val ShamefulColor     = Color(0xFF7E57C2)
val HumorousColor     = Color(0xFFFFCA28)
val SuspiciousColor   = Color(0xFFD4E157)
val BoredColor        = Color(0xFF26C6DA)