package com.lifo.write

import android.annotation.SuppressLint
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.firebase.annotations.PreviewApi
import com.lifo.ui.GalleryImage
import com.lifo.ui.GalleryState
import com.lifo.util.model.Diary
import com.lifo.util.model.Mood
import com.lifo.write.UiState
import com.lifo.write.WriteContent
import com.lifo.write.WriteTopBar
import java.time.ZonedDateTime

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
    var selectionCount by remember { mutableStateOf(0) }
    var selectedGalleryImage by remember { mutableStateOf<GalleryImage?>(null) }
var selectedGalleryImageIndex by remember { mutableStateOf<Int?>(null) }    // Aggiungi un effetto secondario per aggiornare la selezione quando cambia la selezione dell'immagine  (aggiungi `selectedGalleryImage` come dipendenza)

    LaunchedEffect(selectedGalleryImage) {
        selectionCount = if (selectedGalleryImage != null) 1 else 0
    }
    selectedGalleryImageIndex = if (selectedGalleryImage != null) {
        galleryImages.indexOf(selectedGalleryImage).takeIf { it >= 0 }
    } else {
        null // Questo assicura che l'indice venga resettato se non c'è immagine selezionata
    }
    val writeViewModel: WriteViewModel = viewModel()


    // Update the Mood when selecting an existing Diary
    LaunchedEffect(key1 = uiState.mood) {
        pagerState.scrollToPage(Mood.valueOf(uiState.mood.name).ordinal)
    }
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
            // Aggiunta di un'effetto secondario per aggiornare l'indice dell'immagine selezionata quando cambia selectedGalleryImage
            LaunchedEffect(selectedGalleryImage) {
                selectedGalleryImageIndex = if (selectedGalleryImage != null) {
                    galleryImages.indexOf(selectedGalleryImage).takeIf { it >= 0 }
                } else {
                    null // Questo assicura che l'indice venga resettato se non c'è immagine selezionata
                }
            }

            AnimatedVisibility(visible = selectedGalleryImageIndex != null) {
                Dialog(onDismissRequest = {
                    selectedGalleryImageIndex = null
                    selectedGalleryImage = null // Aggiungi questa linea per resettare l'immagine selezionata
                })  {
                    if (selectedGalleryImageIndex != null) {
                        // Sostituisci `initialIndex = selectedGalleryImageIndex!!` con `pageIndex = selectedGalleryImageIndex!!`
                        // per mantenere l'indice corrente aggiornato con l'immagine selezionata.
                        ZoomableImagePager(
                            galleryImages = galleryImages,
                            pageIndex = selectedGalleryImageIndex!!,
                            onCloseClicked = { selectedGalleryImage = null },
                            onDeleteClicked = { if (selectedGalleryImage != null) {
                                onImageDeleteClicked(selectedGalleryImage!!)
                                selectedGalleryImage = null
                            } },

                            onImageClicked = { index ->
                                val newImage = galleryImages.getOrNull(index) // Ottieni l'immagine o null se l'indice non è valido
                                selectedGalleryImage = if (selectedGalleryImage != newImage) {
                                    newImage
                                } else {
                                    null // Deseleziona se la stessa immagine è già selezionata
                                }
                            } // Passa il callback di click dell'immagine
                        )
                    }
                }
            }
        }
    )
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

    // Se non ci sono immagini, non inizializzare PagerState e mostra un placeholder
    if (galleryImages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Nessuna immagine da visualizzare", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    // Altrimenti, procedi come al solito
    val pagerState = rememberPagerState(initialPage = pageIndex.coerceIn(0, galleryImages.size - 1))

    LaunchedEffect(pageIndex, galleryImages.size) {
        if (galleryImages.isNotEmpty()) {
            pagerState.scrollToPage(pageIndex)
        }
    }

    HorizontalPager(
        count = galleryImages.size,
        state = pagerState,
    ) { page ->
        Box(
            modifier = Modifier
                .graphicsLayer {
                    // Aggiungi il tuo codice per la trasformazione dell'immagine
                }
                .clickable { onImageClicked(page) }
                .fillMaxSize()
        ) {
            // Il tuo codice per mostrare l'immagine
            ZoomableImage(
                selectedGalleryImage = galleryImages[page],
                onCloseClicked = onCloseClicked,
                onDeleteClicked = { galleryImage ->
                    onDeleteClicked(galleryImage)
                },
                isZoomEnabled = false
            )
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
    // La modifier per gestire i gesti di zoom e panning
    val zoomableModifier = if (isZoomEnabled) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale *= zoom
                offsetX += pan.x
                offsetY += pan.y
            }
        }
    } else Modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(zoomableModifier)
            .pointerInput(isZoomEnabled) { // Usa il flag per abilitare o disabilitare i gesti di trasformazione
                if (isZoomEnabled) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Resto del codice di trasformazione...
                    }
                }
            }
    ) {
        SubcomposeAsyncImage(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = maxOf(.5f, minOf(3f, scale)),
                    scaleY = maxOf(.5f, minOf(3f, scale)),
                    translationX = offsetX,
                    translationY = offsetY
                ),
            loading = { CircularProgressIndicator(modifier = Modifier.fillMaxSize().align(Alignment.Center).padding(8.dp)) },
            error = { Text("Failed to load image") },
            model = ImageRequest.Builder(LocalContext.current)
                .data(selectedGalleryImage.image.toString())
                .crossfade(true)
                .build(),
            contentScale = ContentScale.Fit,
            contentDescription = "Gallery Image"
        )

    }
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
        Button(onClick = { onDeleteClicked(selectedGalleryImage) }) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Icon")
            Text(text = "Delete")
        }
    }
}