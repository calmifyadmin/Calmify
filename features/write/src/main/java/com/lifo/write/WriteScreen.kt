package com.lifo.write

import ErrorBoundary
import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import java.time.ZonedDateTime

@Composable
fun LifecycleAwareComposable(
    onResume: () -> Unit = {},
    onPause: () -> Unit = {},
    onDispose: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onDispose()
        }
    }

    content()
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun WriteScreen(
    uiState: UiState,
    pagerState: PagerState,
    galleryState: GalleryState,
    moodName: () -> String,
    galleryImages: SnapshotStateList<GalleryImage> = galleryState.images,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDateTimeUpdated: (ZonedDateTime) -> Unit,
    onBackPressed: () -> Unit,
    onImageClicked: (Int) -> Unit,
    onSaveClicked: (Diary) -> Unit,
    onImageSelect: (Uri) -> Unit,
    onImageDeleteClicked: (GalleryImage) -> Unit,
    viewModel: WriteViewModel
) {
    // Usa ErrorBoundary per gestire gli errori
    ErrorBoundary {
        // Lifecycle management
        LifecycleAwareComposable(
            onResume = { Log.d("WriteScreen", "Screen resumed") },
            onPause = { Log.d("WriteScreen", "Screen paused") },
            onDispose = { Log.d("WriteScreen", "Screen disposed, cleaning up resources") }
        ) {
            // UI state for image selection
            var selectionCount by remember { mutableStateOf(0) }
            var selectedGalleryImage by remember { mutableStateOf<GalleryImage?>(null) }
            var selectedGalleryImageIndex by remember { mutableStateOf<Int?>(null) }

            // Update selection count when image selection changes
            LaunchedEffect(selectedGalleryImage) {
                selectionCount = if (selectedGalleryImage != null) 1 else 0
                selectedGalleryImageIndex = if (selectedGalleryImage != null) {
                    galleryImages.indexOf(selectedGalleryImage).takeIf { it >= 0 }
                } else {
                    null // Reset index if no image is selected
                }
            }

            // Get WriteViewModel
            val writeViewModel: WriteViewModel = viewModel()

            // Update the Mood when selecting an existing Diary
            LaunchedEffect(key1 = uiState.mood) {
                pagerState.scrollToPage(Mood.valueOf(uiState.mood.name).ordinal)
            }

            // Main content
            Scaffold(
                topBar = {
                    WriteTopBar(
                        selectedDiary = uiState.selectedDiary,
                        moodName = moodName,
                        onDeleteConfirmed = onDeleteConfirmed,
                        onBackPressed = onBackPressed,
                        onDateTimeUpdated = onDateTimeUpdated
                    )
                },
                content = { paddingValues ->
                    WriteContent(
                        uiState = uiState,
                        pagerState = pagerState,
                        galleryState = galleryState,
                        title = uiState.title,
                        onTitleChanged = onTitleChanged,
                        description = uiState.description,
                        onDescriptionChanged = onDescriptionChanged,
                        paddingValues = paddingValues,
                        onSaveClicked = onSaveClicked,
                        onImageSelect = onImageSelect,
                        onImageClicked = { selectedGalleryImage = it },
                        viewModel = writeViewModel
                    )

                    // Show dialog for image preview/editing when an image is selected
                    if (selectedGalleryImageIndex != null) {
                        Dialog(onDismissRequest = {
                            selectedGalleryImageIndex = null
                            selectedGalleryImage = null
                        }) {
                            if (selectedGalleryImageIndex != null && galleryImages.isNotEmpty()) {
                                val safeIndex = selectedGalleryImageIndex!!.coerceIn(0, galleryImages.size - 1)
                                ZoomableImagePager(
                                    galleryImages = galleryImages,
                                    pageIndex = safeIndex,
                                    onCloseClicked = { selectedGalleryImage = null },
                                    onDeleteClicked = {
                                        if (selectedGalleryImage != null) {
                                            onImageDeleteClicked(selectedGalleryImage!!)
                                            selectedGalleryImage = null
                                        }
                                    },
                                    onImageClicked = { index ->
                                        val newImage = galleryImages.getOrNull(index)
                                        selectedGalleryImage = if (selectedGalleryImage != newImage) {
                                            newImage
                                        } else {
                                            null // Deselect if the same image is already selected
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
internal fun ZoomableImagePager(
    galleryImages: List<GalleryImage>,
    pageIndex: Int,
    onCloseClicked: () -> Unit,
    onDeleteClicked: (GalleryImage) -> Unit,
    onImageClicked: (Int) -> Unit
) {
    // If no images, show a placeholder
    if (galleryImages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No images to display", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    // Otherwise, proceed normally with safe index handling
    val safePageIndex = remember(pageIndex, galleryImages.size) {
        pageIndex.coerceIn(0, (galleryImages.size - 1).coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(initialPage = safePageIndex)

    // Update pager when page index changes
    LaunchedEffect(safePageIndex, galleryImages.size) {
        if (galleryImages.isNotEmpty()) {
            pagerState.scrollToPage(safePageIndex)
        }
    }

    HorizontalPager(
        count = galleryImages.size,
        state = pagerState,
    ) { page ->
        Box(
            modifier = Modifier
                .graphicsLayer {}
                .clickable { onImageClicked(page) }
                .fillMaxSize()
        ) {
            val galleryImage = galleryImages.getOrNull(page)
            if (galleryImage != null) {
                ZoomableImage(
                    selectedGalleryImage = galleryImage,
                    onCloseClicked = onCloseClicked,
                    onDeleteClicked = { onDeleteClicked(galleryImage) },
                    isZoomEnabled = false
                )
            } else {
                // Fallback for null image
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Image not available")
                }
            }
        }
    }
}

@Composable
internal fun ZoomableImage(
    selectedGalleryImage: GalleryImage,
    onCloseClicked: () -> Unit,
    onDeleteClicked: (GalleryImage) -> Unit,
    isZoomEnabled: Boolean
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var imageLoadError by remember { mutableStateOf(false) }

    // Modifier for zoom gestures
    val zoomableModifier = if (isZoomEnabled) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.5f, 3f)
                offsetX += pan.x
                offsetY += pan.y
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(zoomableModifier)
    ) {
        SubcomposeAsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            loading = {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp)
                )
            },
            error = {
                imageLoadError = true
                Text("Failed to load image")
            },
            model = ImageRequest.Builder(LocalContext.current)
                .data(selectedGalleryImage.image.toString())
                .crossfade(true)
                .build(),
            contentScale = ContentScale.Fit,
            contentDescription = "Gallery Image"
        )
    }

    // Action buttons
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onCloseClicked) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close Icon")
            Text(text = "Close")
        }

        if (!imageLoadError) {
            Button(onClick = { onDeleteClicked(selectedGalleryImage) }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Icon")
                Text(text = "Delete")
            }
        }
    }
}