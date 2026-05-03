package com.lifo.ui.accessibility

import androidx.compose.runtime.Composable

/**
 * Desktop has no first-party reduced-motion API. Default to false; users can
 * opt in via app-level settings if/when desktop ships (Level 3).
 */
@Composable
actual fun isReducedMotionEnabled(): Boolean = false
