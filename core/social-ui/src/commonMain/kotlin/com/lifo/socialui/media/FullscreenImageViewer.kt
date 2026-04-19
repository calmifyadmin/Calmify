package com.lifo.socialui.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lifo.ui.i18n.Strings
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Fullscreen image viewer with:
 * - HorizontalPager for multi-image swipe navigation
 * - Pinch-to-zoom per image (multi-touch only)
 * - Vertical drag to dismiss with alpha fade
 * - Page indicator + close button
 *
 * Key: ZoomableImage only consumes gestures when multi-touch (zoom) or when
 * already zoomed in (pan). Single-finger horizontal swipes at zoom=1 pass
 * through to HorizontalPager for page navigation.
 */
@Composable
fun FullscreenImageViewer(
    imageUrls: List<String>,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, (imageUrls.size - 1).coerceAtLeast(0)),
            pageCount = { imageUrls.size },
        )

        // Vertical drag-to-dismiss
        var dragOffsetY by remember { mutableFloatStateOf(0f) }
        val dismissThreshold = 200f
        val alpha by animateFloatAsState(
            targetValue = 1f - (dragOffsetY.absoluteValue / dismissThreshold).coerceIn(0f, 0.6f),
            animationSpec = tween(50),
            label = "dismissAlpha",
        )

        val draggableState = rememberDraggableState { delta ->
            dragOffsetY += delta
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                    .draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = {
                            if (dragOffsetY.absoluteValue > dismissThreshold) {
                                onDismiss()
                            }
                            dragOffsetY = 0f
                        },
                    ),
            ) { page ->
                ZoomableImage(
                    imageUrl = imageUrls[page],
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Strings.A11y.close),
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp),
                )
            }

            // Page indicator + count
            if (imageUrls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(imageUrls.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color.White
                                    else Color.White.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Image with pinch-to-zoom and double-tap zoom.
 *
 * Gesture strategy:
 * - Multi-touch (2+ fingers): always handle zoom + pan, consume events
 * - Single-touch when zoomed (scale > 1): handle pan, consume events
 * - Single-touch when NOT zoomed (scale == 1): DO NOT consume -> HorizontalPager handles swipe
 */
@Composable
private fun ZoomableImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            // Pinch-to-zoom: only consumes when multi-touch or when zoomed
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount >= 2) {
                            // Multi-touch: handle zoom + pan
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += panChange
                            } else {
                                offset = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        } else if (scale > 1f && pointerCount == 1) {
                            // Single-touch while zoomed: handle pan
                            val panChange = event.calculatePan()
                            offset += panChange
                            event.changes.forEach { it.consume() }
                        }
                        // Single-touch at zoom=1: don't consume -> HorizontalPager swipes
                    } while (event.changes.any { it.pressed })
                }
            }
            // Double-tap to zoom in/out
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.5f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(Strings.SharedA11y.image),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
