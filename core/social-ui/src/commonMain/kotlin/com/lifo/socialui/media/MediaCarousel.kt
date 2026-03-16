package com.lifo.socialui.media

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

/**
 * Media carousel for thread images.
 *
 * Features:
 * - Single image: rounded card, tap to fullscreen
 * - Multiple: HorizontalPager with animated pill page indicators
 * - Parallax scale effect on page transition
 * - Tap to open fullscreen image viewer
 * - Image count badge for multiple images
 */
@Composable
fun MediaCarousel(
    mediaUrls: List<String>,
    modifier: Modifier = Modifier,
    onImageClick: ((Int) -> Unit)? = null,
) {
    if (mediaUrls.isEmpty()) return

    // Fullscreen viewer state
    var showViewer by remember { mutableStateOf(false) }
    var viewerInitialPage by remember { mutableStateOf(0) }

    if (showViewer) {
        FullscreenImageViewer(
            imageUrls = mediaUrls,
            initialPage = viewerInitialPage,
            onDismiss = { showViewer = false },
        )
    }

    Column(modifier = modifier) {
        if (mediaUrls.size == 1) {
            // Single image
            AsyncImage(
                model = mediaUrls.first(),
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
            // Multiple images with pager
            val pagerState = rememberPagerState(pageCount = { mediaUrls.size })

            Box {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                    pageSpacing = 8.dp,
                ) { page ->
                    val pageOffset = ((pagerState.currentPage - page) +
                            pagerState.currentPageOffsetFraction).absoluteValue

                    AsyncImage(
                        model = mediaUrls[page],
                        contentDescription = "Post media ${page + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 1f - (pageOffset * 0.08f)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - (pageOffset * 0.25f)
                            }
                            .clickable {
                                onImageClick?.invoke(page) ?: run {
                                    viewerInitialPage = page
                                    showViewer = true
                                }
                            },
                    )
                }

                // Image count badge
                Text(
                    text = "${pagerState.currentPage + 1}/${mediaUrls.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            // Animated pill page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(mediaUrls.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 16.dp else 6.dp,
                        animationSpec = tween(200),
                        label = "indicatorWidth",
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(6.dp)
                            .width(indicatorWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
        }
    }
}
