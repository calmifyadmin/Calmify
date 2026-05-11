package com.lifo.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Calmify motion tokens — Material 3 standard easings.
 * Mirrors the CSS port at `design/biosignal/calmify.css`
 * (`--ease-emphasized`, `--ease-emphasized-decel`, ..., `--ease-spring-bouncy`).
 *
 * Use these everywhere over ad-hoc `CubicBezierEasing(...)` declarations.
 *
 * Source: Material Design 3 motion spec
 * - emphasized:        primary transitions (screen changes, expansive content)
 * - emphasizedDecel:   incoming elements (decelerate to rest)
 * - emphasizedAccel:   outgoing elements (accelerate off-screen)
 * - standard:          simple property animations (default for tweens)
 * - springBouncy:      playful confirmations (pulse, success haptic feedback)
 */
object CalmifyEasing {
    /** `cubic-bezier(0.2, 0.0, 0, 1.0)` — `--ease-emphasized` */
    val Emphasized: Easing      = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** `cubic-bezier(0.05, 0.7, 0.1, 1.0)` — `--ease-emphasized-decel` */
    val EmphasizedDecel: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** `cubic-bezier(0.3, 0.0, 0.8, 0.15)` — `--ease-emphasized-accel` */
    val EmphasizedAccel: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** `cubic-bezier(0.2, 0.0, 0, 1.0)` — `--ease-standard` (same as Emphasized in M3 spec) */
    val Standard: Easing        = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** `cubic-bezier(0.34, 1.56, 0.64, 1)` — `--ease-spring-bouncy` */
    val SpringBouncy: Easing    = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
}

/**
 * Calmify duration tokens — Material 3 standard durations in milliseconds.
 * Mirrors the CSS port (`--dur-short-1` ... `--dur-long-1`).
 *
 * Use these instead of ad-hoc literal `300` / `500` int values.
 *
 * Guidance:
 * - `Short1` (100ms):  micro-interactions (icon swap, ripple end)
 * - `Short2` (200ms):  hover/press state transitions, chip toggles
 * - `Medium1` (300ms): default for most property tweens
 * - `Medium2` (400ms): more deliberate content transitions
 * - `Long1` (500ms):   screen-level enter/exit, hero animations
 */
object CalmifyDuration {
    const val Short1: Int  = 100   // --dur-short-1
    const val Short2: Int  = 200   // --dur-short-2
    const val Medium1: Int = 300   // --dur-medium-1
    const val Medium2: Int = 400   // --dur-medium-2
    const val Long1: Int   = 500   // --dur-long-1
}
