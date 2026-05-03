package com.lifo.ui.accessibility

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android implementation reads `Settings.Global.ANIMATOR_DURATION_SCALE`.
 *
 * When the user disables animations system-wide (Settings → Accessibility →
 * "Remove animations" or Developer Options → "Animator duration scale = Off"),
 * this value is `0.0f`. We treat that as the canonical "reduce motion" signal
 * because it matches what most Android users actually toggle — there is no
 * separate `isReduceMotionEnabled` until API 33+ and even then it is rarely set.
 *
 * Read once on first composition (system setting requires a settings round-trip
 * — re-reading every recomposition is wasteful and the user would have had to
 * leave the app to change the setting anyway).
 */
@Composable
actual fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}
