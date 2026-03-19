package com.lifo.socialui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Android actual: Material 3 Expressive HorizontalMultiBrowseCarousel.
 *
 * Peek effect, smooth transitions, rounded cards — same as Journal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun MediaCarousel(
    mediaUrls: List<String>,
    modifier: Modifier,
    onImageClick: ((Int) -> Unit)?,
) {
    if (mediaUrls.isEmpty()) return

    var showViewer by remember { mutableStateOf(false) }
    var viewerInitialPage by remember { mutableStateOf(0) }

    if (showViewer) {
        FullscreenImageViewer(
            imageUrls = mediaUrls,
            initialPage = viewerInitialPage,
            onDismiss = { showViewer = false },
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mediaUrls.size == 1) {
            // Single image: full-width, rounded
            AsyncImage(
                model = mediaUrls[0],
                contentDescription = "Post media",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onImageClick?.invoke(0) ?: run {
                            viewerInitialPage = 0
                            showViewer = true
                        }
                    },
            )
        } else {
            // Multiple images: Material 3 Expressive carousel
            val carouselState = rememberCarouselState { mediaUrls.size }

            HorizontalMultiBrowseCarousel(
                state = carouselState,
                preferredItemWidth = 240.dp,
                itemSpacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) { index ->
                AsyncImage(
                    model = mediaUrls[index],
                    contentDescription = "Post media ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(RoundedCornerShape(16.dp))
                        .clickable {
                            onImageClick?.invoke(index) ?: run {
                                viewerInitialPage = index
                                showViewer = true
                            }
                        },
                )
            }
        }
    }
}
