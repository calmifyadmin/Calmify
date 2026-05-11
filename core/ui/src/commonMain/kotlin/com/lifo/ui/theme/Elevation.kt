package com.lifo.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Calmify elevation tokens — Material 3 surface-tint elevation levels.
 *
 * **Important**: in Material 3 (especially dark themes), elevation is rendered
 * predominantly as a **surface tint** rather than a drop shadow. M3 Compose
 * automatically tints the surface upward through the `surfaceContainer*` ladder
 * as elevation increases.
 *
 * The `Dp` values below are the **logical** elevation levels; the actual visual
 * effect is computed by Compose via `Surface.tonalElevation` + the color scheme's
 * `surfaceContainer*` tokens (see `Color.kt`).
 *
 * The CSS port at `design/biosignal/calmify.css` declares explicit `box-shadow`
 * recipes for parity with Web — those are CSS-only equivalents of what Compose
 * achieves natively via surface tinting. **Do not port the shadow recipes into
 * Kotlin** — they would double-render with the M3 surface tint and create
 * heavier-than-intended elevation.
 *
 * Usage:
 * ```kotlin
 * Surface(tonalElevation = Elevation.Level2) { … }
 * Card(elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Level1))
 * ```
 *
 * Mapping (logical → CSS reference, for designer cross-check only):
 * - Level0 (0dp)  → `--elev-0: none`
 * - Level1 (1dp)  → `--elev-1` (small shadow, used by base cards)
 * - Level2 (3dp)  → `--elev-2` (interactive cards, hover state)
 * - Level3 (6dp)  → `--elev-3` (modals, drawers, sticky headers)
 * - Level4 (8dp)  → `--elev-4` (floating action button, pop-overs)
 * - Level5 (12dp) → `--elev-5` (dialog surfaces, full-screen overlays)
 */
object Elevation {
    val Level0 =  0.dp
    val Level1 =  1.dp
    val Level2 =  3.dp
    val Level3 =  6.dp
    val Level4 =  8.dp
    val Level5 = 12.dp
}
