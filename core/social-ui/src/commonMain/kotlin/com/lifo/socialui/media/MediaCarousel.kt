package com.lifo.socialui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific media carousel for thread images.
 *
 * - Android: HorizontalMultiBrowseCarousel (Material 3 Expressive)
 * - Other platforms: HorizontalPager fallback
 *
 * Features:
 * - Single image: rounded card, tap to fullscreen
 * - Multiple: carousel with peek effect + page indicators
 * - Tap to open fullscreen image viewer
 */
@Composable
expect fun MediaCarousel(
    mediaUrls: List<String>,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null,
)
