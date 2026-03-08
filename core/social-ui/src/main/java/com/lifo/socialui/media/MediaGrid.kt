package com.lifo.socialui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Media display using Material 3 Expressive carousel.
 *
 * - 1 image: Full-width, 16:9 aspect ratio (no carousel needed)
 * - 2+ images: HorizontalMultiBrowseCarousel with rounded corners
 *
 * Tap any image to open fullscreen viewer with pager + pinch-to-zoom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrid(
    mediaUrls: List<String>,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null,
) {
    if (mediaUrls.isEmpty()) return

    var showViewer by remember { mutableStateOf(false) }
    var viewerInitialPage by remember { mutableIntStateOf(0) }

    if (showViewer) {
        FullscreenImageViewer(
            imageUrls = mediaUrls,
            initialPage = viewerInitialPage,
            onDismiss = { showViewer = false },
        )
    }

    val openViewer: (Int) -> Unit = { index ->
        onImageClick?.invoke(index) ?: run {
            viewerInitialPage = index
            showViewer = true
        }
    }

    if (mediaUrls.size == 1) {
        // Single image: full-width, no carousel
        AsyncImage(
            model = mediaUrls[0],
            contentDescription = "Post media",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { openViewer(0) },
        )
    } else {
        // Multiple images: Material 3 Expressive carousel
        val carouselState = rememberCarouselState { mediaUrls.size }

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 240.dp,
            itemSpacing = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
        ) { index ->
            Box(
                modifier = Modifier
                    .maskClip(RoundedCornerShape(12.dp))
                    .fillMaxSize()
                    .clickable { openViewer(index) },
            ) {
                AsyncImage(
                    model = mediaUrls[index],
                    contentDescription = "Post media ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Show total count badge on the last visible item
                if (index == mediaUrls.size - 1 && mediaUrls.size > 3) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${mediaUrls.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}
