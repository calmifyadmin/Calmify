package com.lifo.ui.accessibility

import androidx.compose.runtime.Composable

/**
 * True when the user has expressed a preference for reduced motion at the
 * platform level (Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0`;
 * iOS: `UIAccessibility.isReduceMotionEnabled`; desktop/web: best-effort,
 * defaults to false).
 *
 * Composables that drive long animations (multi-second tweens, looping pulses,
 * easing-heavy transforms) should branch on this and either:
 * - Snap to end values immediately (`animationSpec = snap()`), or
 * - Clamp `tween.durationMillis` to a small constant (e.g. 200ms), or
 * - Replace ambient infinite animations with a static rendering
 *
 * The Calmify breath pacer reads this and clamps per-segment scale tweens to
 * 200ms while disabling the halo's infinite pulse — the breathing visual stays
 * present (so the user still sees the rhythm via cue+count) but stops animating.
 */
@Composable
expect fun isReducedMotionEnabled(): Boolean
