package com.lifo.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Calmify spacing scale — source-of-truth for paddings, margins, gaps.
 * Mirrors the CSS port at `design/biosignal/calmify.css` (`--space-xs` ... `--space-3xl`).
 *
 * Use scale values everywhere except decorative animations and chart geometry.
 * Non-scale values (e.g. `13.dp`, `20.dp`) are drift — see `.claude/THEME_DRIFT_AUDIT.md`.
 */
object CalmifySpacing {
    val xs   =  4.dp   // --space-xs
    val sm   =  8.dp   // --space-sm
    val md   = 12.dp   // --space-md
    val lg   = 16.dp   // --space-lg
    val xl   = 24.dp   // --space-xl
    val xxl  = 32.dp   // --space-2xl
    val xxxl = 48.dp   // --space-3xl  (added 2026-05-11 — Phase R1)
}

/**
 * Calmify corner-radius scale — Material 3 Expressive defaults to 28dp (`xxl`)
 * for cards. `pill` is used for buttons, chips, FAB, and segmented selectors.
 *
 * Mirrors the CSS port (`--radius-sm` ... `--radius-2xl` + `--radius-pill`).
 */
object CalmifyRadius {
    val sm   =   8.dp   // --radius-sm
    val md   =  12.dp   // --radius-md
    val lg   =  16.dp   // --radius-lg
    val xl   =  20.dp   // --radius-xl
    val xxl  =  28.dp   // --radius-2xl — M3 Expressive card default
    val pill = 999.dp   // --radius-pill (added 2026-05-11 — Phase R1)
}
