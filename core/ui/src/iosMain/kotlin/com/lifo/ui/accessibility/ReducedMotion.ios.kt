package com.lifo.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun isReducedMotionEnabled(): Boolean {
    // Safe to read once on composition — iOS posts UIAccessibilityReduceMotionStatusDidChangeNotification
    // when the user toggles, but in-app reactive updates would require a notification subscriber;
    // the breath pacer reads on entry and that's sufficient for the meditation flow's lifetime.
    return remember { UIAccessibilityIsReduceMotionEnabled() }
}
